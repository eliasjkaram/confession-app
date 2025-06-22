// Priest Verification API Server

const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
// Morgan will be replaced by pino-http
// const morgan = require('morgan');
const admin = require('firebase-admin');
const multer = require('multer');
const { v4: uuidv4 } = require('uuid');
const pino = require('pino');
const pinoHttp = require('pino-http');

// --- Configuration ---
const PORT = process.env.PORT || 3000;
const FIREBASE_STORAGE_BUCKET = process.env.FIREBASE_STORAGE_BUCKET || 'your-project-id.appspot.com'; // Ensure this is set in your env
const SERVICE_ACCOUNT_KEY_PATH = process.env.GOOGLE_APPLICATION_CREDENTIALS || './firebase-admin-key.json'; // For local dev
const LOG_LEVEL = process.env.LOG_LEVEL || 'info';

// --- Initialize Logger ---
const logger = pino({ level: LOG_LEVEL });
const httpLogger = pinoHttp({ logger });

// --- Initialize Express App ---
const app = express();

// --- Initialize Firebase Admin SDK ---
try {
  let firebaseAppOptions = {
    storageBucket: FIREBASE_STORAGE_BUCKET,
  };

  // For local development, explicitly use the service account key if path is provided and valid
  // GOOGLE_APPLICATION_CREDENTIALS env var is the standard way for ADC,
  // but explicit path can be used for local key files not set as GOOGLE_APPLICATION_CREDENTIALS.
  if (process.env.NODE_ENV !== 'production' && SERVICE_ACCOUNT_KEY_PATH.endsWith('.json')) {
    try {
        // Check if file exists, otherwise admin.credential.cert throws a less clear error
        require('fs').accessSync(SERVICE_ACCOUNT_KEY_PATH);
        firebaseAppOptions.credential = admin.credential.cert(SERVICE_ACCOUNT_KEY_PATH);
        logger.info(`Initializing Firebase Admin SDK with service account key: ${SERVICE_ACCOUNT_KEY_PATH}`);
    } catch (fe) {
        logger.warn(`Service account key file not found at ${SERVICE_ACCOUNT_KEY_PATH} or NODE_ENV is production. Attempting ADC.`);
        // Fall through to ADC if key not found or in production without explicit key path
    }
  } else if (process.env.GOOGLE_APPLICATION_CREDENTIALS) {
    // If GOOGLE_APPLICATION_CREDENTIALS is set (common in GCP environments like Cloud Run if not using service identity directly)
    logger.info(`Initializing Firebase Admin SDK using GOOGLE_APPLICATION_CREDENTIALS: ${process.env.GOOGLE_APPLICATION_CREDENTIALS}`);
    // No need to set credential explicitly, initializeApp will pick it up.
  } else {
    // Otherwise, rely on Application Default Credentials (ADC)
    // This is typical for Cloud Run when the service has an assigned IAM service account.
    logger.info('Initializing Firebase Admin SDK with Application Default Credentials (ADC).');
  }

  admin.initializeApp(firebaseAppOptions);
  logger.info('Firebase Admin SDK initialized successfully.');

} catch (error) {
  logger.fatal({ err: error }, 'FATAL: Error initializing Firebase Admin SDK');
  process.exit(1);
}

const db = admin.firestore();
const bucket = admin.storage().bucket(); // Uses the storageBucket from initializeApp

// --- Middleware ---
app.use(cors());
app.use(helmet());
app.use(httpLogger); // Replace morgan with pino-http
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// --- Multer Setup for File Uploads ---
// Configure multer for memory storage. This is suitable for small files.
// For larger files or production, consider using diskStorage or streaming directly to Firebase Storage.
const storage = multer.memoryStorage();
const upload = multer({
  storage: storage,
  limits: { fileSize: 10 * 1024 * 1024 } // Limit file size to 10MB
});

// --- API Endpoints ---

/**
 * POST /verify-priest
 * Handles new priest verification requests.
 * Expects multipart/form-data with priest details and document files.
 * Required fields: 'priestName', 'diocese', 'email'
 * Files: 'letterOfGoodStanding', 'priestIdPhoto'
 */
app.post('/verify-priest', upload.fields([
  { name: 'letterOfGoodStanding', maxCount: 1 },
  { name: 'priestIdPhoto', maxCount: 1 }
]), async (req, res) => {
  try {
    // Expect priestAuthId (Firebase UID) instead of email as primary identifier
    const { priestName, diocese, priestAuthId, email, additionalInfo } = req.body;
    const files = req.files;

    // Validate required fields
    if (!priestName || !diocese || !priestAuthId) {
      return res.status(400).send({ error: 'Missing required fields: priestName, diocese, priestAuthId (Firebase UID).' });
    }
    if (email && typeof email !== 'string') { // Email is optional but should be string if provided
        return res.status(400).send({ error: 'Invalid email format.' });
    }


    if (!files || !files.letterOfGoodStanding || !files.priestIdPhoto) {
      return res.status(400).send({ error: 'Missing required document files: letterOfGoodStanding and priestIdPhoto.' });
    }

    const requestId = uuidv4();
    const fileUploadPromises = [];
    const filePathsInStorage = {};

    // Process letterOfGoodStanding
    if (files.letterOfGoodStanding && files.letterOfGoodStanding[0]) {
      const file = files.letterOfGoodStanding[0];
      const fileName = `verification/${requestId}/letterOfGoodStanding/${file.originalname}`;
      const fileUpload = bucket.file(fileName);
      const blobStream = fileUpload.createWriteStream({
        metadata: {
          contentType: file.mimetype,
          metadata: { // Custom metadata
            priestAuthId: priestAuthId, // Use Firebase UID
            requestId: requestId,
            documentType: 'letterOfGoodStanding'
          }
        }
      });

      fileUploadPromises.push(new Promise((resolve, reject) => {
        blobStream.on('error', (error) => reject(error));
        blobStream.on('finish', () => {
          filePathsInStorage.letterOfGoodStanding = fileName;
          resolve();
        });
        blobStream.end(file.buffer);
      }));
    }

    // Process priestIdPhoto
    if (files.priestIdPhoto && files.priestIdPhoto[0]) {
      const file = files.priestIdPhoto[0];
      const fileName = `verification/${requestId}/priestIdPhoto/${file.originalname}`;
      const fileUpload = bucket.file(fileName);
      const blobStream = fileUpload.createWriteStream({
        metadata: {
          contentType: file.mimetype,
          metadata: {
            priestAuthId: priestAuthId, // Use Firebase UID
            requestId: requestId,
            documentType: 'priestIdPhoto'
          }
        }
      });

      fileUploadPromises.push(new Promise((resolve, reject) => {
        blobStream.on('error', (error) => reject(error));
        blobStream.on('finish', () => {
          filePathsInStorage.priestIdPhoto = fileName;
          resolve();
        });
        blobStream.end(file.buffer);
      }));
    }

    await Promise.all(fileUploadPromises);

    // Store metadata in Firestore
    const verificationRequestRef = db.collection('verificationRequests').doc(requestId);
    await verificationRequestRef.set({
      requestId,
      priestAuthId, // Store Firebase UID as the primary priest identifier
      priestName,
      diocese,
      email: email || null, // Store email if provided, but it's not the primary ID
      additionalInfo: additionalInfo || null,
      status: 'pending', // Initial status
      submittedAt: admin.firestore.FieldValue.serverTimestamp(),
      filePaths: filePathsInStorage
    });

    logger.info({ requestId, priestAuthId, priestName }, 'Verification request submitted successfully.');
    res.status(201).send({
      message: 'Verification request submitted successfully.',
      requestId: requestId,
      status: 'pending'
    });

  } catch (error) {
    logger.error({ err: error, body: req.body }, 'Error submitting verification request');
    res.status(500).send({ error: 'Failed to submit verification request.' });
  }
});

/**
 * GET /verification-status/:requestId
 * Allows the app or user to check the status of a verification request.
 */
app.get('/verification-status/:requestId', async (req, res) => {
  try {
    const requestId = req.params.requestId;
    if (!requestId) {
      return res.status(400).send({ error: 'Request ID is required.' });
    }

    const requestRef = db.collection('verificationRequests').doc(requestId);
    const doc = await requestRef.get();

    if (!doc.exists) {
      return res.status(404).send({ error: 'Verification request not found.' });
    }

    const data = doc.data();
    res.status(200).send({
      requestId: data.requestId,
      status: data.status,
      priestName: data.priestName,
      submittedAt: data.submittedAt,
      updatedAt: data.updatedAt || null, // Include if/when status is updated
      adminNotes: data.adminNotes || null // Include if admins add notes
    });

  } catch (error) {
    logger.error({ err: error, requestId: req.params.requestId }, 'Error fetching verification status');
    res.status(500).send({ error: 'Failed to fetch verification status.' });
  }
});

// --- Admin Endpoints (Placeholder - Requires Admin Authentication/Authorization) ---
// This is a simplified example. In a real application, you'd need robust admin authentication
// (e.g., using Firebase Auth custom claims or a separate admin user system)
// and authorization checks.

const isAdmin = async (req, res, next) => {
  const authorizationHeader = req.headers.authorization;

  if (!authorizationHeader || !authorizationHeader.startsWith('Bearer ')) {
    return res.status(401).send({ error: 'Unauthorized: No token provided or malformed header.' });
  }

  const idToken = authorizationHeader.split('Bearer ')[1];
  if (!idToken) {
    return res.status(401).send({ error: 'Unauthorized: No token provided.' });
  }

  try {
    const decodedToken = await admin.auth().verifyIdToken(idToken);
    // Check for a custom claim like 'admin: true' or 'role: "admin"'
    // For this example, we'll assume 'admin: true'
    if (decodedToken.admin === true) {
      req.user = decodedToken; // Add user (with UID, claims, etc.) to request object
      logger.info({ adminUid: decodedToken.uid, path: req.path }, 'Admin access granted.');
      return next();
    } else {
      logger.warn({ userUid: decodedToken.uid, path: req.path }, 'Admin access denied: user is not an admin.');
      return res.status(403).send({ error: 'Forbidden: User does not have admin privileges.' });
    }
  } catch (error) {
    logger.error({ err: error, tokenProvided: idToken ? 'yes' : 'no' }, 'Error verifying admin token');
    if (error.code === 'auth/id-token-expired') {
      return res.status(401).send({ error: 'Unauthorized: Token expired.' });
    }
    return res.status(401).send({ error: 'Unauthorized: Invalid token.' });
  }
};

/**
 * PUT /admin/verify-request/:requestId
 * Allows an admin to approve or reject a verification request.
 * Expects JSON body with 'status' ('approved' or 'rejected') and optional 'adminNotes'.
 */
app.put('/admin/verify-request/:requestId', isAdmin, async (req, res) => {
  try {
    const requestId = req.params.requestId;
    const { status, adminNotes } = req.body;

    if (!requestId) {
      return res.status(400).send({ error: 'Request ID is required.' });
    }

    if (!status || !['approved', 'rejected'].includes(status)) {
      return res.status(400).send({ error: "Invalid status. Must be 'approved' or 'rejected'." });
    }

    const requestRef = db.collection('verificationRequests').doc(requestId);
    const doc = await requestRef.get();

    if (!doc.exists) {
      return res.status(404).send({ error: 'Verification request not found.' });
    }

    await requestRef.update({
      status: status,
      adminNotes: adminNotes || null,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      // Potentially, if approved, trigger other actions like setting a custom claim for the priest
    });

    // Example: If approved, set a custom claim for the priest's Firebase Auth user
    // This assumes `priestAuthId` stored in the request is the priest's Firebase Auth UID.
    const priestToVerifyAuthId = doc.data().priestAuthId; // Get the UID of the priest being verified
    if (status === 'approved' && priestToVerifyAuthId) {
      try {
        await admin.auth().setCustomUserClaims(priestToVerifyAuthId, { verifiedPriest: true });
        logger.info({ requestId, priestAuthId: priestToVerifyAuthId }, `Custom claim 'verifiedPriest: true' set for user.`);
      } catch (claimError) {
        logger.error({ err: claimError, requestId, priestAuthId: priestToVerifyAuthId }, 'Failed to set custom claim for verified priest.');
        // Decide if this failure should cause the main request to fail or just be logged.
        // For now, just logging. It might be important enough to cause a 500 error for the admin action.
        // For example: return res.status(500).send({ error: 'Request status updated, but failed to set custom claim.' });
      }
    }

    logger.info({ requestId, newStatus: status, adminUid: req.user.uid }, 'Verification request updated by admin.');
    res.status(200).send({
      message: `Verification request ${requestId} has been ${status}.`,
      requestId: requestId,
      newStatus: status
    });

  } catch (error) {
    logger.error({ err: error, requestId: req.params.requestId, adminUid: req.user?.uid }, 'Error updating verification request');
    res.status(500).send({ error: 'Failed to update verification request.' });
  }
});


// --- Generic Error Handling Middleware ---
// This should be the last middleware
// eslint-disable-next-line no-unused-vars
app.use((err, req, res, next) => {
  logger.error({ err: err, path: req.path, method: req.method }, 'Unhandled error occurred');
  res.status(500).send({ error: 'Something went wrong on the server!' });
});

// --- Start Server ---
app.listen(PORT, () => {
  logger.info(`Server is running on port ${PORT}`);
});

module.exports = app; // For potential testing with Supertest

// Priest Verification API Server
// This file will contain the Node.js Express server implementation
// for handling priest verification requests.

const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const morgan = require('morgan');
const admin = require('firebase-admin');
const multer = require('multer');
const { v4: uuidv4 } = require('uuid');

// --- Configuration ---
const PORT = process.env.PORT || 3000;
// Make sure to replace 'your-project-id.appspot.com' with your actual Firebase project ID
const FIREBASE_STORAGE_BUCKET = 'your-project-id.appspot.com';
// Path to your Firebase Admin SDK service account key
// Ensure this file is present on your server and the path is correct.
const SERVICE_ACCOUNT_KEY_PATH = './firebase-admin-key.json';

// --- Initialize Express App ---
const app = express();

// --- Initialize Firebase Admin SDK ---
try {
  admin.initializeApp({
    credential: admin.credential.cert(SERVICE_ACCOUNT_KEY_PATH),
    storageBucket: FIREBASE_STORAGE_BUCKET
  });
  console.log('Firebase Admin SDK initialized successfully.');
} catch (error) {
  console.error('Error initializing Firebase Admin SDK:', error);
  process.exit(1); // Exit if Firebase Admin SDK fails to initialize
}

const db = admin.firestore();
const bucket = admin.storage().bucket();

// --- Middleware ---
app.use(cors()); // Enable Cross-Origin Resource Sharing
app.use(helmet()); // Set various HTTP headers for security
app.use(morgan('dev')); // HTTP request logger
app.use(express.json()); // Parse JSON bodies
app.use(express.urlencoded({ extended: true })); // Parse URL-encoded bodies

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
    const { priestName, diocese, email, additionalInfo } = req.body;
    const files = req.files;

    // Validate required fields
    if (!priestName || !diocese || !email) {
      return res.status(400).send({ error: 'Missing required fields: priestName, diocese, email.' });
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
            priestId: email, // Assuming email is a unique identifier for the priest for now
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
            priestId: email,
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
      priestName,
      diocese,
      email,
      additionalInfo: additionalInfo || null,
      status: 'pending', // Initial status
      submittedAt: admin.firestore.FieldValue.serverTimestamp(),
      filePaths: filePathsInStorage,
      // Storing priestId (email) on the record for easier querying by admins if needed
      // This assumes priest's email is unique and used as their identifier in Firebase Auth
      priestAuthId: email // This should ideally be the Firebase Auth UID of the priest if they are already authenticated
    });

    res.status(201).send({
      message: 'Verification request submitted successfully.',
      requestId: requestId,
      status: 'pending'
    });

  } catch (error) {
    console.error('Error submitting verification request:', error);
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
    console.error('Error fetching verification status:', error);
    res.status(500).send({ error: 'Failed to fetch verification status.' });
  }
});

// --- Admin Endpoints (Placeholder - Requires Admin Authentication/Authorization) ---
// This is a simplified example. In a real application, you'd need robust admin authentication
// (e.g., using Firebase Auth custom claims or a separate admin user system)
// and authorization checks.

const isAdmin = async (req, res, next) => {
  // Placeholder for admin check.
  // In a real app, verify an ID token and check for admin custom claims.
  // const idToken = req.headers.authorization?.split('Bearer ')[1];
  // if (!idToken) return res.status(401).send({ error: 'Unauthorized: No token provided.' });
  // try {
  //   const decodedToken = await admin.auth().verifyIdToken(idToken);
  //   if (decodedToken.admin === true) { // Assuming 'admin' custom claim
  //     req.user = decodedToken; // Add user to request object
  //     return next();
  //   } else {
  //     return res.status(403).send({ error: 'Forbidden: User is not an admin.' });
  //   }
  // } catch (error) {
  //   console.error('Error verifying admin token:', error);
  //   return res.status(401).send({ error: 'Unauthorized: Invalid token.' });
  // }
  console.warn('Skipping admin check for PUT /admin/verify-request - THIS IS NOT SECURE FOR PRODUCTION');
  next(); // Bypassing admin check for now for easier testing without full auth setup
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
    if (status === 'approved' && doc.data().priestAuthId) {
      try {
        await admin.auth().setCustomUserClaims(doc.data().priestAuthId, { verifiedPriest: true });
        console.log(`Custom claim 'verifiedPriest: true' set for user ${doc.data().priestAuthId}`);
      } catch (claimError) {
        console.error(`Failed to set custom claim for ${doc.data().priestAuthId}:`, claimError);
        // Decide if this failure should cause the main request to fail or just be logged.
        // For now, just logging.
      }
    }


    res.status(200).send({
      message: `Verification request ${requestId} has been ${status}.`,
      requestId: requestId,
      newStatus: status
    });

  } catch (error) {
    console.error('Error updating verification request:', error);
    res.status(500).send({ error: 'Failed to update verification request.' });
  }
});


// --- Error Handling Middleware ---
app.use((err, req, res, next) => {
  console.error(err.stack);
  res.status(500).send({ error: 'Something went wrong!' });
});

// --- Start Server ---
app.listen(PORT, () => {
  console.log(`Server is running on port ${PORT}`);
});

module.exports = app; // For potential testing

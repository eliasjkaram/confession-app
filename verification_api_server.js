const express = require('express');
const admin = require('firebase-admin');
const cors = require('cors');
const helmet = require('helmet'); // For basic security headers
const morgan = require('morgan'); // HTTP request logger

// --- Firebase Admin SDK Initialization ---
// IMPORTANT: Replace with your actual service account key file path
// or ensure your hosting environment (e.g., Google Cloud Run, Functions)
// provides default credentials for Firebase Admin.
try {
    const serviceAccount = require('./firebase-admin-key.json'); // TODO: Update path
    admin.initializeApp({
        credential: admin.credential.cert(serviceAccount),
        // storageBucket: 'your-project-id.appspot.com' // Needed if manipulating Storage directly
    });
    console.log("Firebase Admin SDK initialized successfully.");
} catch (error) {
    console.error("Error initializing Firebase Admin SDK:", error.message);
    console.error("Ensure 'firebase-admin-key.json' is present and valid, or your environment provides credentials.");
    process.exit(1); // Exit if Admin SDK can't be initialized
}

const db = admin.firestore();
const app = express();

// --- Middleware ---
app.use(cors()); // Enable CORS for all routes (configure specific origins in production)
app.use(helmet()); // Set security-related HTTP headers
app.use(morgan('tiny')); // Log HTTP requests
app.use(express.json()); // Parse JSON request bodies

// --- Authentication Middleware (Example - Protect Admin Routes) ---
// In a real app, you'd protect these admin endpoints.
// This could be Firebase Auth ID token verification, or a secret API key.
// For simplicity, this example doesn't fully implement auth middleware for endpoints.
// It's CRITICAL to secure these in a production environment.
const authenticateAdmin = async (req, res, next) => {
    const idToken = req.headers.authorization?.split('Bearer ')[1];
    if (!idToken) {
        return res.status(401).send({ error: 'Unauthorized: No token provided.' });
    }
    try {
        const decodedToken = await admin.auth().verifyIdToken(idToken);
        // Additionally, check if this user is an admin (e.g., custom claim or Firestore 'admins' collection)
        const adminDoc = await db.collection('admins').doc(decodedToken.uid).get();
        if (!adminDoc.exists) {
            return res.status(403).send({ error: 'Forbidden: User is not an admin.' });
        }
        req.user = decodedToken; // Add user to request object
        next();
    } catch (error) {
        console.error('Error verifying admin token:', error);
        return res.status(403).send({ error: 'Forbidden: Invalid token.' });
    }
};
// To use this middleware: app.use('/admin', authenticateAdmin, adminRoutes);


// --- API Routes ---

// GET /pending-verifications - List all verification requests with 'pending' status
app.get('/pending-verifications', authenticateAdmin, async (req, res) => {
    try {
        const snapshot = await db.collection('verificationRequests')
                                 .where('status', '==', 'pending')
                                 .orderBy('timestamp', 'asc')
                                 .get();
        if (snapshot.empty) {
            return res.status(200).send({ message: 'No pending verification requests found.', requests: [] });
        }
        const requests = snapshot.docs.map(doc => ({ id: doc.id, ...doc.data() }));
        res.status(200).send({ requests });
    } catch (error) {
        console.error('Error fetching pending verifications:', error);
        res.status(500).send({ error: 'Failed to fetch pending verification requests.' });
    }
});

// POST /approve-verification/:requestId - Approve a verification request
app.post('/approve-verification/:requestId', authenticateAdmin, async (req, res) => {
    const { requestId } = req.params;
    if (!requestId) {
        return res.status(400).send({ error: 'Request ID is required.' });
    }

    const verificationRef = db.collection('verificationRequests').doc(requestId);
    const batch = db.batch(); // Use a batch for atomic updates

    try {
        const doc = await verificationRef.get();
        if (!doc.exists) {
            return res.status(404).send({ error: 'Verification request not found.' });
        }
        const requestData = doc.data();
        if (requestData.status !== 'pending') {
            return res.status(400).send({ error: `Request is already ${requestData.status}.` });
        }

        const priestId = requestData.priestId;
        if (!priestId) {
            return res.status(500).send({ error: 'Priest ID missing in verification request.' });
        }
        const userRef = db.collection('users').doc(priestId);

        // Update verificationRequest status
        batch.update(verificationRef, {
            status: 'approved',
            reviewedBy: req.user.uid, // Admin who reviewed
            reviewedAt: admin.firestore.FieldValue.serverTimestamp()
        });
        // Update user profile
        batch.update(userRef, {
            isPriestVerified: true,
            verificationStatus: 'approved'
        });

        await batch.commit();
        console.log(`Verification request ${requestId} for priest ${priestId} approved by ${req.user.uid}.`);
        res.status(200).send({ message: `Verification request ${requestId} approved successfully.` });

    } catch (error) {
        console.error(`Error approving verification ${requestId}:`, error);
        res.status(500).send({ error: 'Failed to approve verification request.' });
    }
});

// POST /reject-verification/:requestId - Reject a verification request
app.post('/reject-verification/:requestId', authenticateAdmin, async (req, res) => {
    const { requestId } = req.params;
    const { reason } = req.body; // Optional: reason for rejection

    if (!requestId) {
        return res.status(400).send({ error: 'Request ID is required.' });
    }

    const verificationRef = db.collection('verificationRequests').doc(requestId);
    const batch = db.batch();

    try {
        const doc = await verificationRef.get();
        if (!doc.exists) {
            return res.status(404).send({ error: 'Verification request not found.' });
        }
        const requestData = doc.data();
        if (requestData.status !== 'pending') {
            return res.status(400).send({ error: `Request is already ${requestData.status}.` });
        }

        const priestId = requestData.priestId;
         if (!priestId) {
            return res.status(500).send({ error: 'Priest ID missing in verification request.' });
        }
        const userRef = db.collection('users').doc(priestId);

        // Update verificationRequest status
        batch.update(verificationRef, {
            status: 'rejected',
            rejectionReason: reason || 'No reason provided.',
            reviewedBy: req.user.uid, // Admin who reviewed
            reviewedAt: admin.firestore.FieldValue.serverTimestamp()
        });
        // Update user profile (set isPriestVerified to false, update status)
        batch.update(userRef, {
            isPriestVerified: false,
            verificationStatus: 'rejected'
        });

        await batch.commit();
        console.log(`Verification request ${requestId} for priest ${priestId} rejected by ${req.user.uid}. Reason: ${reason}`);
        res.status(200).send({ message: `Verification request ${requestId} rejected successfully.` });

    } catch (error) {
        console.error(`Error rejecting verification ${requestId}:`, error);
        res.status(500).send({ error: 'Failed to reject verification request.' });
    }
});


// --- Server Start ---
const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
    console.log(`Verification API server listening on port ${PORT}`);
    console.log("Ensure that you have secured the admin endpoints before deploying to production!");
});

module.exports = app; // For testing or serverless deployment

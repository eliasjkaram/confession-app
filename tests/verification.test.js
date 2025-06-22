// tests/verification.test.js
const request = require('supertest');
const app = require('../verification_api_server'); // Adjust path if your app is exported differently or located elsewhere
const admin = require('firebase-admin');

// Mock Firebase Admin SDK
// We will mock specific functions as needed by the tests.
// For GET /verification-status, we need to mock db.collection().doc().get()
jest.mock('firebase-admin', () => {
  const actualFirebaseAdmin = jest.requireActual('firebase-admin'); // Get actual to mock some parts but keep others if needed

  // Mock Firestore
  const mockGet = jest.fn();
  const mockDoc = jest.fn(() => ({ get: mockGet }));
  const mockCollection = jest.fn(() => ({ doc: mockDoc }));

  // Mock Auth (if testing admin endpoints later)
  const mockVerifyIdToken = jest.fn();
  const mockSetCustomUserClaims = jest.fn();

  return {
    // Spread actualFirebaseAdmin to keep other functionalities like `firestore.FieldValue`
    ...actualFirebaseAdmin,
    initializeApp: jest.fn().mockReturnValue({
      // Return a mock app object if needed by other parts of your code
    }),
    firestore: () => ({
      collection: mockCollection,
      doc: mockDoc, // If you use db.doc() directly
      FieldValue: actualFirebaseAdmin.firestore.FieldValue, // Keep actual FieldValue
    }),
    auth: () => ({
      verifyIdToken: mockVerifyIdToken,
      setCustomUserClaims: mockSetCustomUserClaims,
    }),
    // Mock storage if needed for upload tests
    storage: () => ({
      bucket: jest.fn(() => ({
        file: jest.fn(() => ({
          createWriteStream: jest.fn(() => ({
            on: jest.fn((event, handler) => {
              if (event === 'finish') handler(); // Simulate immediate finish for tests
              return { end: jest.fn() };
            }),
            end: jest.fn(),
          })),
        })),
      })),
    }),
    // Keep other exports like credential.cert if your init logic uses it and isn't fully mocked
    credential: {
        cert: jest.fn(), // Mock cert if initializeApp is called with it during tests
    }
  };
});

describe('Verification API - /verification-status/:requestId', () => {
  // Clear mocks before each test to ensure test isolation
  beforeEach(() => {
    // Reset all mock implementations and call counts
    admin.firestore().collection().doc().get.mockReset();
    // admin.auth().verifyIdToken.mockReset(); // For admin tests later
  });

  it('should return 404 if the verification request is not found', async () => {
    // Arrange: Mock Firestore's get() to return no document
    admin.firestore().collection().doc().get.mockResolvedValue({ exists: false });

    const nonExistentRequestId = 'non-existent-id-123';

    // Act: Make the API request
    const response = await request(app).get(`/verification-status/${nonExistentRequestId}`);

    // Assert: Check for 404 status and appropriate error message
    expect(response.statusCode).toBe(404);
    expect(response.body).toHaveProperty('error', 'Verification request not found.');
    expect(admin.firestore().collection).toHaveBeenCalledWith('verificationRequests');
    expect(admin.firestore().collection().doc).toHaveBeenCalledWith(nonExistentRequestId);
  });

  it('should return 200 and the request status if the request is found', async () => {
    // Arrange: Mock Firestore's get() to return a document
    const mockRequestId = 'existing-id-456';
    const mockRequestData = {
      requestId: mockRequestId,
      priestAuthId: 'priest-uid-123',
      priestName: 'Test Priest',
      diocese: 'Test Diocese',
      status: 'pending',
      submittedAt: admin.firestore.Timestamp.now(), // Use actual Timestamp for structure
      // filePaths: { letterOfGoodStanding: 'path1', priestIdPhoto: 'path2' }
    };
    admin.firestore().collection().doc().get.mockResolvedValue({
      exists: true,
      data: () => mockRequestData,
    });

    // Act: Make the API request
    const response = await request(app).get(`/verification-status/${mockRequestId}`);

    // Assert: Check for 200 status and correct data
    expect(response.statusCode).toBe(200);
    expect(response.body).toEqual({
      requestId: mockRequestData.requestId,
      status: mockRequestData.status,
      priestName: mockRequestData.priestName,
      submittedAt: expect.any(Object), // Firestore timestamps get serialized
      updatedAt: null,
      adminNotes: null,
    });
    expect(admin.firestore().collection).toHaveBeenCalledWith('verificationRequests');
    expect(admin.firestore().collection().doc).toHaveBeenCalledWith(mockRequestId);
  });

  // Conceptual Outline for testing with Firebase Emulators (for a real test environment):
  //
  // 1. Setup:
  //    - Before running tests, ensure Firebase Emulators (Firestore, Auth, Storage) are started.
  //    - Your test setup script (e.g., jest.setup.js) would configure `admin.initializeApp`
  //      to point to these emulators (e.g., by setting FIRESTORE_EMULATOR_HOST env var).
  //    - A helper function could be used to clear emulator data before each test suite or test.
  //
  // 2. Test Case: Successful retrieval
  //    - Arrange:
  //        - Directly populate the emulated Firestore with a test verification request document.
  //          const testRequestId = 'emulator-test-id';
  //          const firestore = admin.firestore(); // Configured for emulator
  //          await firestore.collection('verificationRequests').doc(testRequestId).set({
  //            requestId: testRequestId,
  //            priestName: 'Emulator Priest',
  //            status: 'approved',
  //            // ... other fields
  //          });
  //    - Act:
  //        - const response = await request(app).get(`/verification-status/${testRequestId}`);
  //    - Assert:
  //        - expect(response.statusCode).toBe(200);
  //        - expect(response.body.status).toBe('approved');
  //
  // This approach tests the actual Firebase interaction logic against a real (emulated) Firebase environment.
  // The current tests use Jest mocks for `firebase-admin` to test the API logic in isolation from live Firebase services or emulators.
});

describe('Health Check API - /health', () => {
  it('should return 200 OK with status and timestamp', async () => {
    const response = await request(app).get('/health');

    expect(response.statusCode).toBe(200);
    expect(response.body).toHaveProperty('status', 'ok');
    expect(response.body).toHaveProperty('timestamp');
    expect(typeof response.body.timestamp).toBe('string');
    // Optional: check if timestamp is a valid ISO string
    expect(() => new Date(response.body.timestamp)).not.toThrow();
    if (process.uptime) { // Uptime might not be available in all test environments consistently
        expect(response.body).toHaveProperty('uptime');
        expect(typeof response.body.uptime).toBe('string');
    }
  });
});

// Add more describe blocks for other endpoints like POST /verify-priest and PUT /admin/verify-request
// Example for POST /verify-priest (conceptual, needs more setup for file uploads and storage mocks/emulators)
// describe('POST /verify-priest', () => {
//   it('should return 201 and request ID on successful submission', async () => {
//      // Mock admin.storage().bucket().file().createWriteStream() to simulate successful upload
//      // Mock admin.firestore().collection().doc().set() to simulate successful Firestore write
//
//      const response = await request(app)
//        .post('/verify-priest')
//        .field('priestAuthId', 'test-priest-uid')
//        .field('priestName', 'Father Test')
//        .field('diocese', 'Test Diocese')
//        .attach('letterOfGoodStanding', Buffer.from('test data'), 'letter.pdf')
//        .attach('priestIdPhoto', Buffer.from('image data'), 'photo.jpg');
//
//      expect(response.statusCode).toBe(201);
//      expect(response.body).toHaveProperty('requestId');
//      expect(response.body).toHaveProperty('status', 'pending');
//   });
// });

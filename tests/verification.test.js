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

describe('POST /verify-priest', () => {
  beforeEach(() => {
    // Clear relevant mocks before each test in this suite
    admin.firestore().collection().doc().set.mockClear();
    const mockStream = {
      on: jest.fn((event, handler) => {
        if (event === 'finish') handler(); // Simulate immediate finish
        return { end: jest.fn() }; // Return an object with an 'end' method
      }),
      end: jest.fn(),
    };
    admin.storage().bucket().file().createWriteStream.mockReturnValue(mockStream);
  });

  it('should return 201 and request details on successful submission', async () => {
    // Arrange
    admin.firestore().collection().doc().set.mockResolvedValue({}); // Simulate successful Firestore write

    // Act
    const response = await request(app)
      .post('/verify-priest')
      .field('priestAuthId', 'test-uid-12345')
      .field('priestName', 'Father Test')
      .field('diocese', 'Diocese of Testing')
      .field('email', 'father.test@example.com')
      .attach('letterOfGoodStanding', Buffer.from('pdf content'), { filename: 'letter.pdf', contentType: 'application/pdf' })
      .attach('priestIdPhoto', Buffer.from('jpg content'), { filename: 'photo.jpg', contentType: 'image/jpeg' });

    // Assert
    expect(response.statusCode).toBe(201);
    expect(response.body).toHaveProperty('message', 'Verification request submitted successfully.');
    expect(response.body).toHaveProperty('requestId');
    expect(response.body).toHaveProperty('status', 'pending');

    // Check if Firestore 'set' was called correctly
    expect(admin.firestore().collection().doc().set).toHaveBeenCalledWith(
      expect.objectContaining({
        priestAuthId: 'test-uid-12345',
        priestName: 'Father Test',
        diocese: 'Diocese of Testing',
        email: 'father.test@example.com',
        status: 'pending',
        filePaths: expect.objectContaining({
          letterOfGoodStanding: expect.stringContaining('letter.pdf'),
          priestIdPhoto: expect.stringContaining('photo.jpg'),
        }),
      })
    );

    // Check if storage methods were called (simplified check)
    expect(admin.storage().bucket().file().createWriteStream).toHaveBeenCalledTimes(2);
  });

  it('should return 400 if required fields (priestAuthId, priestName, diocese) are missing', async () => {
    const testCases = [
      { priestName: 'Test', diocese: 'Test Diocese' }, // Missing priestAuthId
      { priestAuthId: 'uid', diocese: 'Test Diocese' }, // Missing priestName
      { priestAuthId: 'uid', priestName: 'Test' },      // Missing diocese
    ];

    for (const body of testCases) {
      const response = await request(app)
        .post('/verify-priest')
        .field(body) // Send partial body
        .attach('letterOfGoodStanding', Buffer.from('pdf content'), 'letter.pdf')
        .attach('priestIdPhoto', Buffer.from('jpg content'), 'photo.jpg');

      expect(response.statusCode).toBe(400);
      expect(response.body).toHaveProperty('error', 'Missing required fields: priestName, diocese, priestAuthId (Firebase UID).');
    }
  });

  it('should return 400 if required document files are missing', async () => {
    const response = await request(app)
      .post('/verify-priest')
      .field('priestAuthId', 'test-uid-12345')
      .field('priestName', 'Father Test')
      .field('diocese', 'Diocese of Testing');
      // No files attached

    expect(response.statusCode).toBe(400);
    expect(response.body).toHaveProperty('error', 'Missing required document files: letterOfGoodStanding and priestIdPhoto.');
  });
});

describe('PUT /admin/verify-request/:requestId', () => {
  const mockRequestId = 'test-request-id-admin';
  const adminUserToken = { uid: 'admin-user-uid', admin: true }; // Decoded token for an admin
  const regularUserToken = { uid: 'regular-user-uid', admin: false }; // Decoded token for a non-admin

  beforeEach(() => {
    admin.auth().verifyIdToken.mockReset();
    admin.firestore().collection().doc().get.mockReset();
    admin.firestore().collection().doc().update.mockReset();
    admin.auth().setCustomUserClaims.mockReset();
  });

  // Admin Auth Tests
  it('should return 401 if no auth token is provided', async () => {
    const response = await request(app)
      .put(`/admin/verify-request/${mockRequestId}`)
      .send({ status: 'approved' });
    expect(response.statusCode).toBe(401);
    expect(response.body.error).toContain('No token provided');
  });

  it('should return 401 if the auth token is invalid or expired', async () => {
    admin.auth().verifyIdToken.mockRejectedValue(new Error('Invalid token'));
    const response = await request(app)
      .put(`/admin/verify-request/${mockRequestId}`)
      .set('Authorization', 'Bearer invalidtoken123')
      .send({ status: 'approved' });
    expect(response.statusCode).toBe(401);
    expect(response.body.error).toContain('Invalid token');
  });

  it('should return 403 if the user is not an admin', async () => {
    admin.auth().verifyIdToken.mockResolvedValue(regularUserToken);
    const response = await request(app)
      .put(`/admin/verify-request/${mockRequestId}`)
      .set('Authorization', 'Bearer regulartoken456')
      .send({ status: 'approved' });
    expect(response.statusCode).toBe(403);
    expect(response.body.error).toContain('User does not have admin privileges');
  });

  // Successful Update Tests
  it('should return 200 and update status to "approved" for a valid admin', async () => {
    admin.auth().verifyIdToken.mockResolvedValue(adminUserToken);
    admin.firestore().collection().doc().get.mockResolvedValue({
      exists: true,
      data: () => ({ priestAuthId: 'priest-to-verify-uid', status: 'pending' })
    });
    admin.firestore().collection().doc().update.mockResolvedValue({});
    admin.auth().setCustomUserClaims.mockResolvedValue({});

    const response = await request(app)
      .put(`/admin/verify-request/${mockRequestId}`)
      .set('Authorization', 'Bearer admintoken789')
      .send({ status: 'approved', adminNotes: 'Looks good.' });

    expect(response.statusCode).toBe(200);
    expect(response.body.newStatus).toBe('approved');
    expect(admin.firestore().collection().doc().update).toHaveBeenCalledWith(
      expect.objectContaining({ status: 'approved', adminNotes: 'Looks good.' })
    );
    expect(admin.auth().setCustomUserClaims).toHaveBeenCalledWith('priest-to-verify-uid', { verifiedPriest: true });
  });

  it('should return 200 and update status to "rejected" for a valid admin', async () => {
    admin.auth().verifyIdToken.mockResolvedValue(adminUserToken);
    admin.firestore().collection().doc().get.mockResolvedValue({
      exists: true,
      data: () => ({ priestAuthId: 'priest-to-verify-uid', status: 'pending' })
    });
    admin.firestore().collection().doc().update.mockResolvedValue({});

    const response = await request(app)
      .put(`/admin/verify-request/${mockRequestId}`)
      .set('Authorization', 'Bearer admintoken789')
      .send({ status: 'rejected', adminNotes: 'Info missing.' });

    expect(response.statusCode).toBe(200);
    expect(response.body.newStatus).toBe('rejected');
    expect(admin.firestore().collection().doc().update).toHaveBeenCalledWith(
      expect.objectContaining({ status: 'rejected', adminNotes: 'Info missing.' })
    );
    expect(admin.auth().setCustomUserClaims).not.toHaveBeenCalled();
  });

  it('should not call setCustomUserClaims if priestAuthId is missing when approving', async () => {
    admin.auth().verifyIdToken.mockResolvedValue(adminUserToken);
    admin.firestore().collection().doc().get.mockResolvedValue({
      exists: true,
      data: () => ({ status: 'pending' }) // No priestAuthId in mock data
    });
    admin.firestore().collection().doc().update.mockResolvedValue({});

    await request(app)
      .put(`/admin/verify-request/${mockRequestId}`)
      .set('Authorization', 'Bearer admintoken789')
      .send({ status: 'approved' });

    expect(admin.auth().setCustomUserClaims).not.toHaveBeenCalled();
  });


  // Validation and Logic Error Tests
  it('should return 404 if the request ID does not exist', async () => {
    admin.auth().verifyIdToken.mockResolvedValue(adminUserToken);
    admin.firestore().collection().doc().get.mockResolvedValue({ exists: false });

    const response = await request(app)
      .put(`/admin/verify-request/nonexistent${mockRequestId}`)
      .set('Authorization', 'Bearer admintoken789')
      .send({ status: 'approved' });

    expect(response.statusCode).toBe(404);
    expect(response.body.error).toBe('Verification request not found.');
  });

  it('should return 400 if the status in the request body is invalid', async () => {
    admin.auth().verifyIdToken.mockResolvedValue(adminUserToken);
    // No need to mock Firestore get for this validation error, as it's checked before DB interaction

    const response = await request(app)
      .put(`/admin/verify-request/${mockRequestId}`)
      .set('Authorization', 'Bearer admintoken789')
      .send({ status: 'invalid-status-value' });

    expect(response.statusCode).toBe(400);
    expect(response.body.error).toBe("Invalid status. Must be 'approved' or 'rejected'.");
  });

  it('should return 400 if status is missing in request body', async () => {
    admin.auth().verifyIdToken.mockResolvedValue(adminUserToken);
    const response = await request(app)
      .put(`/admin/verify-request/${mockRequestId}`)
      .set('Authorization', 'Bearer admintoken789')
      .send({ adminNotes: 'Some notes' }); // Missing status

    expect(response.statusCode).toBe(400);
    expect(response.body.error).toBe("Invalid status. Must be 'approved' or 'rejected'.");
  });
});

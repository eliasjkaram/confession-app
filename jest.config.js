// jest.config.js
module.exports = {
  testEnvironment: 'node',
  verbose: true,
  // Automatically clear mock calls and instances between every test
  clearMocks: true,
  // The directory where Jest should output its coverage files
  coverageDirectory: 'coverage',
  // An array of glob patterns indicating a set of files for which coverage information should be collected
  collectCoverageFrom: ['verification_api_server.js'], // Adjust if you have more source files
  // Test timeout (useful if emulators are slow to start or tests are long)
  testTimeout: 30000, // 30 seconds
  // Setup files to run before each test file (if any)
  // setupFilesAfterEnv: ['./tests/setupTests.js'], // Example if you need global setup
};

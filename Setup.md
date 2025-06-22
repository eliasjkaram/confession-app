# Confession App - Detailed Setup Guide

This comprehensive guide will walk you through all the steps needed to set up and host the Confession App with all production enhancements. Follow each section carefully to ensure proper configuration and deployment.

## Table of Contents
1. [Development Environment Setup](#1-development-environment-setup)
2. [Firebase Project Configuration](#2-firebase-project-configuration)
3. [Google Pay Integration Setup](#3-google-pay-integration-setup)
4. [TURN Server Deployment](#4-turn-server-deployment)
5. [Priest Verification Backend Deployment](#5-priest-verification-backend-deployment)
6. [App Deployment Process](#6-app-deployment-process)
7. [Monitoring and Maintenance](#7-monitoring-and-maintenance)
8. [Troubleshooting](#8-troubleshooting)

## 1. Development Environment Setup

### 1.1 Install Android Studio

1. Download Android Studio from [developer.android.com/studio](https://developer.android.com/studio)
2. Run the installer and follow the installation wizard
3. During setup, ensure you install:
   - Android SDK
   - Android SDK Platform
   - Android Virtual Device
   - Performance (Intel HAXM)

### 1.2 Configure Android SDK

1. Open Android Studio
2. Go to Tools > SDK Manager
3. Install the following components:
   - Android SDK Platform 33 (Android 13.0)
   - Android SDK Build-Tools 33.0.0
   - Android SDK Command-line Tools
   - Android Emulator
   - Android SDK Platform-Tools

### 1.3 Set Up Project

1. Clone the Confession App repository:
   ```bash
   git clone https://github.com/eliasjkaram/confession-app.git
   ```
2. Open the project in Android Studio:
   - Select "Open an existing Android Studio project"
   - Navigate to the cloned repository and select it
3. Install required dependencies:
   - Sync the project with Gradle files
   - Resolve any dependency issues that appear

### 1.4 Configure Gradle

1. Ensure your project-level `build.gradle` includes Google services plugin:
   ```gradle
   buildscript {
       dependencies {
           classpath 'com.google.gms:google-services:4.3.15'
       }
   }
   ```
2. Verify app-level `build.gradle` includes all required dependencies:
   ```gradle
   dependencies {
       // Firebase
       implementation platform('com.google.firebase:firebase-bom:31.2.3')
       implementation 'com.google.firebase:firebase-auth-ktx'
       implementation 'com.google.firebase:firebase-firestore-ktx'
       implementation 'com.google.firebase:firebase-database-ktx'
       implementation 'com.google.firebase:firebase-storage-ktx'
       
       // WebRTC
       implementation 'org.webrtc:google-webrtc:1.0.32006'
       
       // Google Pay
       implementation 'com.google.android.gms:play-services-wallet:19.1.0'
   }
   ```

## 2. Firebase Project Configuration

### 2.1 Create Firebase Project

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Click "Add project"
3. Enter "Confession App" as the project name
4. Enable Google Analytics if desired
5. Click "Create project"

### 2.2 Register Android App

1. In the Firebase Console, click the Android icon to add an app
2. Enter your app's package name (e.g., `com.example.confessionapp`)
3. Enter app nickname (optional)
4. Enter SHA-1 signing certificate (for authentication)
   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```
5. Download the `google-services.json` file
6. Place the file in your app module directory

### 2.3 Configure Authentication

1. In Firebase Console, go to Authentication
2. Click "Get started"
3. Enable the following sign-in methods:
   - Email/Password (for priests)
   - Anonymous (for confessors)
4. Configure email templates for verification

### 2.4 Set Up Firestore Database

1. In Firebase Console, go to Firestore Database
2. Click "Create database"
3. Start in production mode
4. Choose a location closest to your users
5. Create the following collections:
   - `users`
   - `confessions`
   - `donations`
   - `verificationRequests`
   - `admins`

### 2.5 Set Up Realtime Database

1. In Firebase Console, go to Realtime Database
2. Click "Create database"
3. Start in locked mode
4. Choose a location closest to your users

### 2.6 Configure Storage

1. In Firebase Console, go to Storage
2. Click "Get started"
3. Set up storage rules
4. Choose a location closest to your users

### 2.7 Deploy Security Rules

1. For Firestore, go to Firestore Database > Rules
2. Copy and paste the contents of `firestore_rules.rules`
3. Click "Publish"
4. For Realtime Database, go to Realtime Database > Rules
5. Copy and paste the contents of `firebase_database_rules.json`
6. Click "Publish"
7. For Storage, go to Storage > Rules
8. Configure rules to allow authenticated access:
   ```
   rules_version = '2';
   service firebase.storage {
     match /b/{bucket}/o {
       match /verification/{requestId}/{documentType}/{fileName} {
         allow read: if request.auth != null && 
                      (request.auth.uid == resource.metadata.priestId || 
                       exists(/databases/$(database)/documents/admins/$(request.auth.uid)));
         allow write: if request.auth != null && 
                       request.auth.uid == request.resource.metadata.priestId;
       }
     }
   }
   ```
9. Click "Publish"

## 3. Google Pay Integration Setup

### 3.1 Create Google Pay Merchant Account

1. Go to [Google Pay Business Console](https://pay.google.com/business/console/)
2. Sign in with your Google account
3. Complete the merchant registration process
4. Verify your business information

### 3.2 Configure Payment Processor

1. Sign up for a payment processor (e.g., Stripe, Braintree)
2. Follow the processor's instructions to integrate with Google Pay
3. Obtain your merchant ID and gateway merchant ID

### 3.3 Update App Configuration

1. Open `PaymentsUtil.kt`
2. Update the following parameters:
   ```kotlin
   private fun getMerchantInfo(): JSONObject {
       return JSONObject().apply {
           put("merchantName", "Your Merchant Name")
           put("merchantId", "Your Merchant ID") // From Google Pay Business Console
       }
   }
   
   private fun gatewayTokenizationSpecification(): JSONObject {
       return JSONObject().apply {
           put("type", "PAYMENT_GATEWAY")
           put("parameters", JSONObject().apply {
               put("gateway", "Your Gateway Name") // e.g., "stripe"
               put("gatewayMerchantId", "Your Gateway Merchant ID")
           })
       }
   }
   ```
3. In `GooglePayDonationActivity.kt`, update the environment:
   ```kotlin
   private fun createPaymentsClient(context: Context): PaymentsClient {
       val walletOptions = Wallet.WalletOptions.Builder()
           .setEnvironment(WalletConstants.ENVIRONMENT_PRODUCTION) // Change from TEST to PRODUCTION
           .build()
       
       return Wallet.getPaymentsClient(context, walletOptions)
   }
   ```

## 4. TURN Server Deployment

### 4.1 Set Up Server Infrastructure

1. Provision a VPS or cloud instance (Ubuntu 22.04 LTS recommended)
   - AWS EC2, Google Cloud Compute Engine, or DigitalOcean Droplet
   - Minimum specs: 2 vCPUs, 4GB RAM, 20GB SSD
2. Ensure the server has a public IP address
3. Configure DNS to point a subdomain to your server (e.g., `turn.yourapp.com`)
4. Configure firewall to allow the following ports:
   - TCP/UDP 3478 (TURN)
   - TCP/UDP 5349 (TURN over TLS)
   - UDP 49152-65535 (TURN relay ports)

### 4.2 Install Coturn TURN Server

1. SSH into your server
2. Update package lists:
   ```bash
   sudo apt update && sudo apt upgrade -y
   ```
3. Install Coturn:
   ```bash
   sudo apt install coturn -y
   ```
4. Enable the service:
   ```bash
   sudo systemctl enable coturn
   ```

### 4.3 Configure TLS Certificates

1. Install Certbot:
   ```bash
   sudo apt install certbot -y
   ```
2. Obtain certificates:
   ```bash
   sudo certbot certonly --standalone -d turn.yourapp.com
   ```
3. Create a directory for TURN certificates:
   ```bash
   sudo mkdir -p /etc/turnserver
   ```
4. Copy certificates:
   ```bash
   sudo cp /etc/letsencrypt/live/turn.yourapp.com/fullchain.pem /etc/turnserver/
   sudo cp /etc/letsencrypt/live/turn.yourapp.com/privkey.pem /etc/turnserver/
   ```
5. Generate DH parameters:
   ```bash
   sudo openssl dhparam -out /etc/turnserver/dhparam.pem 2048
   ```

### 4.4 Configure Coturn

1. Create a backup of the default configuration:
   ```bash
   sudo cp /etc/turnserver.conf /etc/turnserver.conf.backup
   ```
2. Create a new configuration file:
   ```bash
   sudo nano /etc/turnserver.conf
   ```
3. Copy and paste the contents of `turnserver.conf` from the production enhancements
4. Update the following parameters:
   - `listening-ip`: Your server's private IP
   - `external-ip`: Your server's public IP
   - `realm`: Your domain (e.g., `turn.yourapp.com`)
   - `user`: Change username and password
   - `static-auth-secret`: Generate a secure random string
   - `cert` and `pkey`: Update paths if different

### 4.5 Start and Test TURN Server

1. Start the service:
   ```bash
   sudo systemctl start coturn
   ```
2. Check status:
   ```bash
   sudo systemctl status coturn
   ```
3. Test the TURN server:
   ```bash
   turnutils_uclient -v -t -T -u username -w password -p 3478 turn.yourapp.com
   ```

### 4.6 Update App Configuration

1. Open `WebRtcConfiguration.kt`
2. Update TURN server URLs and credentials:
   ```kotlin
   // Primary TURN server (UDP)
   iceServers.add(
       PeerConnection.IceServer.builder("turn:turn.yourapp.com:3478?transport=udp")
           .setUsername(turnUsername!!)
           .setPassword(turnPassword!!)
           .createIceServer()
   )
   
   // Backup TURN server (TCP)
   iceServers.add(
       PeerConnection.IceServer.builder("turn:turn.yourapp.com:3478?transport=tcp")
           .setUsername(turnUsername!!)
           .setPassword(turnPassword!!)
           .createIceServer()
   )
   
   // TLS TURN server
   iceServers.add(
       PeerConnection.IceServer.builder("turns:turn.yourapp.com:5349")
           .setUsername(turnUsername!!)
           .setPassword(turnPassword!!)
           .createIceServer()
   )
   ```
3. Implement credential fetching from your backend:
   ```kotlin
   private fun fetchTurnCredentials() {
       executor.execute {
           try {
               // Replace with actual API call
               val apiService = RetrofitClient.getApiService()
               val response = apiService.getTurnCredentials().execute()
               
               if (response.isSuccessful) {
                   val credentials = response.body()
                   turnUsername = credentials?.username
                   turnPassword = credentials?.password
               }
           } catch (e: Exception) {
               Log.e(TAG, "Error fetching TURN credentials", e)
           }
       }
   }
   ```

## 5. Priest Verification Backend Deployment

### 5.1 Set Up Server Infrastructure

1. Provision a server or use a serverless platform:
   - Option 1: VPS/Cloud VM (Ubuntu 22.04 LTS)
   - Option 2: Google Cloud Run (recommended)
   - Option 3: AWS Lambda with API Gateway
2. For VM option, ensure it has:
   - At least 2 vCPUs and 4GB RAM
   - Public IP address
   - Domain name configured (e.g., `api.yourapp.com`)

### 5.2 Set Up Node.js Environment (VM Option)

1. SSH into your server
2. Install Node.js and npm:
   ```bash
   curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
   sudo apt install -y nodejs
   ```
3. Install PM2 for process management:
   ```bash
   sudo npm install -g pm2
   ```
4. Create a directory for the application:
   ```bash
   mkdir -p ~/verification-api
   cd ~/verification-api
   ```

### 5.3 Deploy Verification API

1. Copy `verification_api_server.js` to your server
2. Create a `package.json` file:
   ```bash
   npm init -y
   ```
3. Install dependencies:
   ```bash
   npm install express cors helmet morgan firebase-admin multer uuid
   ```
   (Ensure your `package.json` reflects these dependencies. If you created one with `npm init -y` and then installed these, it should be correct. Otherwise, manually create/update `package.json`.)

4. Set up Firebase Admin SDK:
   - Go to Firebase Console > Project Settings > Service Accounts
   - Generate a new private key (Node.js option).
   - Save the JSON file to your server, typically in the root of your API project, as `firebase-admin-key.json` (or a name of your choice, but ensure it's gitignored if in a public repo).
5. Update the server code (`verification_api_server.js`) to initialize Firebase Admin SDK correctly:
   ```javascript
   // Initialize Firebase Admin SDK
   const admin = require('firebase-admin');
   const SERVICE_ACCOUNT_KEY_PATH = './firebase-admin-key.json'; // Or your chosen path
   const FIREBASE_STORAGE_BUCKET = 'your-project-id.appspot.com'; // **Replace with your actual bucket URL**

   try {
     admin.initializeApp({
       credential: admin.credential.cert(SERVICE_ACCOUNT_KEY_PATH),
       storageBucket: FIREBASE_STORAGE_BUCKET
     });
     console.log('Firebase Admin SDK initialized successfully.');
   } catch (error) {
     console.error('Error initializing Firebase Admin SDK:', error);
     process.exit(1);
   }
   ```
   The API server implements the following key endpoints:
    - `POST /verify-priest`: Handles submission of priest verification details and documents. Documents are uploaded to Firebase Storage, and a request record is created in Firestore.
    - `GET /verification-status/:requestId`: Allows checking the status of a submitted verification request.
    - `PUT /admin/verify-request/:requestId`: (Admin functionality) Allows updating the status of a request (e.g., to 'approved' or 'rejected'). Requires proper admin authentication in production.

6. Start the server with PM2:
   ```bash
   pm2 start verification_api_server.js --name verification-api
   pm2 save
   pm2 startup
   ```

### 5.4 Set Up HTTPS (VM Option)

1. Install Nginx:
   ```bash
   sudo apt install nginx -y
   ```
2. Configure Nginx as a reverse proxy:
   ```bash
   sudo nano /etc/nginx/sites-available/verification-api
   ```
3. Add the following configuration:
   ```
   server {
       listen 80;
       server_name api.yourapp.com;
       
       location / {
           proxy_pass http://localhost:3000;
           proxy_http_version 1.1;
           proxy_set_header Upgrade $http_upgrade;
           proxy_set_header Connection 'upgrade';
           proxy_set_header Host $host;
           proxy_cache_bypass $http_upgrade;
       }
   }
   ```
4. Enable the site:
   ```bash
   sudo ln -s /etc/nginx/sites-available/verification-api /etc/nginx/sites-enabled/
   sudo nginx -t
   sudo systemctl reload nginx
   ```
5. Set up SSL with Certbot:
   ```bash
   sudo apt install certbot python3-certbot-nginx -y
   sudo certbot --nginx -d api.yourapp.com
   ```

### 5.5 Deploy to Google Cloud Run (Alternative)

1. Install Google Cloud SDK
2. Initialize a new Node.js project:
   ```bash
   mkdir verification-api && cd verification-api
   npm init -y
   npm install express cors helmet morgan firebase-admin multer uuid
   ```
3. Copy `verification_api_server.js` to the project directory
4. Create a `Dockerfile`:
   ```
   FROM node:18-slim
   WORKDIR /usr/src/app
   COPY package*.json ./
   RUN npm install
   COPY . ./
   CMD [ "node", "verification_api_server.js" ]
   ```
5. Build and deploy to Cloud Run:
   ```bash
   gcloud builds submit --tag gcr.io/your-project-id/verification-api
   gcloud run deploy verification-api \
     --image gcr.io/your-project-id/verification-api \
     --platform managed \
     --allow-unauthenticated
   ```

### 5.6 Update App Configuration

1. Open your API client code in the app
2. Update the base URL to point to your deployed API. The specific endpoints implemented are relative to this base URL (e.g., `BASE_URL + "verify-priest"`).
   ```kotlin
   private const val BASE_URL = "https://api.yourapp.com/" // Example for VM/Nginx setup
   // or for Cloud Run
   // private const val BASE_URL = "https://verification-api-xxxx-uc.a.run.app/" // Example for Cloud Run
   ```

## 6. App Deployment Process

### 6.1 Configure App for Production

1. Update `build.gradle` to increment version code and name:
   ```gradle
   android {
       defaultConfig {
           versionCode 1
           versionName "1.0.0"
       }
   }
   ```
2. Configure ProGuard for release builds:
   ```gradle
   android {
       buildTypes {
           release {
               minifyEnabled true
               proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
           }
       }
   }
   ```
3. Add WebRTC ProGuard rules to `proguard-rules.pro`:
   ```
   # WebRTC
   -keep class org.webrtc.** { *; }
   ```

### 6.2 Create Signing Key

1. Generate a signing key:
   ```bash
   keytool -genkey -v -keystore confession-app.keystore -alias confession -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Configure signing in `build.gradle`:
   ```gradle
   android {
       signingConfigs {
           release {
               storeFile file("confession-app.keystore")
               storePassword "your-store-password"
               keyAlias "confession"
               keyPassword "your-key-password"
           }
       }
       buildTypes {
           release {
               signingConfig signingConfigs.release
           }
       }
   }
   ```
3. For CI/CD, use environment variables or a secure keystore

### 6.3 Build Release APK/Bundle

1. Build an APK:
   ```bash
   ./gradlew assembleRelease
   ```
2. Or build an Android App Bundle (recommended for Play Store):
   ```bash
   ./gradlew bundleRelease
   ```
3. The output will be in `app/build/outputs/`

### 6.4 Test Release Build

1. Install the release APK on a test device:
   ```bash
   adb install app/build/outputs/apk/release/app-release.apk
   ```
2. Verify all features work correctly:
   - Authentication
   - WebRTC audio calls
   - Google Pay donations
   - Priest verification flow

### 6.5 Publish to Google Play Store

1. Create a developer account at [play.google.com/apps/publish](https://play.google.com/apps/publish)
2. Create a new application
3. Fill in all required information:
   - App details
   - Graphics assets
   - Content rating
   - Pricing & distribution
4. Upload your AAB or APK file
5. Submit for review

## 7. Monitoring and Maintenance

### 7.1 Set Up Firebase Crashlytics

1. Add Crashlytics to your app-level `build.gradle`:
   ```gradle
   dependencies {
       implementation 'com.google.firebase:firebase-crashlytics-ktx'
       implementation 'com.google.firebase:firebase-analytics-ktx'
   }
   ```
2. Apply the Crashlytics Gradle plugin:
   ```gradle
   apply plugin: 'com.google.firebase.crashlytics'
   ```
3. Rebuild your app

### 7.2 Configure Firebase Performance Monitoring

1. Add Performance Monitoring to your app-level `build.gradle`:
   ```gradle
   dependencies {
       implementation 'com.google.firebase:firebase-perf-ktx'
   }
   ```
2. Apply the Performance Monitoring Gradle plugin:
   ```gradle
   apply plugin: 'com.google.firebase.firebase-perf'
   ```
3. Rebuild your app

### 7.3 Set Up Server Monitoring

1. For VM-based servers, install monitoring tools:
   ```bash
   sudo apt install -y prometheus node-exporter
   ```
2. For Cloud Run or serverless, use the platform's built-in monitoring
3. Set up alerts for:
   - High CPU/memory usage
   - Error rates
   - Response times

### 7.4 Database Backup

1. Set up regular Firestore backups:
   - Go to Firebase Console > Firestore > Backups
   - Configure daily backups
2. For critical data, implement additional backup solutions

### 7.5 Update Management

1. Regularly check for dependency updates:
   ```bash
   ./gradlew dependencyUpdates
   ```
2. Keep Firebase libraries updated
3. Plan for regular app updates to address security issues and add features

## 8. Troubleshooting

### 8.1 Common Firebase Issues

1. Authentication failures:
   - Verify SHA-1 fingerprint is correctly added to Firebase project
   - Check internet connectivity
   - Verify Firebase rules allow the operation

2. Database access issues:
   - Review security rules
   - Check user authentication state
   - Verify data structure matches rules requirements

### 8.2 WebRTC Connection Problems

1. ICE connection failures:
   - Verify TURN server is accessible
   - Check TURN credentials
   - Test in different network environments

2. Audio quality issues:
   - Verify audio permissions are granted
   - Check audio routing configuration
   - Test with different devices

### 8.3 Google Pay Integration Issues

1. Google Pay not available:
   - Verify device supports Google Pay
   - Check Google Play Services version
   - Test with a supported payment card

2. Payment processing failures:
   - Verify merchant account configuration
   - Check payment processor integration
   - Review Google Pay API parameters

### 8.4 Priest Verification API Issues

1. API connection failures:
   - Verify API endpoint URLs
   - Check network connectivity
   - Verify authentication tokens

2. Document upload failures:
   - Check file size limits
   - Verify storage permissions
   - Test with different file types

## Conclusion

This setup guide covers all aspects of deploying and hosting the Confession App with its production enhancements. Follow each section carefully to ensure a successful deployment. For any issues not covered in the troubleshooting section, refer to the specific documentation for each component or contact technical support.

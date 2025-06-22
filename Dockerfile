# Use an official Node.js runtime as a parent image
FROM node:18-slim

# Set the working directory in the container
WORKDIR /usr/src/app

# Copy package.json and package-lock.json (if available)
COPY package*.json ./

# Install app dependencies
# Using --only=production will skip devDependencies
RUN npm install --only=production

# Copy the rest of the application code into the container
COPY . .

# Make sure the service account key is available (see deployment notes)
# This Dockerfile assumes firebase-admin-key.json is copied or mounted if not using service account identity.
# If using a service account for the Cloud Run service, the Admin SDK can auto-discover credentials.

# The application listens on port 3000 by default (or PORT env var)
# Cloud Run will set the PORT environment variable.
# EXPOSE 3000 # Not strictly necessary for Cloud Run as it uses the PORT env var

# Define the command to run your app
CMD [ "node", "verification_api_server.js" ]

#!/bin/bash

unset AWS_ACCESS_KEY_ID
unset AWS_SECRET_ACCESS_KEY
unset AWS_SESSION_TOKEN
unset AWS_CREDENTIAL_EXPIRATION

echo "🔑 Loading AWS credentials..."
eval "$(aws configure export-credentials --format env)"

echo "🚀 Starting WeVolunteer Backend..."
./gradlew bootRun
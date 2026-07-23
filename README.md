# WeVolunteer Backend

Spring Boot REST API for the WeVolunteer ADA C#25 capstone project.

---

# Quick Start

1. Clone the repository.
2. Log in to AWS.
3. Start the backend.
4. Verify the setup.

---

# Prerequisites

Install:

- Java 21
- Git
- Docker Desktop
- AWS CLI v2
- IntelliJ IDEA (recommended)

---

# Clone the Repository

```bash
git clone https://github.com/sashavershkova/wevolunteer-backend.git
cd wevolunteer-backend
```

---

# AWS Credentials

The backend accesses shared AWS resources (DynamoDB, Cognito, etc.).

Each teammate has their own IAM (Identity and Access Management) user.

Before running the backend:

Login to AWS:

```bash
aws login
```

Export temporary credentials:

```bash
aws configure export-credentials --format env
```

Copy the generated environment variables into the terminal that will run the backend.

Verify your credentials:

```bash
aws sts get-caller-identity
```

Alternatively, use the helper script:

```bash
./run.sh
```

---

# Build the Project

Build the project:

```bash
./gradlew build
```

Run the tests:

```bash
./gradlew test
```

> Unit tests are currently being added. The CI/CD pipeline already executes the test suite automatically before building the Docker image.

---

# Run the Application

Start the backend:

```bash
./gradlew bootRun
```

Application URL:

```
http://localhost:8080
```

> Visiting `http://localhost:8080/` returns an error because the root endpoint is not implemented. Use one of the API endpoints below.

---

# Verify the Setup

Example:

```http
GET http://localhost:8080/opportunities
```

---

# Docker

Build the Docker image locally:

```bash
docker build -t wevolunteer-backend .
```

Run it:

```bash
docker run -p 8080:8080 wevolunteer-backend
```

---

# Authentication

Authentication is handled by **Amazon Cognito**.

The frontend authenticates users using the Cognito Hosted UI.

Protected backend endpoints require a valid Bearer access token.

Example:

```http
Authorization: Bearer eyJhbGc...
```

Examples of protected endpoints:

```
GET /users/me
POST /users
```

---

# Health Check

The backend exposes

```http
GET /actuator/health
```

Amazon ECS (Elastic Container Service) and the Application Load Balancer use this endpoint during deployments to verify that the application started successfully before routing traffic to it.

---

# Continuous Integration / Continuous Deployment (CI/CD)

The backend is automatically built and deployed using AWS.

Deployment workflow:

```
GitHub (main)
        ↓
AWS CodePipeline
        ↓
AWS CodeBuild
        ↓
Amazon ECR
        ↓
Amazon ECS (Fargate)
        ↓
Application Load Balancer
```

Whenever code is pushed to the `main` branch:

1. CodePipeline detects the GitHub push.
2. CodeBuild runs the Gradle build.
3. The test suite executes.
4. A Docker image is built.
5. The image is pushed to Amazon Elastic Container Registry (Amazon ECR).
6. Amazon ECS deploys the new container.
7. The Application Load Balancer verifies `/actuator/health`.
8. If deployment fails, Amazon ECS automatically rolls back to the previously running version.

---

# Architecture

```
React + Vite
        ↓
Amazon Cognito
        ↓
Spring Boot
        ↓
Amazon DynamoDB

Hosted on:
Amazon ECS (Fargate)

Container images:
Amazon ECR

Deployment:
AWS CodePipeline + AWS CodeBuild
```

---

# DynamoDB

The application uses the shared **WeVolunteer** DynamoDB table hosted in the team's AWS account.

No additional setup is required.

Please avoid modifying or deleting data created by other teammates.

When creating your own test data, include your name or initials in the IDs.

Example:

```
user-sasha-1
org-sasha-1
opp-sasha-1
```

---

# Seed Data

The shared DynamoDB table already contains sample data.

## Organizations

| ID | Name |
|----|------|
| org1 | Seattle Food Bank |
| org2 | Green City Cleanup |
| org3 | Happy Paws Shelter |

## Opportunities

| ID | Title | Organization | Status |
|----|-------|--------------|--------|
| opp1 | Food Bank Volunteer Shift | org1 | OPEN |
| opp2 | Community Meal Prep | org1 | OPEN |
| opp3 | Park Cleanup Day | org2 | OPEN |
| opp4 | Tree Planting Event | org2 | OPEN |
| opp5 | Animal Shelter Helper | org3 | OPEN |
| opp6 | Food Donation Pickup | org1 | OPEN |
| opp7 | Closed Pantry Sorting Shift | org1 | CLOSED |

## Users

| ID | Name |
|----|------|
| user1 | Aleksandra Vershkova |
| user2 | Masha |
| user3 | Luxi |
| user4 | Chelsea |
| user5 | Gandalf Grey |
| user6 | Legolas Trandulion |
| user7 | Frodo Baggins |
| user9 | Peregrin Took |
| user10 | Samwise Gamgee |

Please do not modify or delete the seeded records.

---

# Common Issues

## AccessDeniedException

Verify:

- AWS credentials are configured.
- You are logged in.
- Your IAM user belongs to the `WeVolunteerDevelopers` group.
- Region is `us-east-1`.

Check:

```bash
aws sts get-caller-identity
```

---

## Unable to Connect to DynamoDB

Verify:

- AWS credentials
- Internet connection
- Region is `us-east-1`

---

## Docker Build Fails with HTTP 429

The project uses Amazon ECR Public base images instead of Docker Hub to avoid Docker Hub rate limits during CI/CD builds.

---

# API Reference

Base URL

```
http://localhost:8080
```

---

# Volunteer Endpoints

## Get all open opportunities

```http
GET /opportunities
```

## Filter by category

```http
GET /opportunities?category=Food
```

## Filter by location

```http
GET /opportunities?location=Seattle
```

## Filter by organization

```http
GET /opportunities?organizationId=org1
```

## Filter by date range

```http
GET /opportunities?startDate=2026-07-10&endDate=2026-07-20
```

## Combined filters

```http
GET /opportunities?location=Seattle&category=Food&organizationId=org1&startDate=2026-07-10&endDate=2026-07-25
```

## Get opportunity details

```http
GET /opportunities/opp1
```

## Get user profile

```http
GET /users/user1
```

## Get logged-in user

```http
GET /users/me
```

## Get user registrations

```http
GET /users/user1/registrations
```

---

# Organization Endpoints

## Get organization profile

```http
GET /organizations/org1
```

## Get all organization opportunities

```http
GET /organizations/org1/opportunities
```

## Get OPEN opportunities

```http
GET /organizations/org1/opportunities?status=OPEN
```

## Get CLOSED opportunities

```http
GET /organizations/org1/opportunities?status=CLOSED
```

## Get opportunity registrations

```http
GET /opportunities/opp1/registrations
```

---

# Write Endpoints

## Create user

```http
POST /users
```

## Update user

```http
PATCH /users/{userId}
```

## Delete user

```http
DELETE /users/{userId}
```

## Create organization

```http
POST /organizations
```

## Update organization

```http
PATCH /organizations/{organizationId}
```

## Delete organization

```http
DELETE /organizations/{organizationId}
```

## Create opportunity

```http
POST /organizations/{organizationId}/opportunities
```

## Update opportunity

```http
PATCH /opportunities/{opportunityId}
```

## Close opportunity

```http
PATCH /opportunities/{opportunityId}/close
```

## Register for an opportunity

```http
POST /registrations
```

## Cancel registration

```http
DELETE /registrations/{userId}/{opportunityId}
```

---

# Development Guidelines

- Do not modify seeded data.
- Create your own test data using your name or initials.
- Never commit AWS credentials or secrets.
- Run

```bash
./gradlew build
```

before committing.

- If you add or modify endpoints, update this README so the documentation stays current.
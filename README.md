# WeVolunteer Backend

Spring Boot REST API for the WeVolunteer capstone project.

---

## Quick Start

1. Clone the repository.
2. Configure your AWS credentials using `aws configure`.
3. Build the project using `./gradlew build`.
4. Start the backend using `./gradlew bootRun`.
5. Verify the setup by calling `GET /opportunities`.

---

## Prerequisites

Before running the application, install:

- Java 21
- Git
- AWS CLI
- IntelliJ IDEA (recommended)

---

## Clone the Repository

```bash
git clone https://github.com/sashavershkova/wevolunteer-backend.git
cd wevolunteer-backend
```

---

## AWS Credentials

Each team member has an individual IAM user and AWS access keys (ask Sasha).

**Do not share your credentials with anyone.**

Configure them locally using the AWS CLI:

```bash
aws configure
```

When prompted, enter:

```text
AWS Access Key ID:     <provided individually>
AWS Secret Access Key: <provided individually>
Default region name:   us-east-1
Default output format: json
```

Alternatively, you can configure the credentials using environment variables:

```bash
export AWS_ACCESS_KEY_ID=<your-access-key>
export AWS_SECRET_ACCESS_KEY=<your-secret-key>
export AWS_REGION=us-east-1
```

---

## Build the Project

Build the project (this automatically downloads all required dependencies the first time):

```bash
./gradlew build
```

Run the test suite:

```bash
./gradlew test
```

> **Note:** Unit tests are currently being added.

---

## Run the Application

Start the backend:

```bash
./gradlew bootRun
```

The application will be available at:

```text
http://localhost:8080
```

---

## Verify the Setup

Open your browser or Postman and call:

```http
GET http://localhost:8080/opportunities
```

If everything is configured correctly, you should receive a JSON list of volunteer opportunities.

---

## DynamoDB

The application uses the shared **WeVolunteer** DynamoDB table hosted in Sasha's AWS account.

No additional setup is required.

Please avoid modifying or deleting data created by other teammates.

When creating your own test data, include your name or initials in the IDs, for example:

```text
user-masha-1
org-masha-1
opp-masha-1
```

This helps everyone identify their own test data and avoids accidental conflicts.

---

## Seed Data

The shared DynamoDB table already contains sample data that can be used for testing.

### Organizations

| ID | Name |
|----|------|
| org1 | Seattle Food Bank |
| org2 | Green City Cleanup |
| org3 | Happy Paws Shelter |

### Opportunities

| ID | Title | Organization | Status |
|----|-------|--------------|--------|
| opp1 | Food Bank Volunteer Shift | org1 | OPEN |
| opp2 | Community Meal Prep | org1 | OPEN |
| opp3 | Park Cleanup Day | org2 | OPEN |
| opp4 | Tree Planting Event | org2 | OPEN |
| opp5 | Animal Shelter Helper | org3 | OPEN |
| opp6 | Food Donation Pickup | org1 | OPEN |
| opp7 | Closed Pantry Sorting Shift | org1 | CLOSED |

### Users

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

> **Please do not modify or delete these seeded records.**
> Create your own users, organizations, and opportunities for testing.

---

## Common Issues

### AccessDeniedException

Verify that:

- Your AWS credentials are configured correctly.
- Your IAM user belongs to the `WeVolunteerDevelopers` group.
- Your AWS region is set to `us-east-1`.

Check your current AWS configuration:

```bash
aws configure list
```

If the issue persists, contact Sasha.

---

### Unable to Connect to DynamoDB

Verify that:

- Your AWS credentials are configured correctly.
- Your internet connection is available.
- The backend is using the correct AWS region (`us-east-1`).

---

## API Reference

Base URL:

```text
http://localhost:8080
```

---

# Volunteer Endpoints

### Get all open opportunities

```http
GET /opportunities
```

---

### Filter by category

```http
GET /opportunities?category=Food
```

---

### Filter by location

```http
GET /opportunities?location=Seattle
```

---

### Filter by organization

```http
GET /opportunities?organizationId=org1
```

---

### Filter by date range

```http
GET /opportunities?startDate=2026-07-10&endDate=2026-07-20
```

---

### Combined filters

```http
GET /opportunities?location=Seattle&category=Food&organizationId=org1&startDate=2026-07-10&endDate=2026-07-25
```

---

### Get opportunity details

```http
GET /opportunities/opp1
```

---

### Get user profile

```http
GET /users/user1
```

---

### Get user registrations

```http
GET /users/user1/registrations
```

---

# Organization Endpoints

### Get organization profile

```http
GET /organizations/org1
```

---

### Get all organization opportunities

```http
GET /organizations/org1/opportunities
```

---

### Get OPEN opportunities

```http
GET /organizations/org1/opportunities?status=OPEN
```

---

### Get CLOSED opportunities

```http
GET /organizations/org1/opportunities?status=CLOSED
```

---

### Get opportunity registrations

```http
GET /opportunities/opp1/registrations
```

---

# Write Endpoints

### Create user

```http
POST /users
```

```json
{
  "userId": "user-yourname-1",
  "name": "Your Name",
  "email": "your.email@example.com",
  "role": "VOLUNTEER"
}
```

---

### Update user

```http
PATCH /users/user-yourname-1
```

```json
{
  "name": "Updated Name",
  "email": "updated@example.com",
  "role": "VOLUNTEER"
}
```

---

### Delete user

```http
DELETE /users/user-yourname-1
```

---

### Create organization

```http
POST /organizations
```

```json
{
  "organizationId": "org-yourname-1",
  "name": "Test Organization",
  "email": "organization@example.com",
  "location": "Seattle, WA"
}
```

---

### Update organization

```http
PATCH /organizations/org-yourname-1
```

```json
{
  "name": "Updated Organization",
  "email": "updated@example.com",
  "location": "Bellevue, WA"
}
```

---

### Delete organization

```http
DELETE /organizations/org-yourname-1
```

---

### Create opportunity

```http
POST /organizations/org1/opportunities
```

```json
{
  "opportunityId": "opp-yourname-1",
  "title": "Food Bank Volunteer",
  "description": "Help sort food donations.",
  "category": "Food",
  "location": "Seattle",
  "date": "2026-08-15",
  "capacity": 10
}
```

---

### Update opportunity

```http
PATCH /opportunities/opp-yourname-1
```

```json
{
  "title": "Updated Title",
  "description": "Updated description.",
  "category": "Food",
  "location": "Seattle",
  "date": "2026-08-20",
  "capacity": 15
}
```

---

### Close opportunity

```http
PATCH /opportunities/opp-yourname-1/close
```

---

### Register for an opportunity

```http
POST /registrations
```

```json
{
  "userId": "user1",
  "opportunityId": "opp1"
}
```

---

### Cancel registration

```http
DELETE /registrations/user1/opp1
```

---

## Development Guidelines

- Do **not** modify or delete the seeded data.
- Create your own test data using your name or initials.
- Never commit AWS credentials or secrets.
- Before committing, verify the project builds successfully:

```bash
./gradlew build
```

- If you add or modify endpoints, please update this README so everyone has the latest API reference.
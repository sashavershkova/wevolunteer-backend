# WeVolunteer Backend

Spring Boot REST API for the WeVolunteer platform — a volunteer/organization matching
application built as an ADA C#25 capstone project. It exposes a JSON API backed by a
single-table Amazon DynamoDB design, authenticates callers via Amazon Cognito-issued
JWTs, stores profile/opportunity images in Amazon S3, and publishes volunteer-facing
notification emails through an SNS → SQS → Lambda → SES pipeline.

This document was rewritten by inspecting the code on `main` (commit `134770c` as of
2026-08-05) rather than carried forward from an earlier draft, so it reflects what the
application actually does today, including gaps and rough edges. Anywhere a detail could
not be confirmed from the repository or from a live, read-only inspection of this
project's AWS account, it is labeled **Not confirmable from this repo** instead of
guessed.

---

## Features

Completed capabilities, confirmed in the code on `main`:

- Amazon Cognito authentication (JWT-validated on every non-health endpoint)
- Volunteer and organization profiles (create/read/update)
- Profile images and opportunity images (pre-signed S3 upload + display URLs)
- Opportunity browse/filter/create/edit/close/reopen/delete
- Registrations and cancellation
- Favorites
- Waitlist, including automatic promotion when a registration is cancelled
- Organization-facing registration and waitlist views
- SNS → SQS → Lambda → SES email notifications
- Automated AWS deployment (CodePipeline → CodeBuild → ECR → ECS)

## Project Links

- **Recorded Demo:** [Coming soon — replace with demo URL](REPLACE_WITH_DEMO_URL)
- **Final Presentation:** [Coming soon — replace with presentation URL](REPLACE_WITH_PRESENTATION_URL)
- **Frontend Repository:** https://github.com/sashavershkova/wevolunteer-frontend
- **Backend Repository:** https://github.com/sashavershkova/wevolunteer-backend

---

## Architecture

```
React frontend (Vite)
        │  Cognito Hosted UI login → Bearer JWT
        ▼
Spring Boot backend (this repo)  ──────────────┐
        │                                       │  publishes JSON events
        ▼                                       ▼
Amazon DynamoDB (single table: "WeVolunteer")   Amazon SNS topic
        ▲                                       │
        │  pre-signed URLs                      ▼
Amazon S3 (profile + opportunity images)   Amazon SQS queue
        ▲                                       │
        │  ECS task role                        ▼
        │                              AWS Lambda (notification-lambda module)
        │                                       │
Amazon ECS (Fargate) ◄── Application Load       ▼
        ▲                Balancer          Amazon SES (sends the email)
        │
Amazon ECR (container images)
        ▲
AWS CodeBuild ◄── AWS CodePipeline ◄── GitHub (main)
```

The backend authenticates every request against a Cognito user pool's JWKS (JSON Web Key
Set), reads/writes a single DynamoDB table, issues pre-signed S3 URLs for image uploads,
and fires-and-forgets notification events to SNS. It never talks to SQS, Lambda, or SES
directly — that entire chain after SNS is downstream infrastructure and the separate
`notification-lambda` Gradle module in this repo.

---

## Tech stack

| Concern | Technology | Where |
|---|---|---|
| Language / runtime | Java 21 (Gradle toolchain) | `build.gradle` |
| Framework | Spring Boot 3.5.16 | `build.gradle` |
| Build | Gradle 8.14.5 (wrapper) | `gradle/wrapper/gradle-wrapper.properties` |
| Auth | Spring Security OAuth2 Resource Server (JWT), Amazon Cognito | `SecurityConfig`, `application.properties` |
| Database | Amazon DynamoDB (AWS SDK v2, no Spring Data) | `repository/DynamoDb*Repository.java` |
| Object storage | Amazon S3 (pre-signed URLs) | `S3Config`, `ProfileImageService`, `OpportunityImageService` |
| Messaging | Amazon SNS → SQS → AWS Lambda → Amazon SES | `notification/`, `notification-lambda/` |
| Container | Docker (multi-stage, Gradle build → Temurin JRE run) | `Dockerfile` |
| CI/CD | AWS CodePipeline + CodeBuild → ECR → ECS (Fargate) behind an ALB | `buildspec.yml` |

---

## Quick start

```bash
git clone https://github.com/sashavershkova/wevolunteer-backend.git
cd wevolunteer-backend
./gradlew test       # runs fully offline, no AWS credentials needed — see Testing
./gradlew bootRun     # needs network access to Cognito + AWS credentials — see Local development setup
curl http://localhost:8080/actuator/health
```

This is the condensed version — see [Local development setup](#local-development-setup)
for what each step actually requires and why.

---

## Table of contents

1. [Repository layout](#repository-layout)
2. [Local development setup](#local-development-setup)
3. [Configuration reference](#configuration-reference)
4. [Authentication and authorization](#authentication-and-authorization)
5. [API reference](#api-reference)
6. [DynamoDB data model](#dynamodb-data-model)
7. [Notification pipeline](#notification-pipeline)
8. [Testing](#testing)
9. [Seed and demo data](#seed-and-demo-data)
10. [CI/CD pipeline](#cicd-pipeline)
11. [Current AWS infrastructure](#current-aws-infrastructure)
12. [Recreating the AWS infrastructure](#recreating-the-aws-infrastructure)
13. [Infrastructure teardown and recreation checklist](#infrastructure-teardown-and-recreation-checklist)
14. [Security notes and known limitations](#security-notes-and-known-limitations)
15. [Known non-obvious behaviors](#known-non-obvious-behaviors)
16. [Troubleshooting](#troubleshooting)

---

## Repository layout

```
build.gradle, settings.gradle        Gradle multi-project build (root "backend" + notification-lambda)
Dockerfile                           Multi-stage build: Gradle build → Temurin 21 JRE runtime image
buildspec.yml                        AWS CodeBuild spec used by the CI/CD pipeline
run.sh                               Convenience script: loads AWS credentials, then `./gradlew bootRun`
src/main/java/com/wevolunteer/backend/
  config/         Spring configuration: security, DynamoDB, S3, SNS clients/properties
  controller/     REST controllers (one per domain, listed below)
  dto/            Request/response records used at the API boundary
  exception/      Domain exceptions + a single @RestControllerAdvice mapping them to HTTP status
  model/          Domain records that mirror DynamoDB items (not JPA entities)
  notification/   NotificationEvent contract + SNS publisher used by services
  repository/     Repository interfaces + their single-table DynamoDB implementations
  service/        Business logic
src/main/resources/
  application.properties             Cognito issuer, actuator, AWS region/S3/SNS config
  application.yaml                   Only sets spring.application.name
src/test/                            JUnit 5 + Mockito unit tests (no live AWS calls — see Testing)
notification-lambda/                 Separate Gradle module: the SQS-triggered Lambda that sends email via SES
```

---

## Local development setup

### Prerequisites

- **Java 21** — `build.gradle` declares a Gradle toolchain of `JavaLanguageVersion.of(21)`.
  If you don't have a JDK 21 installed, Gradle will attempt to auto-provision one via its
  toolchain resolver the first time you build (this requires outbound network access).
- **Git**
- **AWS CLI v2** — needed to obtain credentials for anything that touches real AWS
  resources (DynamoDB, S3, SNS). Not needed to compile or run the unit test suite.
- **Docker** — optional. Only needed if you want to build/run the container image
  locally; `./gradlew bootRun` does not use Docker.
- An IDE such as IntelliJ IDEA (recommended, not required).

### Clone the repository

```bash
git clone https://github.com/sashavershkova/wevolunteer-backend.git
cd wevolunteer-backend
```

### AWS credentials

The backend's AWS SDK clients (`DynamoDbConfig`, `S3Config`, `SnsConfig`) are all built
with the SDK's **default credentials provider chain** — no access keys are ever read from
a config property or hardcoded. Locally this means whatever credentials are active in
your environment (environment variables, `~/.aws/credentials`, SSO session, etc.) are what
the app will use; in ECS it's the task's IAM role.

Use whichever of these matches how your team's AWS accounts are set up — there is no
`aws login` command in the AWS CLI, so don't rely on that:

```bash
# Long-lived/named profile credentials
aws configure

# Or, if your organization uses AWS IAM Identity Center (SSO)
aws sso login --profile <profile>

# Verify whichever method you used actually resolved credentials
aws sts get-caller-identity

# Export a profile's credentials as environment variables in the current shell
aws configure export-credentials --profile <profile> --format env
```

The included helper script does the export step for you and then starts the app:

```bash
./run.sh
```

`run.sh` unsets any stale `AWS_*` environment variables, evaluates
`aws configure export-credentials --format env` (using whatever default/active AWS CLI
profile is configured), and runs `./gradlew bootRun`. It assumes you've already configured
a working AWS CLI profile — it does not configure one for you.

### What actually needs AWS to start vs. to run tests

- **`./gradlew test`** — needs no AWS credentials and makes no network calls to AWS.
  Every repository/service/controller test mocks the AWS SDK client (`DynamoDbClient`,
  `S3Client`, `SnsClient`) with Mockito; nothing hits real DynamoDB, S3, or SNS. See
  [Testing](#testing) for the one exception (the Spring context test) and what it does
  and doesn't require.
- **`./gradlew bootRun`** — starts a full Spring context. This *does* need:
  - Network access to the Cognito issuer's OIDC discovery endpoint (Spring's
    `JwtDecoder` auto-configuration resolves the JWK Set URI from
    `spring.security.oauth2.resourceserver.jwt.issuer-uri` at startup).
  - Valid values for `aws.s3.profile-images-bucket` and `aws.sns.topic-arn` — both have
    committed defaults in `application.properties` (see
    [Configuration reference](#configuration-reference)), so the app *will* start without
    setting anything, but those defaults point at this project's shared AWS resources.
  - AWS credentials are only actually *used* when a request reaches a DynamoDB-, S3-, or
    SNS-backed code path — the app will start and answer `GET /actuator/health` even with
    no credentials configured, but any endpoint that touches the database will fail.
- There is **no local DynamoDB, no LocalStack, and no fully offline mode** for exercising
  the API end-to-end. To exercise real endpoints locally you need working credentials for
  an AWS account that has the `WeVolunteer` DynamoDB table (see
  [DynamoDB data model](#dynamodb-data-model)) and the configured S3 bucket / SNS topic.
- SQS, the Lambda, and SES are **never required to start the backend or to exercise any
  endpoint other than seeing the effects of a notification**. If they're missing or
  misconfigured, registrations/cancellations/waitlist promotions still succeed — SNS
  publishing failures are caught and logged, never surfaced to the caller (see
  [Notification pipeline](#notification-pipeline)).

### Build and test

```bash
./gradlew build     # compiles both modules and runs tests
./gradlew test       # backend module tests only
./gradlew :notification-lambda:test   # Lambda module tests only
```

### Run

```bash
./gradlew bootRun
```

```
http://localhost:8080
```

**Every endpoint except `GET /actuator/health` requires a valid Cognito-issued Bearer
JWT** — including read-only browsing endpoints like `GET /opportunities`. There is no
public, unauthenticated way to browse opportunities in the current configuration (see
[Authentication and authorization](#authentication-and-authorization)). This repo has no
local mock-authentication mode, so to call anything beyond the health check you need a
real access token issued by the Cognito user pool referenced in
`spring.security.oauth2.resourceserver.jwt.issuer-uri` — typically obtained by logging
into the frontend and reading the token it stores, or by driving the OAuth flow directly.

To just confirm the app started successfully:

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

### Docker

```bash
docker build -t wevolunteer-backend .
```

The `Dockerfile` builds from `public.ecr.aws/docker/library/gradle:8-jdk21` and runs on
`public.ecr.aws/docker/library/eclipse-temurin:21-jre` — Amazon ECR Public images are used
instead of Docker Hub specifically to avoid Docker Hub's rate limits during CI/CD builds.
The build stage runs `./gradlew build -x test` (tests are skipped in the image build since
CodeBuild already runs them in an earlier phase — see [CI/CD pipeline](#cicd-pipeline)).

```bash
docker run --rm -p 8080:8080 wevolunteer-backend
```

This bare `docker run` only verifies that the container **starts** (Spring boots, the JAR
is valid, `/actuator/health` responds) when everything the app needs is already reachable
without extra configuration — which it normally isn't. As covered above, boot itself needs
network access to the Cognito issuer, and any DynamoDB/S3/SNS-backed endpoint additionally
needs AWS credentials and the bucket/topic configuration. Running the bare command with no
credentials will still start the container and answer `/actuator/health`, but every other
endpoint will fail once it tries to reach AWS.

To exercise more than the health check, supply credentials and configuration explicitly.
The `Dockerfile` declares no `USER`, so the container runs as **`root`** with **`HOME=/root`**
(confirmed directly against the built image — `docker run --rm <image> id` reports
`uid=0(root)`, and `printenv HOME` reports `/root`; there is no `/home/app` in this image,
so don't assume one):

```bash
docker run --rm -p 8080:8080 \
  -v "$HOME/.aws:/root/.aws:ro" \
  -e AWS_PROFILE=<profile> \
  -e AWS_REGION=us-east-1 \
  -e PROFILE_IMAGES_BUCKET=<bucket-name> \
  -e SNS_TOPIC_ARN=<topic-arn> \
  wevolunteer-backend
```

This mounts your local AWS CLI profile directory **read-only** into the container so the
SDK's default credentials provider chain can resolve it, exactly as it would resolve
`~/.aws` outside a container. In AWS itself, no credentials are ever mounted or baked in —
the ECS task role is what the deployed container actually uses (see
[Current AWS infrastructure](#current-aws-infrastructure) and
[Recreating the AWS infrastructure](#recreating-the-aws-infrastructure)); the mounted-profile
form above is a local-only convenience for exercising the container against real AWS
resources from a dev machine.

---

## Configuration reference

All configuration lives in `src/main/resources/application.properties`. Every AWS-specific
value is overridable via an environment variable, and the account-specific ones fail fast
at startup if unset in a context where the default doesn't apply.

| Property | Env var override | Default (as committed) | Purpose |
|---|---|---|---|
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` | — (not externalized) | `https://cognito-idp.us-east-1.amazonaws.com/us-east-1_Dt3p8SFTj` | Cognito user pool whose JWTs are accepted |
| `management.endpoints.web.exposure.include` | — | `health` | Only `/actuator/health` is exposed; no `/actuator/info`, `/actuator/metrics`, etc. |
| `management.endpoint.health.show-details` | — | `never` | Health responses never leak downstream status details |
| `aws.region` | `AWS_REGION` | `us-east-1` | Region for the S3 and SNS clients (see caveat below) |
| `aws.s3.profile-images-bucket` | `PROFILE_IMAGES_BUCKET` | `wevolunteer-files-images-<AWS_ACCOUNT_ID>` | S3 bucket for **both** profile images and opportunity images (see [Known non-obvious behaviors](#known-non-obvious-behaviors)) |
| `aws.s3.upload-url-duration` | `PROFILE_IMAGE_UPLOAD_URL_DURATION` | `15m` | Lifetime of pre-signed upload URLs (max 7 days, enforced by `S3ProfileImageProperties`) |
| `aws.s3.download-url-duration` | `PROFILE_IMAGE_DOWNLOAD_URL_DURATION` | `60m` | Lifetime of pre-signed image display URLs |
| `aws.sns.topic-arn` | `SNS_TOPIC_ARN` | `arn:aws:sns:us-east-1:<AWS_ACCOUNT_ID>:wevolunteer-notifications` | SNS topic notification events are published to |

`<AWS_ACCOUNT_ID>` above is a placeholder — the real value is the literal 12-digit account
ID committed in `application.properties` and `buildspec.yml`, intentionally not repeated
here.

**Caveat confirmed in code:** `DynamoDbConfig` hardcodes `Region.US_EAST_1` for the
`DynamoDbClient` regardless of the `aws.region` property — only the S3 and SNS clients
actually honor `aws.region`/`AWS_REGION`. If the table is ever moved to another region,
`DynamoDbConfig` itself must be edited; setting the environment variable alone will not
move DynamoDB traffic.

`src/main/resources/application.yaml` sets nothing beyond `spring.application.name:
backend`.

Test-only overrides live in `src/test/resources/application-test.properties` (active under
the `test` Spring profile): a dummy S3 bucket name and SNS topic ARN so the one
`@SpringBootTest` in the suite can start without real AWS resource identifiers.

The Lambda module (`notification-lambda`) is configured entirely through Lambda
environment variables, not `application.properties` — see
[Notification pipeline](#notification-pipeline).

---

## Authentication and authorization

Authentication is Amazon Cognito via Spring Security's OAuth2 resource server support:
every request is expected to carry `Authorization: Bearer <JWT>`, and the JWT is validated
against the issuer configured in `spring.security.oauth2.resourceserver.jwt.issuer-uri`
(`SecurityConfig`).

- **`/actuator/health` is the only endpoint that permits anonymous access.** Every other
  request — including `GET /opportunities`, `GET /organizations/{id}`, and the custom
  `GET /health` — requires a valid, authenticated JWT
  (`.anyRequest().authenticated()` in `SecurityConfig`). The custom `/health` endpoint
  (`HealthController`) is *not* in the same permit-list as `/actuator/health` and is
  therefore an authenticated endpoint despite its name.
- CORS is restricted to an explicit allow-list in `SecurityConfig`:
  `http://localhost:5173` and the deployed Amplify frontend origin
  (`https://main.d1e6h0yn2o3f62.amplifyapp.com`), methods `GET, POST, PATCH, DELETE,
  OPTIONS`, headers `Authorization, Content-Type`.
- **`GET /auth/me`** (`AuthController`) returns `{"userId": "<cognito-sub>"}` — the
  simplest way to confirm what subject a token resolves to.
- **Identity, not roles, drives authorization.** The `User` model has a `role` field
  (`VOLUNTEER` or `ORGANIZATION`, validated by a regex pattern on write), but it is purely
  informational/display data. No controller or service in the current code performs a
  role-based check (e.g. "only ORGANIZATION-role users may create opportunities"). What
  the code actually enforces, consistently on the `/me`-scoped and
  `/organizations/me`-scoped routes, is **ownership**: the caller's JWT subject
  (`jwt.getSubject()`) is used directly as the user ID or organization ID being acted on,
  and mutations to another party's opportunity are rejected with `403 Forbidden` by
  comparing `organizationId` on the record to the JWT subject (see `OpportunityService`,
  `OpportunityImageService`).
- **Not every endpoint has an ownership check**, even though most do — see
  [Security notes and known limitations](#security-notes-and-known-limitations) for the
  specific list.

---

## API reference

Base URL: `http://localhost:8080` locally, or the ALB/CloudFront origin in a deployed
environment. All request/response bodies are JSON. Every endpoint below requires
`Authorization: Bearer <JWT>` unless marked **public**.

### Health

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/actuator/health` | **public** | Spring Boot Actuator; `{"status":"UP"}`. Used by the ALB for deployment health checks. |
| GET | `/health` | required | Custom, minimal `{"status":"ok"}`. Despite the name, **not** public — see [Authentication and authorization](#authentication-and-authorization). |

### Current identity

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/auth/me` | required | `{"userId": "<cognito-sub>"}` |

### Users (volunteers)

| Method | Path | Auth | Ownership check | Notes |
|---|---|---|---|---|
| POST | `/users` | required | caller = new user | Creates a profile with `userId = JWT subject`. |
| GET | `/users/me` | required | caller = self | Current volunteer's profile, with a resolved `profileImageUrl`. |
| PATCH | `/users/me` | required | caller = self | Updates name/email; `role` in the body is **ignored** — the stored role is always preserved server-side. |
| GET | `/users/{userId}` | required | none | Any authenticated caller can view any profile. |
| GET | `/users/{userId}/registrations` | required | none | Any authenticated caller can view any user's registrations. |
| DELETE | `/users/{userId}` | required | none | Deleting a user cascades: cancels all of their registrations first (publishing cancellation events), then deletes the profile. |

Example — create user:
```json
POST /users
{
  "name": "Jordan Lee",
  "email": "jordan@example.com",
  "role": "VOLUNTEER"
}
```

### Organizations

| Method | Path | Auth | Ownership check | Notes |
|---|---|---|---|---|
| POST | `/organizations` | required | caller = new org | Creates a profile with `organizationId = JWT subject`. |
| GET | `/organizations/me` | required | caller = self | |
| PATCH | `/organizations/me` | required | caller = self | |
| GET | `/organizations/{organizationId}` | required | none | Public-style read of any org profile. |
| PATCH | `/organizations/{organizationId}` | required | none | No ownership check on this variant. |
| DELETE | `/organizations/{organizationId}` | required | none | Cascades: cancels every registration on every opportunity the org owns, deletes those opportunities, then deletes the org profile. |
| GET | `/organizations/me/opportunities` | required | caller = self | All opportunities (any status) owned by the caller. |
| GET | `/organizations/{organizationId}/opportunities?status=OPEN\|CLOSED` | required | none | `status` query param optional; omitted returns all statuses. |
| POST | `/organizations/me/opportunities` | required | caller = self | Creates an opportunity owned by the caller — see [Opportunities](#opportunities). |
| DELETE | `/organizations/me/opportunities/{opportunityId}` | required | caller = owner (403 otherwise) | See delete rules below. |
| GET | `/organizations/me/opportunities/{opportunityId}/registrations` | required | caller = owner (403 otherwise) | |
| GET | `/organizations/me/opportunities/{opportunityId}/waitlist` | required | caller = owner (403 otherwise) | Oldest-joined-first order. |

Example — create/update organization:
```json
POST /organizations
{
  "name": "Seattle Food Bank",
  "description": "Community food distribution.",
  "email": "contact@example.org",
  "website": "https://example.org"
}
```
(`description` and `website` are optional; `name` and `email` are required.)

### Opportunities

| Method | Path | Auth | Ownership check | Notes |
|---|---|---|---|---|
| GET | `/opportunities` | required | — | Volunteer browse. Always **OPEN only**. Supports `category`, `location`, `organizationId`, `startDate`+`endDate` query params, single or combined (see filter note below). |
| GET | `/opportunities/{opportunityId}` | required | — | |
| GET | `/opportunities/{opportunityId}/registrations` | required | none | Any authenticated caller can list an opportunity's registrants — not scoped to the owning organization (compare to the `/organizations/me/...` variant, which is). |
| PATCH | `/opportunities/{opportunityId}` | required | **none** | Full-record update (title/description/category/location/date/status/capacity/times/etc.); see the authorization note above. |
| PATCH | `/opportunities/{opportunityId}/close` | required | caller = owner (403 otherwise) | See close rules below. |
| PATCH | `/opportunities/{opportunityId}/reopen` | required | caller = owner (403 otherwise) | See reopen rules below. |

**Filter combination behavior** (`OpportunityController` / `DynamoDbOpportunityRepository`):
a single filter queries its dedicated GSI directly. When more than one filter is supplied,
one is chosen as the DynamoDB partition-key query based on a fixed priority — `location` >
`category` > `organizationId` > `startDate`/`endDate` — and the remaining filters are
applied as a post-query DynamoDB `FilterExpression`.

**Create** (`POST /organizations/me/opportunities`):
```json
{
  "opportunityId": "opp-food-bank-shift-1",
  "title": "Food Bank Volunteer Shift",
  "description": "Sort and pack donations for distribution.",
  "category": "Food",
  "location": "Seattle",
  "date": "2026-09-01",
  "capacity": 10,
  "startTime": "09:00",
  "endTime": "12:00",
  "whatYoullDo": ["Sort donations", "Pack boxes"],
  "recurring": false
}
```
`opportunityId` is **client-supplied**, not server-generated — a `PutItem` conditional
write rejects the request with `409 Conflict` if that ID already exists. `capacity` must be
`>= 1`. `startTime`/`endTime` are `HH:mm` 24-hour strings on the same calendar day
(overnight ranges aren't supported); `endTime` must be strictly after `startTime`. New
opportunities are created with `status = OPEN` and `registeredCount = 0`.

**Update** (`PATCH /opportunities/{opportunityId}`) takes the same shape minus
`opportunityId`, plus a required `status` field. It fully replaces the stored record's
editable fields; `registeredCount` and the S3 image key are carried forward from the
existing record rather than accepted from the request (there is no dedicated PATCH-image
field — that goes through the separate image endpoints below).

**Capacity / `registeredCount` / `availableSpots`:** `availableSpots` is computed, not
stored — `capacity - registeredCount`, recalculated on every read. `registeredCount` is
only ever changed through the conditional DynamoDB transactions described in
[DynamoDB data model](#dynamodb-data-model) (registration create/cancel), never by a plain
opportunity update. An update's `capacity` is **not validated** against the opportunity's
current `registeredCount` — lowering capacity below the number already registered is
possible and produces a negative `availableSpots` on read. `POST /registrations`' response
body echoes `registeredCount + 1` / `availableSpots - 1` based on the value read *before*
the transaction — under concurrent registrations this echoed number can be briefly stale
even though the underlying DynamoDB counter is correct.

**Close rules** (`PATCH /opportunities/{id}/close`, `OpportunityService.closeOpportunity`):
only the owning organization may close it (403 otherwise). Closing cancels every active
registration first — one `REGISTRATION_CANCELLED_BY_ORGANIZATION` notification event per
affected volunteer — and only then flips `status` to `CLOSED`, zeroes `registeredCount`,
and removes the opportunity from the "open opportunities" GSI. There is **no check that the
opportunity isn't already closed** — closing an already-closed opportunity succeeds
(idempotently, since `registeredCount` is already 0 and there are no active registrations
left to cancel). Closing does **not** clean up any pending waitlist entries — see
[Waitlist](#waitlist).

**Reopen rules** (`PATCH /opportunities/{id}/reopen`): only the owner may reopen (403
otherwise); the opportunity must currently be `CLOSED` (enforced by a DynamoDB conditional
update — reopening a non-`CLOSED` opportunity throws `409 Conflict`); and the opportunity's
`date` must not be in the past (`409 Conflict` otherwise). Reopening resets
`registeredCount` to 0 and restores the "open opportunities" GSI entry — anyone previously
registered is **not** restored; they must re-register.

**Delete rules** (`DELETE /organizations/me/opportunities/{id}`, service-enforced, not a
DynamoDB condition beyond existence): only the owner may delete (403 otherwise); the
opportunity must be `CLOSED`; `registeredCount` must be 0; and the opportunity's `date`
must not be in the past. All three checks are application-level `ConflictException`s, not
DynamoDB conditional-write failures.

**Past-opportunity rules:** an opportunity is "past" once its `date` is before today in the
JVM's default time zone (`OpportunityDatePolicy`) — it remains eligible for registration
and waitlisting through its entire event date, becoming ineligible only the following
calendar day. This rule is shared by registration, waitlist-join, waitlist auto-promotion,
and reopen/delete.

### Registrations

| Method | Path | Auth | Ownership check | Notes |
|---|---|---|---|---|
| POST | `/registrations` | required | **none** — `userId` comes from the request body | See authorization note above. |
| GET | `/registrations/me` | required | caller = self | |
| DELETE | `/registrations/me/{opportunityId}` | required | caller = self | |
| DELETE | `/registrations/{userId}/{opportunityId}` | required | **none** | Legacy-style path-variable route; not scoped to the caller. |

```json
POST /registrations
{
  "userId": "us-east-1:abcd1234-...",
  "opportunityId": "opp-food-bank-shift-1"
}
```

Registration is a single DynamoDB transaction enforcing three things atomically:
opportunity must be `OPEN` and have `registeredCount < capacity`; the user must not already
have a registration item; the opportunity-side registration item must not already exist.
Any failure maps to `409 Conflict` with a message identifying whether it was "already
registered" or "no longer open / at capacity". A successful registration publishes a
`REGISTRATION_CREATED` notification event.

Cancelling (`DELETE /registrations/me/{opportunityId}` or the unscoped path-variable
route) atomically decrements `registeredCount` and deletes both dual-write registration
items, publishes `REGISTRATION_CANCELLED`, and then — best-effort, swallowing any failure
so a successful cancellation is never reported as failed — attempts to promote the
longest-waiting volunteer from the opportunity's waitlist into the newly opened spot (see
[Waitlist](#waitlist)).

### Favorites

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/favorites/me` | required | |
| POST | `/favorites/me/{opportunityId}` | required | Idempotent — favoriting an already-favorited opportunity just refreshes the stored snapshot (no error, no `409`). |
| DELETE | `/favorites/me/{opportunityId}` | required | Idempotent — un-favoriting something not favorited is a no-op, not an error. |

No notification events are published for favorites.

### Waitlist

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/waitlist/me` | required | |
| POST | `/waitlist/me/{opportunityId}` | required | Join. |
| DELETE | `/waitlist/me/{opportunityId}` | required | Leave. |
| GET | `/organizations/me/opportunities/{opportunityId}/waitlist` | required | Organization view, owner-only (403 otherwise). Oldest-joined-first. |

Joining requires: the opportunity is `OPEN`; the opportunity is not past
(`OpportunityDatePolicy`); and `availableSpots == 0` (if spots are open, the API rejects
the join with `409 Conflict` and tells the caller to register instead). Duplicate joins are
rejected with `409 Conflict` via a DynamoDB conditional write. **Joining the waitlist does
not itself publish a notification event** — no `NotificationPublisher` call exists in
`WaitlistService`.

FIFO ordering is structural, not a separate sort step: the opportunity-side waitlist item's
DynamoDB sort key is `WAITLIST#<joinedAt>#<userId>`, so a plain query already returns
entries oldest-joined-first.

**Auto-promotion**: triggered only as the last step of a volunteer's own
`DELETE /registrations/me/{opportunityId}` (or the unscoped cancel route) — never when an
organization closes an opportunity, since that leaves no open spot to promote into. It pops
the first (longest-waiting) waitlist entry, registers that volunteer through the same
DynamoDB transaction path as a normal registration, removes them from the waitlist, and
publishes a `REGISTRATION_CREATED` event for them — deliberately reusing the same event
type and email template a self-registration produces, rather than a dedicated
promotion-specific event. If the waitlist is empty, the opportunity's date has since passed,
or the promotion transaction fails for any reason, the failure is logged and swallowed —
the triggering cancellation is never rolled back or reported as failed because of it.

**No close-cleanup for the waitlist**: as verified in `OpportunityService.closeOpportunity`,
closing an opportunity cancels active registrations but does **not** remove or notify
anyone still on that opportunity's waitlist — those items remain in DynamoDB.

### Profile images

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/users/me/profile-image/upload-url` | required | Issues a pre-signed S3 PUT URL under `users/<sub>/profile/<uuid>.<ext>`. |
| PATCH | `/users/me/profile-image` | required | Confirms the upload and attaches it to the caller's profile. |
| POST | `/organizations/me/profile-image/upload-url` | required | Same, under `organizations/<sub>/profile/<uuid>.<ext>`. |
| PATCH | `/organizations/me/profile-image` | required | |

```json
POST /users/me/profile-image/upload-url
{ "contentType": "image/jpeg" }
```
```json
// 200 response
{
  "objectKey": "users/<sub>/profile/<uuid>.jpg",
  "uploadUrl": "https://...presigned...",
  "expiresInSeconds": 900
}
```
The client `PUT`s the image bytes straight to `uploadUrl`, then confirms:
```json
PATCH /users/me/profile-image
{ "objectKey": "users/<sub>/profile/<uuid>.jpg" }
```
Attaching validates that the key matches the caller's own prefix (rejecting path traversal
or another user's key with `403`), that the object actually exists in S3, and that its
stored content type/size are within the allowed set (`image/jpeg`, `image/png`,
`image/webp`; 5 MB max — `ImageContentTypes`). Only the durable S3 key is stored; every API
response resolves it to a fresh, time-limited pre-signed **download** URL
(`profileImageUrl`) rather than exposing the key itself.

### Opportunity images

| Method | Path | Auth | Ownership check | Notes |
|---|---|---|---|
| POST | `/organizations/me/opportunity-images/upload-url` | required | caller must have an org profile | Under `organizations/<sub>/opportunities/<uuid>.<ext>`. |
| PATCH | `/organizations/me/opportunities/{opportunityId}/image` | required | caller = opportunity owner (403 otherwise) | Attaches (or replaces) the opportunity's image. |

Same upload-then-confirm flow and the same allowed types/size limit as profile images. Note
they share the same S3 bucket/property (`aws.s3.profile-images-bucket` /
`PROFILE_IMAGES_BUCKET`) despite the property name — see
[Known non-obvious behaviors](#known-non-obvious-behaviors).

---

## DynamoDB data model

Single table, name **`WeVolunteer`**, hardcoded as a `private static final String
TABLE_NAME` constant in every `DynamoDb*Repository` class — it is not read from a
configuration property, so renaming the table requires an application code change, not
just an environment variable. The DynamoDB client's region is likewise hardcoded to
`us-east-1` in `DynamoDbConfig` (see the caveat in
[Configuration reference](#configuration-reference)).

Every attribute is a String, Number, Boolean, or List of Strings — no DynamoDB Maps or
Sets are used in the schema as written.

### Entities and key patterns

| Entity / access pattern | PK | SK | Index |
|---|---|---|---|
| Opportunity details | `OPPORTUNITY#<opportunityId>` | `DETAILS` | — |
| User profile | `USER#<userId>` | `PROFILE` | — |
| Organization profile | `ORG#<organizationId>` | `PROFILE` | — |
| A user's own registrations | `USER#<userId>` | `REGISTRATION#<opportunityDate>#<opportunityId>` | — |
| An opportunity's registrants | `OPPORTUNITY#<opportunityId>` | `REGISTRATION#<userId>` | — |
| A user's own waitlist entries | `USER#<userId>` | `WAITLIST#<opportunityDate>#<opportunityId>` | — |
| An opportunity's waitlist, FIFO | `OPPORTUNITY#<opportunityId>` | `WAITLIST#<joinedAt>#<userId>` | — |
| A user's favorites | `USER#<userId>` | `FAVORITE#<opportunityId>` | — |
| All open opportunities, by date | `OPPORTUNITIES#OPEN` (GSI1PK) | `DATE#<date>#OPPORTUNITY#<id>` (GSI1SK) | `GSI1_OpenOpportunities` |
| Opportunities by category | `CATEGORY#<category>` (GSI2PK) | same date-based SK (GSI2SK) | `GSI2_Category` |
| Opportunities by location | `LOCATION#<location>` (GSI3PK) | same date-based SK (GSI3SK) | `GSI3_Location` |
| Opportunities by organization | `ORG#<organizationId>` (GSI4PK) | same date-based SK (GSI4SK) | `GSI4_Organization` |

Registrations, waitlist entries, and favorites are each written as **two separate items**
(dual-write, not a single item with two access patterns) — one under the `USER#` partition
so "what am I registered for / waitlisted for" is a single fast query, and one under the
`OPPORTUNITY#` partition so "who's registered / waiting for this opportunity" is a single
fast query. Registration's two items are kept consistent via a DynamoDB transaction;
favorites' single item is a straightforward overwrite (see below).

### GSI purpose and status-dependent behavior

- **`GSI1_OpenOpportunities`** — the *only* GSI that is conditionally present. Its
  `GSI1PK`/`GSI1SK` attributes are written only while an opportunity's `status` is `OPEN`;
  `close()` explicitly `REMOVE`s them, and `reopen()` explicitly restores them. This is
  what makes `GET /opportunities` (no filter) and the date-range filter O(open items) reads
  instead of a full-table scan.
- **`GSI2_Category`**, **`GSI3_Location`**, **`GSI4_Organization`** — always present
  regardless of status (needed so an organization can see its `CLOSED` opportunities too).
  Volunteer-facing queries against these three add an application-level `FilterExpression`
  of `status = OPEN` on top of the GSI query; the organization's own "all my opportunities"
  query (`findAllByOrganizationId`) omits that filter.
- `GSI3PK` strips a trailing `", WA"` suffix from the stored `location` value before
  indexing (`LOCATION#Seattle`, not `LOCATION#Seattle, WA`), and the corresponding filter
  re-adds `", WA"` when applying a non-primary location filter — this suffix handling is
  hardcoded, not configurable.

### Conditional writes and race protection

- **Opportunity create**: `PutItem` with `attribute_not_exists(PK) AND
  attribute_not_exists(SK)` → `409 Conflict` if the client-supplied `opportunityId` already
  exists.
- **Opportunity update/delete**: require `attribute_exists(PK) AND attribute_exists(SK)`.
- **Opportunity close**: `UpdateItem` setting `status = CLOSED`, `registeredCount = 0`, and
  removing `GSI1PK`/`GSI1SK`; condition only checks the item exists (no status guard — see
  the close-rules note in the API reference).
- **Opportunity reopen**: condition requires `status = CLOSED` in addition to existence —
  this is the one place a `ConditionalCheckFailedException` is deliberately used as a
  concurrency/state guard (mapped to `409 Conflict`, "Only closed opportunities can be
  reopened").
- **Registration create** (`registerUserForOpportunity`): a single `TransactWriteItems`
  with three actions — (1) `Update` the opportunity's `registeredCount = registeredCount +
  1` conditioned on `status = OPEN AND registeredCount < capacity` (capacity enforcement),
  (2) `Put` the user-side registration item conditioned on non-existence, (3) `Put` the
  opportunity-side registration item conditioned on non-existence (duplicate prevention).
  `TransactionCanceledException` cancellation reasons are inspected by index to distinguish
  "already registered" (reasons 1 or 2) from "no longer open or at capacity" (reason 0).
- **Registration cancel**: `TransactWriteItems` decrementing `registeredCount` (guarded by
  `registeredCount > 0` so it can never go negative) plus two conditional `Delete`s
  requiring both dual-write items to exist.
- **Registration cancel during an organization's close** (`cancelRegistrationForOpportunityClose`):
  same shape, but first `Query`s for the user-side item to discover its real sort key
  (rather than reconstructing it) before building the transaction.
- **Waitlist join**: `TransactWriteItems` with two `Put`s, each conditioned on
  non-existence — duplicate joins fail the whole transaction with `409 Conflict`.
- **Waitlist leave**: conditional `Delete` of the user-side item; the corresponding
  opportunity-side item (found via a `Query`, since its sort key embeds `joinedAt` which
  the caller doesn't have) is deleted **without** a conditional check.
- **Favorites**: deliberately **no conditional expression** on either save or delete —
  favoriting/un-favoriting is designed to be idempotent (toggle-button UX), not a
  one-time create.

---

## Notification pipeline

```
Spring Boot service layer
    │  NotificationPublisher.publish(NotificationEvent)
    ▼
SnsNotificationPublisher — serializes to JSON, SnsClient.publish() to aws.sns.topic-arn
    │  best-effort: serialization or publish failures are logged and swallowed,
    │  never surfaced to the caller — a business operation's own success/failure
    │  is independent of whether its notification made it out
    ▼
Amazon SNS topic  (provisioned in AWS, not in this repo)
    ▼
Amazon SQS queue, subscribed to the topic  (provisioned in AWS, not in this repo;
    the subscription does NOT have raw message delivery enabled — confirmed by
    NotificationHandler expecting an SNS envelope)
    ▼
AWS Lambda — notification-lambda module, com.wevolunteer.notification.NotificationHandler
    │  Parses the SNS envelope (or a raw event JSON as a fallback), maps the
    │  event type to subject/plain-text/HTML content, sends via SES.
    │  Parsing or SES-send failures are NOT caught — they fail the Lambda
    │  invocation so SQS redelivers per the queue's redrive policy.
    ▼
Amazon SES  (verified sending identity required — see sandbox note below)
```

### Event types supported by current code

Defined once as `NotificationEventType` and hand-duplicated (by design — the Lambda module
has no dependency on the backend module) in `notification-lambda`:

| Event | Published from | When |
|---|---|---|
| `REGISTRATION_CREATED` | `RegistrationService.register` | A volunteer successfully registers. |
| `REGISTRATION_CREATED` | `RegistrationService.promoteNextWaitlistedVolunteer` | A volunteer is auto-promoted off a waitlist into a newly opened spot (reuses the same event type/template rather than a dedicated one). |
| `REGISTRATION_CANCELLED` | `RegistrationService.cancelRegistration` | A volunteer cancels their own registration. |
| `REGISTRATION_CANCELLED_BY_ORGANIZATION` | `RegistrationService.cancelAllRegistrationsForOpportunity` | An organization closes an opportunity; fired once per affected volunteer. |

**Not currently published as an event, confirmed by absence of any
`NotificationPublisher`/`notificationPublisher` reference in `WaitlistService`:** joining a
waitlist does not send a notification email in the current code, despite that behavior
having existed on a feature branch at one point in this project's history — `main` as
inspected does not call it.

Generated email content never includes internal IDs (`userId`, `opportunityId`,
`organizationId`) — only volunteer-facing names, dates, and the organization's display
name (`NotificationEmailContentFactory`).

### Lambda module details

- Separate Gradle subproject (`notification-lambda`), built with the Shadow plugin into a
  fat jar: `notification-lambda-0.0.1-SNAPSHOT-all.jar` (`./gradlew
  :notification-lambda:shadowJar`). That jar is the deployment artifact for the Lambda
  function; this repository does not include the Lambda's infrastructure definition
  (function name, memory/timeout, IAM role, or the SQS trigger/event source mapping).
- Handler class: `com.wevolunteer.notification.NotificationHandler`, implementing
  `RequestHandler<SQSEvent, Void>`.
- Required Lambda environment variables:
  - `SES_FROM_EMAIL` — a verified SES sending identity. The Lambda fails fast at
    construction if unset or blank.
  - `AWS_REGION` — defaults to `us-east-1` if unset (used to build the `SesV2Client`).
- The `SesV2Client` and `ObjectMapper` are built once in the no-arg constructor and reused
  across warm invocations of the same execution environment (standard Lambda cold-start
  optimization).

### SES sandbox mode

**Not confirmable from this repo** whether the SES account/identity used in this project's
AWS account currently runs in sandbox mode — that's an AWS account-level setting, not
something reflected in code. What's relevant if it is (the default for any new AWS
account's SES setup, until production access is requested and granted): in sandbox mode,
SES can only send **to** addresses/domains that are themselves verified identities, in
addition to `SES_FROM_EMAIL` needing to be a verified sending identity. Concretely, that
means until production access is granted, real notification emails will only be delivered
to volunteer/organization addresses that have been individually verified in SES —
registrations from arbitrary real email addresses will fail to deliver (the Lambda's SES
call will throw, failing the invocation, and SQS will retry per its redrive policy until
the message is eventually moved to a dead-letter queue, if one is configured). No verified
sender or recipient addresses are reproduced here since they're account-specific and not
secrets this README should redistribute.

---

## Testing

- At the time of this README update, the full Gradle test run (`./gradlew clean test`,
  both modules) executed **374 tests with 0 failures, 0 errors, and 0 skipped** — 356
  tests in the backend module (28 top-level test classes, several using JUnit 5 `@Nested`
  classes, which is why the backend module produces 76 separate `TEST-*.xml` report files)
  plus 18 tests in `notification-lambda` (3 files). These totals were computed directly
  from `build/test-results/test/TEST-*.xml` and
  `notification-lambda/build/test-results/test/TEST-*.xml` after a clean run — not from
  counting `@Test` annotations in source — and were cross-checked by an independent count
  of `<testcase>` elements in the same XML files, which matched exactly. Counts are current
  as of commit `134770c`; re-run `./gradlew clean test` to reproduce them on a later commit.
- **Every backend test is a plain JUnit 5 / Mockito unit test** — controllers, services,
  and DynamoDB repository implementations are all tested with the relevant AWS SDK client
  (`DynamoDbClient`, `S3Client`, `SnsClient`) or collaborator mocked via
  `@ExtendWith(MockitoExtension.class)` / `@Mock` / `@InjectMocks`. **No test in this repo
  makes a real AWS API call.**
- The **one exception** is `BackendApplicationTests` (`@SpringBootTest`, `@ActiveProfiles("test")`)
  — it boots the full Spring context, which does construct real `DynamoDbClient`,
  `S3Client`, and `SnsClient` beans (construction alone doesn't call AWS) and does resolve
  the Cognito JWK Set URI from the issuer configured in `application.properties` (this step
  does perform a real network call to Cognito's OIDC discovery endpoint during context
  startup). `src/test/resources/application-test.properties` supplies dummy values for the
  S3 bucket and SNS topic ARN specifically so this one test doesn't need the real resource
  identifiers. In this environment, `./gradlew clean test` completed the entire suite
  (both modules) in about 8 seconds with no AWS credentials configured.
- `notification-lambda`'s tests similarly mock `EmailSender`/`SesV2Client` — no real SES
  calls.
- CI (CodeBuild) runs `./gradlew test` as a build-phase command before the Docker image is
  built — see [CI/CD pipeline](#cicd-pipeline).

Run everything:
```bash
./gradlew test
```
Run a single module:
```bash
./gradlew :notification-lambda:test
```

---

## Seed and demo data

**There is no reproducible seed script in this repository** — confirmed by searching the
tree for anything named or referencing "seed" (scripts, resources, Java classes): nothing
matches. The only mention of sample data was in the previous version of this README (a
hand-written table of example organization/opportunity/user IDs), which was not backed by
any script or fixture in the codebase and has been removed from this document.

Practical implications:

- The DynamoDB table currently has data in it because people have been using the deployed
  application (registering, creating opportunities, etc.) — not because anything in this
  repo populated it.
- **If the `WeVolunteer` table is deleted (e.g. as part of tearing down the AWS
  infrastructure after the capstone), all of that data disappears with it.** There is no
  export/import or backup mechanism defined in this repository. If the team wants to keep
  any of it, export it manually (e.g. `aws dynamodb scan`) before deleting the table — see
  [Infrastructure teardown and recreation checklist](#infrastructure-teardown-and-recreation-checklist).
- Going forward, demo/test data has to be created the same way real data is: through the
  frontend, or by calling the API directly (see [API reference](#api-reference)) — or by
  writing a seed script, which does not currently exist.
- This README does not reproduce any names, emails, or other values copied from the
  current shared table's contents, live or historical.

---

## CI/CD pipeline

```
GitHub (main)
    ↓  detected by
AWS CodePipeline           (pipeline definition itself is not in this repo)
    ↓  triggers
AWS CodeBuild               (phases defined in buildspec.yml, in this repo)
    ↓  produces
Amazon ECR (image push)
    ↓  triggers
Amazon ECS (Fargate) deployment
    ↓  health-checked by
Application Load Balancer → GET /actuator/health
```

Exactly what `buildspec.yml` does, phase by phase:

1. **pre_build** — logs the CodeBuild environment in to Amazon ECR
   (`aws ecr get-login-password | docker login`) and computes the target repository URI and
   image tag (`CODEBUILD_RESOLVED_SOURCE_VERSION`, i.e. the commit SHA, falling back to
   `latest`).
2. **build** — runs `./gradlew test` (the full test suite must pass before an image is even
   built), then `docker build` and tags the image both with the commit SHA and `latest`.
3. **post_build** — pushes both tags to ECR, then writes `imagedefinitions.json` — the
   standard file format an ECS `CodePipeline` deploy action consumes to know which
   container/image pair to deploy — using the fixed container name declared in
   `buildspec.yml`'s environment variables.

If the ECS deployment fails its health check against `/actuator/health` post-deploy, ECS
rolls back to the previously running task definition automatically — this is standard ECS
deployment-circuit-breaker/rolling-update behavior, not something configured in this repo's
files; **not confirmable from this repo** whether the deployment circuit breaker is
explicitly enabled on the service, since the ECS service definition itself isn't here.

**What is not in this repository**: the CodePipeline definition (source/build/deploy
stages), the CodeBuild project's IAM role and trigger configuration, the ECS
cluster/service/task-definition, the ALB/target-group/listener configuration, and the ECR
repository's lifecycle policy. All of that lives in AWS, configured outside version
control — see the next section.

---

## Current AWS infrastructure

This is a point-in-time, read-only inventory of the AWS resources actually deployed for
this project in `us-east-1`, gathered directly from the AWS account via read-only
`describe`/`get`/`list` API calls (not from any file in this repo) on 2026-08-05. **These
resources are expected to be torn down after the capstone concludes** — this section exists
so a future reader has a concrete record of what was really running, not just what the code
implies, to work from when reconstructing it. Nothing here was created, modified, or
deleted to produce this inventory. AWS account IDs and full ARNs are redacted; resource
names, configuration values, and non-identifying counts are not.

**Identity**
- Cognito user pool (ID referenced in `application.properties`'s issuer URL — see
  [Configuration reference](#configuration-reference)): deletion protection **ACTIVE**;
  password policy requires 8+ characters with upper/lower/number/symbol, temporary
  passwords valid 7 days.
- One app client, a **public client with no client secret** (OAuth authorization-code flow
  with PKCE, as expected for a browser SPA): scopes `email openid phone`; callback and
  logout URLs are `http://localhost:5173` and the deployed Amplify origin (matching the
  backend's CORS allow-list exactly); access and ID tokens valid 60 minutes, refresh token
  valid 5 days; token revocation enabled.

**Data**
- DynamoDB table `WeVolunteer`: **`PAY_PER_REQUEST`** billing mode, `PK`/`SK` both String,
  four GSIs (`GSI1_OpenOpportunities`, `GSI2_Category`, `GSI3_Location`,
  `GSI4_Organization`), **all four using `ALL` projection** (confirmed live — not a guess).
  No DynamoDB Streams configured. TTL disabled. **Deletion protection is disabled** on the
  table itself (unlike the Cognito pool). ~121 items / ~66 KB at inventory time — this will
  have changed by the time anyone reads this.

**Storage**
- S3 bucket `wevolunteer-files-images-<AWS_ACCOUNT_ID>`: versioning **Enabled**; default
  encryption **SSE-S3 (AES256)**; all four public-access-block settings **true** (fully
  blocked — no public access possible); no bucket policy (access is IAM-only, via the ECS
  task role below); CORS allows `GET`/`PUT` with a `Content-Type` header and `ETag`
  exposed, from the same two origins as Cognito's app client.

**Messaging / notifications**
- SNS topic `wevolunteer-notifications`: default AWS-managed access policy (publish/manage
  restricted to the account owner); one confirmed subscription, protocol `sqs`.
- SQS queue `wevolunteer-notifications-queue`: visibility timeout 60s, retention 4 days,
  redrive policy sends a message to the DLQ below after **3** failed receives, queue policy
  allows only `sns.amazonaws.com` to `SendMessage`, conditioned on the source being this
  project's SNS topic. SQS-managed SSE enabled.
- SQS dead-letter queue `wevolunteer-notifications-dlq`: visibility timeout 30s, retention
  4 days, SSE enabled. **At inventory time it held 9 messages** — i.e. some notification
  deliveries have exhausted their 3 retries and dead-lettered. This README update does not
  investigate or clear them; worth a separate look if email delivery seems to be silently
  failing.
- Lambda function `wevolunteer-notification-consumer`: runtime **java21**, architecture
  **x86_64**, 512 MB memory, 10s timeout, package type Zip (not a container image), handler
  `com.wevolunteer.notification.NotificationHandler::handleRequest`. One environment
  variable, `SES_FROM_EMAIL` (set to a verified individual email address — the value itself
  is not reproduced here). Execution role has the AWS-managed
  `AWSLambdaBasicExecutionRole` (CloudWatch Logs) plus two inline policies: `ses:SendEmail`
  scoped to two specific verified SES identity ARNs, and
  `sqs:ReceiveMessage`/`DeleteMessage`/`GetQueueAttributes` scoped to the queue above. One
  SQS event-source mapping (batch size 1, no batching window, state Enabled).
- SES account: **`ProductionAccessEnabled: false` — the account is in sandbox mode.**
  24-hour send quota 200, max send rate 1/second (both sandbox defaults). Five identity
  verification requests exist, **all `EMAIL_ADDRESS` type — no domain identity is
  configured**; two are verified with sending enabled, three remain `PENDING`. No addresses
  are reproduced here.

**Container platform**
- ECR repository `wevolunteer-backend-repository`: mutable tags, scan-on-push enabled,
  AES256 encryption, no lifecycle policy, no repository policy.
- ECS cluster `wevolunteer-cluster`: `FARGATE` + `FARGATE_SPOT` capacity providers,
  Container Insights disabled, one active service.
- ECS service `wevolunteer-backend-service`: Fargate launch type, platform version 1.4.0,
  desired/running count **1**, rolling-update strategy, **deployment circuit breaker
  enabled with rollback enabled**, minimum healthy 100% / maximum 200%. Registered against
  **two target groups at once** (see the load balancers below). Tasks run with
  `assignPublicIp: DISABLED` in 2 subnets behind 1 security group.
- ECS task definition `wevolunteer-backend` (revision 40 at inventory time): 0.5 vCPU /
  1024 MiB, **`ARM64`** architecture (Graviton — note this differs from the Lambda above,
  which runs `x86_64`), `awsvpc` network mode, one container on port 8080 with exactly two
  environment variables (`PROFILE_IMAGES_BUCKET`, `SNS_TOPIC_ARN`) and no Secrets Manager
  references, logging via the `awslogs` driver to `/ecs/wevolunteer-backend`. The deployed
  image tag is the full 40-character git commit SHA beginning `134770c` — the same commit
  this README documents.
- Task role permissions (confirmed live): DynamoDB
  `GetItem`/`PutItem`/`UpdateItem`/`DeleteItem`/`Query`/`Scan`/`BatchGetItem`/`BatchWriteItem`
  on the table and all of its indexes; `sns:Publish` on the notifications topic; S3
  `PutObject`/`GetObject`/`DeleteObject` scoped to the `users/*` and `organizations/*`
  prefixes of the images bucket. (Note: there is no `s3:HeadObject` IAM action — `s3:GetObject`
  is what authorizes the `HeadObject` calls `ProfileImageService`/`OpportunityImageService`
  make.) Task execution role uses the AWS-managed `AmazonECSTaskExecutionRolePolicy`.

**Networking**
- One non-default VPC, `/16` CIDR, spanning `us-east-1a` and `us-east-1b`, with 4 subnets:
  2 route `0.0.0.0/0` to an Internet Gateway (public — the public ALB and its NAT Gateway
  live here) and 2 route `0.0.0.0/0` to that NAT Gateway (private — the ECS tasks and the
  internal ALB live here, which is how tasks with `assignPublicIp: DISABLED` still reach
  Cognito's OIDC discovery endpoint and pull from ECR at startup). None of the 4 subnets
  auto-assigns public IPs at the subnet level; the public ALB gets public IPs from being
  `internet-facing` in subnets routed to the Internet Gateway, not from subnet
  auto-assignment.
- 3 security groups: the public ALB's SG allows inbound `80` from `0.0.0.0/0` **and** from
  a dedicated VPC Link security group; the internal ALB's SG allows inbound `80` **only**
  from that same VPC Link security group (not the public internet); the ECS tasks' SG
  allows inbound `8080` only from the two ALB security groups (tasks are not reachable
  except through one of the two ALBs).

**Edge / API layer**
- Public Application Load Balancer `wevolunteer-alb-v2`: internet-facing, one **HTTP-only**
  listener on port 80 (**no HTTPS/TLS listener is configured**), forwards everything to one
  target group.
- Internal Application Load Balancer `wevolunteer-internal-alb`: `internal` scheme, one
  HTTP listener on port 80, forwards to a second target group, reachable only from the VPC
  Link security group above.
- Both target groups (`wevolunteer-target-group-v2`, `wevolunteer-internal-tg`): HTTP/1,
  port `8080`, target type `ip` (required for Fargate `awsvpc` mode), health check path
  **`/actuator/health`**, HTTP 200 matcher, 30s interval, 5s timeout, 5 healthy / 2
  unhealthy thresholds.
- **Amazon API Gateway (HTTP API) `wevolunteer-api`** — not mentioned anywhere in the
  previous README and not derivable from this repo's code; found live during this
  inventory. One route, `ANY /{proxy+}`, with an `HTTP_PROXY` integration through a
  dedicated **VPC Link** straight to the internal ALB's listener. CORS is configured at the
  API Gateway level, restricted to the deployed Amplify origin, methods
  `GET/POST/PUT/PATCH/DELETE/OPTIONS`, headers `authorization`/`content-type`. No custom
  domain is attached — it uses its default `execute-api` endpoint. Access logging is
  enabled to its own CloudWatch log group. **Not confirmable from this repo** which of the
  two paths — the public ALB directly, or API Gateway → VPC Link → internal ALB — the
  deployed frontend is actually configured to call; that lives in the frontend repository.

**Deployment pipeline**
- CodeConnections connection `wevolunteer-github-connection`: GitHub provider, status
  `AVAILABLE`.
- CodePipeline `wevolunteer-backend-pipeline`: pipeline type **V2**, exactly the 3 stages
  described in [CI/CD pipeline](#cicd-pipeline) (Source → Build → Deploy). It triggers on
  push to `main` via the V2 pipeline-level Git trigger — the older Source-action
  `DetectChanges` polling flag is explicitly `false`, i.e. push events (not polling) are
  what start a run.
- CodeBuild project `wevolunteer-backend-build`: **ARM** build image
  (`aws/codebuild/amazonlinux-aarch64-standard:4.0`, matching the ECS task's `ARM64`
  target), `BUILD_GENERAL1_SMALL` compute, **privileged mode enabled** (required for
  `docker build` inside CodeBuild), no build cache, 60-minute timeout, buildspec sourced
  from this repo's `buildspec.yml` (no inline override).

**Observability**
- 4 CloudWatch log groups: `/ecs/wevolunteer-backend`, `/aws/lambda/wevolunteer-notification-consumer`,
  `/aws/codebuild/wevolunteer-backend-build`, `/aws/apigateway/wevolunteer-api`. **None of
  the four has a retention policy set**, so logs accumulate indefinitely by default — a
  cost consideration if this environment stays up beyond the capstone (see the checklist
  below).

---

## Recreating the AWS infrastructure

**There is no Infrastructure-as-Code in this repository** — no Terraform, CloudFormation,
CDK, or SAM template was found anywhere in the tree, and none of the steps below can be run
as a single automated command. This is a manual, dependency-ordered guide assembled from
the application code, `buildspec.yml`, and the live read-only inventory above, for
rebuilding the infrastructure from nothing. Where a setting could not be confirmed from
either source, it's labeled **Not confirmable from this repo** rather than invented —
exact IAM trust-policy documents, the VPC's precise CIDR allocation, and ALB TLS/ACM
configuration (there currently isn't any — see above) fall in that category.

### 1. Region and IAM access

**Purpose**: everything below is built in one region. **Configuration**: `us-east-1`, used
consistently across every AWS-facing property in this repo (`aws.region` default,
`DynamoDbConfig`'s hardcoded region, `buildspec.yml`'s `AWS_DEFAULT_REGION`). **Dependencies**:
none — this is the starting point. **Required permissions**: an IAM principal (user or
role) with sufficient administrative access to create the resources in the steps below;
exact least-privilege policies for a *builder* identity are **not confirmable from this
repo** (only the *runtime* roles' policies were inventoried above). **Property/env var**:
`AWS_REGION` / `aws.region`. **Verify**:
```bash
aws sts get-caller-identity --profile <profile>
```

### 2. Cognito user pool and app client

**Purpose**: issues and validates the JWTs every non-health endpoint requires. **Important
configuration**: password policy (8+ chars, upper/lower/number/symbol required, 7-day temp
password validity — confirmed live, reasonable to reuse); one app client, **public (no
client secret)**, OAuth authorization-code flow, scopes `email openid phone`, callback/logout
URLs set to the frontend's local dev URL and its deployed origin, access/ID token validity
60 minutes, refresh token validity 5 days. **Dependencies**: none. **Required permissions**:
`cognito-idp:CreateUserPool`, `cognito-idp:CreateUserPoolClient` for the builder identity.
**Property**: `spring.security.oauth2.resourceserver.jwt.issuer-uri` in
`application.properties` must be updated to the new pool's issuer URL
(`https://cognito-idp.<region>.amazonaws.com/<new-pool-id>`); the frontend's Cognito
configuration (out of scope of this repo) must point at the new pool ID and app client ID.
**Verify**:
```bash
aws cognito-idp describe-user-pool --user-pool-id <new-pool-id>
```

### 3. DynamoDB table and all four GSIs

**Purpose**: the single-table store for every entity — see
[DynamoDB data model](#dynamodb-data-model). **Important configuration**: table name
**exactly** `WeVolunteer` (hardcoded in every `DynamoDb*Repository` class — not
configuration-driven, so it cannot be renamed without an application code change); `PK`
(String, partition) / `SK` (String, sort); four GSIs, **exactly** named
`GSI1_OpenOpportunities` (`GSI1PK`/`GSI1SK`), `GSI2_Category` (`GSI2PK`/`GSI2SK`),
`GSI3_Location` (`GSI3PK`/`GSI3SK`), `GSI4_Organization` (`GSI4PK`/`GSI4SK`), all String
keys, all with **`ALL` projection** (confirmed live in the current deployment).
`PAY_PER_REQUEST` billing mode (confirmed live). No Streams, no TTL, deletion protection
disabled (all confirmed live — reasonable defaults to reuse, not required by the code).
**Dependencies**: none. **Required permissions**: `dynamodb:CreateTable`; at runtime, the
ECS task role needs `dynamodb:GetItem/PutItem/UpdateItem/DeleteItem/Query/Scan/BatchGetItem/BatchWriteItem`
on the table and `.../index/*`. **Property**: none — the table name and region are
hardcoded in `DynamoDbConfig` and the repository classes, so recreating the table under a
different name requires editing that code, not just AWS-side configuration. **Verify**:
```bash
aws dynamodb describe-table --table-name WeVolunteer --query "Table.{Status:TableStatus,GSIs:GlobalSecondaryIndexes[*].IndexName}"
```

### 4. S3 bucket security and CORS

**Purpose**: private object storage for profile and opportunity images, accessed only via
short-lived pre-signed URLs. **Important configuration**: versioning **Enabled**; default
encryption **SSE-S3 (AES256)**; all four Block Public Access settings **true**; no bucket
policy needed (IAM-only); CORS allowing `GET`/`PUT` with an allowed `Content-Type` header
and `ETag` exposed, from the frontend's local dev and deployed origins (confirmed live —
reuse this shape). **Dependencies**: none. **Required permissions**: `s3:CreateBucket`,
`s3:PutBucketVersioning`, `s3:PutEncryptionConfiguration`, `s3:PutBucketPublicAccessBlock`,
`s3:PutBucketCors`; at runtime, the ECS task role needs `s3:PutObject`/`s3:GetObject`/`s3:DeleteObject`
scoped to `users/*` and `organizations/*` under the new bucket (note there is no
`s3:HeadObject` IAM action — `s3:GetObject` covers the app's `HeadObject` calls).
**Property**: `aws.s3.profile-images-bucket` / `PROFILE_IMAGES_BUCKET` — despite the name,
this single bucket/property is used for **both** profile images and opportunity images (see
[Known non-obvious behaviors](#known-non-obvious-behaviors)). **Verify**:
```bash
aws s3api get-bucket-versioning --bucket <bucket-name>
aws s3api get-public-access-block --bucket <bucket-name>
```

### 5. SNS topic

**Purpose**: the fan-out point the backend publishes notification events to. **Important
configuration**: default AWS-managed access policy (account-owner only) is sufficient —
confirmed that's what's live today; no custom policy is required by the code.
**Dependencies**: none (the SQS subscription is step 7). **Required permissions**:
`sns:CreateTopic`; at runtime, the ECS task role needs `sns:Publish` on it. **Property**:
`aws.sns.topic-arn` / `SNS_TOPIC_ARN`. **Verify**:
```bash
aws sns get-topic-attributes --topic-arn <topic-arn>
```

### 6. SQS queue and dead-letter queue

**Purpose**: decouples SNS delivery from Lambda execution and gives failed notification
deliveries a place to land instead of disappearing. **Important configuration**: main queue
— visibility timeout 60s, retention 4 days, SQS-managed SSE enabled (all confirmed live);
DLQ — visibility timeout 30s, retention 4 days, SSE enabled; redrive policy on the main
queue sends a message to the DLQ after **3** failed receives (`maxReceiveCount: 3`), which
matters because the Lambda **never catches** parsing or SES-send failures — every such
failure is a redelivery, so without a DLQ, a permanently-unparseable or permanently-failing
message would retry forever. **Dependencies**: none (SNS subscription is step 7).
**Required permissions**: `sqs:CreateQueue`, `sqs:SetQueueAttributes`; at runtime, the
Lambda's execution role needs `sqs:ReceiveMessage`/`DeleteMessage`/`GetQueueAttributes` on
the main queue (not the DLQ). **Property**: none directly — the queue is wired to the
Lambda via the event-source mapping in step 11, not an application property.
**Verify**:
```bash
aws sqs get-queue-attributes --queue-url <queue-url> --attribute-names RedrivePolicy
```

### 7. SNS subscription and SQS resource policy

**Purpose**: connects the topic to the queue. **Important configuration**: protocol `sqs`,
endpoint = the queue's ARN, **raw message delivery NOT enabled** — the Lambda's parsing
code expects and primarily handles the SNS envelope shape (`{"Message": "<json>", ...}`),
with a raw-body fallback only. The queue also needs a resource policy allowing
`sns.amazonaws.com` to `sqs:SendMessage`, conditioned on `aws:SourceArn` equaling the
topic's ARN (confirmed live — this is the standard SNS→SQS subscription policy shape).
**Dependencies**: the topic (step 5) and queue (step 6) must exist first. **Required
permissions**: `sns:Subscribe`, `sqs:SetQueueAttributes` (to attach the resource policy).
**Property**: none. **Verify**:
```bash
aws sns list-subscriptions-by-topic --topic-arn <topic-arn>
```

### 8. SES identity and sandbox considerations

**Purpose**: the actual email-sending service the Lambda calls. **Important configuration**:
at minimum, verify one email-address identity to use as the sender. **This account is
currently in SES sandbox mode** (`ProductionAccessEnabled: false`, confirmed live) — in
sandbox mode, SES can only send *to* addresses/domains that are *also* verified identities,
and the 24-hour send quota/rate are heavily limited (200 emails/day, 1/second here).
Request production access before relying on this for real users. **Dependencies**: none.
**Required permissions**: `ses:CreateEmailIdentity` (or the SESv1 equivalent); at runtime,
the Lambda's execution role needs `ses:SendEmail` scoped to the specific verified identity
ARN(s). **Property**: `SES_FROM_EMAIL` (Lambda environment variable — see step 10; the
Lambda fails fast at construction if this is unset or blank). **Verify**:
```bash
aws sesv2 get-account --query "ProductionAccessEnabled"
aws sesv2 get-email-identity --email-identity <verified-address>
```

### 9. Lambda function

**Purpose**: consumes SQS, renders the notification email, sends it via SES. **Important
configuration**: runtime **`java21`**; deployed as a Zip package (not a container image)
built by `./gradlew :notification-lambda:shadowJar`, producing
`notification-lambda/build/libs/notification-lambda-0.0.1-SNAPSHOT-all.jar`; handler
**`com.wevolunteer.notification.NotificationHandler::handleRequest`**; the currently
deployed function uses **512 MB memory, a 10-second timeout, and `x86_64` architecture**
(confirmed live — note this differs from the ECS task, which runs `ARM64`; nothing in the
code requires either architecture specifically, so this is just what's deployed today, not
a hard requirement). **Dependencies**: the SES identity from step 8 (for the environment
variable) and the SQS queue from step 6 (wired in step 11). **Required permissions**: for
the builder identity, `lambda:CreateFunction`, `iam:PassRole` for the execution role from
step 10. **Property**: none on the backend side — this is a separate deployable artifact.
**Verify**:
```bash
aws lambda get-function-configuration --function-name <function-name> --query "{Runtime:Runtime,Handler:Handler,State:State}"
```

### 10. Lambda IAM permissions and environment variables

**Purpose**: the execution role and configuration the function needs to actually run.
**Important configuration**: execution role needs the AWS-managed
`AWSLambdaBasicExecutionRole` (CloudWatch Logs) plus two scoped inline policies —
`ses:SendEmail` restricted to the specific verified identity ARN(s) from step 8, and
`sqs:ReceiveMessage`/`sqs:DeleteMessage`/`sqs:GetQueueAttributes` restricted to the queue
from step 6 (confirmed live — this is the exact shape to reuse, least-privilege rather than
wildcarded). Environment variables: **`SES_FROM_EMAIL`** (required — the Lambda throws
`IllegalStateException` at construction if unset or blank) and optionally `AWS_REGION`
(defaults to `us-east-1` if unset). **Dependencies**: steps 6, 8, 9. **Required
permissions**: `iam:CreateRole`, `iam:PutRolePolicy`/`iam:AttachRolePolicy` for the builder
identity. **Property**: `SES_FROM_EMAIL`, `AWS_REGION` (Lambda environment variables, not
`application.properties`). **Verify**:
```bash
aws lambda get-function-configuration --function-name <function-name> --query "Environment"
```

### 11. SQS event-source mapping

**Purpose**: what actually triggers the Lambda when a message arrives. **Important
configuration**: source = the SQS queue from step 6; **batch size 1, no batching window**
(confirmed live — one notification email per invocation, not batched); state `Enabled`.
**Dependencies**: steps 6 and 9. **Required permissions**:
`lambda:CreateEventSourceMapping`. **Property**: none. **Verify**:
```bash
aws lambda list-event-source-mappings --function-name <function-name> --query "EventSourceMappings[*].{State:State,BatchSize:BatchSize}"
```

### 12. ECR

**Purpose**: stores the backend's container images between CodeBuild and ECS. **Important
configuration**: repository name referenced in `buildspec.yml`:
`wevolunteer-backend-repository`; mutable tags, scan-on-push enabled, AES256 encryption, no
lifecycle policy (confirmed live — old images accumulate indefinitely unless one is added).
**Dependencies**: none. **Required permissions**: `ecr:CreateRepository`; CodeBuild's
service role needs `ecr:GetAuthorizationToken`, `ecr:BatchCheckLayerAvailability`,
`ecr:PutImage`, and related push permissions. **Property**: the repository name is a
literal in `buildspec.yml` (`ECR_REPOSITORY_NAME`), not an application property. **Verify**:
```bash
aws ecr describe-repositories --repository-names wevolunteer-backend-repository
```

### 13. VPC, subnets, route/public-access requirements, and security groups

**Purpose**: the network the ECS tasks and load balancers run in. **Important
configuration** (confirmed live, reasonable to reuse): one non-default VPC; 4 subnets
across 2 AZs — **2 public** (route `0.0.0.0/0` to an Internet Gateway; host the public ALB
and a NAT Gateway) and **2 private** (route `0.0.0.0/0` to that NAT Gateway; host the ECS
tasks and the internal ALB — this NAT route is what lets tasks with
`assignPublicIp: DISABLED` still reach Cognito's OIDC discovery endpoint and pull images
from ECR at startup); 3 security groups — one for the public ALB (inbound 80 from
`0.0.0.0/0` and from the VPC Link SG), one for the internal ALB (inbound 80 **only** from
the VPC Link SG), and one for the ECS tasks (inbound 8080 **only** from the two ALB
security groups — tasks are never reachable directly). Exact CIDR allocations are **not
reproduced here** (not needed to recreate the shape; any non-overlapping `/16` with 2
public + 2 private `/24`s works). **Dependencies**: none. **Required permissions**:
`ec2:CreateVpc`, `ec2:CreateSubnet`, `ec2:CreateInternetGateway`, `ec2:CreateNatGateway`,
`ec2:CreateRouteTable`, `ec2:CreateSecurityGroup`, and associated `*:Attach*`/`*:Associate*`
actions. **Property**: none directly. **Verify**:
```bash
aws ec2 describe-nat-gateways --filter "Name=vpc-id,Values=<vpc-id>" --query "NatGateways[*].State"
```

### 14. Public/internal ALBs, listeners, and target groups

**Purpose**: routes HTTP traffic to the ECS tasks and health-checks them. **Important
configuration**: two Application Load Balancers — one `internet-facing` in the public
subnets, one `internal` in the private subnets; each has **one HTTP listener on port 80**
(confirmed live — **there is currently no HTTPS/TLS listener or ACM certificate
configured**; adding TLS is a deliberate choice to make when rebuilding, not something to
carry forward unquestioned); each forwards to its own target group — HTTP/1, port `8080`,
target type `ip` (required for Fargate `awsvpc` mode), health check path
**`/actuator/health`**, HTTP 200 matcher, 30s interval, 5s timeout, 5 healthy / 2 unhealthy
thresholds. **Dependencies**: the VPC/subnets/security groups from step 13. **Required
permissions**: `elasticloadbalancing:CreateLoadBalancer`, `CreateListener`,
`CreateTargetGroup`. **Property**: the health-check path must stay in sync with whatever
`management.endpoints.web.exposure.include` exposes in `application.properties` (currently
just `health`). **Verify**:
```bash
aws elbv2 describe-target-health --target-group-arn <target-group-arn>
```

### 15. ECS task role and task execution role

**Purpose**: two distinct roles — the **task role** is what the running application code
uses (via the SDK's default credentials provider chain, exactly like local `bootRun`); the
**task execution role** is what ECS itself uses to pull the image and ship logs, and the
application never sees its credentials. **Important configuration**: task role needs
DynamoDB `GetItem/PutItem/UpdateItem/DeleteItem/Query/Scan/BatchGetItem/BatchWriteItem` on
the table and its indexes (step 3), `sns:Publish` on the topic (step 5), and S3
`PutObject/GetObject/DeleteObject` scoped to `users/*` and `organizations/*` under the
bucket (step 4) — this is the exact scope confirmed live, least-privilege rather than
account-wide. Execution role: the AWS-managed `AmazonECSTaskExecutionRolePolicy` is
sufficient (confirmed live — grants ECR pull and CloudWatch Logs write). **Dependencies**:
steps 3, 4, 5. **Required permissions**: `iam:CreateRole`, `iam:PutRolePolicy`/`AttachRolePolicy`.
**Property**: neither role is referenced by any application property — they're wired into
the task definition (step 16) and used implicitly via the credentials provider chain.
**Verify**:
```bash
aws iam list-role-policies --role-name <task-role-name>
```

### 16. ECS task definition and service

**Purpose**: what actually runs the container. **Important configuration** (confirmed
live): **0.5 vCPU / 1024 MiB**, `awsvpc` network mode, `FARGATE` compatibility; one
container, port `8080`, environment variables **`PROFILE_IMAGES_BUCKET`** and
**`SNS_TOPIC_ARN`** pointing at steps 4 and 5, no Secrets Manager references; `awslogs`
driver to a `/ecs/<name>` log group (step 17); the currently deployed task uses **`ARM64`**
architecture — nothing in the application requires this specifically (the Dockerfile's base
images are multi-arch), but the CodeBuild image in step 18 must match whichever
architecture is chosen, since it builds the image that gets pushed to ECR. Service: desired
count 1, rolling deployment, **deployment circuit breaker enabled with rollback enabled**
(this is what makes ECS automatically roll back a deployment that never passes its target
groups' health checks — confirmed live, not merely assumed from `buildspec.yml`), minimum
healthy 100% / maximum 200%, registered against **both** target groups from step 14
simultaneously, tasks placed in the private subnets with `assignPublicIp: DISABLED`.
**Dependencies**: steps 12 (image), 14 (target groups), 15 (roles), 17 (log group).
**Required permissions**: `ecs:RegisterTaskDefinition`, `ecs:CreateService`,
`iam:PassRole` for both roles from step 15. **Property**: `PROFILE_IMAGES_BUCKET`,
`SNS_TOPIC_ARN` (task definition environment variables, matching the
`application.properties` property overrides). **Verify**:
```bash
aws ecs describe-services --cluster <cluster-name> --services <service-name> --query "services[0].{Running:runningCount,Desired:desiredCount,Rollout:deployments[0].rolloutState}"
```

### 17. CloudWatch logs

**Purpose**: where ECS, the Lambda, CodeBuild, and (if used) API Gateway write their logs.
**Important configuration**: log groups are created automatically by each service
(`awslogs-create-group: true` in the task definition; Lambda and CodeBuild create their own
by default) — no manual creation is strictly required. **None of the four log groups in
the current deployment has a retention policy set**, meaning logs are kept indefinitely by
default; explicitly setting a retention period (e.g. 14 or 30 days) is a cost optimization
worth making deliberately when rebuilding, not something to leave to the default.
**Dependencies**: the services that write to them. **Required permissions**:
`logs:CreateLogGroup`, `logs:PutRetentionPolicy`. **Property**: the ECS log group name is
set via the task definition's `logConfiguration` (`awslogs-group`), not an application
property. **Verify**:
```bash
aws logs describe-log-groups --log-group-name-prefix /ecs/<name> --query "logGroups[*].retentionInDays"
```

### 18. CodeBuild

**Purpose**: runs the test suite and builds/pushes the Docker image. **Important
configuration**: source = GitHub via the CodeConnections connection (step 19); environment
= **ARM container** (`aws/codebuild/amazonlinux-aarch64-standard:4.0`, `BUILD_GENERAL1_SMALL`
compute, **privileged mode enabled** — required for `docker build` inside CodeBuild;
confirmed live, and must match whatever architecture the ECS task in step 16 uses); buildspec
= this repo's `buildspec.yml` (not an inline override); 60-minute timeout; no build cache.
**Dependencies**: step 12 (pushes here), step 19 (source connection). **Required
permissions**: service role needs the standard CodeBuild logging/network permissions plus
ECR push (`ecr:GetAuthorizationToken`, `BatchCheckLayerAvailability`, `PutImage`, etc.) and
`codeconnections:UseConnection` for the GitHub source. **Property**: none — entirely driven
by `buildspec.yml` in this repo. **Verify**:
```bash
aws codebuild batch-get-projects --names <project-name> --query "projects[0].environment.image"
```

### 19. CodePipeline and GitHub CodeConnections

**Purpose**: wires GitHub pushes to CodeBuild to an ECS deployment. **Important
configuration**: a **CodeConnections** connection to GitHub must be created and manually
authorized in the AWS Console first (this step cannot be done via CLI alone — it requires
the GitHub OAuth handshake); pipeline type **V2**, 3 stages — Source (`CodeStarSourceConnection`
provider, watching `main`), Build (the CodeBuild project from step 18), Deploy (`ECS`
provider, consuming `imagedefinitions.json`, pointed at the cluster/service from step 16);
triggers on push to `main` via the V2 pipeline-level Git trigger (confirmed live — not the
older polling-based `DetectChanges` flag, which is explicitly off). **Dependencies**: steps
16, 18. **Required permissions**: the pipeline's service role needs
`codebuild:StartBuild`/`BatchGetBuilds`, `ecs:DescribeServices`/`UpdateService` (or
equivalent ECS deploy-action permissions), and `codeconnections:UseConnection`.
**Property**: none. **Verify**:
```bash
aws codepipeline get-pipeline-state --name <pipeline-name> --query "stageStates[*].{Stage:stageName,Status:latestExecution.status}"
```

### 20. Frontend values that must be updated after recreating the backend

Everything below lives in the **frontend** repository/deployment, not here, but every one
of these values changes when the backend infrastructure is rebuilt:

- The Cognito user pool ID and app client ID from step 2.
- Whichever base URL the frontend actually calls — the public ALB's DNS name, or the API
  Gateway endpoint from the live inventory above; **not confirmable from this repo** which
  one the current frontend deployment uses.
- If a new API Gateway/VPC Link/internal ALB path is rebuilt, its new `execute-api`
  endpoint (there's no custom domain today, so this URL changes every time the API is
  recreated).
- The CORS allow-list on both the backend (`SecurityConfig`) and, if recreated, the API
  Gateway — both need the frontend's real deployed origin(s), not just `localhost`.

### 21. End-to-end verification

Once every step above is in place, verify the full chain rather than trusting each piece in
isolation:

```bash
# 1. Backend is up and reachable
curl https://<alb-or-api-gateway-host>/actuator/health

# 2. A real Cognito-issued token is accepted (replace with a real token from the frontend/OAuth flow)
curl -H "Authorization: Bearer <token>" https://<host>/auth/me

# 3. DynamoDB round-trip
curl -H "Authorization: Bearer <token>" https://<host>/opportunities

# 4. S3 pre-signed upload flow (issues a URL; actually uploading is a separate manual step)
curl -X POST -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"contentType":"image/jpeg"}' https://<host>/users/me/profile-image/upload-url

# 5. Notification pipeline: register for an opportunity, then check
aws sqs get-queue-attributes --queue-url <queue-url> --attribute-names ApproximateNumberOfMessages
# and the Lambda's CloudWatch Logs for a successful send (or a dead-lettered failure in the DLQ)
```

---

## Infrastructure teardown and recreation checklist

A planning checklist for the transition out of the capstone environment — **not** an
automated script. None of the steps below that remove or export data are destructive AWS
CLI commands you can copy-paste unattended; each one needs a human decision first (what to
keep, which profile/account to run against). Treat every unchecked box as a decision point,
not a command to run blindly.

**Before tearing anything down**
- [ ] Capture the final configuration of every resource in
      [Current AWS infrastructure](#current-aws-infrastructure) — re-run the `describe`/`get`/`list`
      commands used to build that section and save the output somewhere durable (this repo
      is a fine place, in a dated file, if the team wants a longer-lived record than this
      README's point-in-time snapshot).
- [ ] Record every non-secret resource name (table name, bucket name, topic/queue names,
      cluster/service names, etc.) — the list in
      [Current AWS infrastructure](#current-aws-infrastructure) is a starting point.
- [ ] If the team wants to preserve any of the current demo/production DynamoDB data,
      export it manually first — e.g. `aws dynamodb scan --table-name WeVolunteer` piped to
      a file, or AWS's built-in export-to-S3 feature. There is no export tooling in this
      repo (see [Seed and demo data](#seed-and-demo-data)); once the table is deleted, its
      contents are gone.
- [ ] Save the demo recording and presentation URLs into
      [Project Links](#project-links) in this README (currently placeholders) before access
      to wherever they're hosted lapses.

**Tearing down**
- [ ] Tear down the cost-incurring resources: the ECS service/cluster, the two ALBs and
      their NAT Gateway, the API Gateway/VPC Link, DynamoDB table, S3 bucket contents,
      Lambda function, and CodeBuild/CodePipeline. (NAT Gateways and ALBs bill hourly
      regardless of traffic — these are usually the highest-priority resources to remove
      if the goal is stopping ongoing cost.)
- [ ] **Retain the GitHub repositories** (this backend and the frontend) — nothing about
      removing AWS resources requires or implies deleting source code or its history.

**Recreating (when needed)**
- [ ] Recreate resources in the dependency order laid out in
      [Recreating the AWS infrastructure](#recreating-the-aws-infrastructure) (region/IAM →
      Cognito → DynamoDB → S3 → SNS → SQS/DLQ → subscription/policy → SES → Lambda → Lambda
      permissions/env vars → event-source mapping → ECR → networking → ALBs/target groups →
      ECS roles → ECS task definition/service → CloudWatch → CodeBuild → CodePipeline/CodeConnections).
- [ ] Update `application.properties`'s Cognito issuer URI, and the environment variables
      (`PROFILE_IMAGES_BUCKET`, `SNS_TOPIC_ARN`, `SES_FROM_EMAIL`, `AWS_REGION`) to the
      newly created resources' real values.
- [ ] Redeploy the backend (push to `main` once the pipeline exists, or build/push the
      Docker image and update the ECS service manually the first time, before the pipeline
      is wired up).
- [ ] Deploy the notification Lambda (`./gradlew :notification-lambda:shadowJar`, then
      upload/update the function code) and confirm its event-source mapping is `Enabled`.
- [ ] Update the frontend's environment variables/config — Cognito pool/client IDs, and
      whichever base URL it's meant to call (see step 20 in
      [Recreating the AWS infrastructure](#recreating-the-aws-infrastructure)).
- [ ] Verify end-to-end, in this order, before considering the environment ready:
  - [ ] Authentication — a real login through the frontend (or direct OAuth flow) returns a
        token that `GET /auth/me` accepts.
  - [ ] Health — `GET /actuator/health` returns `200 {"status":"UP"}`.
  - [ ] Database — a simple authenticated read (e.g. `GET /opportunities`) succeeds.
  - [ ] Images — a profile or opportunity image upload-URL request, upload, and attach
        round-trip succeeds.
  - [ ] Registrations — register for and cancel a test opportunity.
  - [ ] Waitlist — join a full opportunity's waitlist and confirm auto-promotion after a
        cancellation.
  - [ ] Notifications — confirm a registration/cancellation email is actually delivered (or,
        in SES sandbox mode, confirm it reaches the SQS→Lambda step and only fails at the
        final SES send because the recipient isn't a verified sandbox identity).
  - [ ] Close/reopen — close a test opportunity (confirm active registrations are
        cancelled) and reopen it (confirm it's usable again).

---

## Security notes and known limitations

This section exists so security-relevant behavior isn't left buried inside general prose
elsewhere in this document. Everything below is directly derivable from the public source
code in this repository — nothing here describes an exploit technique or operational
detail beyond what anyone with read access to this repo could already see themselves.

**Authorization model.** Every non-`/actuator/health` endpoint requires a valid Cognito
JWT, but *authorization beyond that* is ownership-based, not role-based: the `User.role`
field (`VOLUNTEER`/`ORGANIZATION`) is display data only and is never checked by any
controller or service. Ownership is enforced by comparing the caller's JWT subject
(`jwt.getSubject()`) to the record being acted on — consistently on `/me`-scoped and
`/organizations/me`-scoped routes, but **not on every endpoint**:

- `PATCH /opportunities/{opportunityId}` takes no JWT subject at all — any authenticated
  caller can edit any opportunity by ID, regardless of ownership. (`.../close` and
  `.../reopen` on the same resource do check ownership.)
- `POST /registrations` takes `userId` from the request body, not the JWT — any
  authenticated caller can register any user ID for any opportunity.
- `DELETE /registrations/{userId}/{opportunityId}` takes both IDs from the path,
  unchecked against the caller (`DELETE /registrations/me/{opportunityId}` is the
  JWT-scoped equivalent that does check).
- `GET /users/{userId}` and `GET /users/{userId}/registrations` let any authenticated
  caller look up any other user's profile or registration history by ID.
- `DELETE /users/{userId}`, `DELETE /organizations/{organizationId}`, and
  `PATCH /organizations/{organizationId}` (the non-`/me` variants) perform no ownership
  check.

**Transport.** Both load balancers currently serve plain HTTP on port 80 — there is no
HTTPS/TLS listener or ACM certificate attached to either the public or internal ALB (see
[Current AWS infrastructure](#current-aws-infrastructure)). The API Gateway HTTP API, if
that's the path in use, does terminate TLS by default on its `execute-api` endpoint;
whichever path the frontend actually calls determines whether traffic to this backend is
encrypted in transit.

**Notification pipeline.** SES is in sandbox mode for this project's AWS account (see
[Current AWS infrastructure](#current-aws-infrastructure)), so real emails only reach
individually-verified recipient addresses today. Two SES identities are verified as
individual email addresses; no domain identity is configured. Notification-publishing
failures from the backend to SNS are swallowed by design (logged, not surfaced to the
caller), and the notification Lambda's dead-letter queue held undelivered messages at the
time of this inventory.

**Data.** The DynamoDB table has deletion protection **disabled**. The S3 image bucket has
all four Block Public Access settings enabled and no bucket policy, so object access is
governed entirely by IAM (the ECS task role); there is no known path to public object
exposure in the current configuration.

None of the above is a judgment on whether the current behavior is acceptable for this
project's context (a capstone demo, not a production system handling sensitive user data)
— it's a factual record for whoever picks this up next, especially if the scope ever
changes to something with real users and real risk.

---

## Known non-obvious behaviors

A short index of operational quirks confirmed in code that are easy to miss — see
[Security notes and known limitations](#security-notes-and-known-limitations) for the
authorization-specific list; this one is everything else:

- `GET /health` requires authentication; only `GET /actuator/health` is public.
- The DynamoDB client's region is hardcoded to `us-east-1` and ignores `aws.region`; the S3
  and SNS clients honor it.
- `aws.s3.profile-images-bucket` (`PROFILE_IMAGES_BUCKET`) is one bucket used for both
  profile images and opportunity images, despite the property name.
- Closing an opportunity does not clean up its waitlist; joining a waitlist does not send a
  notification email.
- Opportunity capacity edits aren't validated against the current `registeredCount`.
- `POST /registrations`' response numbers are computed from a pre-transaction read and can
  be briefly stale under concurrency, even though the DynamoDB counter itself is correct.
- The `WeVolunteer` table name and all four GSI names are hardcoded across the repository
  classes, not configuration-driven.

---

## Troubleshooting

**`software.amazon.awssdk.core.exception.SdkClientException` / `AccessDeniedException` on
requests that touch DynamoDB, S3, or SNS**
Confirm credentials are actually resolvable and current:
```bash
aws sts get-caller-identity
```
If you're using `run.sh`, make sure a working AWS CLI profile is active/default *before*
running it — the script exports whatever `aws configure export-credentials` currently
resolves, it doesn't prompt for one.

**Context fails to start / hangs on startup**
`bootRun` and the one `@SpringBootTest` both resolve the Cognito issuer's OIDC discovery
document over the network at startup. If you have no outbound network access (e.g. an
isolated CI runner or sandbox), this step will fail or hang — this is unrelated to AWS
credentials.

**`aws.s3.profile-images-bucket is not configured` / `aws.sns.topic-arn is not
configured`**
These fail fast (`IllegalStateException`) if explicitly overridden to blank. They ship
with working defaults in `application.properties`, so this normally only happens if
`PROFILE_IMAGES_BUCKET` or `SNS_TOPIC_ARN` is set to an empty string in your environment.

**Docker build fails with HTTP 429 pulling a base image**
Shouldn't happen with the current `Dockerfile` — it intentionally uses
`public.ecr.aws/docker/library/...` images rather than Docker Hub to avoid Docker Hub's
rate limits. If you've modified the base images, this is the tradeoff you're reintroducing.

**A registration/cancellation succeeded but no email arrived**
Notification publishing from the backend to SNS is intentionally best-effort — check the
backend logs for `Failed to serialize notification event` / `Failed to publish notification
event` first. If publishing succeeded, check the Lambda's CloudWatch Logs for parsing or
SES failures next (see [SES sandbox mode](#ses-sandbox-mode) — an unverified recipient in a
sandboxed SES account is a common cause).

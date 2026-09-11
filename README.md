# DevMetrics Service

Spring Boot REST API for the DevMetrics GitHub analytics platform. It authenticates users with GitHub OAuth, connects repositories, synchronizes repository activity, persists metric snapshots, and serves the data used by the dashboard, comparisons, trends, and exports.

The React frontend lives in the sibling [`Dev-Metrics`](../Dev-Metrics) repository. Its README describes the dashboard experience and frontend structure.

## Backend capabilities

- Authenticate users through GitHub OAuth2 and establish an application session with short-lived access and refresh JWT cookies.
- Keep access and refresh tokens in `HttpOnly`, `SameSite=Lax` cookies; no application JWT is exposed in a URL or readable by frontend JavaScript.
- List repositories visible to the authenticated GitHub user and validate a repository with GitHub before connecting it.
- Enforce repository ownership before synchronizing a repository or reading its metrics.
- Soft-disconnect repositories by setting `connected` to `false`, preserving contributors, pull requests, commits, and snapshots linked by foreign keys.
- Reconnect the existing repository row instead of creating duplicate historical data.
- Synchronize pull requests, reviews, commits, contributors, and per-commit code statistics from GitHub.
- Backfill a repository's full commit history once, then request only commits newer than the newest stored commit on later syncs. A one-minute overlap plus SHA de-duplication protects against timestamp-boundary gaps.
- Compute and persist 13 analytics values after each successful sync, including pull-request flow, code churn, contributor velocity, and a 0–100 health score.
- Return the latest metric snapshot and time-bounded snapshot history for dashboard trends.

## Architecture

```text
React dashboard
      |
      | HTTPS + credentialed requests
      v
Spring Security -> GitHub OAuth2 -> OAuth2SuccessHandler
      |                              |
      |                              +-> issue HttpOnly JWT cookies
      v
Controllers -> Services -> Spring Data JPA -> PostgreSQL
      |             |
      |             +-> GitHub REST API
      |             +-> Metrics snapshot computation
      |
      +-> JWT cookie filter -> SecurityContext
```

Packages under `src/main/java/com/devMetrics/develop`:

- `Config`: CORS and Spring Security configuration.
- `auth`: GitHub OAuth success handling, JWT issuance, validation, and cookie authentication.
- `controller`: authentication, repository, sync, and metrics REST endpoints.
- `service`: GitHub API client, repository lifecycle, synchronization, and metric computation.
- `entity`: JPA domain model.
- `repository`: Spring Data access methods and snapshot queries.
- `dto`: GitHub API and metric response contracts.
- `scheduler`: periodic-sync component.
- `exceptions`: domain exceptions and centralized error handling.

## Authentication and authorization

1. A user signs in through GitHub OAuth2.
2. The OAuth success handler upserts the local user and stores the GitHub access token for subsequent GitHub API calls.
3. The service issues a 15-minute access-token cookie and a 7-day refresh-token cookie, then redirects to the frontend callback route.
4. `JwtAuthFilter` reads and validates the access-token cookie for protected requests.
5. When the access token expires, the frontend calls `POST /api/auth/refresh`; a valid refresh token issues a new access cookie.

All routes except the OAuth login flow and health endpoint require authentication. Repository, sync, and metrics operations additionally verify that the requested repository belongs to the authenticated user.

## Repository lifecycle

### Connect

The frontend submits a GitHub repository full name. The service fetches that repository from GitHub to validate access and save its metadata, including its name, language, default branch, visibility, stars, and owner.

### Disconnect and reconnect

Disconnecting never deletes a repository row. It changes `connected` to `false`, leaving dependent contributors, pull requests, reviews, commits, and historical snapshots intact. A later reconnect restores the original row, which avoids duplicate data and preserves relationships.

Only connected repositories are returned to the dashboard and accepted by sync or metrics endpoints.

## Synchronization flow

```text
Connect repository
      |
      v
POST /api/sync/{repoId} returns 202
      |
      v
Async sync service fetches GitHub PRs, reviews, commits, and contributors
      |
      v
Upsert records and de-duplicate immutable commit SHAs
      |
      v
Compute metrics and upsert today's snapshot
      |
      v
Update repository lastSyncedAt
```

The first successful commit sync imports the full available commit history and marks `commitHistorySynced` as complete. Later syncs use the newest stored commit timestamp as GitHub's `since` parameter. The one-minute overlap is intentional: duplicate SHAs are skipped, while commits near the previous timestamp are not missed.

Manual sync endpoints return `202 Accepted` immediately and run the work asynchronously. A `SyncScheduler` component is also present for six-hourly syncs; scheduling must be enabled with `@EnableScheduling` before it is relied on in a deployed environment.

## Metrics

A metric snapshot is created or updated once per repository per day after a successful sync. Snapshots preserve trend history without requiring the frontend to recompute metrics from raw activity data.

| Group | Metrics |
| --- | --- |
| Pull-request flow | Average merge time, average time to first review, open PR count, merged PR count, closed PR count |
| Code churn | Total lines added, total lines deleted, total commits, churn ratio |
| Team velocity | Active contributors, average PRs per contributor per week, average commits per contributor per week |
| Health | Composite health score from 0 to 100 |

Active contributors are calculated from commits or opened pull requests within the last 30 days. Velocity uses a rolling four-week window. The health score assigns up to 25 points each for review speed, merge speed, churn balance, and team activity.

## API summary

All API routes use the `/api` prefix and, except the OAuth login route, require the authentication cookies described above.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/api/auth/me` | Return the authenticated user's profile. |
| `POST` | `/api/auth/refresh` | Exchange a valid refresh cookie for a new access cookie. |
| `POST` | `/api/auth/logout` | Clear the access and refresh cookies. |
| `GET` | `/api/repositories` | List the current user's connected repositories. |
| `GET` | `/api/repositories/available` | List repositories visible to the user on GitHub. |
| `POST` | `/api/repositories` | Connect a repository; body: `{ "fullName": "owner/repository" }`. |
| `DELETE` | `/api/repositories/{id}` | Soft-disconnect a repository. |
| `POST` | `/api/sync/{repoId}` | Start asynchronous sync for one repository. |
| `POST` | `/api/sync/all` | Start asynchronous sync for all connected repositories of the user. |
| `GET` | `/api/metrics/{repoId}/latest` | Return the most recent metric snapshot. |
| `GET` | `/api/metrics/{repoId}/history?days=30` | Return snapshots from the requested lookback period. |
| `POST` | `/api/metrics/{repoId}/compute` | Recompute and return today's snapshot without fetching GitHub data. |

## Data model

- `User`: GitHub identity, profile data, and GitHub access token.
- `Repository`: connected GitHub repository metadata, sync state, and owner.
- `Contributor`: a repository-scoped GitHub contributor.
- `PullRequest`: pull-request state, author, timestamps, and change statistics.
- `PrReview`: individual PR review and reviewer information.
- `Commit`: immutable SHA, author, timestamp, message, and change statistics.
- `MetricsSnapshot`: daily aggregated values used by the dashboard.

## Run locally

### Requirements

- Java 17 or later
- Maven Wrapper (`./mvnw` is included)
- PostgreSQL
- A GitHub OAuth application with a callback URL that matches the local server configuration

### Configuration

Copy the application settings into environment-specific configuration or environment variables. Do not commit real database passwords, OAuth client secrets, JWT secrets, or GitHub tokens.

For local HTTP development, set `app.cookie.secure=false`; set it to `true` behind HTTPS in deployed environments. Set `app.frontend-url` to the frontend origin, typically `http://localhost:5173` during local development.

The default local service port is `8080` and the configured PostgreSQL schema is `devmetrics`.

### Start and verify

```bash
./mvnw test
./mvnw spring-boot:run
```

The frontend can then be started from the sibling `Dev-Metrics` directory:

```bash
npm install
npm run dev
```

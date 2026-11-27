# FlagGuard OSS — Self-Healing Release Controller for Self-Hosted Flagsmith

**FlagGuard OSS** turns self-hosted **Flagsmith** from a passive feature-toggle service into a closed-loop, self-healing release controller. By wrapping Spring Boot endpoints with a declarative `@FlagGuarded` annotation, FlagGuard continuously tracks real-time error rates and latency, streams live telemetry to a Next.js Control Plane over Server-Sent Events (SSE), and automatically invokes Flagsmith's REST Management API to disable degraded flags before cascading failures hit users.

## Architecture

- **Next.js Control Plane UI (`:3000`):** Subscribes to `/api/events/flags` via SSE for real-time telemetry, fault injection, and one-click recovery.
- **Spring Boot 3 Engine (`:8080`, Java 21):** Evaluates flags via Flagsmith's SDK API (`/api/v1/flags/`), tracks sliding error/latency windows via Spring AOP (`@FlagGuarded`), and triggers automated rollbacks.
- **Self-Hosted Flagsmith (`:8000`) + PostgreSQL:** Stores feature states and receives automated `PATCH /api/v1/environments/{envKey}/featurestates/{id}/` kill-switch calls.

## How Flagsmith Is Used

1. **Self-Hosted Core Stack:** Runs Flagsmith, Task Processor, and PostgreSQL locally via Docker Compose (`:8000`).
2. **Real-Time Flag Evaluation (`X-Environment-Key`):** Every call to `@FlagGuarded(flag = "checkout-v2", maxErrorRate = 0.15, maxLatencyMs = 800)` evaluates live flag state via Flagsmith's SDK endpoint (`/api/v1/flags/`) and routes traffic between `express-checkout-v2` and `legacy-checkout-v1`.
3. **Automated Kill-Switch (`Authorization: Api-Key`):** A background watchdog evaluates error rates and latency every 3 seconds. When an SLA threshold is breached, FlagGuard calls Flagsmith's Management API to flip `"enabled": false` automatically and logs an auditable rollback incident.
4. **One-Click Recovery:** Operators inspect the incident in the Next.js Control Plane (`:3000`) and re-enable the flag in Flagsmith once the underlying issue is resolved.

## Quickstart (One-Command Docker Setup)

1. Clone the repository and create your `.env` file:
   - `git clone https://github.com/Kotra-Lingaraju/flagguard-oss.git && cd flagguard-oss`
   - `cp .env.example .env`
2. Start Flagsmith infrastructure:
   - `docker compose up -d postgres flagsmith-migrate-db flagsmith flagsmith-task-processor`
3. Open `http://localhost:8000`, create an organisation and project, create a feature flag named `checkout-v2` (toggled **ON** in Development), and paste your **Client-side Environment Key**, **Server-side Environment Key**, and **Organisation API Key** into `.env`.
4. Boot the Spring Boot Engine and Next.js Control Plane:
   - `docker compose up -d --build flagguard-backend flagguard-ui`
5. Open the interfaces:
   - **Control Plane UI:** `http://localhost:3000`
   - **Spring Boot Telemetry API:** `http://localhost:18080/api/state`
   - **Self-Hosted Flagsmith UI:** `http://localhost:8000`

## Live Demo Flow (Reproducing Auto-Rollback)

1. Open `http://localhost:3000` and verify `checkout-v2` shows **ENABLED IN FLAGSMITH**.
2. Click **Chaos Mode: OFF** to inject simulated `502 Payment Gateway` failures into `checkout-v2`.
3. Click **Send 6 Checkout Requests**.
4. Within 3 seconds, the watchdog detects a 100% error rate (above the 15% SLA threshold), automatically flips `checkout-v2` **OFF** in Flagsmith, logs an incident in the Audit Log, and routes subsequent checkout calls to the safe `legacy-checkout-v1` fallback.
5. Refresh `http://localhost:8000` to confirm `checkout-v2` is toggled **OFF** inside Flagsmith, then click **Re-Enable Flag** in the Control Plane to restore service.

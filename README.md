# Renewal Backend — Spring Boot + MySQL

## Stack
Spring Boot 3.3 (Java 17) · Spring Data JPA · MySQL 8 · Lombok

## 1. Database Setup
```bash
mysql -u root -p < src/main/resources/schema.sql
mysql -u root -p < src/main/resources/data.sql
```
This creates `renewal_db` with all 22 tables and a working seed dataset (3 branches, agents, customers, 6 policies, claims, an active September-2026 rule set for Motor Private Car, notification templates, etc.) matching the demo data used in the Angular frontend's mock mode.

Update credentials in `src/main/resources/application.yml` (`spring.datasource.username/password`) to match your local MySQL.

## 2. Run
```bash
mvn spring-boot:run
```
API starts on `http://localhost:8080`. CORS is pre-configured for `http://localhost:4200` (the Angular dev server) — change `app.cors.allowed-origins` in `application.yml` if serving the frontend elsewhere.

## 3. Key Endpoints

| Purpose | Endpoint |
|---|---|
| **Policy 360° View** (the single-screen aggregate) | `GET /api/policies/{policyNo}/360-view` |
| Trigger the renewal batch on demand | `POST /api/renewal-batch/run?runDate=2026-09-14` |
| Worklist by status | `GET /api/renewals/status/{status}` (AUTO_RENEWAL_ELIGIBLE, REFERRAL_PENDING, REJECTED, RENEWED, BUSINESS_LOST) |
| Branch worklist | `GET /api/renewals/branch/{branchId}` |
| Agent worklist | `GET /api/renewals/agent/{agentId}` |
| Underwriter approve/reject/modify referral | `POST /api/renewals/{processingId}/underwriter-action` |
| Branch Manager mark renewed (branch-collected) | `POST /api/renewals/{processingId}/mark-renewed` |
| Agent performance rollup | `GET /api/branch-performance/branch/{branchId}` |
| Customer search by mobile | `GET /api/policies/search/by-mobile?mobileNo=...` |
| Full CRUD | `/api/products`, `/api/branches`, `/api/agents`, `/api/customers`, `/api/policies`, `/api/claims`, `/api/criteria` (+ `/{id}/bands`), `/api/rule-sets` (+ `/{id}/rules`), `/api/notifications/templates`, `/api/notifications/schedule`, `/api/payments` |

The daily batch (`RenewalBatchService`) is also `@Scheduled` for 06:00 every day, matching the "batch created every morning" requirement — no manual trigger needed in production.

## 4. Role-Based Access (demo-grade)
Every write-sensitive endpoint carries `@RequiresRole({...})`. The Angular client sends the current role in an `X-User-Role` header, checked by `RoleInterceptor`.

**This is a placeholder, not production auth** — anyone can spoof the header directly against the API. Before going live, replace `security/RequiresRole` + `RoleInterceptor` with Spring Security + JWT, and derive the role from a verified token instead of a client-supplied header.

## 5. What Still Needs Building
- Actual SMS/Email dispatch (currently `NotificationLog` is just a data model — wire it to an SMS gateway/SMTP provider and a scheduler that reads `NotificationScheduleConfig` day-offsets).
- Real payment gateway integration (`PaymentController#gatewayCallback` simulates the webhook — swap in your gateway's actual callback contract and signature verification).
- Spring Security/JWT login (`/api/auth/login` issuing tokens) — not built here, since the demo uses the mock role-header approach.
- Multi-vehicle/multi-driver policies (see schema notes doc — currently one set of vehicle/driver columns per policy).

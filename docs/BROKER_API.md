# Broker API v1

Partner-facing API under `/api/broker/v1`, separate from the internal `/api/**` endpoints.

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/broker/v1/policies?policyNo=` | Get one of your policies |
| POST | `/api/broker/v1/policies` | Submit a policy (brokerCode is taken from the API key) |
| GET | `/api/broker/v1/policies/renewal-quote?policyNo=` | Latest renewal quote |
| GET | `/api/broker/v1/renewals-due?fromDate=&toDate=&page=&size=` | Your active policies expiring in a range |

## Auth
Send `X-API-Key: <key>`. Each key maps to one broker code; all reads/writes are scoped to it.
A policy that belongs to another broker returns 404, same as a missing one.

## Onboarding a broker
In `application.properties` add an entry and set the key via an environment variable:

    broker.api.clients[1].broker-code=BRK002
    broker.api.clients[1].name=Another Broker
    broker.api.clients[1].api-key=${BROKER_BRK002_API_KEY:}

The `broker-code` must match `broker_code` in `policy_master`. A blank key disables the client.
Generate keys with e.g. `openssl rand -hex 32`. Serve over HTTPS only.

## OpenAPI spec
- The broker endpoints appear in the existing Swagger (`/swagger-ui.html`) and the existing spec `/v3/api-docs`, under the "Broker Policies" tag. No extra spec links were added.
- Static broker-only copy to hand to brokers: `docs/openapi/broker-api-v1.yaml` (set `servers.url` first)

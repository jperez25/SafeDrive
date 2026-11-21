# Notification provider (external, mocked)

Called by `rewards`. In the cluster it is the WireMock deployment `notification-mock` (`http://notification-mock:8080`).

`POST /v1/notifications`

Headers: `Content-Type: application/json`, `Idempotency-Key: <rewardId>` (the provider must dedupe on it),
`X-Correlation-Id: <correlation id>`.

Body (no PII, no device or position data):

```json
{ "rewardId": "4f6c2a9e-0d57-4d0a-8a0b-5a7c3e1f9b11", "driverId": "d-1", "kind": "safe_driver_bonus" }
```

Responses: `202` accepted. `5xx`, `429` and timeouts are transient (retried by `rewards`); any other `4xx` is permanent (dead-lettered).

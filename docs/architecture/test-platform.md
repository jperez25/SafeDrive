# SafeDrive: Test platform

> Status: DRAFT, PARKED. Drafted early; we are finishing the app side first.

## Modules

- `integration-tests/framework`: REST Assured clients, factories (`withX`), validators with child
  validators, constants, and a waiter (poll with timeout) for eventual consistency.
- `integration-tests/<service>`: black-box tests, one directory per service.
- `integration-tests/e2e`: cross-service flows, from ingest to premium.
- `integration-tests/contracts`: Pact and JSON Schema contract tests.
- `integration-tests/suites`: one TestNG XML suite per service, plus `e2e`, `contracts`, `smoke`, `master`.
- `simulator`: generates trips for N drivers and injects faults (duplicate, out-of-order, late,
  malformed, burst). Used by tests and load tests.

## Run order

`smoke` (gates everything) then `contracts` then per-service suites then `e2e`.
A smoke failure stops the run; a contract failure stops it before integration tests.

## Conventions

TestNG, REST Assured, Allure, `AbstractTest`. Tests are atomic and parallel-safe: every test creates its
own driver and trip IDs, and best-effort cleanup runs after it. Jackson 3 only (see code-style rules).

## Failure-mode matrix

| Scenario | Where it bites | What the test checks |
|---|---|---|
| Duplicate batch | `telemetry-ingest` | One publish; second call returns `200`; changed content returns `409` |
| Concurrent duplicates | `telemetry-ingest` | Exactly one event on `trip.raw` |
| Out-of-order batches | `trip-processor` | One correct trip regardless of arrival order |
| Late data after close | `trip-processor`, `scoring` | `trip.completed` revision + 1, score recomputed |
| Service down mid-pipeline | any consumer | No loss after restart; no double effect |
| Poison event | any consumer | Lands on the DLT with reason; replay succeeds once fixed |
| Eventual consistency | read APIs | Waiter converges; no fixed sleeps |

## Decision

The tracing helper follows a correlation ID through logs and topics only. Black-box tests do not read
service schemas, so there is no DB access.

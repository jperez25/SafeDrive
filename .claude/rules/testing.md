---
paths:
  - "integration-tests/**"
  - "services/**/src/test/**"
---
# Testing rules
- Unit tests live in the service or `commons`. Integration tests are black-box, in `integration-tests/<service>/`.
- Conventions: TestNG, REST Assured, Allure, an `AbstractTest` base class, validators with child
  validators, factories with `withX` builders, constants for shared values.
- Tests must be atomic and parallel-safe: unique data per test, best-effort cleanup.
- Async assertions use the shared waiter in `framework`. Never use `Thread.sleep`.
- Suites: one XML suite per service, plus `e2e`, `contracts`, `smoke`, `master`.
  Smoke gates everything; contracts run before integration tests.
- Cover failure modes: duplicates, out-of-order, late data, service down, DLQ, replay, eventual consistency.

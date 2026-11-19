---
paths:
  - "integration-tests/**"
  - "services/**/src/test/**"
---
# Testing rules
- Unit tests (including contract tests) live in the service or `commons`. Integration tests are black-box, in `integration-tests/<service>/`; reusable code goes in `integration-tests/core`; the full flow goes in `integration-tests/e2e`.
- Conventions: TestNG, REST Assured, Allure, an `AbstractTest` base class, validators with child
  validators, factories with `withX` builders, constants for shared values.
- Tests must be atomic and parallel-safe: unique data per test, best-effort cleanup.
- Async assertions use the shared waiter in `core`. Never use `Thread.sleep`.
- Inside a service, organize by feature: `Abstract<Feature>Test`, `<Feature>PositiveTest` and
  `<Feature>NegativeTest`, with both concrete classes extending the abstract one.
- Each service module has `src/test/resources/environment.<env>.json` and `suites/{master,regression,smoke}.xml`;
  `master.xml` calls the other two. Every new test class goes into `regression.xml` (and `smoke.xml` if it is a smoke check).
- Cover failure modes: duplicates, out-of-order, late data, service down, DLQ, replay, eventual consistency.

---
name: new-integration-test
description: Write a new black-box integration test for a SafeDrive service following project conventions (TestNG, REST Assured, Allure, validators, factories, Lombok models). Use when adding or extending tests in integration-tests/.
---
# New integration test
Args: service name and scenario.

1. Put the test in `integration-tests/<service>/` under the feature's package. Add it to the feature's
   `<Feature>PositiveTest` or `<Feature>NegativeTest`; both extend `Abstract<Feature>Test`, which extends
   `AbstractTest` from `core`. Create the feature's three classes if it is new.
2. Build input with a factory from `core` that returns the model (e.g. `aBatch().withTripId(null)`; Lombok `@With` / `toBuilder()`, full-word field names); create unique data per test so it is parallel-safe.
3. Call the service through the shared REST Assured client from `core`.
4. Assert with the fluent validators (`Validator.with(...)`, master then child, see `testing.md`), using the constants in `core/model/constants` (`Http`, `Statuses`, `Topics`, ...) for finite values; wait on async results with the shared waiter, never `Thread.sleep`.
5. Annotate with Allure (`@Epic`, `@Feature`, `@Story`) and give every `@Test` exactly one group from `core`'s `Groups`: `SMOKE` for smoke checks (health is always smoke), otherwise `REGRESSION`. Suites select by package and group, so no suite edit is needed for a class under the service's package.
6. Add best-effort cleanup. Follow `.claude/rules/testing.md`.

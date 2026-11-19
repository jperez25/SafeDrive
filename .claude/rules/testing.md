---
paths:
  - "integration-tests/**"
  - "services/**/src/test/**"
---
# Testing rules
- Unit tests (including contract tests) live in the service or `commons`. Integration tests are black-box, in `integration-tests/<service>/`; reusable code goes in `integration-tests/core`; the full flow goes in `integration-tests/e2e`.
- Conventions: TestNG, REST Assured, Allure, an `AbstractTest` base class, fluent validators,
  factories, constants for shared values.
- Validators are fluent and live in `core`: `Validator.with(response)` (or `with(message)`) returns a master
  validator that checks only what is true of any response or message (status, headers, topic, key). Its methods
  create child validators for the specific part (`.ingestBody()`, `.envelope()`, `.tripRawPayload()`), each a
  plain class that holds its value. Put master checks before the child call; children cannot go back up.
  Every check is named at the call site (`hasStatus`, `hasBatchId`); no hidden checks inside factories.
- Finite values in checks (HTTP status, body status, event type, health status) come from the constants in
  `core/model/constants`, grouped by concept (`Services`, `Topics`, `EventTypes`, `Http`, `Paths`, `Statuses`,
  `Groups`), never inline literals: `.hasStatus(Http.ACCEPTED)`. A new value goes in the file for its concept.
- Test models (request/response bodies, event payloads, anything in `core/model`) all follow one pattern:
  Lombok `@With` + `@Builder(toBuilder = true)` on a record. Use `withX` to change one field of a valid
  object, `toBuilder()` for several, `builder()` to build from scratch. No hand-written setters, builders or
  copy methods. Factories return the model itself (e.g. `aBatch().withTripId(null)`), never a parallel
  builder class that copies its fields.
- Model field names are full words, no abbreviations or contractions: `timestamp` not `ts`,
  `latitude` not `lat`, `batchSequence` not `batchSeq`, `speedKilometersPerHour` not `speedKmh`. `id` is the
  one accepted short form (`tripId`). When the contract uses a short wire name, keep the contract as is and map it
  with `@JsonProperty("ts") String timestamp` (the annotation is allowed, see code-style rules).
- Factory methods are named so the call reads as English: start with the article `a` or `an`
  (`aBatch()`, `aSpeedingBatch()`, `anInvalidBatch()`), never `createBatch()` or `newBatch()`.
  Chain overrides with `withX`: `aBatch().withTripId(null)` reads "a batch with no trip id".
- Random identifiers (correlation, batch, trip, driver, device) come only from `core`'s `Ids`
  (`Ids.correlation()`), never from `UUID.randomUUID()` inline or helpers on `AbstractTest`. A test that needs
  to look up or re-use an ID keeps it in a local variable and passes it explicitly.
- Tests must be atomic and parallel-safe: unique data per test, best-effort cleanup.
- Async assertions use the shared waiter in `core`. Never use `Thread.sleep`.
- Inside a service, organize by feature: `Abstract<Feature>Test`, `<Feature>PositiveTest` and
  `<Feature>NegativeTest`, with both concrete classes extending the abstract one.
- Each service module has `src/test/resources/environment.<env>.json` and `suites/{master,regression,smoke}.xml`;
  `master.xml` calls the other two and registers `SmokeGateListener` (from `core`), so a failing smoke test
  skips all of regression. Suites select by package (`com.qnt.safedrive.tests.<service>.*`) and TestNG group, so new classes
  need no suite edit. Each test has exactly one group from `core`'s `Groups`: `SMOKE` (service up, main path;
  all health tests) or `REGRESSION` (everything else): `@Test(groups = Groups.SMOKE)`.
- Services are reached over HTTP on localhost; start the forwards with `platform/scripts/port-forward.sh`.
- Cover failure modes: duplicates, out-of-order, late data, service down, DLQ, replay, eventual consistency.

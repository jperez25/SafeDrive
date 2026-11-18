---
name: new-integration-test
description: Write a new black-box integration test for a SafeDrive service following project conventions (TestNG, REST Assured, Allure, validators, factories). Use when adding or extending tests in integration-tests/.
---
# New integration test
Args: service name and scenario.

1. Put the test in `integration-tests/<service>/` and extend `AbstractTest`.
2. Build input with a factory (`withX` builder); create unique data per test so it is parallel-safe.
3. Call the service through the shared REST Assured client from `framework`.
4. Assert with validators and child validators; wait on async results with the shared waiter, never `Thread.sleep`.
5. Annotate with Allure (`@Epic`, `@Feature`, `@Story`) and add the class to the service suite XML.
6. Add best-effort cleanup. Follow `.claude/rules/testing.md`.

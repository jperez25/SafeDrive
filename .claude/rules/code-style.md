# Code style rules
- Java 25 Corretto, Maven. Package root `com.qnt.safedrive.<service>`.
- Structured JSON logs that always include the correlation ID. No secrets or PII in logs.
- Config via environment variables; `.env` is gitignored and never committed.
- Keep services thin: prefer simple, readable code over frameworks and abstraction.
- Do not commit until asked; no code before the plan is approved (see `docs/design/plan.md`).

# Spring Boot and Jackson
- Spring Boot 4.x only. Do not add Spring Boot 3.x dependencies or starters.
- Jackson 3+ only (`tools.jackson.*`: `tools.jackson.databind.json.JsonMapper`, `tools.jackson.core`). Never use Jackson 2 (`com.fasterxml.jackson.databind` / `com.fasterxml.jackson.core`) and never add the Spring Boot Jackson 2 compatibility module.
- Exception: the Jackson annotations package stays `com.fasterxml.jackson.annotation` (Jackson 3 still uses it). Nothing else under `com.fasterxml.jackson` is allowed.
- Test libraries (REST Assured, Pact, etc.) must be configured to use the Jackson 3 mapper; check transitive dependencies (`mvn dependency:tree`) so Jackson 2 does not leak in.
- Use `JsonMapper` (built via builder, one shared instance from Spring), not `new ObjectMapper()`.

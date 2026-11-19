---
name: add-event-contract
description: Add or change an event schema in docs/contracts with matching contract tests. Use when introducing a new Kafka event or modifying an existing one.
---
# Add event contract
Args: event name, producer service, consumer services.

1. Create or update `docs/contracts/<event>.v<N>.json` using the shared envelope fields.
2. Keep changes backward compatible; otherwise bump the version.
3. Add producer and consumer contract tests as unit tests in the producing and consuming services (schema checks for shared examples stay in `commons`).
4. Update the event catalog in `docs/architecture/`.
5. Follow `.claude/rules/contracts.md`.

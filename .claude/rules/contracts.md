---
paths:
  - "docs/contracts/**"
  - "commons/**"
---
# Contract rules
- Event schemas and API specs live in `docs/contracts/`, versioned, one file per event or API.
- Schema changes must be backward compatible; breaking changes need a new version.
- Every event uses the shared envelope: `eventId`, `correlationId`, `type`, `version`, `occurredAt`, `payload`.
- Update the contract and its contract tests in the same change.

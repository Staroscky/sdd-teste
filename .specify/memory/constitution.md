<!--
SYNC IMPACT REPORT
==================
Version change: (unversioned template) → 1.0.0
Principles added:
  - I. Reactive, Non-Blocking Architecture (new)
  - II. Contract-First Design (new)
  - III. Test Discipline (new)
  - IV. Structured Error Handling (new)
  - V. Observability (new)
Sections added:
  - Code Patterns (from user input §6)
  - Decisions Requiring Explicit Approval (from user input §7)
Sections removed: none
Templates reviewed:
  ✅ .specify/templates/plan-template.md — Constitution Check gate is generic; no update required
  ✅ .specify/templates/spec-template.md — scope/requirements sections align; no update required
  ✅ .specify/templates/tasks-template.md — task categories align; no update required
  ✅ .specify/templates/commands/ — no command files found; skipped
Follow-up TODOs:
  - None. All placeholders resolved.
-->

# extrato-bff Constitution

## Core Principles

### I. Reactive, Non-Blocking Architecture

All request handling MUST be non-blocking. Endpoints MUST be implemented using
Project Reactor (`Mono`/`Flux`). No blocking I/O is permitted on the request
thread. External calls MUST be made through reactive HTTP clients.

Rationale: the BFF is the aggregation point for multiple upstream calls; blocking
one thread degrades throughput for all concurrent requests under load.

### II. Contract-First Design

The BFF response contract (field names, structure, types) MUST be defined and
approved before implementation begins. No field may be added, renamed, or removed
without explicit approval. Downstream consumers are the authority on stability.

Rationale: breaking contract changes silently introduce integration regressions
in consumers that depend on a stable BFF interface.

### III. Test Discipline

Every service-layer behaviour MUST have a unit test. Every route exposed to
consumers MUST have an integration test exercising the full request path. No
coverage exceptions for the core aggregation logic. Tests MUST be written before
or alongside implementation — not after.

### IV. Structured Error Handling

Errors returned to consumers MUST conform to the project error envelope (HTTP
status code, application error code, human-readable message). Raw stack traces
MUST NOT appear in response bodies. Upstream failures MUST be mapped to
BFF-defined error codes before propagation.

### V. Observability

Every inbound request MUST emit structured log entries at entry and exit,
including HTTP status, latency, and outcome. A `correlationId` MUST be
propagated and included in all logs for the lifetime of the request.

## Code Patterns

Nomenclature MUST use English for all classes, methods, and variables.
Request/response DTOs MUST be Java records (immutable by construction). No
domain field may be `public` — all access MUST go through a getter or record
accessor. No conditional logic is permitted in constructors or field
initializers.

## Decisions Requiring Explicit Approval

The following changes MUST NOT be made without explicit team approval documented
before implementation:

- Adding any new dependency to `pom.xml`.
- Altering the BFF response contract (fields, structure, names).
- Introducing cache or shared state between requests.
- Any blocking synchronous call outside the request thread.

## Governance

This constitution supersedes all other practices and informal conventions.
Amendments MUST include a rationale, receive team review, and be propagated to
all dependent templates and documentation before taking effect. All PRs MUST
verify compliance with every applicable principle. Version bumps follow semantic
versioning: MAJOR for principle removal/redefinition, MINOR for additions,
PATCH for clarifications.

**Version**: 1.0.0 | **Ratified**: 2026-05-31 | **Last Amended**: 2026-05-31

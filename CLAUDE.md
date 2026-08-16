# Code rules

- **Structure**: flat folders/files; new layers only if reused 2+ times or file too large; match existing conventions.
- **Robustness**: validate inputs, handle errors explicitly, no silent failures, fail loudly with clear messages.
- **Readability**: simple over clever; names reflect business meaning, not just type.
- **Comments**: explain why (business purpose, edge cases, assumptions), not what; flag business rules at branch points; skip obvious comments.
- **Traceability**: small single-purpose functions; structured error/log messages (what+why); note reasons behind workarounds.

Goal: a mid-level dev with no context can trace and fix bugs unaided.

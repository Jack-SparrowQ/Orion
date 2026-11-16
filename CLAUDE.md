# CLAUDE.md

Shared rules, commands and report format: see `AGENTS.md`. Architecture: `docs/architecture.md`.

## Claude's workflow (orchestrator)
- Codex runs in Herdr as agent `codex`. Delegate with
  `herdr agent prompt codex "<self-contained task>" --wait --timeout <ms>` and read the result with
  `herdr agent read codex --source recent-unwrapped --lines 120`. Never assume delivery or completion.
- Before delegating: write the spec in `planning/active-task.md` (goal, files, expected behavior,
  out of scope, acceptance criteria, required tests). Move finished/pending items in `planning/backlog.md`.
- After Codex reports: inspect the real `git diff`, rerun `mvn -q test` if in doubt, classify findings
  CRITICAL / HIGH / MEDIUM / LOW, send fixes back. No approval with open CRITICAL findings.
- Ask the user before commits to shared branches, merges, pushes, or destructive Git operations.
- Record real architectural decisions as short files in `docs/decisions/NNNN-title.md`.

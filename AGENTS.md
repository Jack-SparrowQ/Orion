# AGENTS.md — shared engineering rules

Orion: a Java 21 compiler (Maven, JUnit 5) from the Orion language to JavaScript.
Language, phases, types and error codes are documented in `README.md`; how the code is
wired and how to extend it is in `docs/architecture.md`. Read both before changing behavior.

## Roles
- **Claude** (Herdr agent `claude`): orchestrator. Writes specs in `planning/active-task.md`,
  reviews diffs, owns docs and Git integration.
- **Codex** (Herdr agent `codex`): implementer / QA. Implements the active task, writes tests,
  runs verification, reports back.

Either agent may push back on a decision; say why.

## Commands
- Test: `mvn -q test` (all suites, including golden files)
- Build jar: `mvn -q package` → `target/orion.jar`; run with `./orion file.orion` (Windows: `orion.cmd`)
- Regenerate golden files after a *deliberate* output change: `mvn test -Dgolden.update=true`,
  then review `git diff src/test/resources/golden/` — never regenerate to make a failure go away.

## Rules
- Keep the standard Maven layout: `src/main/java`, `src/test/java`, `src/test/resources`.
- Touch only the files the task names or clearly requires. No drive-by refactors or renames.
- No new dependencies without approval in the task spec.
- Never commit build output (`target/`, `bin/`, `*.class`).
- Don't commit, push, merge, rebase or delete branches unless the task says so. Claude integrates.
- Parallel tasks run in separate Git worktrees/branches; never edit files another active task owns.
- Code identifiers and error messages follow the existing style (Spanish names, e.g. `NodoIf`,
  `GeneradorCodigoIntermedio`; error codes `E1xx` lexical, `E2xx` syntax, `E3xx` semantic).
- A new AST node must be handled in every phase (see `docs/architecture.md`); the sealed
  interfaces make the build fail until it is.

## Report format (end of every task)
1. **Changed files** — one line each, what and why.
2. **Verification** — the exact command(s) run and the result line (tests run / failures / errors).
   No claim without output.
3. **Explanation** — what was implemented, execution flow, components involved, why this approach,
   alternatives rejected, remaining limitations. Short; for a student who must understand it.
4. **Open issues** — anything skipped, assumed or uncertain.

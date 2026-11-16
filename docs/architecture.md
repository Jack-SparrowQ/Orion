# Architecture

The language, grammar, type rules, error codes, IR format and CLI are described in `README.md`
and `docs/CLI.md`. This file covers how the code is wired and what a change must touch.

## Pipeline

`Compiler` (entry point, `com.compiler`) parses options (`cli/Options`) and runs the phases in
order. A phase runs only if every earlier phase finished without errors; within a phase all
errors are collected in `error/ErrorHandler` and reported together.

| Phase | Package / class | Input → output |
|---|---|---|
| Lexical | `lexer/Lexer` (`Tokenizer`, `Token`, `TokenType`) | source → tokens |
| Syntax | `parser/Parser` (recursive descent, error recovery) | tokens → AST (`parser/ast`) |
| Symbols | `semantic/SymbolTable` (`Symbol`, `SymbolCategory`, `Type`) | AST → declarations with inferred types |
| Semantic | `semantic/SemanticAnalyzer` | AST + symbols → type/scope errors |
| IR | `ir/GeneradorCodigoIntermedio` (`Cuadrupla`, `FuncionIR`, `ProgramaIR`) | AST + types → `ProgramaIR`: functions + main, unique names, type table (`--emit-ir` prints it as `.tac`) |
| Optimization | `Compiler.OPTIMIZACIONES` + `ir/opt/*` (repeated to a fixed point, max 10 rounds) | `ProgramaIR` → `ProgramaIR`; walkthrough in `docs/optimizacion.md`, design in ADR 0002 |
| Codegen | `codegen/GeneradorJS` | `ProgramaIR` → JavaScript (`.js`): linear code, or a `switch` dispatcher when a body has labels. `util/JSGenerator` (AST → JS) is kept only as a test oracle — see `docs/decisions/0001-js-desde-ir.md` |
| Debug output | `util/ASTPrinter` | AST → text (`--ast`) |

## Key invariant: sealed AST

`Sentencia` and `Expresion` are sealed interfaces; every node (`NodoIf`, `NodoBinario`, ...) is a
`record`. Each phase walks the tree with an exhaustive `switch`, so a new node type does not compile
until every phase handles it.

## Adding a language feature — checklist

1. `TokenType` / `Lexer` — new tokens, plus lexical error codes (`E1xx`).
2. `Parser` + a new `Nodo*` record in `parser/ast` — syntax errors `E2xx`.
3. `SymbolTable` / `SemanticAnalyzer` / `Type` — typing and scope rules, errors `E3xx`.
4. `GeneradorCodigoIntermedio` — TAC lowering.
5. `JSGenerator` — JavaScript output (runtime checks if the semantics need them).
6. `ASTPrinter`.
7. Tests: unit tests per phase and a golden file in `src/test/resources/golden/`.
8. `README.md`: grammar, types table, error codes.

## Tests

`src/test/java/com/compiler/`: per-phase unit suites, `CliTest`/`OptionsTest` for the CLI, and
golden-file end-to-end tests (`GoldenFile`, `CompilationResult`). See `AGENTS.md` for commands.

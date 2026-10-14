from pathlib import Path
content = r"""# Orion Command-Line Interface (CLI) Design

> **Status:** Design proposal only. No implementation changes have been made.
>
> This document adapts the proposed CLI requirements to the current capabilities and structure of the Orion compiler.

## 1. Current Project Constraints

The following facts have been verified in the source code and `pom.xml`:

- **Input:** The source program is hard-coded in `Main` as `CODIGO_ORION`. The compiler does not read source files or parse command-line arguments.
- **Output:** Output filenames are fixed in the current working directory: `program.js` and `programa.tac`. Both are written on every run.
- **Backend:** `JSGenerator` is the only backend. Three-address code (TAC) is an intermediate representation, not a backend.
- **Compiler phases:** Lexical analysis, parsing, symbol-table construction, semantic analysis, intermediate representation (IR), and JavaScript generation all exist. Starting at step 3, each phase is implemented as a separate method in `Main`.
- **Console output:** Output is hard-coded throughout the project. `Tokenizer.run()`, `SemanticAnalyzer`, `SymbolTable.mostrar()`, `ASTPrinter`, and `ErrorHandler` print directly to the console. In addition, `ErrorHandler.mostrarErrores` currently writes to stdout instead of stderr.
- **Exit status:** The process always exits with status `0`, even when errors occur (issue F6).
- **Version and packaging:** The only version declaration is in `pom.xml` (`1.0-SNAPSHOT`). There is no JAR plugin configuration, `orion` launcher script, or `examples/` directory.
- **Language limitations:** The lexer does not support string literals. Therefore, `hello.orion` cannot use `print("hello")`. Use this valid example instead:

  ```orion
  let a = 10;
  let b = 3;
  print(a + b * 2);
  ```

## 2. General CLI Rules

### Command syntax

```text
orion [options] <file>
```

Options may appear before or after the input file. Both `--output <path>` and `--output=<path>` are accepted. Combined short options such as `-hV` are not supported.

### Standard streams

- **stdout** is reserved for explicitly requested data: tokens, the AST, and any artifact written with `-o -`.
- **stderr** is used for errors, warnings, and informational messages.

This separation allows commands such as:

```bash
orion --tokens example.orion | grep NUMERO
```

to work without informational messages mixed into the token stream.

### Phase stopping rules

| Invocation       | Phases executed                                                  | Artifact                |
| ---------------- | ---------------------------------------------------------------- | ----------------------- |
| No mode option   | Lexing → parsing → symbols → semantic analysis → IR → JavaScript | JavaScript file         |
| `--emit-ir`      | Through IR generation                                            | TAC file; no JavaScript |
| `--check`        | Through semantic analysis                                        | None                    |
| `--ast` alone    | Through parsing                                                  | None                    |
| `--tokens` alone | Lexical analysis only                                            | None                    |

`--tokens` and `--ast` are inspection modes. Unless `--check` or `--emit-ir` is also specified, compilation stops after the requested inspection phase and no file is written.

### Exit codes

| Code | Meaning                                                                       |
| ---: | ----------------------------------------------------------------------------- |
|  `0` | Success                                                                       |
|  `1` | Source-program errors: lexical, syntactic, or semantic                        |
|  `2` | CLI usage error: unknown option, missing input, or invalid option combination |
|  `3` | I/O error: input cannot be read or an artifact cannot be written              |

### Usage errors

Usage errors should be written to stderr in this form:

```text
orion: error: <message>
Try 'orion --help'.
```

Compiler diagnostics should retain the existing format, for example:

```text
E301[SEMANTIC] Line 2, column 5: ...
```

## 3. Option Specifications

### Input file (positional argument)

- **Purpose:** Specifies the source program to compile.
- **Arguments:** Exactly one file is required, except when `-h` or `-V` is used.
- **Encoding:** Read as UTF-8. No filename extension is required.
- **Compatibility:** Compatible with all modes.
- **Output:** Determined by the selected mode.
- **Errors:**
  - Missing file: exit code `2`.
  - More than one input file: exit code `2`.
  - File does not exist or cannot be read: exit code `3`.
  - Empty file: accepted and compiled, as in the current behavior.
- **Status:** Not implemented. File reading must be added to the CLI layer.

### `--help`, `-h`

- **Purpose:** Display command help.
- **Arguments:** None.
- **Compatibility:** Takes precedence over all other arguments; the remaining arguments are ignored.
- **Output:** Usage text to stdout; exit code `0`.
- **Errors:** None expected.
- **Status:** Not implemented; no compiler changes block this option.

### `--version`, `-V`

- **Purpose:** Display the compiler version.
- **Arguments:** None.
- **Compatibility:** `--help` takes precedence if both are present.
- **Output:** `orion 1.0-SNAPSHOT` to stdout, with exit code `0`. When running unpackaged classes, display `orion (development version)`.
- **Status:** Partially supported by existing project metadata. Build configuration is needed to expose the `pom.xml` version to the application, for example through resource filtering or the JAR manifest.

### `--output`, `-o`

- **Purpose:** Set the output path.
- **Arguments:** One path, or `-` to write the artifact to stdout.
- **Compatibility:** Only with the default compilation mode or `--emit-ir`, because these modes generate artifacts. Incompatible with `--check`, and with `--tokens` or `--ast` when `--emit-ir` is not also specified.
- **Default output:** `<basename>.js` or `<basename>.tac` in the **current working directory**, not beside the source file.
- **Behavior:** Create parent directories when necessary, overwrite an existing output file, and refuse to overwrite the input source file (exit code `2`).
- **Errors:** Missing path argument: exit code `2`. Any output-write failure: exit code `3`.
- **Status:** Not implemented. Output names are currently fixed constants, and `compilar` accepts only a directory.

### `--verbose`

- **Purpose:** Display additional process information.
- **Arguments:** None.
- **Compatibility:** Compatible with all options except `--quiet`; conflict results in exit code `2`.
- **Output:** To stderr, show one line per phase with its counts, the symbol table, quadruples, and the path of the written artifact.
- **Status:** Not implemented. The current direct `println` calls in `Tokenizer`, `SemanticAnalyzer`, and `SymbolTable` must be separated from presentation logic first.

### `--quiet`

- **Purpose:** Suppress informational messages.
- **Arguments:** None.
- **Compatibility:** Compatible with all options except `--verbose`; conflict results in exit code `2`.
- **Output:** No informational output on success. Errors and explicitly requested data (`--tokens`, `--ast`, and `-o -`) are never suppressed.
- **Status:** Depends on separating presentation from compiler logic, as with `--verbose`.
- **Default mode:** Print one informational line to stderr after success, for example:

  ```text
  orion: generated hello.js
  ```

### `--check`

- **Purpose:** Validate a source program without generating an artifact.
- **Arguments:** None.
- **Compatibility:** Compatible with `--tokens`, `--ast`, `--verbose`, and `--quiet`. Incompatible with `--emit-ir` and `-o`.
- **Output:** Nothing on success, apart from the default informational line when not quiet.
- **Errors:** Diagnostics to stderr; exit code `1`.
- **Status:** Nearly ready. The compiler already has lexical, syntactic, symbol-table, and semantic validation phases. The CLI needs to stop after semantic analysis and return the correct exit status (issue F6). Current semantic validation is limited (E301, E302, E304) and has known issues F1 and F2.

### `--emit-ir`

- **Purpose:** Generate the textual intermediate representation (three-address code, or TAC).
- **Arguments:** None.
- **Compatibility:** Compatible with `-o`, `--tokens`, `--ast`, `--verbose`, and `--quiet`. Incompatible with `--check`.
- **Output:** Write `<basename>.tac`, or the path supplied by `-o`. Do not generate JavaScript. Display quadruples only with `--verbose`.
- **Errors:** Validate the program first. If compilation errors occur, do not write IR and return exit code `1`.
- **Status:** The `ir/GeneradorCodigoIntermedio` component exists. The CLI must stop after phase 6 and support configurable output paths.

### `--tokens`

- **Purpose:** Display tokens produced by lexical analysis.
- **Arguments:** None.
- **Compatibility:** Compatible with other options according to the matrix below.
- **Output:** One token per line on stdout, using the current token format, including `EOF`. For example:

  ```text
  Token: LET             Lexeme: 'let' [1:1]
  Token: IDENTIFIER      Lexeme: 'a' [1:5]
  ```

- **Errors:** Print tokens recognized before the failure, then lexical diagnostics to stderr; exit code `1`.
- **Status:** Token data is available through `Tokenizer.getTokensList()`. The printing logic must be moved out of `Tokenizer.run()`.

### `--ast`

- **Purpose:** Display the abstract syntax tree (AST).
- **Arguments:** None.
- **Compatibility:** Compatible with other options according to the matrix below.
- **Output:** Print the current `ASTPrinter` format to stdout. Example:

  ```text
  ├── [Variable Declaration]: let a = 10
  ├── [Variable Declaration]: let b = 3
  ├── [Print Statement]: (a + (b * 2))
  ```

- **Errors:** If parsing fails, no AST is printed; diagnostics go to stderr and the exit code is `1`.
- **Status:** Nearly ready; the CLI only needs to stop after parsing. Limitation: `ASTPrinter` currently handles only variable declarations, `if` statements, and print statements.

### `--target`

- **Purpose:** Select a compilation backend.
- **Status:** Not proposed for the current version. The project has only one backend, and `JSGenerator` is a concrete class rather than an implementation of a backend interface.
- **Requirements before adding it:** Introduce a backend abstraction and at least one additional backend.
- **Current behavior:** The option should be treated as unknown and return exit code `2`. Supporting a single target now would add complexity without meaningful choice.

## 4. Option Compatibility

Legend: `✓` = compatible; `✗` = incompatible.

| Option      | `--check` | `--emit-ir` |            `--tokens` |               `--ast` | `-o`                  | `--verbose` | `--quiet` |
| ----------- | --------: | ----------: | --------------------: | --------------------: | --------------------- | ----------: | --------: |
| `--check`   |         — |           ✗ |                     ✓ |                     ✓ | ✗                     |           ✓ |         ✓ |
| `--emit-ir` |         ✗ |           — |                     ✓ |                     ✓ | ✓                     |           ✓ |         ✓ |
| `--tokens`  |         ✓ |           ✓ |                     — |                     ✓ | Only with `--emit-ir` |           ✓ |         ✓ |
| `--ast`     |         ✓ |           ✓ |                     ✓ |                     — | Only with `--emit-ir` |           ✓ |         ✓ |
| `-o`        |         ✗ |           ✓ | Only with `--emit-ir` | Only with `--emit-ir` | —                     |           ✓ |         ✓ |
| `--verbose` |         ✓ |           ✓ |                     ✓ |                     ✓ | ✓                     |           — |         ✗ |
| `--quiet`   |         ✓ |           ✓ |                     ✓ |                     ✓ | ✓                     |           ✗ |         — |

Any incompatible combination returns exit code `2` and produces a message naming both conflicting options.

## 5. Example Commands

```bash
orion --help
orion --version
orion examples/hello.orion
orion examples/hello.orion -o build/hello.js
orion --check examples/hello.orion
orion --emit-ir examples/hello.orion
orion --tokens examples/hello.orion
orion --ast examples/hello.orion
```

Expected behavior:

- `orion --help`: prints help to stdout and returns exit code `0`.
- `orion --version`: prints `orion 1.0-SNAPSHOT`.
- `orion examples/hello.orion`: writes `./hello.js`.
- `orion examples/hello.orion -o build/hello.js`: writes JavaScript to `build/hello.js`.
- `orion --check examples/hello.orion`: produces no output on success when quiet, and returns exit code `0`.
- `orion --emit-ir examples/hello.orion`: writes `./hello.tac`.
- `orion --tokens examples/hello.orion`: prints tokens to stdout and does not write files.
- `orion --ast examples/hello.orion`: prints the AST to stdout and does not write files.

The output filename should reflect the selected artifact. In normal mode, the artifact is JavaScript, so `-o build/hello.ir` would be misleading. For IR, use:

```bash
orion --emit-ir examples/hello.orion -o build/hello.tac
```

The `.tac` extension follows the project's current naming convention; the extension itself is a convention.

### Expected TAC output

For `--emit-ir` with output directed to stdout (`-o -`), the expected output for the example program is:

```text
a = 10
b = 3
t1 = b * 2
t2 = a + t1
print t2
```

### Expected AST output

```text
├── [Variable Declaration]: let a = 10
├── [Variable Declaration]: let b = 3
├── [Print Statement]: (a + (b * 2))
```

### Draft help text

```text
Usage: orion [options] <file>

Compile an Orion program to JavaScript.

Modes:
  (none)        Compile and generate <name>.js
  --check       Validate without generating files
  --emit-ir     Generate <name>.tac (three-address code)
  --tokens      Display tokens
  --ast         Display the abstract syntax tree

Options:
  -o, --output <path>   Output path ('-' for stdout)
      --verbose         Display process details
      --quiet           Suppress informational messages
  -h, --help            Display this help
  -V, --version         Display the compiler version

Exit codes: 0 success, 1 source errors, 2 usage error, 3 I/O error.
```

## 6. Alternatives Considered

- **Subcommands** (for example, `orion check`, `orion ast`): potentially more extensible, but the requested interface uses options, and subcommands are unnecessary for the current project size.
- **CLI library such as picocli:** reduces custom parsing code but introduces a dependency into a project that currently only uses JUnit. For educational purposes, a small custom parser of approximately 100 lines in a `cli` package is recommended. Suggested classes: `Options`, `ArgumentParser`, and `Cli`.

## 7. Implementation Roadmap

Each step should be implemented and verified independently with tests.

1. **Compilation result and exit status** — fix issue F6. This changes runtime behavior and requires approval.
2. **Separate presentation from compiler logic** — remove direct console printing from `Tokenizer`, `SemanticAnalyzer`, `SymbolTable`, `ASTPrinter`, and `ErrorHandler`. Define verbose, normal, and quiet levels, and always send errors to stderr. This enables `--verbose`, `--quiet`, and `--tokens`.
3. **Argument parser, `--help`, and `--version`** — configure the build to expose the version and add an `orion` launcher script.
4. **File input and output** — implement `-o`, default output paths, parent-directory creation, and protection against overwriting the source file.
5. **Compilation modes** — implement `--check`, `--emit-ir`, `--tokens`, and `--ast`.
6. **Future: `--target`** — add only when a second backend exists.

The current 14 characterization tests should remain valid. The new CLI can be introduced in a separate package while `Main.compilar` retains its complete existing trace until a decision is made to retire it.

## 8. Decisions to Confirm

The following defaults are recommended:

1. **Default output path:** `<basename>.js` and `<basename>.tac` in the current working directory. This replaces the current fixed filenames `program.js` and `programa.tac`.
2. **IR extension:** `.tac`, not `.ir`.
3. **No arguments:** display help and exit with code `2`. This removes the current fixed-program demo behavior; preserving it would require a dedicated option.
4. **No `--target` for now:** wait until a second backend exists.
5. **Informational messages to stderr:** reserve stdout for data.
6. **Inspection semantics:** `--tokens` and `--ast` alone do not generate files.

After these decisions are approved, implementation should begin with step 1 (exit status), because it is the only first-step change that alters existing behavior.
"""
path = Path("/mnt/data/document.md")
path.write_text(content, encoding="utf-8")
print(f"Created: {path}\nSize: {path.stat().st_size} bytes")

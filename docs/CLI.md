# Orion Command-Line Interface (CLI) Design

> **Status:** Implemented. Section 1 describes the project before the CLI was added.
>
> This document adapts the proposed CLI requirements to the current capabilities and structure of the Orion compiler.

## 1. Project Constraints (before the CLI)

The following facts have been verified in the source code and `pom.xml`:

- **Input:** The source program is hard-coded in `Compiler` as `ORION_CODE`. The compiler does not read source files or parse command-line arguments.
- **Output:** Output filenames are fixed in the current working directory: `program.js` and `programa.tac`. Both are written on every run.
- **Backend:** `JSGenerator` is the only backend. Three-address code (TAC) is an intermediate representation, not a backend.
- **Compiler phases:** Lexical analysis, parsing, symbol-table construction, semantic analysis, intermediate representation (IR), and JavaScript generation all exist. Starting at step 3, each phase is implemented as a separate method in `Compiler`.
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

Only one mode may be given per invocation; combining modes (for example `--check --tokens`) is a usage error with exit code `2`. `--tokens` and `--ast` never write files.

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

### `--help`, `-h`

- **Purpose:** Display command help.
- **Arguments:** None.
- **Compatibility:** Takes precedence over all other arguments; the remaining arguments are ignored.
- **Output:** Usage text to stdout; exit code `0`.
- **Errors:** None expected.

### `--version`, `-V`

- **Purpose:** Display the compiler version.
- **Arguments:** None.
- **Compatibility:** `--help` takes precedence if both are present.
- **Output:** `orion 1.0-SNAPSHOT` to stdout, with exit code `0`. When running unpackaged classes, display `orion (development version)`.

### `--output`, `-o`

- **Purpose:** Set the output path.
- **Arguments:** One path, or `-` to write the artifact to stdout.
- **Compatibility:** Only with the default compilation mode or `--emit-ir`, because these modes generate artifacts.
- **Default output:** `<basename>.js` or `<basename>.tac` in the **current working directory**, not beside the source file.
- **Behavior:** Create parent directories when necessary, overwrite an existing output file, and refuse to overwrite the input source file (exit code `2`).
- **Errors:** Missing path argument: exit code `2`. Any output-write failure: exit code `3`.

### `--verbose`

- **Purpose:** Display additional process information.
- **Arguments:** None.
- **Compatibility:** Compatible with all options except `--quiet`; conflict results in exit code `2`.
- **Output:** To stderr, show one line per phase with its counts, the symbol table, quadruples, and the path of the written artifact.

### `--quiet`

- **Purpose:** Suppress informational messages.
- **Arguments:** None.
- **Compatibility:** Compatible with all options except `--verbose`; conflict results in exit code `2`.
- **Output:** No informational output on success. Errors and explicitly requested data (`--tokens`, `--ast`, and `-o -`) are never suppressed.
- **Default mode:** Print one informational line to stderr after success, for example:

  ```text
  orion: generated hello.js
  ```

### `--check`

- **Purpose:** Validate a source program without generating an artifact.
- **Arguments:** None.
- **Compatibility:** Compatible with `--verbose` and `--quiet`. Exclusive with the other modes; incompatible with `-o`.
- **Output:** Nothing on success, apart from the default informational line when not quiet.
- **Errors:** Diagnostics to stderr; exit code `1`.
- **Limitation:** Semantic validation is limited (E301, E302, E304) and has known issues F1 and F2.

### `--emit-ir`

- **Purpose:** Generate the textual intermediate representation (three-address code, or TAC).
- **Arguments:** None.
- **Compatibility:** Compatible with `-o`, `--verbose`, and `--quiet`. Exclusive with the other modes.
- **Output:** Write `<basename>.tac`, or the path supplied by `-o`. Do not generate JavaScript. Display quadruples only with `--verbose`.
- **Errors:** Validate the program first. If compilation errors occur, do not write IR and return exit code `1`.

### `--tokens`

- **Purpose:** Display tokens produced by lexical analysis.
- **Arguments:** None.
- **Compatibility:** Compatible with `--verbose` and `--quiet`. Exclusive with the other modes; incompatible with `-o`.
- **Output:** One token per line on stdout, using the current token format, including `EOF`. For example:

  ```text
  Token: LET             Lexeme: 'let' [1:1]
  Token: IDENTIFIER      Lexeme: 'a' [1:5]
  ```

- **Errors:** Print tokens recognized before the failure, then lexical diagnostics to stderr; exit code `1`.

### `--ast`

- **Purpose:** Display the abstract syntax tree (AST).
- **Arguments:** None.
- **Compatibility:** Compatible with `--verbose` and `--quiet`. Exclusive with the other modes; incompatible with `-o`.
- **Output:** Print the current `ASTPrinter` format to stdout. Example:

  ```text
  ├── [Variable Declaration]: let a = 10
  ├── [Variable Declaration]: let b = 3
  ├── [Print Statement]: (a + (b * 2))
  ```

- **Errors:** If parsing fails, no AST is printed; diagnostics go to stderr and the exit code is `1`.
- **Limitation:** `ASTPrinter` handles only variable declarations, `if` statements, and print statements, and prints its labels in Spanish (`[Declaración Variable]`, `[Instrucción Imprimir]`).

### `--target`

- **Purpose:** Select a compilation backend.
- **Status:** Not proposed for the current version. The project has only one backend, and `JSGenerator` is a concrete class rather than an implementation of a backend interface.
- **Requirements before adding it:** Introduce a backend abstraction and at least one additional backend.
- **Current behavior:** The option should be treated as unknown and return exit code `2`. Supporting a single target now would add complexity without meaningful choice.

## 4. Option Compatibility

Modes (`--check`, `--emit-ir`, `--tokens`, `--ast`) are mutually exclusive; repeating the same mode is allowed. `-o` is accepted only by the default mode and `--emit-ir`. `--verbose` and `--quiet` cannot be combined. Every conflict returns exit code `2` with a message naming both options.

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

For `--emit-ir` with output directed to stdout (`-o -`), the expected output for the example program is (the generator indents each instruction with four spaces):

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

## 7. Implementation

- `Options.parse` (package `com.compiler.cli`) parses the command line; invalid input raises `Options.UsageException`.
- `Compiler.run(String[])` handles help, version, input reading, default output paths, and exit codes. `Compiler.compile(...)` runs the phases up to the selected mode.
- The phase trace goes to stderr only with `--verbose`; diagnostics always go to stderr.
- `--version` reads `Implementation-Version` from the JAR manifest (`maven-jar-plugin` in `pom.xml`).
- Launchers: `orion` (POSIX shell) and `orion.cmd` (Windows) run `target/orion.jar`. Build it with `mvn package`.
- Example program: `examples/hello.orion`.
- Tests: `OptionsTest` (parser), `CliTest` (`Compiler.run`), and `CompilatorTest` (modes and golden files).

Decisions taken: default output in the current working directory, `.tac` extension for IR, no arguments shows help with exit code `2`, no `--target`, informational messages on stderr, and one mode per invocation.

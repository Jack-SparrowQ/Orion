# Graph Report - orion-compiler  (2026-10-21)

## Corpus Check
- 46 files · ~52,001 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 33 file(s) not represented in the graph (top: .woff 12, .woff2 12, (none) 3)

## Summary
- 317 nodes · 780 edges · 12 communities (8 shown, 4 thin omitted)
- Extraction: 86% EXTRACTED · 14% INFERRED · 0% AMBIGUOUS · INFERRED: 108 edges (avg confidence: 0.84)
- Token cost: 55,626 input · 0 output

## Community Hubs (Navigation)
- Token Type Catalog
- AST Node Hierarchy
- Lexer and IR Generation
- Semantic Analysis & Symbols
- Compiler Pipeline Driver
- Recursive Descent Parser
- Golden File Tests
- Compilation Errors
- JS Codegen & Printing
- Maven Project

## God Nodes (most connected - your core abstractions)
1. `TokenType` - 61 edges
2. `ElementoAST` - 60 edges
3. `Parser` - 41 edges
4. `ErrorHandler` - 33 edges
5. `Token` - 33 edges
6. `Lexer` - 25 edges
7. `SymbolTable` - 21 edges
8. `SemanticAnalyzer` - 18 edges
9. `ErrorType` - 17 edges
10. `GeneradorCodigoIntermedio` - 17 edges

## Surprising Connections (you probably didn't know these)
- `E100 Lexical Error: Unrecognized Character` --references--> `ErrorType`  [INFERRED]
  src/test/resources/golden/error_lexico.txt → src/main/java/com/compiler/error/ErrorType.java
- `E301 Semantic Error: Undeclared Identifier` --references--> `ErrorType`  [INFERRED]
  src/test/resources/golden/error_semantico_condicion_if.txt → src/main/java/com/compiler/error/ErrorType.java
- `E304 Semantic Error: Division by Zero` --references--> `ErrorType`  [INFERRED]
  src/test/resources/golden/error_semantico_division_cero.txt → src/main/java/com/compiler/error/ErrorType.java
- `E302 Semantic Error: Duplicate Declaration` --references--> `ErrorType`  [INFERRED]
  src/test/resources/golden/error_semantico_duplicado.txt → src/main/java/com/compiler/error/ErrorType.java
- `E203 Syntax Error: Expected Expression` --references--> `ErrorType`  [INFERRED]
  src/test/resources/golden/error_sintactico_expresion.txt → src/main/java/com/compiler/error/ErrorType.java

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Orion phase pipeline: lexer -> parser -> semantic -> TAC -> JS** — src_main_java_com_compiler_lexer_lexer_lexer, src_main_java_com_compiler_parser_parser_parser, src_main_java_com_compiler_semantic_symboltable_symboltable, src_main_java_com_compiler_semantic_semanticanalyzer_semanticanalyzer, src_main_java_com_compiler_ir_generadorcodigointermedio_generadorcodigointermedio, src_main_java_com_compiler_util_jsgenerator_jsgenerator [INFERRED 0.85]
- **Semantic error golden cases** — src_test_resources_golden_error_semantico_condicion_if_golden_case, src_test_resources_golden_error_semantico_division_cero_golden_case, src_test_resources_golden_error_semantico_duplicado_golden_case, src_test_resources_golden_error_semantico_no_declarado_en_declaracion_golden_case, src_test_resources_golden_error_semantico_print_no_declarado_golden_case [INFERRED 0.85]

## Communities (12 total, 4 thin omitted)

### Community 0 - "Token Type Catalog"
Cohesion: 0.04
Nodes (52): TokenType, AND_LOGICO, BREAK, COMPARACION_IGUAL, CONST, CONTINUE, CORCHETE_DER, CORCHETE_IZQ (+44 more)

### Community 1 - "AST Node Hierarchy"
Cohesion: 0.12
Nodes (10): Token, ElementoAST, NodoAgrupacion, NodoBinario, NodoBloque, NodoDeclaracion, NodoExpresionSimple, NodoIf (+2 more)

### Community 2 - "Lexer and IR Generation"
Cohesion: 0.14
Nodes (23): ErrorHandler, GeneradorCodigoIntermedio, Lexer, SemanticAnalyzer, SymbolTable, Compilation Aborted on Phase Error, Golden: Lexical Error (unrecognized '@'), E301 Semantic Error: Undeclared Identifier (+15 more)

### Community 3 - "Semantic Analysis & Symbols"
Cohesion: 0.08
Nodes (15): Symbol, SymbolCategory, CONSTANTE, FUNCION, PARAMETRO, TIPO, VARIABLE, Type (+7 more)

### Community 4 - "Compiler Pipeline Driver"
Cohesion: 0.10
Nodes (3): Compiler, Cuadrupla, Tokenizer

### Community 6 - "Golden File Tests"
Cohesion: 0.20
Nodes (3): ResultadoCompilacion, CompilatorTest, GoldenFile

### Community 7 - "Compilation Errors"
Cohesion: 0.11
Nodes (10): CompilationError, ErrorType, LEXICO, SEMANTICO, SINTACTICO, E100 Lexical Error: Unrecognized Character, E304 Semantic Error: Division by Zero, E302 Semantic Error: Duplicate Declaration (+2 more)

### Community 9 - "JS Codegen & Printing"
Cohesion: 0.20
Nodes (3): ASTPrinter, ConsoleColors, JSGenerator

## Ambiguous Edges - Review These
- `E301 Semantic Error: Undeclared Identifier` → `Golden: Use Before Declaration (accepted)`  [AMBIGUOUS]
  src/test/resources/golden/uso_antes_de_declarar.txt · relation: conceptually_related_to

## Knowledge Gaps
- **67 isolated node(s):** `com.compiler:orion-compiler`, `LEXICO`, `SINTACTICO`, `SEMANTICO`, `IF` (+62 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 108 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **4 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `TokenType` connect `Token Type Catalog` to `AST Node Hierarchy`, `Lexer and IR Generation`, `Recursive Descent Parser`?**
  _High betweenness centrality (0.302) - this node is a cross-community bridge._
- **Are the 11 inferred relationships involving `Parser` (e.g. with `Golden: Undeclared Identifier in If Condition` and `Golden: Division by Zero Semantic Error`) actually correct?**
  _`Parser` has 11 INFERRED edges - model-reasoned connections that need verification._
- **What connects `com.compiler:orion-compiler`, `LEXICO`, `SINTACTICO` to the rest of the system?**
  _67 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Token Type Catalog` be split into smaller, more focused modules?**
  _Cohesion score 0.03636363636363636 - nodes in this community are weakly interconnected._
- **Why does `ElementoAST` connect `AST Node Hierarchy` to `Lexer and IR Generation`, `Semantic Analysis & Symbols`, `Compiler Pipeline Driver`, `Recursive Descent Parser`, `JS Codegen & Printing`?**
  _High betweenness centrality (0.189) - this node is a cross-community bridge._
- **Are the 9 inferred relationships involving `ErrorHandler` (e.g. with `Compilation Aborted on Phase Error` and `Golden: Lexical Error (unrecognized '@')`) actually correct?**
  _`ErrorHandler` has 9 INFERRED edges - model-reasoned connections that need verification._

### Low-confidence Hints
_AMBIGUOUS edges — the extractor was unsure. Verify before acting on these._

- **What is the exact relationship between `E301 Semantic Error: Undeclared Identifier` and `Golden: Use Before Declaration (accepted)`?**
  _Edge tagged AMBIGUOUS (relation: conceptually_related_to) - confidence is low._
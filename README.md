# Orion Compiler

Compilador desarrollado en Java para el análisis y procesamiento de un lenguaje de programación propio. El proyecto implementa las fases de un compilador, desde el análisis léxico y sintáctico hasta el análisis semántico (con comprobación de tipos), la generación de código intermedio y la traducción a JavaScript.

El objetivo es construir una arquitectura modular que permita comprender cómo se transforma un programa fuente en una representación intermedia, facilitando su análisis y el desarrollo posterior de nuevas etapas de compilación.

## Objetivos del proyecto

- Implementar el análisis léxico para reconocer los elementos del lenguaje.
- Analizar la estructura sintáctica de los programas mediante una gramática.
- Construir una representación del programa mediante un árbol de sintaxis abstracta (AST).
- Detectar errores semánticos, como el uso de identificadores no declarados o los errores de tipos.
- Generar código intermedio a partir de la estructura del programa.
- Mantener una arquitectura modular que facilite la extensión del compilador.

## Arquitectura general

El proceso de compilación se organiza en las siguientes etapas:

```text
Programa fuente
      |
      v
Análisis léxico ........................ lista de tokens
      |
      v
Análisis sintáctico .................... árbol de sintaxis abstracta (AST)
      |
      v
Tabla de símbolos ...................... declaraciones con su tipo inferido
      |
      v
Análisis semántico ..................... uso de identificadores y tipos
      |
      v
Generación de código intermedio ........ código de tres direcciones (TAC)
      |
      v
Generación de JavaScript ............... programa .js
```

Cada fase solo se ejecuta si las anteriores terminaron sin errores. Dentro de una fase se informan todos los errores encontrados, no solo el primero.

## Tecnologías utilizadas

- **Java 21** o superior: lenguaje de implementación (usa `record`, interfaces selladas y `switch` con patrones).
- **Maven:** construcción y pruebas (JUnit 5).
- **Git:** control de versiones.

## Estructura del proyecto

```text
orion-compiler/
├── orion, orion.cmd           Lanzadores del compilador (sh / Windows)
├── examples/hello.orion       Programa de ejemplo
├── docs/CLI.md                Diseño de la interfaz de línea de comandos
├── pom.xml
└── src/
    ├── main/java/com/compiler/
    │   ├── Compiler.java      Punto de entrada: CLI y orquestación de las fases
    │   ├── cli/               Opciones de la línea de comandos
    │   ├── lexer/             Lexer, Token, TokenType
    │   ├── parser/            Parser (descendente recursivo)
    │   │   └── ast/           Nodos del AST (records sellados)
    │   ├── semantic/          Tabla de símbolos y análisis semántico
    │   ├── ir/                Código intermedio (cuádruplas / TAC)
    │   ├── error/             Gestión de errores de compilación
    │   └── util/              Generador de JavaScript e impresión del AST
    └── test/                  Pruebas unitarias y de extremo a extremo
```

### Componentes principales

| Componente                       | Responsabilidad                                                                                  |
| -------------------------------- | ------------------------------------------------------------------------------------------------ |
| `Compiler.java`                  | Interpreta la línea de comandos y coordina las fases de compilación.                             |
| `Lexer.java`                     | Reconoce los tokens del programa fuente.                                                         |
| `Parser.java`                    | Analiza la sintaxis, construye el AST y se recupera de errores para informar de todos.           |
| `parser/ast`                     | `Sentencia` y `Expresion`: interfaces selladas cuyos nodos son `record`.                         |
| `SymbolTable.java`               | Mantiene los identificadores declarados, su tipo y su categoría (variable o constante).          |
| `SemanticAnalyzer.java`          | Comprueba el uso de identificadores, la división entre cero y los tipos.                         |
| `GeneradorCodigoIntermedio.java` | Traduce el AST a instrucciones de código intermedio.                                             |
| `Cuadrupla.java`                 | Representa una instrucción del código intermedio mediante operación, operandos y resultado.      |
| `JSGenerator.java`               | Traduce el AST a JavaScript.                                                                     |

Como `Sentencia` y `Expresion` son interfaces selladas, cada fase las recorre con un `switch` exhaustivo: al añadir un tipo de nodo nuevo, el proyecto no compila hasta que todas las fases lo manejen.

## Lenguaje fuente

```javascript
let a = 10;
let b = 3.5;
const max = 20;

let c = a + b * 2;   // float
a += 1;

if (c > max) {
    print(max);
} else if (c > 0) {
    print(c);
} else {
    print(0);
}
```

### Elementos léxicos

- Números enteros (`10`) y decimales (`3.5`).
- Booleanos `true` y `false`.
- Identificadores: letra o `_` seguida de letras, dígitos o `_`.
- Comentarios de línea: `// ...`.
- Operadores: `+ - * / %`, `== != < <= > >=`, `&& || !`, `= += -= *= /= %=`.

### Gramática

```text
programa     -> sentencia* EOF
sentencia    -> declaracion | asignacion | imprimir | if
declaracion  -> ("let" | "const") IDENTIFICADOR "=" expresion ";"
asignacion   -> IDENTIFICADOR ("=" | "+=" | "-=" | "*=" | "/=" | "%=") expresion ";"
imprimir     -> "print" "(" expresion ")" ";"
if           -> "if" "(" expresion ")" bloque ( "else" ( bloque | if ) )?
bloque       -> "{" sentencia* "}"

expresion    -> logicoOr
logicoOr     -> logicoAnd ( "||" logicoAnd )*
logicoAnd    -> igualdad ( "&&" igualdad )*
igualdad     -> comparacion ( ("==" | "!=") comparacion )*
comparacion  -> termino ( ("<" | "<=" | ">" | ">=") termino )*
termino      -> factor ( ("+" | "-") factor )*
factor       -> unario ( ("*" | "/" | "%") unario )*
unario       -> ("!" | "-") unario | primario
primario     -> NUMERO | IDENTIFICADOR | "true" | "false" | "(" expresion ")"
```

La precedencia y la asociatividad (por la izquierda) coinciden con las de JavaScript. Una asignación compuesta `a += b` se traduce a `a = a + b`.

## Análisis semántico

El análisis semántico se hace en dos pasadas:

1. **Tabla de símbolos:** registra cada declaración, en orden, con su tipo inferido y su categoría (`let` → variable, `const` → constante).
2. **Comprobaciones:** recorre el programa en orden y por bloques comprobando el uso de cada identificador y los tipos.

### Tipos

| Expresión                       | Operandos                          | Resultado                                         |
| ------------------------------- | ---------------------------------- | ------------------------------------------------- |
| `+ - * %`                       | numéricos                          | `int` si ambos son `int`; si no, `float`          |
| `/`                             | numéricos                          | `float` (en JavaScript, `7 / 2` es `3.5`)         |
| `< <= > >=`                     | numéricos                          | `bool`                                            |
| `== !=`                         | del mismo tipo, o ambos numéricos  | `bool`                                            |
| `&& \|\|` y `!`                 | `bool`                             | `bool`                                            |
| `-x`                            | numérico                           | el tipo de `x`                                    |
| `x = valor`                     | mismo tipo que `x`                 | se admite asignar un `int` a una variable `float` |
| `if (condición)`                | `bool`                             | —                                                 |

Las variables declaradas dentro de un bloque solo son visibles en ese bloque, igual que `let`/`const` en el JavaScript generado. Cuando una expresión tiene un error, su tipo queda como desconocido y no provoca más errores en cascada.

### Códigos de error

| Código | Fase        | Significado                                                             |
| ------ | ----------- | ----------------------------------------------------------------------- |
| E100   | Léxica      | Carácter no reconocido.                                                 |
| E101   | Léxica      | `&` suelto: se esperaba `&&`.                                           |
| E102   | Léxica      | `\|` suelto: se esperaba `\|\|`.                                        |
| E201   | Sintáctica  | Falta un elemento obligatorio (`;`, `=`, `)`, `}`...).                  |
| E202   | Sintáctica  | Instrucción no reconocida.                                              |
| E203   | Sintáctica  | Se esperaba una expresión.                                              |
| E301   | Semántica   | Identificador no declarado.                                             |
| E302   | Semántica   | Identificador declarado dos veces.                                      |
| E303   | Semántica   | Identificador usado antes de su declaración o fuera de su bloque.       |
| E304   | Semántica   | División entre una constante que vale cero.                             |
| E305   | Semántica   | Reasignación de una constante.                                          |
| E306   | Semántica   | Operador aplicado a tipos incompatibles.                                |
| E307   | Semántica   | Asignación de un valor de tipo incompatible.                            |
| E308   | Semántica   | La condición de un `if` no es `bool`.                                   |

## Generación de código intermedio

El generador transforma el AST en código de tres direcciones (_Three-Address Code_, TAC). Cada instrucción es una cuádrupla:

```text
(operación, argumento1, argumento2, resultado)
```

Para el programa de ejemplo de la sección anterior (`orion --emit-ir ejemplo.orion -o -`):

```text
    a = 10
    b = 3.5
    max = 20
    t1 = b * 2
    t2 = a + t1
    c = t2
    t3 = a + 1
    a = t3
    t4 = c > max
    ifFalse t4 goto L1
    print max
    goto L2
L1:
    t5 = c > 0
    ifFalse t5 goto L3
    print c
    goto L4
L3:
    print 0
L4:
L2:
```

Los temporales (`t1`, `t2`...) dividen las expresiones respetando la precedencia, y las etiquetas (`L1`, `L2`...) implementan los saltos del `if`/`else`.

## Ejecución

Requisitos: JDK 21 o superior y Maven.

```bash
mvn package                       # compila, ejecuta las pruebas y genera target/orion.jar
./orion examples/hello.orion      # genera ./hello.js   (en Windows: orion.cmd ...)
```

Modos principales (uno por invocación):

```bash
orion programa.orion              # compila a programa.js
orion --check programa.orion      # solo valida
orion --emit-ir programa.orion    # genera programa.tac
orion --tokens programa.orion     # muestra los tokens
orion --ast programa.orion        # muestra el AST
orion --help                      # todas las opciones
```

Códigos de salida: `0` éxito, `1` errores en el programa fuente, `2` uso incorrecto, `3` error de E/S. El diseño completo está en [`docs/CLI.md`](orion-compiler/docs/CLI.md).

## Pruebas

```bash
mvn test
```

| Suite                  | Qué cubre                                                                          |
| ---------------------- | ---------------------------------------------------------------------------------- |
| `LexerTest`            | Tokens, operadores, posiciones, comentarios, decimales y errores léxicos.          |
| `ParserTest`           | Precedencia, asociatividad, sentencias, mensajes de error y recuperación.          |
| `SemanticAnalyzerTest` | Inferencia y comprobación de tipos, ámbitos y errores semánticos.                  |
| `CompilatorTest`       | Programas completos: salida, código de salida y archivos generados (golden files). |
| `OptionsTest`, `CliTest` | Línea de comandos.                                                               |

Los _golden files_ (`src/test/resources/golden/`) guardan la salida esperada de programas completos. Tras un cambio de comportamiento deliberado se regeneran con `mvn test -Dgolden.update=true` y se revisan con `git diff`.

## Alcance y trabajo futuro

- Bucles (`while`, `for`) y funciones (`fn`, `return`): sus palabras reservadas ya se reconocen, pero no forman parte de la gramática.
- Cadenas de texto.
- Permitir reutilizar el nombre de una variable en bloques distintos (hoy los nombres son únicos en todo el programa).
- Optimizaciones sobre el código intermedio.

## Documentación académica

Este proyecto sirve como base para estudiar e implementar las fases de un compilador, especialmente el análisis semántico y la generación de código intermedio.

## Autoría

Proyecto académico: **Orion Compiler**.

Desarrollo realizado como parte del estudio de compiladores y lenguajes de programación.

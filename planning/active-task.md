# Tarea activa — 1.4 Tipos en el IR

Asignada a: Codex · Revisa: Claude · Contexto: `planning/backlog.md` (etapa 1), `docs/decisions/0001-js-desde-ir.md`

## Problema
El IR no tiene tipos. El futuro generador IR→JS los necesita igual que hoy `JSGenerator` (que recibe
`Map<Expresion, Type>` del análisis semántico):
- `+` de cadenas debe llamar a `$concatenar` (error en tiempo de ejecución si un operando es `null`);
  `+` numérico no. Incluye `+=` sobre elemento de arreglo de `string` (`NodoAsignacionElemento`).
- `print`/`printn` usan `"%s"` cuando el primer argumento es `string`/`char` (ver `JSGenerator`, ~línea 200).
Las optimizaciones (plegado de constantes) también necesitarán saber qué es cada operando.

## Decisión
1. **Opcode `concat`** para el `+` cuyo resultado es `string`, en lugar de `+`
   (`$t3 = a concat b`, tupla `(concat, a, b, $t3)`). Así el IR→JS no tiene que consultar tipos para decidirlo.
2. **Tabla de tipos** `Map<String, Type> tipos` en `ProgramaIR` (tercer componente, copiada inmutable):
   nombre IR → `Type` para **cada** variable, parámetro y temporal. Los nombres IR ya son únicos en todo
   el programa (1.3), así que una sola tabla basta. Se añade también
   `Type tipo(String operando)` en `ProgramaIR`: busca en la tabla y, si no está, deduce el tipo del
   lexema de un literal (`10`→INT, `3.5`→FLOAT, `"..."`→STRING, `'a'`→CHAR, `true`/`false`→BOOL, `null`→NULL).
3. `generar(List<Sentencia>, Map<Expresion, Type>)`: el generador recibe los tipos del análisis semántico.
   - Temporal: tipo de la expresión que lo produce (los temporales de `&&`/`||`: BOOL; índice/longitud del
     `for`: INT; condición del `for`: BOOL; copia del iterable: tipo del iterable).
   - Variable: la anotación si existe (`let x: string = null;` es STRING, no NULL); si no, el tipo del inicializador.
     Busca cómo convierte el análisis semántico una anotación en `Type` y reutilízalo, no lo dupliques.
   - Parámetro: su anotación. Variable del `for`: elemento del iterable (`string`→CHAR, `T[]`→T).
4. `Compiler.java` pasa al generador el mapa que ya obtiene de `analizarSemantica`.

## Qué hacer
Implementación en `ir/` y `Compiler.java`; comentario de ejemplos de `Cuadrupla` con `concat`; pruebas en
`GeneradorCodigoIntermedioTest` (concat vs suma, `+=` de elemento string, tipos de: variable anotada con null,
parámetro, variable del `for` sobre cadena y sobre arreglo, temporal de llamada, temporal lógico, literales vía
`tipo()`). Adapta las pruebas que llaman a `generar` sin tipos (un helper que corra el análisis semántico real es
mejor que mapas a mano). README: una frase sobre `concat` y la tabla de tipos.

## Fuera de alcance
Generador IR→JS, optimizaciones, `JSGenerator` y demás fases (salvo leerlas).

## Reglas
Las de `AGENTS.md`. Sin dependencias nuevas. Sin commit.

## Criterios de aceptación
- `mvn -q test` con código 0 (reporta tests run / failures / skipped).
- Cada nombre IR que aparece como destino en `--emit-ir` de `null_print.orion` y `for_arreglo_cadena.orion`
  tiene entrada en la tabla (añade una prueba que lo compruebe para todo el corpus de `src/test/resources/programas/`).
- `--emit-ir` de `escapes_concatenacion.orion` en el reporte.

## Reporte
Formato de `AGENTS.md`.

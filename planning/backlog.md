# Backlog

Objetivo actual: dejar el compilador listo para la fase de optimización del código intermedio.
Detalle y justificación: ver la sección "Ruta" más abajo.

## Ruta hacia la optimización

### Etapa 0 — Red de seguridad (antes de tocar el IR)
- [x] 0.1 (hecho: 14 programas en `src/test/resources/programas/`, `EjecucionTest`) Corpus de ejecución: `examples/*.orion` + salida esperada; prueba que compila a JS, lo ejecuta con `node` y compara stdout. Cubrir fn, while, for-in, break/continue, arreglos, null, `**`, print múltiple.
- [ ] 0.2 (Reemplazado por ADR 0001) El oráculo es el generador actual AST→JS: la prueba diferencial compara la salida de `node` del JS desde AST con la del JS desde IR (etapa 2.4).

### Etapa 1 — Corrección del IR (errores confirmados)
- [x] 1.1 (hecho) CRITICAL: los temporales/etiquetas chocan con identificadores del usuario (`let t1 = 5;` lo pisa `t1 = call f, 0`). Usar nombres que el lexer no pueda producir (p. ej. `%t1`, `%L1`).
- [x] 1.2 (hecho) HIGH: `&&` y `||` no tienen cortocircuito en el IR (`false && f()` llama a `f`); el JS sí. Traducir con saltos.
- [x] 1.3 (hecho) HIGH: el IR aplana los ámbitos: una local `x` de una función y una global `x`, o dos `let x` en bloques hermanos, comparten nombre. Renombrar a nombres únicos (`x`, `x.1`) usando la resolución del análisis semántico.
- [x] 1.4 (hecho) MEDIUM: el IR no tiene tipos: `+` es suma o concatenación, los literales son lexemas. Emitir `concat` para cadenas y conservar el tipo de cada literal/temporal (necesario para plegar constantes).
- [x] 1.5 (hecho) MEDIUM: las funciones se emiten intercaladas con el código principal. Estructurar el IR como lista de funciones + programa principal. Incluir la lista de parámetros como dato estructurado (hoy va como texto "a, b$1" en el resultado de `func`).

### Etapa 2 — Infraestructura de optimización
- [x] 2.1 ADR 0001: pipeline `AST -> IR -> optimización -> JS` (decisión del usuario).
- [x] 2.2 (hecho: `ir/GrafoFlujo`, `ir/BloqueBasico`) Bloques básicos y grafo de flujo de control por función (líderes, sucesores/predecesores), con salida de depuración.
- [x] 2.3 (hecho: `Compiler.OPTIMIZACIONES`, lista vacía de `UnaryOperator<ProgramaIR>`) Contrato de una pasada; en `Compiler`, una lista de pasadas (vacía por ahora) entre el IR y el JS.
- [x] 2.4 (hecho: `codegen/GeneradorJS`, prueba diferencial en `EjecucionTest`) Generador IR→JS: despachador, comprobaciones en tiempo de ejecución por operación, mismo formato de `print`.
- [x] 2.5 (hecho) Cambiar `Compiler` para que el `.js` salga del IR; el generador AST→JS queda solo como oráculo de pruebas.

### Etapa 3 — Cierre y documentación (en paralelo, sin dependencias)
- [x] 3.1 (hecho; también sección 16 de `docs/grammar.md`) README desactualizado: la gramática no incluye `while`, `fn`, `return`, `break`/`continue`, `let x: T;`; faltan los códigos E205, E313–E318; enlace roto `orion-compiler/docs/CLI.md`.
- [x] 3.2 (hecho) `import`/`from`/`export` son palabras reservadas sin implementar: documentarlas como reservadas (fuera de alcance).
- [x] 3.3 (hecho, confirmado por el usuario; `graphify-out/` se conserva) Limpiar Javadoc HTML obsoleto en `docs/` y archivos sueltos de la raíz (`repomix-output.xml`, `hello.js`, `hello.tac`, `code.js`, `program.js`, `programa.tac`, carpeta vacía `orion-compiler/`) — confirmar con el usuario.

### Criterio de "listo para optimizar"
El `.js` sale del IR (pasando por la lista de pasadas, aún vacía) y el corpus da la misma salida en `node` que con el generador AST→JS; ningún hallazgo CRITICAL/HIGH abierto; IR con nombres únicos, cortocircuito, tipos y funciones separadas; CFG disponible.

## Notas para la fase de optimización
- `[]`, `[]=`, `len`, `concat` y `call` pueden lanzar o tener efectos: una pasada de código muerto no debe eliminarlos aunque su resultado no se use (la comprobación de índice que precede a `[]=` depende de ello).
- Una declaración sin valor emite `x = void 0` para reiniciarla en cada vuelta de un ciclo; `ProgramaIR.tipo("void 0")` devuelve null.
- Las pasadas reciben y devuelven `ProgramaIR` (`UnaryOperator<ProgramaIR>`); verificación: `EjecucionTest` (corpus + diferencial).

## Otros pendientes
- LOW: E304 (división entre cero constante) no se comprueba para `%`; E309 (lectura sin asignar) no implementado. Ver `docs/grammar.md` §16.
- LOW (previo): identificadores Orion que son palabras reservadas o globales de JS (`class`, `new`, `function`, `console`...) rompen el JS generado.
- El JS desde IR es menos legible (despachador `switch`); si importa, reconstruir `if`/`while` desde el CFG.

## Futuro
- Optimizaciones del IR: plegado y propagación de constantes, propagación de copias, subexpresiones comunes, código muerto, saltos encadenados.

## Done
- Restored standard `src/` layout; untracked compiled `.class` files.

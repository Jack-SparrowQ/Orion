# 0001 — El JavaScript se genera desde el IR optimizado

Fecha: 2026-11-15 · Estado: aceptada (decisión del usuario)

## Contexto
Hasta ahora `util/JSGenerator` traduce el AST a JS y el IR (`ir/GeneradorCodigoIntermedio`) es solo
una salida de depuración (`--emit-ir`). Así, una optimización del IR no cambiaría el programa generado.

## Decisión
Pipeline: `AST -> IR -> optimización -> JS`. El JS final se genera desde las cuádruplas optimizadas.

## Consecuencias
- El IR pasa a ser la representación de la que depende la salida: debe ser correcto y completo
  (nombres únicos, cortocircuito, comprobaciones en tiempo de ejecución de `[]`, `[]=`, `len`, null en `+`).
- JS no tiene `goto`. Primera versión: cada función se traduce a un despachador
  `let pc = 0; for (;;) switch (pc) { case 0: ... case 1: ... }` con un `case` por bloque básico.
  Es correcto para cualquier grafo de flujo; la salida es menos legible que la actual.
  Alternativa descartada por ahora: reconstruir `if`/`while` desde el CFG (relooper/stackifier):
  más legible, mucho más complejo. Se puede retomar si la legibilidad importa.
- Durante la transición, el generador actual AST→JS se conserva como **oráculo de pruebas**:
  para cada programa del corpus, la salida de `node` con el JS desde AST debe ser igual a la del
  JS desde IR. Esto sustituye al intérprete de TAC que se había propuesto.
- Al terminar la transición, el generador AST→JS se elimina o queda solo en las pruebas.

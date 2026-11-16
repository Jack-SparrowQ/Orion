# 0002 — Diseño de la fase de optimización del IR

Fecha: 2026-11-16 · Estado: aceptada

## Contexto
El pipeline es `AST -> IR -> optimización -> JS` (ADR 0001). `Compiler.OPTIMIZACIONES` es una lista de
`UnaryOperator<ProgramaIR>` y `GrafoFlujo` divide cada cuerpo en bloques básicos.

## Decisiones
1. **Cada pasada es un `UnaryOperator<ProgramaIR>`** que transforma por separado el cuerpo de cada
   `FuncionIR` y `principal()`. Una pasada no añade ni quita funciones; puede añadir temporales
   (registrándolos en la tabla de tipos y, si es dentro de una función, en sus `locales`).
2. **La semántica de referencia es la de JavaScript**, porque es lo que ejecuta el programa: plegar
   `7 / 2` da `3.5`, `%` es el resto de JS, `**` la potencia de JS. Una constante plegada debe imprimirse en
   JS exactamente igual que el cálculo original; si no se puede garantizar (resultado no finito, notación
   exponencial, precisión dudosa), **no se pliega**.
3. **Operaciones con efectos o que pueden fallar** no se eliminan ni se reordenan entre sí aunque su
   resultado no se use: `call`, `print`, `printn`, `param`, `[]`, `[]=`, `len`, `concat`, `array`, `return`,
   `goto`, `ifFalse`, `label`. Las demás (`=`, aritméticas, comparaciones, `!`, `-` unario) son puras.
4. **Una `call` puede modificar cualquier global.** Dentro de un bloque, tras una `call` se olvida lo que
   se sabía de las variables que no son locales de la función actual (en `principal()`, de todas las variables).
   Los temporales y los locales de la función actual no se ven afectados (no hay closures).
5. **Verificación obligatoria por pasada**: `EjecucionTest` ejecuta el corpus con las pasadas activas y lo
   compara con el oráculo AST→JS. Cada pasada añade al corpus los casos límite que la ejercitan.
6. **Visibilidad**: `--emit-ir` muestra el IR ya optimizado; la opción `--no-opt` desactiva todas las pasadas
   (para `--emit-ir` y para el `.js`), de modo que se pueda comparar antes y después.

## Orden previsto de las pasadas
1. Plegado y propagación de constantes local (por bloque), incluidas condiciones constantes.
2. Propagación de copias local y eliminación de copias redundantes.
3. Código muerto global (vivacidad) respetando el punto 3, eliminación de bloques inalcanzables y
   simplificación de saltos (salto al siguiente, cadenas de saltos).
4. Opcional: subexpresiones comunes locales.
Las pasadas se pueden repetir hasta punto fijo si una habilita a otra; se decide al implementar la 3.

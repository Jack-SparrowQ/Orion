# Tarea activa — 1.2 Cortocircuito de `&&` y `||` en el IR

Asignada a: Codex · Revisa: Claude · Contexto: `planning/backlog.md` (etapa 1), `docs/decisions/0001-js-desde-ir.md`

## Problema (confirmado)
`GeneradorCodigoIntermedio` trata `&&` y `||` como un `NodoBinario` más: evalúa los dos operandos y
luego emite `$t = a && b`. Con `false && f()` el IR llama a `f`; en JS (y en la semántica de Orion) no
se llama. Cuando el JS salga del IR, se ejecutarían efectos que no deberían (prints, llamadas, errores
de índice o de null).

## Decisión
Traducir con saltos, **sin opcodes nuevos** (solo `=`, `ifFalse`, `goto`, `label`). Un único temporal
recibe el resultado en las dos ramas:

```text
a && b                          a || b
    $t = <a>                        $t = <a>
    ifFalse $t goto $Lfin           ifFalse $t goto $Lder
    $t = <b>                        goto $Lfin
$Lfin:                          $Lder:
                                    $t = <b>
                                $Lfin:
```

`<a>`/`<b>` son el código de cada operando; el de `<b>` se emite **después** del salto, para que solo
se evalúe cuando hace falta. El temporal se asigna en dos sitios (igual que el índice del `for`); no
se exige asignación única.

## Qué hacer
1. `src/main/java/com/compiler/ir/GeneradorCodigoIntermedio.java`: caso especial para `NodoBinario` con
   operador `&&`/`||` según el esquema. El resto de operadores no cambia.
2. Pruebas en `GeneradorCodigoIntermedioTest`: `&&`, `||`, combinación anidada (`a || b && c`), y un operando
   con llamada (`false && f()`) donde el `call` queda después del `ifFalse`.
3. Si algún golden o prueba existente con `&&`/`||` cambia, actualízalo y verifica que solo cambia esto.
4. `README.md`, sección de generación de código intermedio: una frase con el esquema de `&&`/`||`.
5. `Cuadrupla.java`: si el comentario de ejemplos necesita aclaración, una línea como mucho.

## Fuera de alcance
- Generar saltos directos para condiciones de `if`/`while` (`ifFalse a goto ...` sin temporal): eso es optimización.
- Ámbitos, tipos, estructura de funciones (1.3–1.5). `JSGenerator` y demás fases no se tocan.

## Reglas
Las de `AGENTS.md`. Sin dependencias nuevas. Sin commit.

## Criterios de aceptación
- `mvn -q test` con código 0 (reporta tests run / failures / skipped).
- `--emit-ir` de `src/test/resources/programas/cortocircuito.orion`: cada `call visible, 0` aparece después
  del `ifFalse` de su expresión (incluye la salida en el reporte).

## Reporte
Formato de `AGENTS.md`.

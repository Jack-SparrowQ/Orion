# Tarea activa — 1.5 Estructura del IR: funciones separadas del programa principal

Asignada a: Codex · Revisa: Claude · Contexto: `planning/backlog.md` (etapa 1), `docs/decisions/0001-js-desde-ir.md`

## Problema
`GeneradorCodigoIntermedio.generar` devuelve una sola `List<Cuadrupla>` donde las funciones aparecen
intercaladas con el código principal entre `func`/`endfunc`, y los parámetros van como texto
(`"a, b$1"`) en el `resultado` de `func`. Las pasadas de optimización y el futuro generador IR→JS
necesitan trabajar por función (bloques básicos, CFG) sin tener que reconocer delimitadores ni parsear texto.

## Decisión
Nuevos records en `com.compiler.ir` (inmutables, listas copiadas con `List.copyOf`):

```java
public record FuncionIR(String nombre, List<String> parametros, List<Cuadrupla> cuerpo) {}
public record ProgramaIR(List<FuncionIR> funciones, List<Cuadrupla> principal) {}
```

- `generar(List<Sentencia>)` devuelve `ProgramaIR`. Las funciones van en orden de declaración.
- Se eliminan los opcodes `func` y `endfunc` de las cuádruplas. El cuerpo conserva el `return` implícito final.
- Los temporales, etiquetas y nombres IR siguen siendo únicos en **todo** el programa (contadores y tabla de
  declaraciones compartidos), para que una tabla global por nombre siga siendo válida.
- `aTexto(ProgramaIR)`: imprime primero cada función como hasta ahora, `func f, n, [params]` ... `endfunc`
  (o `func f, 0` sin parámetros), y después el programa principal, con la misma sangría actual.
  Es el único cambio de formato visible: las funciones pasan al principio.

## Qué hacer
1. `FuncionIR.java`, `ProgramaIR.java` nuevos; `GeneradorCodigoIntermedio` y `Cuadrupla` (quitar `func`/`endfunc`
   de `toString` y del comentario de ejemplos; documentar en el comentario que las funciones viven en `FuncionIR`).
2. `Compiler.java`: adaptar la llamada a `generar`/`aTexto`. Ningún otro cambio de comportamiento.
3. Pruebas: adaptar las existentes; añadir que `ProgramaIR.funciones()` tiene nombre y parámetros estructurados,
   que `principal()` no contiene cuádruplas de funciones, y que una función declarada después de código principal
   aparece primero en el texto.
4. Goldens con IR, si los hay: regenerar y verificar con `git diff` que solo cambia la posición de las funciones.
5. `README.md` (sección de código intermedio): ajustar la frase sobre `func` y decir que las funciones se listan primero.

## Fuera de alcance
Tipos en el IR (1.4, va después y añadirá la tabla de tipos a `ProgramaIR`). `JSGenerator` y demás fases.

## Reglas
Las de `AGENTS.md`. Sin dependencias nuevas. Sin commit.

## Criterios de aceptación
- `mvn -q test` con código 0 (reporta tests run / failures / skipped).
- `grep -rn '"func"\|"endfunc"' src/main` no encuentra opcodes de cuádrupla (solo el texto que imprime `aTexto`, si aplica).
- `--emit-ir` de `src/test/resources/programas/funciones.orion` en el reporte.

## Reporte
Formato de `AGENTS.md`.

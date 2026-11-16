# La fase de optimización, paso a paso

Esta guía explica qué hace el optimizador de Orion con dos ejemplos reales. Todas las salidas que aparecen
aquí las produjo el compilador; puedes reproducirlas con los comandos de la sección 2.

---

## 1. La idea en una frase

> **Optimizar es reescribir el código intermedio para que haga menos trabajo, sin cambiar nada de lo que el
> programa imprime.**

Esa última parte es la regla de oro. Si una transformación pudiera cambiar la salida en **un solo** caso,
no se hace.

---

## 2. Dónde está y cómo verla

```text
código Orion → léxico → sintaxis → semántica → código intermedio (IR) → OPTIMIZACIÓN → JavaScript
```

El optimizador recibe el IR (las cuádruplas) y devuelve otro IR más corto. Después se genera el JS a
partir del IR optimizado.

```bash
orion --emit-ir --no-opt programa.orion -o -   # IR tal como sale del generador
orion --emit-ir programa.orion -o -            # IR optimizado
orion programa.orion -o -                      # JS (a partir del IR optimizado)
orion --no-opt programa.orion -o -             # JS sin optimizar, para comparar
```

---

## 3. Cuatro palabras que hay que conocer

| Palabra | Qué significa | Ejemplo |
|---|---|---|
| **Cuádrupla** | Una instrucción del IR: operación, dos operandos y un resultado. | `$t1 = precio * cantidad` |
| **Temporal** | Variable inventada por el compilador para guardar resultados intermedios. Empieza por `$t`. | `$t1` |
| **Bloque básico** | Trozo de código que siempre se ejecuta entero, de arriba abajo: solo se entra por la primera instrucción y solo se sale por la última. | ver abajo |
| **Grafo de flujo** | Los bloques básicos unidos con flechas que dicen a qué bloque se puede saltar desde cada uno. | ver abajo |

Un bloque empieza en la primera instrucción, en cada etiqueta (`$L1:`) y justo después de cada salto
(`goto`, `ifFalse`, `return`). Para el programa del ejemplo 1:

```text
 B0 ─────────────────────────┐
 │ precio = 10               │
 │ ...                       │
 │ ifFalse $t2 goto $L1      │──── si es falso ────┐
 └───────────────────────────┘                     │
        │ si es verdadero                          │
        v                                          │
 B1 ─────────────────────────┐                     │
 │ descuento = 5             │                     │
 └───────────────────────────┘                     │
        │                                          │
        v                                          v
 B2 ──────────────────────────────────────────────────┐
 │ $L1:                                               │
 │ $t3 = total - descuento ...                        │
 └────────────────────────────────────────────────────┘
```

¿Por qué importan los bloques? Dentro de un bloque el orden es seguro: si `x = 5` está arriba, abajo `x`
vale 5. Entre bloques no: a `B2` se puede llegar desde `B0` o desde `B1`, con valores distintos.

---

## 4. Ejemplo 1: todo se reduce a `print 30`

```javascript
let precio = 10;
let cantidad = 3;
let total = precio * cantidad;
let descuento = 0;
if (total > 100) {
    descuento = 5;
}
let neto = total - descuento;
print(neto);
```

IR sin optimizar (`--no-opt`):

```text
    precio = 10
    cantidad = 3
    $t1 = precio * cantidad
    total = $t1
    descuento = 0
    $t2 = total > 100
    ifFalse $t2 goto $L1
    descuento = 5
$L1:
    $t3 = total - descuento
    neto = $t3
    print neto
```

El optimizador aplica **cuatro pasadas**, una detrás de otra. Veamos qué cambia cada una.

### Pasada 1 — Plegado y propagación de constantes (`PlegadoConstantes`)

**Idea:** si el compilador ya sabe cuánto vale algo, hace la cuenta él en vez de dejarla para cuando se
ejecute el programa.

- *Propagar*: tras `precio = 10`, cada vez que se lee `precio` en el mismo bloque se escribe `10`.
- *Plegar*: `10 * 3` se convierte en `30`; `30 > 100` en `false`.
- *Condición constante*: `ifFalse false goto $L1` significa «salta siempre», así que pasa a ser `goto $L1`.

```text
    precio = 10
    cantidad = 3
    $t1 = 30                 ← era  precio * cantidad
    total = 30               ← era  $t1
    descuento = 0
    $t2 = false              ← era  total > 100
    goto $L1                 ← era  ifFalse $t2 goto $L1
    descuento = 5
$L1:
    $t3 = total - descuento  ← no cambia: $L1 empieza otro bloque
    neto = $t3
    print neto
```

Fíjate en la última parte: `total` vale 30 en `B0`, pero `$t3 = total - descuento` está en otro bloque, y la
pasada solo trabaja **dentro** de cada bloque. Lo resolverá la segunda vuelta (sección 5).

**Por qué es segura:** las cuentas se hacen con las mismas reglas que JavaScript (`7 / 2` da `3.5`). Si el
resultado se imprimiría distinto en JS (infinito, `-0`, notación `1e21`, números enormes, potencias
inexactas), **no se pliega** y la cuenta se queda para la ejecución.

### Pasada 2 — Propagación de copias (`PropagacionCopias`)

**Idea:** evitar pasos intermedios inútiles.

- Tras `x = y`, se usa `y` directamente donde se leía `x`.
- Un temporal que solo sirve para copiarse en la línea siguiente se elimina:
  `$t3 = total - descuento` + `neto = $t3` → `neto = total - descuento`.

```text
$L1:
    neto = total - descuento   ← eran dos líneas
    print neto
```

### Pasada 3 — Eliminación de código muerto (`EliminacionCodigoMuerto`)

**Idea:** borrar cálculos cuyo resultado nadie va a leer.

Para eso se calcula qué variables están **vivas**: una variable está viva en un punto si su valor actual
todavía se va a leer más adelante. Se recorre el código **de abajo hacia arriba**:

| Instrucción (leída de abajo arriba) | ¿Alguien lee después su resultado? | Decisión |
|---|---|---|
| `print neto` | (no tiene resultado) | se queda |
| `neto = total - descuento` | sí, `print neto` | se queda |
| `goto $L1` | (es un salto) | se queda |
| `$t2 = false` | no | **se borra** |
| `descuento = 0` | sí, en `$L1` | se queda |
| `total = 30` | sí, en `$L1` | se queda |
| `$t1 = 30` | no | **se borra** |
| `cantidad = 3`, `precio = 10` | no (ya se propagaron) | **se borran** |

```text
    total = 30
    descuento = 0
    goto $L1
    descuento = 5     ← todavía aquí: nadie llega a esta línea, pero esta pasada no lo sabe
$L1:
    neto = total - descuento
    print neto
```

**Por qué es segura:** solo se borran operaciones **puras** (asignaciones, sumas, comparaciones...). Nunca se
borran las que hacen algo visible o pueden fallar: `call`, `print`, acceso a arreglo `[]`, `len`, `concat`...
Aunque su resultado no se use, siguen ahí (por ejemplo, `v[10]` debe seguir dando error si el índice no existe).

### Pasada 4 — Simplificación de saltos (`SimplificacionSaltos`)

**Idea:** quitar saltos y código que no sirven.

- `descuento = 5` está después de un `goto` y ningún salto lleva a él: es **inalcanzable** y se borra.
- `goto $L1` salta a la línea siguiente: no hace nada y se borra.
- `$L1:` ya no la usa ningún salto: se borra.

```text
    total = 30
    descuento = 0
    neto = total - descuento
    print neto
```

Ahora todo el programa es **un solo bloque**...

---

## 5. Repetir hasta que no cambie nada (punto fijo)

...y eso permite que la pasada 1 haga lo que antes no podía. Por eso las cuatro pasadas se **repiten** hasta
que una vuelta completa no cambia nada (como máximo 10 vueltas):

| Vuelta | Pasada | Resultado |
|---|---|---|
| 2 | Plegado | `neto = 30` y `print 30` (ahora todo está en el mismo bloque) |
| 2 | Código muerto | borra `total`, `descuento` y `neto`: nadie los lee → queda solo `print 30` |
| 3 | las cuatro | sin cambios → **punto fijo**, se termina |

IR final:

```text
    print 30
```

JS final, comparado con el que se genera con `--no-opt`:

```javascript
// optimizado
console.log(30);
```

```javascript
// --no-opt
let $t1, $t2, $t3, cantidad, descuento, neto, precio, total;
let $pc = 0;
$bucle: for (;;) switch ($pc) {
    case 0:
        precio = 10;
        cantidad = 3;
        $t1 = precio * cantidad;
        total = $t1;
        descuento = 0;
        $t2 = total > 100;
        if (!($t2)) { $pc = 1; continue $bucle; }
        descuento = 5;
    case 1:
        $t3 = total - descuento;
        neto = $t3;
        console.log(neto);
        break $bucle;
}
```

Los dos imprimen `30`.

---

## 6. Ejemplo 2: lo que el optimizador NO toca, y por qué

```javascript
let puntos = 0;
fn ganar(): void { puntos += 10; }
let rondas = 1 + 2;
let i = 0;
while (i < rondas) {
    let antes = puntos;
    ganar();
    print(antes, puntos);
    i += 1;
}
```

IR sin optimizar → IR optimizado:

```text
func ganar, 0                       func ganar, 0
    $t1 = puntos + 10                   puntos = puntos + 10
    puntos = $t1                        return
    return                          endfunc
endfunc                                 puntos = 0
    puntos = 0                          rondas = 3
    $t2 = 1 + 2                         i = 0
    rondas = $t2                    $L1:
    i = 0                               $t3 = i < rondas
$L1:                                    ifFalse $t3 goto $L2
    $t3 = i < rondas                    antes = puntos
    ifFalse $t3 goto $L2                call ganar, 0
    antes = puntos                      param antes
    call ganar, 0                       param puntos
    param antes                         printn 2
    param puntos                        i = i + 1
    printn 2                            goto $L1
    $t4 = i + 1                     $L2:
    i = $t4
    goto $L1
$L2:
```

**Lo que sí se optimizó:** `1 + 2` → `3`; `$t1`, `$t2` y `$t4` desaparecen (copias y código muerto).

**Lo que no, y por qué:**

1. **`antes = puntos` no se sustituye por `puntos` en el `print`.** Entre medias está `call ganar, 0`, y
   `ganar` cambia `puntos`. El optimizador asume que **una llamada puede cambiar cualquier variable global**,
   así que olvida lo que sabía de ellas. Si sustituyera, imprimiría `10 10` en vez de `0 10`.
2. **`rondas` no se sustituye por `3` en `i < rondas`.** `rondas = 3` está en el primer bloque y la
   comparación en otro (`$L1:`), al que también se llega desde el final del ciclo. Las pasadas de constantes y
   copias trabajan solo **dentro** de un bloque.
3. **El ciclo se queda.** Sus saltos son necesarios. En el JS, un cuerpo con saltos se genera como un bucle
   `for (;;) switch ($pc)`; uno sin saltos (como el ejemplo 1) sale lineal.

---

## 7. Las reglas de seguridad, resumidas

| Regla | Ejemplo |
|---|---|
| Las cuentas se hacen como en JavaScript; si no se puede garantizar el mismo resultado impreso, no se pliega. | `7 / 2` → `3.5`; `2 ** 0.5` no se pliega |
| Nunca se borran operaciones con efectos o que pueden fallar, aunque su resultado no se use. | `call`, `print`, `[]`, `[]=`, `len`, `concat` |
| Una llamada puede cambiar cualquier global. | ejemplo 2, punto 1 |
| Constantes y copias: solo dentro de un bloque. Código muerto: en toda la función (sigue las flechas del grafo). | ejemplo 2, punto 2 |

Decisión de diseño completa: [`docs/decisions/0002-optimizacion-ir.md`](decisions/0002-optimizacion-ir.md).

---

## 8. Cómo se comprueba que no se rompe nada

`EjecucionTest` compila con todas las pasadas activas cada programa de `src/test/resources/programas/`,
lo ejecuta con `node` y comprueba dos cosas:

1. que imprime exactamente la salida esperada (escrita a mano en el archivo `.salida`);
2. que imprime lo mismo que el JS generado por el camino antiguo (directamente desde el AST, sin IR ni
   optimizaciones). Si una optimización cambiara el comportamiento, esta comparación falla.

Además, cada pasada tiene sus pruebas unitarias en `src/test/java/com/compiler/ir/opt/`.

---

## 9. Dónde está el código

| Qué | Archivo |
|---|---|
| Lista de pasadas y repetición hasta punto fijo | `Compiler.java` (`OPTIMIZACIONES`, `optimizar`) |
| Bloques básicos y grafo de flujo | `ir/GrafoFlujo.java`, `ir/BloqueBasico.java` |
| Pasada 1 | `ir/opt/PlegadoConstantes.java` |
| Pasada 2 | `ir/opt/PropagacionCopias.java` |
| Pasada 3 | `ir/opt/EliminacionCodigoMuerto.java` |
| Pasada 4 | `ir/opt/SimplificacionSaltos.java` |
| Utilidades comunes (qué lee y qué escribe cada cuádrupla, cuáles son puras) | `ir/opt/OperacionesIR.java` |

---

## 10. Limitaciones conocidas

- Constantes y copias solo se propagan dentro de un bloque (ejemplo 2, punto 2). Hacerlo entre bloques
  requiere un análisis de flujo de datos hacia delante, como el de vivacidad pero en sentido contrario.
- No se eliminan subexpresiones repetidas (`a * b` calculado dos veces).
- Se descartó la optimización *peephole* con un opcode `ifTrue` (invertir `ifFalse c goto L1; goto L2; L1:`).
- Detalles menores pendientes: ver `planning/backlog.md`.

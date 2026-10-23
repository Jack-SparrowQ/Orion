# Especificación formal del lenguaje Orion

**Versión de la especificación:** 2
**Notación:** EBNF (ISO/IEC 14977, con las convenciones de la sección 1)
**Lenguaje destino del compilador:** JavaScript

Este documento es la referencia normativa de Orion. Define:

1. La **gramática léxica** (caracteres → tokens), sección 3.
2. La **gramática sintáctica** (tokens → árbol sintáctico), secciones 4 a 9.
3. Las **restricciones estáticas** que no expresa una gramática libre de
   contexto: ámbitos, tipos, flujo de control (secciones 10 a 13).
4. La **semántica de ejecución** mínima (sección 14) y los **códigos de
   error** (sección 15).

La sección 16 indica qué parte de la especificación está implementada
en el compilador actual. Cuando el compilador y este documento no
coinciden, manda el documento, y la diferencia se considera un defecto
o una tarea pendiente del compilador.

---

## 1. Convenciones de notación

| Notación    | Significado                                                           |
| ----------- | --------------------------------------------------------------------- |
| `A = … ;`   | Definición de la regla `A`.                                           |
| `"x"`       | Terminal literal (palabra reservada, operador o delimitador).         |
| `a , b`     | Concatenación: `a` seguido de `b`.                                    |
| `a \| b`    | Alternativa.                                                          |
| `[ a ]`     | `a` opcional (cero o una vez).                                        |
| `{ a }`     | `a` repetido cero o más veces.                                        |
| `( a )`     | Agrupación.                                                           |
| `a - b`     | Excepción: cualquier cadena de `a` que no sea de `b`.                 |
| `? texto ?` | Secuencia especial: clase de caracteres descrita en lenguaje natural. |
| `ε`         | Cadena vacía (solo se usa en comentarios y en los conjuntos FIRST).   |

En la gramática sintáctica, los nombres en `MAYÚSCULAS` son categorías
de tokens producidas por el lexer (`IDENTIFICADOR`, `ENTERO`, `DECIMAL`,
`CADENA`, `CARACTER`, `EOF`). Los nombres en `CamelCase` son no
terminales.

---

## 2. Estructura de una unidad de compilación

Un archivo `.orion` es una única unidad de compilación. Su texto fuente
se interpreta como una secuencia de caracteres Unicode (UTF-8). El
análisis se hace en dos niveles:

- **Léxico:** el texto se divide en tokens. Los espacios en blanco y los
  comentarios separan tokens y luego se descartan.
- **Sintáctico:** la secuencia de tokens, terminada siempre en `EOF`, se
  analiza con la gramática de la sección 4.

Cada token conserva la **línea** y la **columna** (ambas desde 1) de su
primer carácter. El token `EOF` se ubica en la posición inmediatamente
posterior al último carácter del archivo.

---

## 3. Gramática léxica

### 3.1 Clases de caracteres

```ebnf
Letra             = ? carácter para el que Character.isLetter es verdadero
                      (letras Unicode, incluidas á, é, ñ, ü…) ? ;
DigitoAscii       = "0" | "1" | "2" | "3" | "4"
                  | "5" | "6" | "7" | "8" | "9" ;
DigitoUnicode     = ? carácter para el que Character.isDigit es verdadero ? ;
FinDeLinea        = ? U+000A (LF) ? ;
Blanco            = ? carácter para el que Character.isWhitespace es
                      verdadero (espacio, tabulador, CR, LF, …) ? ;
CualquierCaracter = ? cualquier carácter Unicode ? ;
```

### 3.2 Elementos ignorados

```ebnf
Ignorado          = Blanco | ComentarioLinea | ComentarioBloque ;

ComentarioLinea   = "//" , { CualquierCaracter - FinDeLinea } ;

ComentarioBloque  = "/*" , { CualquierCaracter - "*/" } , "*/" ;
```

- Un comentario de línea termina en el salto de línea (que no forma
  parte del comentario) o en el fin del archivo.
- Los comentarios de bloque **no se anidan**: terminan en el primer
  `*/`. Un `/*` sin su `*/` es un error léxico (E105).
- Dentro de una cadena o de un carácter, `//` y `/*` no inician un
  comentario.

### 3.3 Tokens

```ebnf
Token             = PalabraReservada | IDENTIFICADOR | Literal
                  | Operador | Delimitador ;
```

**Regla de la coincidencia más larga (maximal munch):** en cada
posición, el lexer reconoce el token más largo posible. Por eso `**`
se reconoce antes que `*`, `<=` antes que `<`, `+=` antes que `+`,
`==` antes que `=`, y `iffy` es un identificador, no `if` seguido de
`fy`. Los operadores compuestos son siempre un único token: `!=` nunca
se divide en `!` y `=`.

### 3.4 Identificadores y palabras reservadas

```ebnf
IDENTIFICADOR     = ( ( Letra | "_" ) , { Letra | DigitoUnicode | "_" } )
                    - PalabraReservada ;

PalabraReservada  = "if" | "else" | "while" | "for" | "in"
                  | "break" | "continue" | "return"
                  | "let" | "const" | "fn"
                  | "true" | "false" | "null"
                  | "int" | "float" | "char" | "string" | "bool" | "void"
                  | "print"
                  | "import" | "from" | "export" ;
```

- Los identificadores distinguen mayúsculas de minúsculas: `total` y
  `Total` son distintos.
- `import`, `from` y `export` están **reservadas para versiones futuras**
  (módulos). No tienen producción sintáctica: usarlas produce un error
  sintáctico.
- Los nombres de tipo (`int`, `float`, `char`, `string`, `bool`, `void`)
  son palabras reservadas, de modo que no pueden usarse como
  identificadores.

### 3.5 Literales numéricos

```ebnf
ENTERO            = DigitoAscii , { DigitoAscii } ;

DECIMAL           = DigitoAscii , { DigitoAscii } , "." ,
                    DigitoAscii , { DigitoAscii } ;
```

- Solo se admiten dígitos ASCII y base 10.
- La parte decimal exige al menos un dígito a cada lado del punto: `3.`
  se reconoce como `ENTERO(3)` seguido de `"."`, y `.5` como `"."`
  seguido de `ENTERO(5)`.
- No hay exponentes (`1e3`), separadores (`1_000`), sufijos ni prefijos
  de base (`0x`).
- No existen literales negativos: `-5` son dos tokens, `"-"` y
  `ENTERO(5)`.
- Los ceros a la izquierda están permitidos y no cambian la base:
  `007` vale 7.
- El valor de un `ENTERO` debe estar en el rango `[0, 2^53 − 1]`
  (enteros exactos de JavaScript). Fuera de ese rango es un error
  léxico (E106).

### 3.6 Literales de cadena y de carácter

```ebnf
CADENA            = '"' , { CaracterCadena | Escape } , '"' ;
CaracterCadena    = CualquierCaracter - ( '"' | "\" | FinDeLinea ) ;

CARACTER          = "'" , ( CaracterChar | Escape ) , "'" ;
CaracterChar      = CualquierCaracter - ( "'" | "\" | FinDeLinea ) ;

Escape            = "\" , ( "n" | "t" | "r" | "0" | "\" | '"' | "'" ) ;
```

| Escape | Significado           |
| ------ | --------------------- |
| `\n`   | Salto de línea (LF)   |
| `\t`   | Tabulador horizontal  |
| `\r`   | Retorno de carro (CR) |
| `\0`   | Carácter nulo         |
| `\\`   | Barra invertida       |
| `\"`   | Comilla doble         |
| `\'`   | Comilla simple        |

- Una cadena o un carácter no pueden contener un salto de línea literal.
  Una cadena sin cerrar antes del fin de línea o del archivo es el error
  E103.
- Un escape no listado (por ejemplo `\q`) es el error E104.
- Un literal de carácter contiene **exactamente un** carácter o un
  escape: `''` y `'ab'` son el error E107.

### 3.7 Operadores y delimitadores

```ebnf
Operador          = "+" | "-" | "*" | "/" | "%" | "**"
                  | "=" | "+=" | "-=" | "*=" | "/=" | "%="
                  | "==" | "!=" | "<" | "<=" | ">" | ">="
                  | "&&" | "||" | "!" ;

Delimitador       = "(" | ")" | "{" | "}" | "[" | "]"
                  | "," | ":" | ";" | "." ;
```

- `&` y `|` aislados no son tokens válidos: producen los errores E101 y
  E102.
- Cualquier otro carácter que no inicie un token ni un elemento ignorado
  produce el error E100. El lexer lo descarta y continúa, para poder
  informar varios errores en una sola compilación.

---

## 4. Programa

```ebnf
Programa          = { ElementoGlobal } , EOF ;

ElementoGlobal    = DeclaracionFuncion
                  | Sentencia ;
```

- Un programa puede estar vacío.
- Las funciones solo pueden declararse en el **nivel superior**. No hay
  funciones anidadas: un `fn` dentro de un bloque es un error
  sintáctico.
- Las sentencias del nivel superior se ejecutan en orden de aparición.
  No existe una función `main`.

---

## 5. Sentencias

```ebnf
Sentencia         = DeclaracionVariable , ";"
                  | SentenciaSimple , ";"
                  | SentenciaPrint , ";"
                  | SentenciaReturn , ";"
                  | "break" , ";"
                  | "continue" , ";"
                  | SentenciaIf
                  | SentenciaWhile
                  | SentenciaFor
                  | Bloque ;

Bloque            = "{" , { Sentencia } , "}" ;
```

`Bloque` como sentencia independiente abre un ámbito nuevo
(sección 10). No existe la sentencia vacía: `;` aislado es un error
sintáctico.

### 5.1 Declaraciones de variables y constantes

```ebnf
DeclaracionVariable
                  = "let" , IDENTIFICADOR , EspecificacionLet
                  | "const" , IDENTIFICADOR , [ ":" , Tipo ] ,
                    "=" , Expresion ;

EspecificacionLet = ":" , Tipo , [ "=" , Expresion ]
                  | "=" , Expresion ;
```

La gramática ya impide las formas que no tienen tipo determinable:

| Forma             | ¿Válida? | Tipo de `x`                               |
| ----------------- | -------- | ----------------------------------------- |
| `let x: int = 5;` | Sí       | `int`                                     |
| `let x = 5;`      | Sí       | `int` (inferido)                          |
| `let x: float;`   | Sí       | `float`, sin inicializar                  |
| `let x;`          | **No**   | Error sintáctico: falta `:` Tipo o `=`    |
| `const x = 5;`    | Sí       | `int` (inferido)                          |
| `const x: int;`   | **No**   | Error sintáctico: una constante exige `=` |

### 5.2 Asignaciones y llamadas como sentencia

```ebnf
SentenciaSimple   = ExpPostfija , [ OperadorAsignacion , Expresion ] ;

OperadorAsignacion
                  = "=" | "+=" | "-=" | "*=" | "/=" | "%=" ;
```

`SentenciaSimple` agrupa las asignaciones y las llamadas usadas como
sentencia. Ambas empiezan por una `ExpPostfija`, así que la gramática
las factoriza por la izquierda (sección 9). El parser aplica después
estas dos restricciones a la `ExpPostfija` inicial:

1. **Con operador de asignación**, la parte izquierda debe ser un
   destino asignable:

   ```ebnf
   DestinoAsignable  = IDENTIFICADOR , { "[" , Expresion , "]" } ;
   ```

   Son destinos válidos `x`, `v[i]` y `m[i][j]`. No lo son `f()`,
   `f()[0]`, `(x)`, `v.length` ni `5`. Un destino no válido es el
   error E204.

2. **Sin operador de asignación**, la sentencia debe ser una **llamada a
   función** (su último sufijo es una lista de argumentos). Una
   expresión sin efecto, como `a + 1;` o `x;`, es el error E205.

La asignación es una **sentencia**, no una expresión: `a = b = c;`,
`if (a = b)` y `print(x = 1)` no pertenecen al lenguaje.

**Asignación compuesta.** `d op= e` equivale a `d = d op (e)`, pero los
subíndices de `d` se evalúan una sola vez. Por ejemplo,
`v[f()] += 1` llama a `f` una única vez.

### 5.3 Salida por consola

```ebnf
SentenciaPrint    = "print" , "(" , [ ListaArgumentos ] , ")" ;
```

`print` es una sentencia, no una función: no puede aparecer dentro de
una expresión. Escribe sus argumentos separados por un espacio y
termina con un salto de línea. `print()` escribe solo el salto de línea.
La entrada de datos no forma parte de esta versión.

### 5.4 Control de flujo

```ebnf
SentenciaIf       = "if" , "(" , Expresion , ")" , Bloque ,
                    [ "else" , ( SentenciaIf | Bloque ) ] ;

SentenciaWhile    = "while" , "(" , Expresion , ")" , Bloque ;

SentenciaFor      = "for" , "(" , IDENTIFICADOR , "in" , Expresion , ")" ,
                    Bloque ;

SentenciaReturn   = "return" , [ Expresion ] ;
```

- Los cuerpos son siempre un `Bloque`, así que no existe la
  ambigüedad del _else colgante_: `else` pertenece siempre al `if` cuyo
  bloque acaba de cerrarse.
- `else if` se representa como un `SentenciaIf` anidado en la rama
  `else`.
- En `for (x in e)`, `x` es una variable nueva, de solo lectura y local
  al cuerpo del ciclo, que toma en cada iteración un elemento de `e`
  (sección 12.6).
- La opcionalidad de `Expresion` en `return` se resuelve con un token
  de anticipación: si el siguiente token es `";"`, el `return` no lleva
  valor.

---

## 6. Funciones

```ebnf
DeclaracionFuncion
                  = "fn" , IDENTIFICADOR ,
                    "(" , [ ListaParametros ] , ")" ,
                    [ ":" , TipoRetorno ] ,
                    Bloque ;

ListaParametros   = Parametro , { "," , Parametro } ;

Parametro         = IDENTIFICADOR , ":" , Tipo ;
```

- Los parámetros **siempre** declaran su tipo. Sin anotación no
  habría información para inferirlo.
- Si se omite el tipo de retorno, la función es `void`.
- No hay parámetros por defecto, parámetros variádicos ni sobrecarga.

Ejemplo:

```orion
fn sumar(a: int, b: int): int {
    return a + b;
}

fn saludar(nombre: string) {
    print("Hola", nombre);
}
```

---

## 7. Tipos

```ebnf
Tipo              = TipoBase , { "[" , "]" } ;

TipoBase          = "int" | "float" | "char" | "string" | "bool" ;

TipoRetorno       = Tipo | "void" ;
```

- `void` solo puede usarse como tipo de retorno. La gramática ya impide
  `let x: void` y `v: void[]`.
- `T[]` es un arreglo de elementos de tipo `T`. Se admiten varias
  dimensiones: `int[][]` es un arreglo de arreglos de `int`.
- `null` es un **literal**, no un tipo que se pueda declarar. Su tipo
  interno se escribe `null` en este documento (sección 11.1).

---

## 8. Expresiones

### 8.1 Precedencia y asociatividad

De **menor** a **mayor** precedencia:

| Nivel | Operadores                                   | Aridad  | Asociatividad     | Regla               |
| ----- | -------------------------------------------- | ------- | ----------------- | ------------------- |
| 1     | `\|\|`                                       | binario | izquierda         | `ExpOr`             |
| 2     | `&&`                                         | binario | izquierda         | `ExpAnd`            |
| 3     | `==` `!=`                                    | binario | izquierda         | `ExpIgualdad`       |
| 4     | `<` `<=` `>` `>=`                            | binario | izquierda         | `ExpRelacional`     |
| 5     | `+` `-`                                      | binario | izquierda         | `ExpAditiva`        |
| 6     | `*` `/` `%`                                  | binario | izquierda         | `ExpMultiplicativa` |
| 7     | `!` `-`                                      | unario  | derecha (prefijo) | `ExpUnaria`         |
| 8     | `**`                                         | binario | **derecha**       | `ExpPotencia`       |
| 9     | `f(…)` `a[i]` `a.campo`                      | sufijo  | izquierda         | `ExpPostfija`       |
| 10    | literales, identificadores, `( … )`, `[ … ]` | —       | —                 | `Primario`          |

### 8.2 Producciones

```ebnf
Expresion         = ExpOr ;

ExpOr             = ExpAnd , { "||" , ExpAnd } ;

ExpAnd            = ExpIgualdad , { "&&" , ExpIgualdad } ;

ExpIgualdad       = ExpRelacional , { ( "==" | "!=" ) , ExpRelacional } ;

ExpRelacional     = ExpAditiva ,
                    { ( "<" | "<=" | ">" | ">=" ) , ExpAditiva } ;

ExpAditiva        = ExpMultiplicativa ,
                    { ( "+" | "-" ) , ExpMultiplicativa } ;

ExpMultiplicativa = ExpUnaria , { ( "*" | "/" | "%" ) , ExpUnaria } ;

ExpUnaria         = ( "!" | "-" ) , ExpUnaria
                  | ExpPotencia ;

ExpPotencia       = ExpPostfija , [ "**" , ExpUnaria ] ;

ExpPostfija       = Primario , { Sufijo } ;

Sufijo            = "[" , Expresion , "]"
                  | "." , IDENTIFICADOR ;

Primario          = Literal
                  | IDENTIFICADOR , [ Argumentos ]
                  | "(" , Expresion , ")"
                  | LiteralArreglo ;

Argumentos        = "(" , [ ListaArgumentos ] , ")" ;

ListaArgumentos   = Expresion , { "," , Expresion } ;

LiteralArreglo    = "[" , [ ListaArgumentos ] , "]" ;

Literal           = ENTERO | DECIMAL | CADENA | CARACTER
                  | "true" | "false" | "null" ;
```

### 8.3 Consecuencias de las reglas

- **Asociatividad izquierda.** Las reglas de la forma
  `A = B , { op , B }` se construyen en el AST como un árbol que crece
  hacia la izquierda: `a - b - c` es `(a - b) - c`.
- **Comparaciones encadenadas.** `a < b < c` y `a == b == c` son
  sintácticamente válidas y se agrupan como `(a < b) < c`. La primera es
  un error de tipos (E306: `bool < int`). La segunda es válida solo si
  `c` es `bool`. Orion no da a las comparaciones encadenadas el sentido
  matemático de `a < b && b < c`.
- **Potencia.** `**` es asociativo por la derecha y tiene más
  precedencia que los operadores unarios:
  - `2 ** 3 ** 2` es `2 ** (3 ** 2)` = 512.
  - `-2 ** 2` es `-(2 ** 2)` = −4.
  - `(-2) ** 2` = 4.
  - `2 ** -1` es válido, porque el exponente es una `ExpUnaria`, y vale
    0.5.

  JavaScript rechaza `-2 ** 2` sin paréntesis, así que el generador
  debe emitir siempre los paréntesis explícitos: `-(2 ** 2)`.

- **Llamadas.** Solo se puede llamar a un identificador:
  `IDENTIFICADOR , Argumentos`. Las funciones no son valores, así que
  `(f)(1)` y `v[0](1)` no pertenecen al lenguaje. El resultado de una
  llamada sí admite sufijos: `obtener()[0]`, `nombre().length`.
- **Accesos encadenados.** `m[1][2]` y `v[i].length` son válidos.

---

## 9. Propiedades de la gramática y estrategia de análisis

La gramática sintáctica es **LL(1)**: se puede analizar con un parser
descendente recursivo que mira un solo token, sin retroceso. Los puntos
que lo garantizan son:

1. **Factorización de `IDENTIFICADOR`.** En `Primario`, la llamada y la
   variable comparten el prefijo `IDENTIFICADOR`. Por eso la regla se
   escribe `IDENTIFICADOR , [ Argumentos ]`: el token `"("` decide.
2. **Asignación frente a llamada.** `SentenciaSimple` empieza siempre
   por `ExpPostfija`. Después se mira si sigue un
   `OperadorAsignacion`. Con esto se evita necesitar k > 1 tokens de
   anticipación (un destino como `v[i + 1]` tiene longitud arbitraria).
3. **Selección de la sentencia.** Los conjuntos FIRST de las
   alternativas de `Sentencia` son disjuntos:

   | Alternativa           | FIRST                                                                                         |
   | --------------------- | --------------------------------------------------------------------------------------------- |
   | `DeclaracionVariable` | `let`, `const`                                                                                |
   | `SentenciaPrint`      | `print`                                                                                       |
   | `SentenciaReturn`     | `return`                                                                                      |
   | `break` / `continue`  | `break` / `continue`                                                                          |
   | `SentenciaIf`         | `if`                                                                                          |
   | `SentenciaWhile`      | `while`                                                                                       |
   | `SentenciaFor`        | `for`                                                                                         |
   | `Bloque`              | `{`                                                                                           |
   | `SentenciaSimple`     | `IDENTIFICADOR`, `ENTERO`, `DECIMAL`, `CADENA`, `CARACTER`, `true`, `false`, `null`, `(`, `[` |

   `ElementoGlobal` añade `fn`, que tampoco está en ninguno de esos
   conjuntos. `{` solo inicia `Bloque` porque Orion no tiene literales
   de objeto.

4. **`else` opcional.** FOLLOW(`SentenciaIf`) = FOLLOW(`Sentencia`) =
   FIRST(`Sentencia`) ∪ { `fn`, `}`, `EOF` }. Ese conjunto no contiene
   `else`, así que la decisión `[ "else" … ]` es determinista.
5. **Sin recursión por la izquierda.** Los niveles binarios usan
   repetición `{ … }` (bucles en el parser). `ExpUnaria` y `ExpPotencia`
   usan recursión por la derecha.

Las restricciones E204 y E205 de la sección 5.2 se comprueban en el
parser, sobre la `ExpPostfija` ya construida, y no requieren más
anticipación.

### 9.1 Recuperación de errores

Tras un error sintáctico, el parser entra en **modo pánico**: descarta
tokens hasta una de estas condiciones:

- Acaba de consumir un `";"`.
- El siguiente token puede iniciar una sentencia (`let`, `const`,
  `print`, `if`, `while`, `for`, `return`, `break`, `continue`, `fn`).
- El siguiente token es el `"}"` del bloque actual.

Si en ese recorrido descarta un `"{"`, descarta también todo el bloque
hasta su `"}"` correspondiente, para no analizar su contenido fuera de
contexto. Así se informan varios errores sin errores en cascada.

---

## 10. Ámbitos y declaraciones

### 10.1 Ámbitos

| Ámbito         | Se abre en         | Contiene                                  |
| -------------- | ------------------ | ----------------------------------------- |
| Global         | El programa        | Funciones y variables del nivel superior  |
| De función     | El cuerpo de `fn`  | Parámetros y variables locales del cuerpo |
| De bloque      | Cada `Bloque`      | Variables declaradas en el bloque         |
| De ciclo `for` | El cuerpo de `for` | La variable de iteración                  |

Los parámetros pertenecen al mismo ámbito que el bloque del cuerpo de
la función. Una variable local no puede llamarse igual que un
parámetro.

### 10.2 Reglas de visibilidad

1. **Declaración antes de uso.** Una variable es visible desde el final
   de su declaración hasta el final del ámbito que la contiene. En
   `let a = a + 1;`, la `a` de la derecha no es la que se está
   declarando. Usar un nombre que existe pero aún no es visible es el
   error E303; usar un nombre que no existe en ningún ámbito es E301.
2. **Funciones elevadas (hoisting).** Las funciones se registran en una
   primera pasada, antes de analizar cualquier sentencia. Se pueden
   llamar antes de su declaración textual, lo que permite la recursión
   directa y la mutua.
3. **Acceso a globales desde funciones.** El cuerpo de una función puede
   usar las variables globales declaradas **antes** de la declaración de
   la función, y ninguna otra. Así una variable nunca se lee antes de
   haberse inicializado.
4. **Sin redeclaración ni ocultamiento.** Un nombre no puede declararse
   si ya es visible en el mismo ámbito **o en uno que lo contenga**
   (E302). Dos bloques hermanos sí pueden usar el mismo nombre:

   ```orion
   if (c) { let t = 1; } else { let t = 2; }   // válido
   let n = 1;
   if (c) { let n = 2; }                       // E302: oculta a n
   ```

5. **Espacio de nombres único.** Funciones y variables comparten un
   mismo espacio de nombres: no puede existir una variable global y una
   función con el mismo nombre (E302).

### 10.3 Constantes y variables de solo lectura

- Una `const` no puede ser destino de una asignación, simple o compuesta
  (E305). Tampoco puede serlo un elemento de una `const` de tipo
  arreglo: `const v = [1, 2]; v[0] = 5;` es E305.
- La variable de iteración de un `for` es de solo lectura (E305).

### 10.4 Variables sin inicializar

`let x: T;` declara `x` sin valor. Leer `x` en un punto donde no se
haya asignado en **todos** los caminos posibles es el error E309. El
análisis es conservador: una asignación dentro de un `if` sin `else` o
dentro de un ciclo no cuenta como asignación definitiva.

---

## 11. Sistema de tipos

Orion tiene tipado **estático y fuerte**. Cada expresión tiene un tipo
conocido en tiempo de compilación. La única conversión implícita es la
**promoción de `int` a `float`**.

### 11.1 Tipos de los literales

| Literal              | Tipo                                                      |
| -------------------- | --------------------------------------------------------- |
| `ENTERO`             | `int`                                                     |
| `DECIMAL`            | `float`                                                   |
| `CADENA`             | `string`                                                  |
| `CARACTER`           | `char`                                                    |
| `true`, `false`      | `bool`                                                    |
| `null`               | `null` (solo es compatible con `string` y con arreglos)   |
| `[e1, …, en]`, n ≥ 1 | `T[]`, donde `T` es el tipo común de los elementos (11.4) |
| `[]`                 | Necesita un tipo esperado por el contexto (11.5)          |

### 11.2 Operadores

En la tabla, _num_ significa `int` o `float`. Cuando un operador
numérico recibe un `int` y un `float`, el `int` se promueve a `float`.

| Operador          | Operandos                          | Resultado                        |
| ----------------- | ---------------------------------- | -------------------------------- |
| `+`               | `int`, `int`                       | `int`                            |
| `+`               | _num_, _num_ (al menos un `float`) | `float`                          |
| `+`               | `string`, `string`                 | `string` (concatenación)         |
| `-` `*` `%`       | `int`, `int`                       | `int`                            |
| `-` `*` `%`       | _num_, _num_ (al menos un `float`) | `float`                          |
| `/`               | _num_, _num_                       | `float` (siempre: `7 / 2` = 3.5) |
| `**`              | _num_, _num_                       | `float`                          |
| `<` `<=` `>` `>=` | _num_, _num_                       | `bool`                           |
| `<` `<=` `>` `>=` | `char`, `char`                     | `bool` (orden de código Unicode) |
| `==` `!=`         | mismo tipo, o _num_ y _num_        | `bool`                           |
| `==` `!=`         | `string` o `T[]` con `null`        | `bool`                           |
| `&&` `\|\|`       | `bool`, `bool`                     | `bool`                           |
| `!`               | `bool`                             | `bool`                           |
| `-` (unario)      | `int` / `float`                    | el mismo tipo                    |

- Cualquier combinación que no aparece en la tabla es el error E306. En
  particular, no hay conversión implícita a `string` (`"n=" + 5` es
  E306), ni entre `char` y `string`, ni entre `bool` y los números.
- `==` entre arreglos compara **identidad**, no contenido.
- `==` entre cadenas compara **contenido**.

### 11.3 Sufijos

| Expresión  | Requisito                                          | Tipo resultante        | ¿Asignable?                     |
| ---------- | -------------------------------------------------- | ---------------------- | ------------------------------- |
| `a[i]`     | `a: T[]`, `i: int`                                 | `T`                    | Sí                              |
| `s[i]`     | `s: string`, `i: int`                              | `char`                 | No (las cadenas son inmutables) |
| `a.length` | `a: T[]` o `a: string`                             | `int`                  | No                              |
| `f(e1…en)` | `f` es una función con n parámetros (sección 12.3) | tipo de retorno de `f` | No                              |

- Un índice que no es `int` o un valor no indexable es el error E310.
- `.length` es la **única** propiedad definida. Cualquier otro nombre
  después de `.`, o `.length` sobre otro tipo, es el error E311.

### 11.4 Compatibilidad de asignación

Un valor de tipo `V` es **asignable** a un destino de tipo `D` cuando
se cumple una de estas condiciones:

1. `V = D`.
2. `V = int` y `D = float` (promoción).
3. `V = null` y `D` es `string` o un tipo arreglo.

Los arreglos son **invariantes**: un `int[]` no es asignable a un
`float[]`. Esta relación se aplica en las declaraciones con tipo, en las
asignaciones, al pasar argumentos y en `return`. Si no se cumple, el
error es E307.

El **tipo común** de los elementos de un literal de arreglo es:

- `T`, si todos son de tipo `T`.
- `float`, si todos son numéricos y hay al menos un `float`.
- El tipo `T` de los demás elementos, si algunos son `null` y `T` es
  `string` o un arreglo.

Si no existe un tipo común, el error es E307.

### 11.5 Inferencia de tipos

En `let x = e;` y en `const x = e;`, el tipo de `x` es el tipo de `e`,
sin promoción. La inferencia falla, con el error E312, cuando el tipo de
`e`:

- es `null` (`let x = null;`);
- es un arreglo vacío sin tipo esperado (`let v = [];`);
- es `void` (`let r = procedimiento();`).

Con anotación de tipo, el tipo esperado guía el literal:
`let v: int[] = [];` y `let s: string = null;` son válidos.

---

## 12. Reglas estáticas de sentencias

### 12.1 Condiciones

La condición de `if` y de `while` debe ser de tipo `bool` (E308). No hay
valores "verdaderos" o "falsos" implícitos: `if (1)` y `while (n)` con
`n: int` son errores.

### 12.2 `break` y `continue`

Solo pueden aparecer dentro del cuerpo de un `while` o de un `for`
(E313). Afectan al ciclo más interno que los contiene.

### 12.3 Llamadas

Para una llamada `f(e1, …, en)`:

1. `f` debe ser el nombre de una función (E314). Una variable no se
   puede llamar.
2. `n` debe ser igual al número de parámetros de `f` (E315).
3. Cada `ei` debe ser asignable al tipo del parámetro `i` (E307).
4. Una llamada a una función `void` solo puede usarse como sentencia.
   Usar su resultado en una expresión, un `print` o una inicialización
   es el error E312 o E306, según el contexto.

### 12.4 `return`

- `return` solo puede aparecer dentro de una función (E316).
- En una función `void`, `return` no lleva expresión. En una función
  con tipo de retorno `T`, sí la lleva, y su tipo debe ser asignable a
  `T` (E317 en ambos casos; E307 si el tipo es incompatible).

### 12.5 Análisis de retorno

Una función con tipo de retorno distinto de `void` debe terminar con
`return` en todo camino de ejecución (E318). Se usa el predicado
conservador **R** ("siempre retorna"):

```text
R(return e;)                      = verdadero
R({ s1 … sn })                    = R(s1) ∨ … ∨ R(sn)
R(if (c) B1 else B2)              = R(B1) ∧ R(B2)
R(if (c) B1)                      = falso
R(while (c) B)  /  R(for … B)     = falso
R(cualquier otra sentencia)       = falso
```

El cuerpo `B` de la función debe cumplir `R(B)`. El análisis no evalúa
las condiciones: `while (true) { return 1; }` no se considera un
retorno seguro. Las sentencias que siguen a un `return`, `break` o
`continue` en el mismo bloque son inalcanzables y producen la
advertencia W301 (no impide compilar).

### 12.6 `for … in`

| Tipo de `e` | Tipo de la variable de iteración | Elementos recorridos              |
| ----------- | -------------------------------- | --------------------------------- |
| `T[]`       | `T`                              | Los elementos, en orden de índice |
| `string`    | `char`                           | Los caracteres, en orden          |

Cualquier otro tipo es el error E319. La expresión `e` se evalúa una
sola vez, antes de la primera iteración. En JavaScript se traduce como
`for (const x of e)`.

### 12.7 `print`

Cada argumento puede ser de cualquier tipo excepto `void` (E306).

---

## 13. Resumen de restricciones estáticas

| #   | Restricción                                                 | Sección | Error |
| --- | ----------------------------------------------------------- | ------- | ----- |
| 1   | Destino de asignación válido                                | 5.2     | E204  |
| 2   | Una sentencia de expresión debe ser una llamada             | 5.2     | E205  |
| 3   | Identificador declarado                                     | 10.2    | E301  |
| 4   | Sin redeclaración ni ocultamiento                           | 10.2    | E302  |
| 5   | Uso después de la declaración y dentro de su ámbito         | 10.2    | E303  |
| 6   | División o módulo entre una constante que vale 0            | 14.3    | E304  |
| 7   | Las constantes y las variables de `for` son de solo lectura | 10.3    | E305  |
| 8   | Tipos válidos para cada operador                            | 11.2    | E306  |
| 9   | Compatibilidad de asignación, argumentos y retorno          | 11.4    | E307  |
| 10  | Condiciones de tipo `bool`                                  | 12.1    | E308  |
| 11  | Variable asignada antes de leerse                           | 10.4    | E309  |
| 12  | Índice entero sobre un valor indexable                      | 11.3    | E310  |
| 13  | Propiedad existente (`.length`)                             | 11.3    | E311  |
| 14  | Tipo inferible                                              | 11.5    | E312  |
| 15  | `break` / `continue` dentro de un ciclo                     | 12.2    | E313  |
| 16  | Solo se llaman funciones                                    | 12.3    | E314  |
| 17  | Número de argumentos                                        | 12.3    | E315  |
| 18  | `return` dentro de una función                              | 12.4    | E316  |
| 19  | `return` con o sin valor según el tipo de retorno           | 12.4    | E317  |
| 20  | Retorno en todos los caminos                                | 12.5    | E318  |
| 21  | `for … in` sobre un tipo iterable                           | 12.6    | E319  |

---

## 14. Semántica de ejecución

### 14.1 Orden de evaluación

- Los operandos de un operador binario se evalúan de izquierda a
  derecha.
- Los argumentos de una llamada se evalúan de izquierda a derecha,
  antes de ejecutar la función.
- En una asignación, primero se evalúan los subíndices del destino y
  después la expresión de la derecha.

### 14.2 Cortocircuito

`a && b` no evalúa `b` si `a` es `false`. `a || b` no evalúa `b` si `a`
es `true`.

### 14.3 Aritmética

- `/` produce siempre un `float`. No hay división entera.
- `%` tiene el signo del dividendo: `-7 % 3` = −1.
- **División entre cero.** Si el divisor de `/` o `%` es una expresión
  constante que vale 0, el error se detecta en compilación (E304). Una
  expresión es constante cuando solo contiene literales numéricos,
  paréntesis y los operadores `+`, `-`, `*`, `/`, `%`. Cuando el divisor
  solo se conoce en ejecución, el resultado sigue la aritmética IEEE 754
  de la plataforma destino (`Infinity`, `-Infinity` o `NaN`).

### 14.4 Paso de parámetros y arreglos

- Los valores de tipo base se pasan **por valor**.
- Los arreglos se pasan **por referencia compartida**: si una función
  modifica `v[i]`, la modificación es visible para quien la llamó.
- Acceder a un índice fuera de `[0, length − 1]` es un error en tiempo
  de ejecución que detiene el programa.

---

## 15. Códigos de error

Cada diagnóstico incluye el código, la fase, un mensaje y la línea y
columna del token donde se produjo. Si hay cualquier error, el
compilador no genera código.

| Código | Fase       | Descripción                                                               | Estado                      |
| ------ | ---------- | ------------------------------------------------------------------------- | --------------------------- |
| E100   | Léxica     | Carácter no reconocido                                                    | Implementado                |
| E101   | Léxica     | `&` aislado: se esperaba `&&`                                             | Implementado                |
| E102   | Léxica     | `\|` aislado: se esperaba `\|\|`                                          | Implementado                |
| E103   | Léxica     | Cadena sin cerrar                                                         | Propuesto                   |
| E104   | Léxica     | Secuencia de escape no válida                                             | Propuesto                   |
| E105   | Léxica     | Comentario de bloque sin cerrar                                           | Propuesto                   |
| E106   | Léxica     | Literal entero fuera de rango                                             | Propuesto                   |
| E107   | Léxica     | Literal de carácter vacío o con más de un carácter                        | Propuesto                   |
| E201   | Sintáctica | Se esperaba un token concreto                                             | Implementado                |
| E202   | Sintáctica | Sentencia no reconocida                                                   | Implementado                |
| E203   | Sintáctica | Se esperaba una expresión                                                 | Implementado                |
| E204   | Sintáctica | Destino de asignación no válido                                           | Propuesto                   |
| E205   | Sintáctica | Una expresión usada como sentencia debe ser una llamada                   | Propuesto                   |
| E301   | Semántica  | Identificador no declarado                                                | Implementado                |
| E302   | Semántica  | Identificador ya declarado                                                | Implementado                |
| E303   | Semántica  | Uso antes de la declaración o fuera de su bloque                          | Implementado                |
| E304   | Semántica  | División entre cero constante                                             | Implementado (solo `/`)     |
| E305   | Semántica  | Asignación a una constante o a una variable de solo lectura               | Implementado (solo `const`) |
| E306   | Semántica  | Operación no válida para los tipos de sus operandos                       | Implementado                |
| E307   | Semántica  | Tipos incompatibles en asignación, argumento o retorno                    | Implementado (asignación)   |
| E308   | Semántica  | Condición que no es `bool`                                                | Implementado (solo `if`)    |
| E309   | Semántica  | Variable leída sin haber sido asignada                                    | Propuesto                   |
| E310   | Semántica  | Índice no entero o valor no indexable                                     | Propuesto                   |
| E311   | Semántica  | Propiedad inexistente                                                     | Propuesto                   |
| E312   | Semántica  | No se puede inferir el tipo (o se usa un valor `void`)                    | Propuesto                   |
| E313   | Semántica  | `break` o `continue` fuera de un ciclo                                    | Implementado                |
| E314   | Semántica  | Se llama a algo que no es una función                                     | Propuesto                   |
| E315   | Semántica  | Número de argumentos incorrecto                                           | Propuesto                   |
| E316   | Semántica  | `return` fuera de una función                                             | Propuesto                   |
| E317   | Semántica  | `return` con valor en una función `void`, o sin valor en una que no lo es | Propuesto                   |
| E318   | Semántica  | Falta `return` en algún camino de ejecución                               | Propuesto                   |
| E319   | Semántica  | `for … in` sobre un valor no iterable                                     | Propuesto                   |
| W301   | Semántica  | Código inalcanzable (advertencia)                                         | Propuesto                   |

---

## 16. Estado de implementación

Estado del compilador respecto a esta especificación, según el código
de `src/main/java/com/compiler`:

| Construcción                                      |                    Lexer                    |                Parser                 | Semántico | Generación JS |
| ------------------------------------------------- | :-----------------------------------------: | :-----------------------------------: | :-------: | :-----------: |
| `let` / `const` con inicializador, sin tipo       |                      ✔                      |                   ✔                   |     ✔     |       ✔       |
| Anotación de tipo `: Tipo`, `let x: T;`           |                      ✔                      |           ✔ (sin `T[]`)               | ✔ (sin E309) |       ✔       |
| Asignación `=` y compuesta sobre un identificador |                      ✔                      | ✔ (LL(2), se reduce a `a = a op (e)`) |     ✔     |       ✔       |
| Asignación a `v[i]`                               |                      ✔                      |                   ✘                   |     ✘     |       ✘       |
| `print(e)` con un solo argumento                  |                      ✔                      |                   ✔                   |     ✔     |       ✔       |
| `print` con 0 o varios argumentos                 |                ✘ (falta `,`)                |                   ✘                   |     ✘     |       ✘       |
| `if` / `else` / `else if`                         |                      ✔                      |                   ✔                   |     ✔     |       ✔       |
| `Bloque` como sentencia independiente             |                      ✔                      |                   ✘                   |     ✘     |       ✘       |
| `while`, `for … in`, `break`, `continue`          |           ✔ (palabras reservadas)           |                   ✘                   |     ✘     |       ✘       |
| `fn`, parámetros, `return`, llamadas              |          ✔ / ✘ (falta `,`)                  |                   ✘                   |     ✘     |       ✘       |
| `\|\|` `&&` `==` `!=` `<` `<=` `>` `>=`           |                      ✔                      |                   ✔                   |     ✔     |       ✔       |
| `+` `-` `*` `/` `%`, `!` y `-` unarios            |                      ✔                      |                   ✔                   |     ✔     |       ✔       |
| `**`                                              | ✘ (el token existe, el lexer no lo produce) |                   ✘                   |     ✘     |       ✘       |
| Literales `ENTERO`, `DECIMAL`, `true`, `false`    |      ✔ (como un único token `NUMERO`)       |                   ✔                   |     ✔     |       ✔       |
| `null`                                            |                      ✔                      |                   ✘                   |     ✘     |       ✘       |
| `CADENA`, `CARACTER`                              |                      ✘                      |                   ✘                   |     ✘     |       ✘       |
| Literales de arreglo, `a[i]`, `.length`           |              ✔ (`[`, `]`, `.`)              |                   ✘                   |     ✘     |       ✘       |
| Comentario de línea `//`                          |                      ✔                      |                   —                   |     —     |       —       |
| Comentario de bloque `/* */`                      |                      ✘                      |                   —                   |     —     |       —       |
| Recuperación de errores en modo pánico            |                      —                      |                   ✔                   |     —     |       —       |

Diferencias del compilador actual que deben corregirse:

1. **Ámbitos.** `SymbolTable` es una tabla plana. Por eso dos bloques
   hermanos no pueden declarar el mismo nombre (E302), lo cual la
   regla 10.2.4 sí permite. Se necesita una tabla con un nivel por
   ámbito.
2. **`int` y `float` léxicos.** El lexer produce un único token
   `NUMERO` y el analizador semántico distingue `int` de `float` por la
   presencia de un `.` en el lexema. Es equivalente a esta
   especificación, pero conviene separar los tokens `ENTERO` y
   `DECIMAL`.
3. **E304** solo se comprueba para `/`. Debe extenderse a `%`.
4. **E308** solo se comprueba en `if`. Debe aplicarse también a
   `while`.
5. **Recuperación de errores.** El conjunto de sincronización del
   parser (`let`, `const`, `print`, `if`, `}`) debe ampliarse con las
   palabras de la sección 9.1 a medida que se implementen.

**Criterio de aceptación:** una construcción se considera implementada
cuando tiene pruebas de lexer y parser, una representación en el AST,
validación semántica con sus errores, y generación de JavaScript
correcta.

---

## 17. Ejemplos de validación

### 17.1 Programa válido completo

```orion
// Suma de los pares de un arreglo
fn sumaPares(v: int[]): int {
    let total = 0;
    for (x in v) {
        if (x % 2 != 0) {
            continue;
        }
        total += x;
    }
    return total;
}

const datos: int[] = [3, 8, 5, 10, 1];
let resultado = sumaPares(datos);

if (resultado > 10) {
    print("Suma:", resultado);
} else {
    print("Suma pequeña");
}
```

Salida esperada: `Suma: 18`.

### 17.2 Precedencia y asociatividad

```orion
print(2 + 3 * 4);       // 14
print((2 + 3) * 4);     // 20
print(10 - 4 - 3);      // 3      (izquierda: (10 - 4) - 3)
print(2 ** 3 ** 2);     // 512    (derecha: 2 ** (3 ** 2))
print(-2 ** 2);         // -4     (-(2 ** 2))
print((-2) ** 2);       // 4
print(7 / 2);           // 3.5
print(!true || true);   // true   ((!true) || true)
```

### 17.3 Errores sintácticos

```orion
let x;                  // E201: se esperaba ':' o '=' después de 'x'
a + 1;                  // E205: la expresión no es una llamada
f() = 3;                // E204: destino de asignación no válido
a = b = 1;              // E201: se esperaba ';'
if (a) print(a);        // E201: se esperaba '{'
fn f() { fn g() {} }    // E202: no se permiten funciones anidadas
```

### 17.4 Errores semánticos

```orion
const limite = 10;
limite = 20;            // E305: reasignación de constante

print(noDeclarada);     // E301: identificador no declarado

let n = 1;
if (n > 0) { let n = 2; }   // E302: oculta a 'n'

let e: int = "texto";   // E307: string no es asignable a int
let f: float = 3;       // válido: int se promueve a float
let g: int = 3.0;       // E307: float no es asignable a int

if (1) { }              // E308: la condición no es bool
print(1 < 2 < 3);       // E306: '<' entre bool e int
print("n=" + 5);        // E306: no hay conversión implícita a string
let z = 8 / (2 - 2);    // E304: división entre cero constante

break;                  // E313: fuera de un ciclo
let v = [];             // E312: no se puede inferir el tipo

fn signo(x: int): int {
    if (x > 0) { return 1; }
}                       // E318: falta return en algún camino
```

---

## 18. Fuera del alcance de esta versión

Las siguientes características no forman parte del lenguaje. Se
excluyen de forma deliberada y se podrían añadir en versiones futuras:

- Módulos (`import`, `from` y `export` ya están reservadas).
- Registros, objetos, clases e interfaces.
- Funciones anidadas, funciones como valores y lambdas.
- Parámetros por defecto, variádicos y sobrecarga.
- Conversiones explícitas de tipo (_casts_) y conversión a `string`.
- Entrada de datos (`input`).
- Excepciones.
- Ciclos `for` de estilo C, `do … while`, `switch`.
- Operador ternario e incrementos `++` / `--`.
- Literales numéricos con exponente, hexadecimales o con separadores.

# Orion Compiler

Compilador desarrollado en Java para el análisis y procesamiento de un lenguaje de programación propio. El proyecto implementa las etapas iniciales de un compilador, desde el análisis léxico y sintáctico hasta el análisis semántico y la generación de código intermedio.

El objetivo es construir una arquitectura modular que permita comprender cómo se transforma un programa fuente en una representación intermedia, facilitando su análisis y el desarrollo posterior de nuevas etapas de compilación.

## Objetivos del proyecto

- Implementar el análisis léxico para reconocer los elementos del lenguaje.
- Analizar la estructura sintáctica de los programas mediante una gramática.
- Construir una representación del programa mediante un árbol de sintaxis abstracta (AST).
- Detectar errores semánticos, como el uso de identificadores no declarados.
- Generar código intermedio a partir de la estructura del programa.
- Mantener una arquitectura modular que facilite la extensión del compilador.

## Arquitectura general

El proceso de compilación se organiza en las siguientes etapas:

```text
Programa fuente
      |
      v
Análisis léxico
      |
      v
Análisis sintáctico
      |
      v
Árbol de sintaxis abstracta (AST)
      |
      v
Análisis semántico
      |
      v
Generación de código intermedio
      |
      v
Código de tres direcciones (TAC)
```

Cada etapa cumple una responsabilidad específica y utiliza los resultados de las etapas anteriores.

## Tecnologías utilizadas

- **Java:** lenguaje de implementación del compilador.
- **Maven o Gradle:** herramienta de construcción, según la configuración del proyecto.
- **Git:** control de versiones.
- **Visual Studio Code o IntelliJ IDEA:** entornos de desarrollo compatibles con Java.

## Estructura del proyecto

La organización principal del código fuente se encuentra dentro de `src/main/java/com/compiler/`.

```text
orion-compiler/
├── src/
│   └── main/
│       └── java/
│           └── com/
│               └── compiler/
│                   ├── Main.java
│                   ├── lexer/
│                   ├── parser/
│                   ├── ast/
│                   ├── semantic/
│                   ├── codegen/
│                   └── ...
├── programa.js
├── programa.tac
└── README.md
```

> **Nota:** Los directorios anteriores representan una organización conceptual. Conserva en el README los nombres exactos de los paquetes y archivos existentes en tu repositorio.

### Componentes principales

| Componente                       | Responsabilidad                                                                      |
| -------------------------------- | ------------------------------------------------------------------------------------ |
| `Main.java`                      | Coordina las etapas del proceso de compilación.                                      |
| `Lexer.java`                     | Reconoce los tokens del programa fuente.                                             |
| `Parser.java`                    | Analiza la estructura sintáctica y construye la representación del programa.         |
| AST                              | Representa declaraciones, expresiones, bloques y otras construcciones del lenguaje.  |
| Tabla de símbolos                | Mantiene información sobre los identificadores del programa.                         |
| Análisis semántico               | Comprueba restricciones semánticas del lenguaje.                                     |
| `GeneradorCodigoIntermedio.java` | Traduce nodos del AST a instrucciones de código intermedio.                          |
| `Cuadrupla.java`                 | Representa las instrucciones del código intermedio mediante operaciones y operandos. |

## Lenguaje fuente

Orion Compiler procesa programas escritos en un lenguaje propio con construcciones como declaraciones de variables, constantes, expresiones, operaciones y estructuras de control implementadas en el proyecto.

Por ejemplo:

```javascript
let a = 10;
let b = 3;
let c = a + b * 2;

const ok = true;
```

Este programa permite ilustrar las declaraciones, los valores literales y las expresiones aritméticas.

La sintaxis admitida debe consultarse en la implementación del analizador léxico y del analizador sintáctico.

## Análisis semántico

El análisis semántico verifica propiedades del programa que no pueden determinarse únicamente mediante la gramática.

Entre los aspectos relevantes se encuentran:

- Verificar que los identificadores utilizados hayan sido declarados.
- Mantener información de los símbolos.
- Determinar los tipos de datos de expresiones.
- Comprobar la compatibilidad entre operadores y operandos.
- Validar las condiciones de las estructuras de control.

La cobertura de estas comprobaciones depende de la implementación actual. La inferencia de tipos y las reglas completas de compatibilidad deben considerarse trabajo pendiente mientras no estén implementadas y verificadas.

## Generación de código intermedio

El proyecto incluye un generador de código intermedio que transforma determinadas construcciones del AST en una representación basada en instrucciones de tres direcciones (_Three-Address Code_, TAC).

Una instrucción puede representarse mediante una cuádrupla:

```text
(operación, argumento1, argumento2, resultado)
```

Por ejemplo, la expresión:

```javascript
let c = a + b * 2;
```

puede traducirse a las siguientes instrucciones, suponiendo que `a` y `b` ya están disponibles:

```text
t1 = b * 2
t2 = a + t1
c = t2
```

Los temporales permiten dividir una expresión compleja en operaciones más sencillas, respetando la precedencia de los operadores.

El generador también contempla instrucciones para declaraciones, impresión y estructuras condicionales según las construcciones que admite su implementación.

## Ejecución

Para ejecutar el proyecto, utiliza el entorno Java configurado en el repositorio.

1. Instala una versión del JDK compatible con el proyecto.
2. Abre el proyecto en tu entorno de desarrollo.
3. Identifica la configuración de compilación y las dependencias.
4. Ejecuta la clase `Main.java` o utiliza el comando de ejecución definido por el proyecto.
5. Revisa la salida del programa y el código intermedio generado, si el proceso termina correctamente.

Los comandos exactos dependen del sistema de construcción y de la configuración existente en el repositorio.

## Pruebas recomendadas

Para comprobar el funcionamiento del compilador, conviene utilizar programas que cubran distintos casos:

| Caso                                 | Entrada de ejemplo         | Resultado esperado                                                            |
| ------------------------------------ | -------------------------- | ----------------------------------------------------------------------------- |
| Declaración                          | `let a = 10;`              | Reconocer la declaración y generar su asignación intermedia.                  |
| Expresión aritmética                 | `let c = a + b * 2;`       | Respetar la precedencia y generar temporales cuando sean necesarios.          |
| Identificador no declarado           | `let c = desconocida + 1;` | Reportar un error semántico.                                                  |
| Expresión sintácticamente incorrecta | `let c = 10 + ;`           | Reportar un error de sintaxis.                                                |
| Condicional                          | `if (a > 0) { print(a); }` | Analizar la condición y generar las instrucciones admitidas por el generador. |

Estos casos constituyen una guía de validación. Los resultados deben comprobarse mediante la ejecución real del compilador.

## Alcance y trabajo futuro

Las siguientes mejoras pueden ampliar la funcionalidad del proyecto:

- Completar la inferencia y comprobación de tipos.
- Validar la compatibilidad de los operadores.
- Verificar las condiciones booleanas de las estructuras de control.
- Detectar declaraciones duplicadas y otros errores semánticos definidos por el lenguaje.
- Ampliar la traducción de los nodos del AST a código intermedio.
- Incorporar pruebas automatizadas para cada fase.
- Documentar formalmente la gramática libre de contexto y las reglas de traducción dirigida por sintaxis.
- Verificar que la generación de código intermedio se ejecute únicamente cuando las etapas anteriores hayan terminado sin errores.

## Documentación académica

Este proyecto sirve como base para estudiar e implementar las fases de un compilador, especialmente el análisis semántico y la generación de código intermedio.

La documentación técnica debe describir la gramática del lenguaje, las reglas semánticas, el formato del código intermedio, los esquemas de traducción y las pruebas realizadas.

## Autoría

Proyecto académico: **Orion Compiler**.

Desarrollo realizado como parte del estudio de compiladores y lenguajes de programación.

# Orion Compiler

**Orion Compiler** es un proyecto académico de desarrollo de un compilador para un lenguaje de programación propio llamado Orion. Su objetivo es procesar código fuente, detectar errores, analizar su estructura y generar código JavaScript como salida.

## Características

- **Análisis léxico:** identifica los tokens del código fuente.
- **Análisis sintáctico:** verifica la estructura del programa y construye un árbol de sintaxis abstracta (AST).
- **Análisis semántico:** analiza declaraciones y referencias a identificadores mediante una tabla de símbolos.
- **Manejo de errores:** organiza los errores léxicos, sintácticos y semánticos.
- **Generación de código:** transforma las estructuras reconocidas del lenguaje Orion en código JavaScript.

> Nota: la disponibilidad y el alcance de cada característica dependen del estado actual de su implementación.

## Arquitectura

El compilador sigue un flujo de procesamiento por etapas:

```text
Código fuente Orion
        |
        v
  Análisis léxico
        |
        v
      Tokens
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
 Generación de JavaScript
        |
        v
    programa.js
```

Cada etapa tiene una responsabilidad específica para facilitar el mantenimiento y la ampliación del lenguaje.

## Tecnologías

- **Lenguaje de implementación:** Java
- **Lenguaje de salida:** JavaScript
- **Entorno de desarrollo:** Visual Studio Code
- **Control de versiones:** Git y GitHub

## Estructura del proyecto

```text
orion-compiler/
├── src/
│   └── main/
│       └── java/
│           └── com/
│               └── compiler/
│                   ├── error/
│                   ├── lexer/
│                   ├── parser/
│                   ├── semantic/
│                   ├── util/
│                   └── Main.java
├── bin/
├── programa.js
└── README.md
```

### Responsabilidad de los paquetes

| Paquete    | Responsabilidad                                     |
| ---------- | --------------------------------------------------- |
| `error`    | Representación y gestión de errores de compilación. |
| `lexer`    | Reconocimiento de tokens del código fuente.         |
| `parser`   | Análisis sintáctico y construcción del AST.         |
| `semantic` | Tabla de símbolos y análisis semántico.             |
| `util`     | Generación del código JavaScript.                   |

## Requisitos previos

- JDK de Java instalado.
- Visual Studio Code u otro editor compatible.
- Un entorno configurado para compilar y ejecutar código Java.

## Instalación y ejecución

1. Clona el repositorio:

   ```bash
   git clone <https://github.com/Jack-SparrowQ/Orion.git>
   ```

2. Accede al directorio del proyecto:

   ```bash
   cd orion-compiler
   ```

3. Abre el proyecto en tu editor y verifica que el JDK esté configurado correctamente.

4. Compila y ejecuta el programa desde la configuración de Java del proyecto o mediante los comandos correspondientes a su estructura actual.

> Los comandos definitivos de compilación y ejecución deben documentarse una vez verificados en el entorno del proyecto.

## Ejemplo de uso

Un programa Orion puede contener declaraciones, instrucciones de impresión y estructuras condicionales compatibles con la implementación actual.

```text
let age = 10;
print(age);
```

La salida JavaScript esperada para este ejemplo es equivalente a:

```javascript
let age = 10;
console.log(age);
```

El resultado exacto depende de las reglas de generación de código implementadas.

## Estado del proyecto

Orion Compiler se encuentra en desarrollo incremental. Las siguientes áreas forman parte de su evolución:

- [x] Organización de paquetes Java.
- [ ] Consolidación del manejo de errores.
- [ ] Sistema de expresiones.
- [ ] Asignaciones y asignaciones compuestas.
- [ ] Gestión de ámbitos.
- [ ] Distinción semántica entre `let` y `const`.
- [ ] Verificación de tipos.
- [ ] Ampliación de las estructuras del lenguaje.
- [ ] Pruebas automatizadas.

La lista debe actualizarse conforme cada funcionalidad se implemente y valide.

## Objetivo académico

Este proyecto busca comprender y aplicar los fundamentos del diseño de compiladores, incluyendo el análisis léxico, el análisis sintáctico, el análisis semántico y la generación de código.

## Contribuciones

Las mejoras, correcciones y nuevas funcionalidades pueden desarrollarse mediante ramas de trabajo y solicitudes de cambios (*pull requests*), procurando mantener una arquitectura clara y validar cada modificación antes de integrarla.

## Licencia

Añade aquí la licencia del proyecto cuando hayas definido cómo deseas distribuirlo.

package main.java.com.compiler;

import java.util.ArrayList;
import java.util.List;
import java.io.FileWriter;
import java.io.IOException;

import main.java.com.compiler.error.CompilationError;
import main.java.com.compiler.error.ErrorHandler;
import main.java.com.compiler.error.ErrorType;
import main.java.com.compiler.lexer.*;
import main.java.com.compiler.parser.*;
import main.java.com.compiler.semantic.*;
import main.java.com.compiler.util.JSGenerator;

public class Main {

    public static void main(String[] args) {

        // ============================================================
        // CÓDIGO FUENTE
        // ============================================================

        String codigoOrion = """
                let age = 10;
                """;

        System.out.println("=================================================");
        System.out.println("              COMPILADOR ORION");
        System.out.println("=================================================");
        System.out.println();

        System.out.println("Código fuente:");
        System.out.println("-----------------------------------------");
        System.out.println(codigoOrion);
        System.out.println("-----------------------------------------");
        System.out.println();


        // ============================================================
        // 1. ANÁLISIS LÉXICO
        // ============================================================

        System.out.println("=== 1. FASE DE ANÁLISIS LÉXICO ===");

        Lexer lexer = new Lexer(codigoOrion);

        List<Token> listaTokens = new ArrayList<>();

        Token token;

        do {

            token = lexer.siguienteToken();
            listaTokens.add(token);

            System.out.println(
                    String.format(
                            "Token: %-15s Lexema: '%s'",
                            token.tipo,
                            token.lexema
                    )
            );

        } while (token.tipo != TokenType.EOF);

        System.out.println();

        System.out.println(
                "Total de tokens reconocidos: "
                        + listaTokens.size()
        );

        System.out.println();


        // ============================================================
        // 2. ANÁLISIS SINTÁCTICO
        // ============================================================

        System.out.println("=== 2. FASE DE ANÁLISIS SINTÁCTICO ===");

        Parser parser = new Parser(listaTokens);

        List<ElementoAST> arbolAST = parser.parsear();

        /*
         * Si el Parser devuelve null significa que ocurrió
         * algún error sintáctico.
         */
        if (arbolAST == null) {

            System.err.println();
            System.err.println(
                    "[COMPILACIÓN ABORTADA] "
                    + "Se encontraron errores sintácticos."
            );

            return;
        }

        System.out.println(
                "✓ Análisis sintáctico completado correctamente."
        );

        System.out.println(
                "Número de nodos principales: "
                        + arbolAST.size()
        );

        System.out.println();


        // ============================================================
        // 3. VISUALIZACIÓN DEL AST
        // ============================================================

        System.out.println("=== 3. ÁRBOL DE SINTAXIS ABSTRACTA (AST) ===");

        visualizarAST(arbolAST, "");

        System.out.println();


        // ============================================================
        // 4. TABLA DE SÍMBOLOS
        // ============================================================

        System.out.println("=== 4. CONSTRUCCIÓN DE TABLA DE SÍMBOLOS ===");

        SymbolTable tablaSimbolos = new SymbolTable();

        ErrorHandler gestorErrores = new ErrorHandler();

        analizarSimbolos(
                arbolAST,
                tablaSimbolos,
                gestorErrores
        );

        //tablaSimbolos.mostrar();

        System.out.println();


        // ============================================================
        // 5. ANÁLISIS SEMÁNTICO
        // ============================================================

        System.out.println("=== 5. ANÁLISIS SEMÁNTICO ===");

        analizarSemantica(
                arbolAST,
                tablaSimbolos,
                gestorErrores
        );

        if (gestorErrores.hayErrores()) {

            System.err.println();
            System.err.println(
                    "Se encontraron errores durante "
                            + "el análisis semántico."
            );

            mostrarErrores(gestorErrores);

            System.err.println();
            System.err.println(
                    "[COMPILACIÓN ABORTADA]"
            );

            return;
        }

        System.out.println(
                "✓ Análisis semántico completado correctamente."
        );

        System.out.println();


        // ============================================================
        // 6. GENERACIÓN DE CÓDIGO
        // ============================================================

        System.out.println("=== 6. GENERACIÓN DE CÓDIGO JAVASCRIPT ===");

        JSGenerator generador = new JSGenerator();

        String codigoJavaScript =
                generador.generar(arbolAST);

        System.out.println();
        System.out.println(
                "Código JavaScript generado:"
        );

        System.out.println("-----------------------------------------");
        System.out.println(codigoJavaScript);
        System.out.println("-----------------------------------------");

        System.out.println();


        // ============================================================
        // 7. GENERACIÓN DEL ARCHIVO
        // ============================================================

        System.out.println("=== 7. GENERACIÓN DEL ARCHIVO DE SALIDA ===");

        String archivoSalida = "programa.js";

        try (FileWriter writer =
                     new FileWriter(archivoSalida)) {

            writer.write(codigoJavaScript);

            System.out.println(
                    "✓ Archivo generado correctamente: "
                            + archivoSalida
            );

        } catch (IOException e) {

            System.err.println(
                    "[ERROR] No se pudo generar el archivo."
            );

            System.err.println(
                    "Detalle: " + e.getMessage()
            );

            return;
        }


        // ============================================================
        // FIN
        // ============================================================

        System.out.println();
        System.out.println("=================================================");
        System.out.println("       COMPILACIÓN FINALIZADA CORRECTAMENTE");
        System.out.println("=================================================");
    }


    // ================================================================
    // CONSTRUCCIÓN DE TABLA DE SÍMBOLOS
    // ================================================================

    private static void analizarSimbolos(
            List<ElementoAST> nodos,
            SymbolTable tablaSimbolos,
            ErrorHandler gestorErrores) {

        for (ElementoAST nodo : nodos) {

            // --------------------------------------------------------
            // DECLARACIÓN DE VARIABLE
            // --------------------------------------------------------

            if (nodo instanceof NodoDeclaracion) {

                NodoDeclaracion declaracion =
                        (NodoDeclaracion) nodo;

                String nombre =
                        declaracion.identificador.lexema;


                /*
                 * Por ahora determinamos el tipo a partir
                 * del valor de la declaración.
                 *
                 * Ejemplo:
                 *
                 * let age = 10;
                 *
                 * age -> int
                 */

                Type tipo =
                        determinarTipo(
                                declaracion.valorNumero.lexema
                        );


                Symbol simbolo =
                        new Symbol(
                                nombre,
                                tipo,
                                SymbolCategory.VARIABLE,
                                0,
                                0
                        );


                /*
                 * Intentamos insertar el símbolo.
                 *
                 * Si ya existe, tenemos una redeclaración.
                 */

                boolean insertado =
                        tablaSimbolos.insertar(simbolo);

                if (!insertado) {

                    gestorErrores.agregar(
                            "E002",
                            ErrorType.SEMANTICO,
                            "El identificador '"
                                    + nombre
                                    + "' ya fue declarado.",
                            0,
                            0
                    );
                }
            }


            // --------------------------------------------------------
            // IF
            // --------------------------------------------------------

            else if (nodo instanceof NodoIf) {

                NodoIf nodoIf =
                        (NodoIf) nodo;

                /*
                 * El bloque interno también puede contener
                 * declaraciones.
                 */

                analizarSimbolos(
                        nodoIf.bloqueTrue.sentencias,
                        tablaSimbolos,
                        gestorErrores
                );
            }
        }
    }


    // ================================================================
    // ANÁLISIS SEMÁNTICO
    // ================================================================

    private static void analizarSemantica(
            List<ElementoAST> nodos,
            SymbolTable tablaSimbolos,
            ErrorHandler gestorErrores) {

        for (ElementoAST nodo : nodos) {

            // --------------------------------------------------------
            // DECLARACIÓN
            // --------------------------------------------------------

            if (nodo instanceof NodoDeclaracion) {

                NodoDeclaracion declaracion =
                        (NodoDeclaracion) nodo;

                String nombre =
                        declaracion.identificador.lexema;

                Symbol simbolo =
                        tablaSimbolos.buscar(nombre);


                if (simbolo == null) {

                    gestorErrores.agregar(
                            "E001",
                            ErrorType.SEMANTICO,
                            "El identificador '"
                                    + nombre
                                    + "' no está declarado.",
                            0,
                            0
                    );

                    continue;
                }


                /*
                 * Aquí posteriormente puedes agregar
                 * las reglas reales de compatibilidad
                 * de tipos.
                 *
                 * Ejemplo:
                 *
                 * int + int       -> int
                 * int + float     -> float
                 * float + float   -> float
                 * string + string -> string
                 */

                System.out.println(
                        "✓ Símbolo validado: "
                                + nombre
                                + " -> "
                                + simbolo.getTipo()
                );
            }


            // --------------------------------------------------------
            // IF
            // --------------------------------------------------------

            else if (nodo instanceof NodoIf) {

                NodoIf nodoIf =
                        (NodoIf) nodo;


                /*
                 * Aquí posteriormente validarás que la
                 * condición sea de tipo bool.
                 */

                analizarSemantica(
                        nodoIf.bloqueTrue.sentencias,
                        tablaSimbolos,
                        gestorErrores
                );
            }


            // --------------------------------------------------------
            // PRINT
            // --------------------------------------------------------

            else if (nodo instanceof NodoImprimir) {

                NodoImprimir imprimir =
                        (NodoImprimir) nodo;


                /*
                 * Aquí posteriormente puedes comprobar
                 * que la expresión utilizada por print
                 * sea válida.
                 */

                if (imprimir.expresion
                        instanceof NodoExpresionSimple) {

                    NodoExpresionSimple expresion =
                            (NodoExpresionSimple)
                                    imprimir.expresion;

                    String valor =
                            expresion.valor.lexema;

                    /*
                     * Si el valor es un identificador,
                     * verificamos que exista.
                     */

                    if (esIdentificador(valor)
                            && !tablaSimbolos.existe(valor)) {

                        gestorErrores.agregar(
                                "E001",
                                ErrorType.SEMANTICO,
                                "El identificador '"
                                        + valor
                                        + "' no ha sido declarado.",
                                0,
                                0
                        );
                    }
                }
            }
        }
    }


    // ================================================================
    // DETERMINACIÓN SIMPLE DE TIPO
    // ================================================================

    private static Type determinarTipo(String valor) {

        if (valor == null) {
            return Type.NULL;
        }


        // Entero

        if (valor.matches("-?\\d+")) {
            return Type.INT;
        }


        // Real

        if (valor.matches("-?\\d+\\.\\d+")) {
            return Type.FLOAT;
        }


        // Booleano

        if (valor.equals("true")
                || valor.equals("false")) {

            return Type.BOOL;
        }


        // Carácter

        if (valor.length() >= 3
                && valor.startsWith("'")
                && valor.endsWith("'")) {

            return Type.CHAR;
        }


        // Cadena

        if (valor.length() >= 2
                && valor.startsWith("\"")
                && valor.endsWith("\"")) {

            return Type.STRING;
        }


        /*
         * Si todavía no sabemos qué tipo es,
         * dejamos que el análisis semántico
         * posterior lo determine.
         */

        return null;
    }


    // ================================================================
    // COMPROBAR SI ES IDENTIFICADOR
    // ================================================================

    private static boolean esIdentificador(String valor) {

        if (valor == null || valor.isEmpty()) {
            return false;
        }

        return valor.matches("[a-zA-Z_][a-zA-Z0-9_]*");
    }


    // ================================================================
    // MOSTRAR ERRORES
    // ================================================================

    private static void mostrarErrores(
            ErrorHandler gestorErrores) {

        System.err.println();
        System.err.println(
                "---------------- ERRORES ----------------"
        );

        for (CompilationError error :
                gestorErrores.obtenerErrores()) {

            System.err.println(error);
        }

        System.err.println(
                "------------------------------------------"
        );

        System.err.println(
                "Total de errores: "
                        + gestorErrores.obtenerErrores().size()
        );
    }


    // ================================================================
    // VISUALIZACIÓN DEL AST
    // ================================================================

    private static void visualizarAST(
            List<ElementoAST> nodos,
            String prefijo) {

        for (ElementoAST nodo : nodos) {

            // --------------------------------------------------------
            // DECLARACIÓN
            // --------------------------------------------------------

            if (nodo instanceof NodoDeclaracion) {

                NodoDeclaracion d =
                        (NodoDeclaracion) nodo;

                System.out.println(
                        prefijo
                                + "├── [Declaración Variable]: "
                                + d.palabraClave.lexema
                                + " "
                                + d.identificador.lexema
                                + " = "
                                + d.valorNumero.lexema
                );
            }


            // --------------------------------------------------------
            // IF
            // --------------------------------------------------------

            else if (nodo instanceof NodoIf) {

                NodoIf nIf =
                        (NodoIf) nodo;

                System.out.println(
                        prefijo
                                + "├── [Estructura Control IF]"
                );


                String condText =
                        (nIf.condicion
                                instanceof NodoExpresionSimple)

                                ? ((NodoExpresionSimple)
                                        nIf.condicion)
                                        .valor.lexema

                                : "Expresión compleja";


                System.out.println(
                        prefijo
                                + "│   ├── Condición: ("
                                + condText
                                + ")"
                );


                System.out.println(
                        prefijo
                                + "│   └── Cuerpo del Bloque:"
                );


                visualizarAST(
                        nIf.bloqueTrue.sentencias,
                        prefijo + "│       "
                );
            }


            // --------------------------------------------------------
            // PRINT
            // --------------------------------------------------------

            else if (nodo instanceof NodoImprimir) {

                NodoImprimir imp =
                        (NodoImprimir) nodo;


                String valText =
                        (imp.expresion
                                instanceof NodoExpresionSimple)

                                ? ((NodoExpresionSimple)
                                        imp.expresion)
                                        .valor.lexema

                                : "Expresión";


                System.out.println(
                        prefijo
                                + "├── [Instrucción Imprimir]: print("
                                + valText
                                + ")"
                );
            }
        }
    }
}
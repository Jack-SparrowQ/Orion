package com.compiler;

import java.util.ArrayList;
import java.util.List;
import java.io.FileWriter;
import java.io.IOException;

import com.compiler.error.ErrorHandler;
import com.compiler.error.ErrorType;
import com.compiler.lexer.*;
import com.compiler.parser.*;
import com.compiler.parser.Nodes.NodoAgrupacion;
import com.compiler.parser.Nodes.NodoBinario;
import com.compiler.parser.Nodes.NodoDeclaracion;
import com.compiler.parser.Nodes.NodoExpresionSimple;
import com.compiler.parser.Nodes.NodoIf;
import com.compiler.parser.Nodes.NodoImprimir;
import com.compiler.parser.Nodes.NodoUnario;
import com.compiler.semantic.*;
import com.compiler.util.JSGenerator;
import com.compiler.codegen.Cuadrupla;
import com.compiler.codegen.GeneradorCodigoIntermedio;

public class Main {

    public static void main(String[] args) {

        // ============================================================
        // CÓDIGO FUENTE
        // ============================================================

        String codigoOrion = """
                print(1/0);
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

        ErrorHandler gestorErrores = new ErrorHandler();
        Lexer lexer = new Lexer(
                codigoOrion,
                gestorErrores
        );

        List<Token> listaTokens = new ArrayList<>();

        Token token;

        do {

            token = lexer.siguienteToken();
            listaTokens.add(token);

            System.out.println(
                    String.format(
                            "Token: %-15s Lexema: '%s' [%d:%d]",
                            token.tipo,
                            token.lexema,
                            token.line,
                            token.column
                    )
            );

        } while (token.tipo != TokenType.EOF);

        if(reportarSiHayErrores(gestorErrores, "lexico")) {
                return;
        }

        System.out.println(
                "Total de tokens reconocidos: "
                        + listaTokens.size()
        );

        System.out.println();


        // ============================================================
        // 2. ANÁLISIS SINTÁCTICO
        // ============================================================

        System.out.println("=== 2. FASE DE ANÁLISIS SINTÁCTICO ===");

        Parser parser = new Parser(
                listaTokens,
                gestorErrores
        );

        List<ElementoAST> arbolAST = parser.parsear();

        /*
         * Si el Parser devuelve null significa que ocurrió
         * algún error sintáctico.
         */
        if (reportarSiHayErrores(gestorErrores, "sintactic")) {
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

        analizarSimbolos(
                arbolAST,
                tablaSimbolos,
                gestorErrores
        );

        tablaSimbolos.mostrar();

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

        if (reportarSiHayErrores(gestorErrores, "Semantic")) {
            return;
        }

        System.out.println(
                "✓ Análisis semántico completado correctamente."
        );

        System.out.println();

        // ============================================================
        // 6. GENERACIÓN DE CÓDIGO INTERMEDIO
        // ============================================================

        System.out.println("=== 6. GENERACIÓN DE CÓDIGO INTERMEDIO ===");

        GeneradorCodigoIntermedio generadorIR = new GeneradorCodigoIntermedio();
        List<Cuadrupla> codigoIntermedio = generadorIR.generar(arbolAST);
        String textoTAC = GeneradorCodigoIntermedio.aTexto(codigoIntermedio);

        System.out.println();
        System.out.println("Código de tres direcciones:");
        System.out.println("-----------------------------------------");
        System.out.println(textoTAC);
        System.out.println("-----------------------------------------");
        System.out.println();

        System.out.println("Cuádruplas (op, arg1, arg2, resultado):");
        for (int i = 0; i < codigoIntermedio.size(); i++) {
        System.out.println(String.format("%3d  %s", i, codigoIntermedio.get(i).comoTupla()));
        }
        System.out.println();

        try (FileWriter writer = new FileWriter("programa.tac")) {
        writer.write(textoTAC);
        System.out.println("✓ Archivo generado correctamente: programa.tac");
        } catch (IOException e) {
        // No es fatal: el resto de la compilación continúa
        System.err.println("[AVISO] No se pudo generar programa.tac: " + e.getMessage());
        }
        System.out.println();


        // ============================================================
        // 7. GENERACIÓN DE CÓDIGO JS
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
        // 8. GENERACIÓN DEL ARCHIVO
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
                        determinarTipoExpresion(
                                declaracion.valorNumero
                        );


                Symbol simbolo =
                        new Symbol(
                                nombre,
                                tipo,
                                SymbolCategory.VARIABLE,
                                declaracion.identificador.line,
                                declaracion.identificador.column
                        );


                /*
                 * Intentamos insertar el símbolo.
                 *
                 * Si ya existe, tenemos una redeclaración.
                 */

                boolean insertado =
                        tablaSimbolos.insertar(simbolo);

                if (!insertado) {

                    Symbol previo = tablaSimbolos.buscar(nombre);

                    gestorErrores.agregar(
                            "E302",
                            ErrorType.SEMANTICO,
                            "El identificador '"
                                    + nombre
                                    + "' ya fue declarado (primera declaracion en la linea "
                                    + previo.getLinea() + ").",
                            declaracion.identificador.line,
                            declaracion.identificador.column
                    );
                }
            }


            // --------------------------------------------------------
            // IF
            // --------------------------------------------------------

            else if (nodo instanceof NodoIf) {

                NodoIf nodoIf =
                        (NodoIf) nodo;

                // La condición también puede usar identificadores
                validarExpresion(nodoIf.condicion, tablaSimbolos, gestorErrores);

                /*
                * Aquí posteriormente se validara que la
                * condición sea de tipo bool.
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

                validarExpresion(declaracion.valorNumero, tablaSimbolos, gestorErrores);

                Symbol simbolo =
                        tablaSimbolos.buscar(nombre);


                if (simbolo == null) {

                    gestorErrores.agregar(
                            "E301",
                            ErrorType.SEMANTICO,
                            "El identificador '"
                                    + nombre
                                    + "' no ha sido declarado.",
                            declaracion.identificador.line,
                            declaracion.identificador.column
                    );

                    continue;
                }


                /*
                 *
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


                validarExpresion(nodoIf, tablaSimbolos, gestorErrores);

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

                validarExpresion(imprimir.expresion, tablaSimbolos, gestorErrores);
                /*
                 * Aquí posteriormente puedes comprobar
                 * que la expresión utilizada por print
                 * sea válida.
                 */

                if (imprimir.expresion
                        instanceof NodoExpresionSimple) {

                    NodoImprimir print = 
                                (NodoImprimir) nodo;
                                
                        validarExpresion(imprimir.expresion, tablaSimbolos, gestorErrores);
                }
            }
        }
    }

    // ================================================================
// VALIDACIÓN DE EXPRESIONES (recursiva)
// ================================================================

// Recorre la expresión y comprueba que cada identificador esté declarado.
private static void validarExpresion(
        ElementoAST expresion,
        SymbolTable tablaSimbolos,
        ErrorHandler gestorErrores) {

    if (expresion instanceof NodoExpresionSimple) {
        Token valor = ((NodoExpresionSimple) expresion).valor;

        // Se decide por el tipo de token, no por el texto:
        // 'true' y 'false' también "parecen" identificadores.
        if (valor.tipo == TokenType.IDENTIFICADOR
                && !tablaSimbolos.existe(valor.lexema)) {

            gestorErrores.agregar(
                    "E301",
                    ErrorType.SEMANTICO,
                    "El identificador '" + valor.lexema
                            + "' no ha sido declarado.",
                    valor.line,
                    valor.column
            );
        }
    }
    else if (expresion instanceof NodoBinario) {
        NodoBinario b = (NodoBinario) expresion;
        validarExpresion(b.izquierda, tablaSimbolos, gestorErrores);
        validarExpresion(b.derecha, tablaSimbolos, gestorErrores);

        if (b.operador.tipo == TokenType.DIVISION
            && esCeroConstante(b.derecha)) {

        gestorErrores.agregar(
                "E304",
                ErrorType.SEMANTICO,
                "División entre cero: el divisor de la "
                        + "operación no puede ser 0.",
                b.operador.line,
                b.operador.column
        );
    }
    }
    else if (expresion instanceof NodoUnario) {
        validarExpresion(((NodoUnario) expresion).operando,
                tablaSimbolos, gestorErrores);
    }
    else if (expresion instanceof NodoAgrupacion) {
        validarExpresion(((NodoAgrupacion) expresion).expresion,
                tablaSimbolos, gestorErrores);
    }
}


/**
 * Comprueba si una expresión representa una constante numérica
 * cuyo resultado es cero.
 *
 * Devuelve false cuando no puede determinarse estáticamente
 * el valor de la expresión.
 */
private static boolean esCeroConstante(ElementoAST expresion) {
    Double valor = evaluarConstanteNumerica(expresion);

    return valor != null && valor == 0.0;
}

/**
 * Evalúa expresiones aritméticas compuestas únicamente por
 * números, operadores aritméticos, agrupaciones y signos unarios.
 *
 * Devuelve null si la expresión contiene variables, operadores
 * no aritméticos o una operación cuyo resultado no puede
 * determinarse de forma segura.
 */
private static Double evaluarConstanteNumerica(
        ElementoAST expresion) {

    if (expresion instanceof NodoExpresionSimple) {
        Token token = ((NodoExpresionSimple) expresion).valor;

        if (token.tipo != TokenType.NUMERO) {
            return null;
        }

        try {
            return Double.parseDouble(token.lexema);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    if (expresion instanceof NodoAgrupacion) {
        return evaluarConstanteNumerica(
                ((NodoAgrupacion) expresion).expresion
        );
    }

    if (expresion instanceof NodoUnario) {
        NodoUnario unario = (NodoUnario) expresion;

        Double operando =
                evaluarConstanteNumerica(unario.operando);

        if (operando == null) {
            return null;
        }

        if (unario.operador.tipo == TokenType.RESTA) {
            return -operando;
        }

        return null;
    }

    if (expresion instanceof NodoBinario) {
        NodoBinario binario = (NodoBinario) expresion;

        Double izquierda =
                evaluarConstanteNumerica(binario.izquierda);

        Double derecha =
                evaluarConstanteNumerica(binario.derecha);

        if (izquierda == null || derecha == null) {
            return null;
        }

        switch (binario.operador.tipo) {
            case SUMA:
                return izquierda + derecha;

            case RESTA:
                return izquierda - derecha;

            case MULTI:
                return izquierda * derecha;

            case DIVISION:
                if (derecha == 0.0) {
                    return null;
                }
                return izquierda / derecha;

            case MODULO:
                if (derecha == 0.0) {
                    return null;
                }
                return izquierda % derecha;

            default:
                return null;
        }
    }

    return null;
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

    // Tipo provisional de una expresión de inicialización.
        private static Type determinarTipoExpresion(ElementoAST expresion) {

        if (expresion instanceof NodoExpresionSimple) {
                Token valor = ((NodoExpresionSimple) expresion).valor;

                // Literales: el tipo se deduce del propio valor, como hasta ahora
                if (valor.tipo == TokenType.NUMERO
                        || valor.tipo == TokenType.TRUE
                        || valor.tipo == TokenType.FALSE) {
                return determinarTipo(valor.lexema);
                }
        }

        // TODO (Tarea 7): calcular el tipo real de identificadores y de
        // expresiones compuestas (int + float -> float, a > b -> bool, ...).
        return Type.NULL;
        }

    // ================================================================
    // MOSTRAR ERRORES
    // ================================================================

    // Si la fase dejó errores en el gestor, los muestra y devuelve true
    // para que main() aborte la compilación.
    private static boolean reportarSiHayErrores(
        ErrorHandler gestorErrores,
        String phase
    ) {
        if(!gestorErrores.hayErrores()) {
                return false;
        }

        System.err.println();
        System.err.println(
                "Se encontraron errores durante el análisis " + phase + "."
        );

        gestorErrores.mostrarErrores();

        System.err.println();
        System.err.println("[COMPILACIÓN ABORTADA]");

        return true;
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
                                + d.valorNumero
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
                                + "├── Condition: "
                                + nIf.condicion
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


                System.out.println(
                        prefijo
                                + "├── [Instrucción Imprimir]: "
                                + imp.expresion
                );
            }
        }
    }
}
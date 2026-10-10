package com.compiler.semantic;

import java.util.List;

import com.compiler.error.ErrorHandler;
import com.compiler.error.ErrorType;
import com.compiler.lexer.Token;
import com.compiler.lexer.TokenType;
import com.compiler.parser.ElementoAST;
import com.compiler.parser.Nodes.NodoAgrupacion;
import com.compiler.parser.Nodes.NodoBinario;
import com.compiler.parser.Nodes.NodoDeclaracion;
import com.compiler.parser.Nodes.NodoExpresionSimple;
import com.compiler.parser.Nodes.NodoIf;
import com.compiler.parser.Nodes.NodoImprimir;
import com.compiler.parser.Nodes.NodoUnario;

/**
 * 
 * SemanticAnalyzer
 */
public final class SemanticAnalyzer {
    
    private SemanticAnalyzer() {}

    /**
     * 
     * @param nodes
     * @param symbolsTable
     * @param errorHandler
     */
    public static void analyzeSymbols(
        List<ElementoAST> nodes,
        SymbolTable symbolsTable,
        ErrorHandler errorHandler
    ) {

        for(ElementoAST node: nodes) {
            
            //VARIABLE DECLARATION
            if(node instanceof NodoDeclaracion) {
                NodoDeclaracion declaration = 
                        (NodoDeclaracion) node;

                String name = declaration.identificador.lexema;

                Type type = determineExpressionType(declaration.valorNumero);

                Symbol symbol =
                        new Symbol(
                            name, 
                            type, 
                            SymbolCategory.VARIABLE, 
                            declaration.identificador.line, 
                            declaration.identificador.column
                        );

                boolean inserted =
                        symbolsTable.insertar(symbol);
                
                if(!inserted) {
                    Symbol previous = symbolsTable.buscar(name);

                    errorHandler.agregar(
                        "E302", 
                        ErrorType.SEMANTICO, 
                        "El identificador '"
                        + name 
                        + "' ya fue declarado (primera declaracion en la linea: "
                        + previous.getLinea() + ").", 
                        declaration.identificador.line,
                        declaration.identificador.column);
                }
            }
        
            //IF
            else if(node instanceof NodoIf) {

                NodoIf nodeIf =
                    (NodoIf) node;

                validateExpression(nodeIf.condicion, symbolsTable, errorHandler);

                analyzeSymbols(
                    nodeIf.bloqueTrue.sentencias,
                    symbolsTable,
                    errorHandler
                );
            }

        }


    }

    /**
     * 
     * @param nodes
     * @param symbolsTable
     * @param errorHandler
     */
    public static void analyzeSemantic(
        List<ElementoAST> nodes,
        SymbolTable symbolsTable,
        ErrorHandler errorHandler
    ) {

        for(ElementoAST node: nodes) {

            //DECLARATION
            if(node instanceof NodoDeclaracion) {
                NodoDeclaracion declaration =
                                (NodoDeclaracion) node;
                
                String name = declaration.identificador.lexema;

                validateExpression(declaration.valorNumero, symbolsTable, errorHandler);

                Symbol symbol = 
                       symbolsTable.buscar(name);

                
                if(symbol == null) {

                    errorHandler.agregar(
                        "E301", 
                        ErrorType.SEMANTICO, 
                        "El identificador '"
                        + name
                        + "' no ha sido declarado.", 
                        declaration.identificador.line, 
                        declaration.identificador.column
                    );

                    continue;
                }

                System.out.println(
                       "Symbol validated: "
                          + name 
                        + " -> "
                        + symbol.getTipo()
                );
            }

            //IF
            else if(node instanceof NodoIf) {
                NodoIf nodoIf =
                       (NodoIf) node;

                validateExpression(nodoIf, symbolsTable, errorHandler);

                analyzeSemantic(
                    nodoIf.bloqueTrue.sentencias, 
                    symbolsTable, 
                    errorHandler
                );
            }

            //PRINT()
            else if(node instanceof NodoImprimir) {

                NodoImprimir printNode = 
                            (NodoImprimir) node;
                
                validateExpression(
                    printNode.expresion,
                    symbolsTable,
                    errorHandler
                );

                if(printNode.expresion
                    instanceof NodoExpresionSimple) {

                   NodoImprimir print = 
                            (NodoImprimir) node; 

                   validateExpression(
                    print.expresion,
                    symbolsTable,
                    errorHandler
                  );
                }
            }
        }
    }
    
    /**
     * Traverse the expression and verify that each identifier is declared.
     * @param expression
     * @param symbolsTable
     * @param errorHandler
     */
    private static void validateExpression(
        ElementoAST expression,
        SymbolTable symbolsTable,
        ErrorHandler errorHandler
    ) {
       if (expression instanceof NodoExpresionSimple) {
        Token valor = ((NodoExpresionSimple) expression).valor;

        // Se decide por el tipo de token, no por el texto:
        // 'true' y 'false' también "parecen" identificadores.
        if (valor.tipo == TokenType.IDENTIFICADOR
                && !symbolsTable.existe(valor.lexema)) {

            errorHandler.agregar(
                    "E301",
                    ErrorType.SEMANTICO,
                    "El identificador '" + valor.lexema
                            + "' no ha sido declarado.",
                    valor.line,
                    valor.column
            );
        }
    }
        else if (expression instanceof NodoBinario) {
            NodoBinario b = (NodoBinario) expression;
            validateExpression(b.izquierda, symbolsTable, errorHandler);
            validateExpression(b.derecha, symbolsTable, errorHandler);

            if (b.operador.tipo == TokenType.DIVISION
                && isAConstantZero(b.derecha)) {

            errorHandler.agregar(
                    "E304",
                    ErrorType.SEMANTICO,
                    "División entre cero: el divisor de la "
                            + "operación no puede ser 0.",
                    b.operador.line,
                    b.operador.column
            );
        }
        }
        else if (expression instanceof NodoUnario) {
            validateExpression(((NodoUnario) expression).operando,
                    symbolsTable, errorHandler);
        }
        else if (expression instanceof NodoAgrupacion) {
            validateExpression(((NodoAgrupacion) expression).expresion,
                    symbolsTable, errorHandler);
        }
    } 

    /**
     * Comprueba si una expresión representa una constante numérica
    * cuyo resultado es cero.
    *
    * Devuelve false cuando no puede determinarse estáticamente
    * el valor de la expresión.
     * @param expression
     * @return
     */
    private static boolean isAConstantZero(ElementoAST expression) {
        Double val = evaluateNumericConstant(expression);

        return val != null && val == 0.0;
    }

    /**
     * Evalúa expresiones aritméticas compuestas únicamente por
    * números, operadores aritméticos, agrupaciones y signos unarios.
    *
    * Devuelve null si la expresión contiene variables, operadores
    * no aritméticos o una operación cuyo resultado no puede
    * determinarse de forma segura.
     * @param expression
     * @return
     */
    private static Double evaluateNumericConstant(
        ElementoAST expression 
    ) {
        if (expression instanceof NodoExpresionSimple) {
        Token token = ((NodoExpresionSimple) expression).valor;

        if (token.tipo != TokenType.NUMERO) {
            return null;
        }

        try {
            return Double.parseDouble(token.lexema);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    if (expression instanceof NodoAgrupacion) {
        return evaluateNumericConstant(
                ((NodoAgrupacion) expression).expresion
        );
    }

    if (expression instanceof NodoUnario) {
        NodoUnario unario = (NodoUnario) expression;

        Double operando =
                evaluateNumericConstant(unario.operando);

        if (operando == null) {
            return null;
        }

        if (unario.operador.tipo == TokenType.RESTA) {
            return -operando;
        }

        return null;
    }

    if (expression instanceof NodoBinario) {
        NodoBinario binario = (NodoBinario) expression;

        Double izquierda =
                evaluateNumericConstant(binario.izquierda);

        Double derecha =
                evaluateNumericConstant(binario.derecha);

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

    //DETERMINACION SIMPLE DE TIPO
    /**
     * Determinacion simple de tipo.
     * @param valor
     * @return
     */
    private static Type determineType(String valor) {

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
    /**
     * Tipo provisional de una expresion de inicializacion.
     * @param expresion
     * @return
     */
    private static Type determineExpressionType(ElementoAST expresion) {

        if (expresion instanceof NodoExpresionSimple) {
                Token valor = ((NodoExpresionSimple) expresion).valor;

                // Literales: el tipo se deduce del propio valor, como hasta ahora
                if (valor.tipo == TokenType.NUMERO
                        || valor.tipo == TokenType.TRUE
                        || valor.tipo == TokenType.FALSE) {
                return determineType(valor.lexema);
                }
        }

        // TODO (Tarea 7): calcular el tipo real de identificadores y de
        // expresiones compuestas (int + float -> float, a > b -> bool, ...).
        return Type.NULL;
    }    
}

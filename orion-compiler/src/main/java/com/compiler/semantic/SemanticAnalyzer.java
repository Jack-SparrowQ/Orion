package com.compiler.semantic;

import java.util.List;

import com.compiler.error.ErrorHandler;
import com.compiler.error.ErrorType;
import com.compiler.lexer.Token;
import com.compiler.lexer.TokenType;
import com.compiler.parser.ast.*;

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
        List<Sentencia> nodes,
        SymbolTable symbolsTable,
        ErrorHandler errorHandler
    ) {

        for(Sentencia node: nodes) {

            switch (node) {

                //VARIABLE DECLARATION
                case NodoDeclaracion declaration -> {
                    String name = declaration.identificador().lexema;

                    Type type = determineExpressionType(declaration.valor());

                    Symbol symbol =
                            new Symbol(
                                name,
                                type,
                                SymbolCategory.VARIABLE,
                                declaration.identificador().line,
                                declaration.identificador().column
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
                            declaration.identificador().line,
                            declaration.identificador().column);
                    }
                }

                //IF
                case NodoIf nodeIf -> {
                    validateExpression(nodeIf.condicion(), symbolsTable, errorHandler);

                    analyzeSymbols(
                        nodeIf.bloqueTrue().sentencias(),
                        symbolsTable,
                        errorHandler
                    );
                }

                //PRINT: no declara símbolos
                case NodoImprimir print -> { }
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
        List<Sentencia> nodes,
        SymbolTable symbolsTable,
        ErrorHandler errorHandler
    ) {

        for(Sentencia node: nodes) {

            switch (node) {

                //DECLARATION
                case NodoDeclaracion declaration -> {
                    String name = declaration.identificador().lexema;

                    validateExpression(declaration.valor(), symbolsTable, errorHandler);

                    if(symbolsTable.buscar(name) == null) {
                        errorHandler.agregar(
                            "E301",
                            ErrorType.SEMANTICO,
                            "El identificador '"
                            + name
                            + "' no ha sido declarado.",
                            declaration.identificador().line,
                            declaration.identificador().column
                        );
                    }
                }

                //IF: la condición ya se validó en analyzeSymbols
                case NodoIf nodoIf -> analyzeSemantic(
                        nodoIf.bloqueTrue().sentencias(),
                        symbolsTable,
                        errorHandler
                    );

                //PRINT()
                case NodoImprimir print -> validateExpression(print.expresion(), symbolsTable, errorHandler);
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
        Expresion expression,
        SymbolTable symbolsTable,
        ErrorHandler errorHandler
    ) {
        switch (expression) {

            case NodoLiteral literal -> { }

            case NodoVariable variable -> {
                Token nombre = variable.nombre();

                if (!symbolsTable.existe(nombre.lexema)) {
                    errorHandler.agregar(
                            "E301",
                            ErrorType.SEMANTICO,
                            "El identificador '" + nombre.lexema
                                    + "' no ha sido declarado.",
                            nombre.line,
                            nombre.column
                    );
                }
            }

            case NodoBinario b -> {
                validateExpression(b.izquierda(), symbolsTable, errorHandler);
                validateExpression(b.derecha(), symbolsTable, errorHandler);

                if (b.operador().tipo == TokenType.DIVISION
                        && isAConstantZero(b.derecha())) {

                    errorHandler.agregar(
                            "E304",
                            ErrorType.SEMANTICO,
                            "División entre cero: el divisor de la "
                                    + "operación no puede ser 0.",
                            b.operador().line,
                            b.operador().column
                    );
                }
            }

            case NodoUnario u -> validateExpression(u.operando(), symbolsTable, errorHandler);

            case NodoAgrupacion g -> validateExpression(g.expresion(), symbolsTable, errorHandler);
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
    private static boolean isAConstantZero(Expresion expression) {
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
        Expresion expression
    ) {
        return switch (expression) {

            case NodoLiteral literal -> {
                Token token = literal.valor();

                if (token.tipo != TokenType.NUMERO) {
                    yield null;
                }

                try {
                    yield Double.parseDouble(token.lexema);
                } catch (NumberFormatException e) {
                    yield null;
                }
            }

            case NodoVariable variable -> null;

            case NodoAgrupacion g -> evaluateNumericConstant(g.expresion());

            case NodoUnario unario -> {
                Double operando =
                        evaluateNumericConstant(unario.operando());

                if (operando == null) {
                    yield null;
                }

                yield unario.operador().tipo == TokenType.RESTA ? -operando : null;
            }

            case NodoBinario binario -> {
                Double izquierda =
                        evaluateNumericConstant(binario.izquierda());

                Double derecha =
                        evaluateNumericConstant(binario.derecha());

                if (izquierda == null || derecha == null) {
                    yield null;
                }

                yield switch (binario.operador().tipo) {
                    case SUMA -> izquierda + derecha;
                    case RESTA -> izquierda - derecha;
                    case MULTI -> izquierda * derecha;
                    case DIVISION -> derecha == 0.0 ? null : izquierda / derecha;
                    case MODULO -> derecha == 0.0 ? null : izquierda % derecha;
                    default -> null;
                };
            }
        };
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
    private static Type determineExpressionType(Expresion expresion) {

        // Literales: el tipo se deduce del propio valor, como hasta ahora
        if (expresion instanceof NodoLiteral literal) {
            return determineType(literal.valor().lexema);
        }

        // TODO (Tarea 7): calcular el tipo real de identificadores y de
        // expresiones compuestas (int + float -> float, a > b -> bool, ...).
        return Type.NULL;
    }    
}

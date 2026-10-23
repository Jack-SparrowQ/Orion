package com.compiler.semantic;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.compiler.error.ErrorHandler;
import com.compiler.error.ErrorType;
import com.compiler.lexer.Token;
import com.compiler.lexer.TokenType;
import com.compiler.parser.ast.*;

/**
 * Análisis semántico en dos fases:
 * <ol>
 *   <li>{@link #analyzeSymbols}: registra cada declaración con su tipo inferido (E302).</li>
 *   <li>{@link #analyzeSemantic}: comprueba, en orden y por bloques, el uso de cada
 *       identificador y los tipos de expresiones, asignaciones y condiciones.</li>
 * </ol>
 *
 * Reglas de tipos (int se promueve a float donde hace falta):
 * <pre>
 *   + - * %          int op int -> int ; numérico op numérico -> float
 *   /                numérico / numérico -> float  (en JS, 7 / 2 es 3.5)
 *   &lt; &lt;= &gt; &gt;=      numérico op numérico -> bool
 *   == !=            mismo tipo, o ambos numéricos -> bool
 *   &amp;&amp; ||  !         bool -> bool
 *   -x               numérico -> mismo tipo
 *   x = valor        mismo tipo, o int en una variable float
 *   if (c)           c debe ser bool
 * </pre>
 */
public final class SemanticAnalyzer {

    private SemanticAnalyzer() {}

    /**
     * Fase 3: registra las declaraciones (en orden) con su tipo inferido.
     * Cada bloque abre un ámbito hijo en la tabla; la fase 4 recorre esos
     * ámbitos en el mismo orden.
     * @param nodes sentencias a registrar
     * @param symbolsTable ámbito donde se registran
     * @param errorHandler destino de los errores E302
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

                    // El tipo anotado manda; si no hay, se infiere del valor
                    // (sin reportar errores: eso lo hace la fase 4)
                    Type type = declaration.tipo() != null
                            ? tipoAnotado(declaration.tipo())
                            : new Verificador(symbolsTable, null).tipo(declaration.valor());

                    Symbol symbol =
                            new Symbol(
                                name,
                                type,
                                declaration.palabraClave().tipo == TokenType.CONST
                                        ? SymbolCategory.CONSTANTE
                                        : SymbolCategory.VARIABLE,
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

                //IF: cada rama es un ámbito propio
                case NodoIf nodeIf -> {
                    analyzeSymbols(nodeIf.bloqueTrue().sentencias(), symbolsTable.abrirAmbito(), errorHandler);
                    analyzeSymbols(nodeIf.bloqueFalse().sentencias(), symbolsTable.abrirAmbito(), errorHandler);
                }

                //PRINT y ASIGNACIÓN: no declaran símbolos
                case NodoImprimir print -> { }
                case NodoAsignacion asignacion -> { }

                //BLOQUE: registra sus declaraciones en un ámbito nuevo
                case NodoBloque bloque -> analyzeSymbols(bloque.sentencias(), symbolsTable.abrirAmbito(), errorHandler);
            }
        }
    }

    /**
     * Fase 4: uso de identificadores (E301, E303, E305), división entre cero (E304)
     * y tipos (E306, E307, E308).
     * @param nodes
     * @param symbolsTable
     * @param errorHandler
     */
    public static void analyzeSemantic(
        List<Sentencia> nodes,
        SymbolTable symbolsTable,
        ErrorHandler errorHandler
    ) {
        new Verificador(symbolsTable, errorHandler).sentencias(nodes);
    }

    /**
     * Recorre sentencias y expresiones calculando tipos. Un tipo null significa
     * "desconocido" (por un error ya reportado): no genera más errores en cascada.
     */
    private static final class Verificador {

        /** Ámbito raíz del recorrido. */
        private final SymbolTable tabla;

        /** null: solo inferir tipos, sin reportar errores ni comprobar ámbitos. */
        private final ErrorHandler errores;

        /** Ámbito del punto que se está verificando. */
        private SymbolTable actual;

        /** Símbolos cuya declaración ya se recorrió: los demás aún no son visibles (E303). */
        private final Set<Symbol> declarados = new HashSet<>();

        /** Por cada ámbito, sus hijos aún no visitados (en el orden de la fase 3). */
        private final Map<SymbolTable, Iterator<SymbolTable>> pendientes = new HashMap<>();

        /**
         * @param tabla ámbito donde empieza el recorrido (el global en la fase 4)
         * @param errores destino de los errores, o null para solo inferir tipos
         */
        Verificador(SymbolTable tabla, ErrorHandler errores) {
            this.tabla = tabla;
            this.errores = errores;
            this.actual = tabla;
        }

        void sentencias(List<Sentencia> nodos) {
            for (Sentencia nodo : nodos) {
                sentencia(nodo);
            }
        }

        /**
         * Verifica un bloque dentro del ámbito que le abrió la fase 3: las
         * variables declaradas dentro solo son visibles en él (el JavaScript
         * generado usa let/const, que también son de bloque).
         *
         * @param bloque cuerpo de un if o bloque independiente
         */
        private void bloque(NodoBloque bloque) {
            SymbolTable padre = actual;
            actual = pendientes.computeIfAbsent(padre, t -> t.getHijos().iterator()).next();
            sentencias(bloque.sentencias());
            actual = padre;
        }

        private void sentencia(Sentencia nodo) {
            switch (nodo) {

                // El valor se comprueba antes de declarar: "let a = a + 1;" es un error
                case NodoDeclaracion declaracion -> {
                    if (declaracion.valor() != null) {
                        Type valor = tipo(declaracion.valor());
                        Token anotado = declaracion.tipo();
                        if (anotado != null && !asignable(valor, tipoAnotado(anotado))) {
                            reportar("E307", "No se puede asignar un valor " + nombreTipo(valor)
                                    + " a '" + declaracion.identificador().lexema + "', que es de tipo "
                                    + anotado.lexema + ".", declaracion.identificador());
                        }
                    }
                    // shortcut: "let x: T;" sin valor no se rastrea; leerla antes de asignarla es E309 (pendiente).
                    // Si fue un E302, buscar() devuelve la declaración previa, ya visible
                    declarados.add(actual.buscar(declaracion.identificador().lexema));
                }

                case NodoAsignacion asignacion -> {
                    Type valor = tipo(asignacion.valor());
                    Token nombre = asignacion.identificador();
                    Symbol symbol = buscarVisible(nombre);

                    if (symbol == null) {
                        return;
                    }

                    if (symbol.getCategoria() == SymbolCategory.CONSTANTE) {
                        reportar("E305", "No se puede reasignar la constante '" + nombre.lexema
                                + "' (declarada en la linea: " + symbol.getLinea() + ").", nombre);
                    } else if (!asignable(valor, symbol.getTipo())) {
                        reportar("E307", "No se puede asignar un valor " + nombreTipo(valor)
                                + " a '" + nombre.lexema + "', que es de tipo "
                                + nombreTipo(symbol.getTipo()) + ".", nombre);
                    }
                }

                case NodoIf nodoIf -> {
                    Type condicion = tipo(nodoIf.condicion());
                    if (condicion != null && condicion != Type.BOOL) {
                        reportar("E308", "La condición del if debe ser bool, no "
                                + nombreTipo(condicion) + ".", primerToken(nodoIf.condicion()));
                    }
                    bloque(nodoIf.bloqueTrue());
                    bloque(nodoIf.bloqueFalse());
                }

                case NodoImprimir print -> tipo(print.expresion());

                case NodoBloque b -> bloque(b);
            }
        }

        /** Tipo de la expresión, o null si no puede determinarse (error ya reportado). */
        Type tipo(Expresion expresion) {
            return switch (expresion) {

                case NodoLiteral literal -> literal.valor().tipo == TokenType.NUMERO
                        ? (literal.valor().lexema.contains(".") ? Type.FLOAT : Type.INT)
                        : Type.BOOL;

                case NodoVariable variable -> {
                    Symbol symbol = buscarVisible(variable.nombre());
                    yield symbol == null ? null : symbol.getTipo();
                }

                case NodoAgrupacion grupo -> tipo(grupo.expresion());

                case NodoUnario unario -> {
                    Type operando = tipo(unario.operando());
                    if (operando == null) {
                        yield null;
                    }

                    boolean valido = unario.operador().tipo == TokenType.RESTA
                            ? esNumerico(operando)
                            : operando == Type.BOOL; // '!'
                    if (!valido) {
                        reportar("E306", "Operación inválida: no se puede aplicar '"
                                + unario.operador().lexema + "' a " + nombreTipo(operando) + ".",
                                unario.operador());
                        yield null;
                    }
                    yield operando;
                }

                case NodoBinario binario -> {
                    Type izquierda = tipo(binario.izquierda());
                    Type derecha = tipo(binario.derecha());

                    if (binario.operador().tipo == TokenType.DIVISION
                            && isAConstantZero(binario.derecha())) {
                        reportar("E304", "División entre cero: el divisor de la "
                                + "operación no puede ser 0.", binario.operador());
                    }

                    if (izquierda == null || derecha == null) {
                        yield null;
                    }

                    Type resultado = tipoBinario(binario.operador().tipo, izquierda, derecha);
                    if (resultado == null) {
                        reportar("E306", "Operación inválida: no se puede aplicar '"
                                + binario.operador().lexema + "' a " + nombreTipo(izquierda)
                                + " y " + nombreTipo(derecha) + ".", binario.operador());
                    }
                    yield resultado;
                }
            };
        }

        /**
         * Símbolo de un identificador usado en este punto, o null si no es
         * visible (E301 si no existe, E303 si existe pero aún no está declarado
         * o pertenece a otro bloque).
         */
        private Symbol buscarVisible(Token nombre) {
            Symbol symbol = actual.buscar(nombre.lexema);

            if (errores == null) {
                return symbol; // inferencia: basta con que ya esté en la tabla
            }

            if (symbol != null && declarados.contains(symbol)) {
                return symbol;
            }

            // No es visible: puede estar más adelante en este ámbito o en otro bloque
            Symbol enOtroLugar = symbol != null ? symbol : tabla.buscarEnSubambitos(nombre.lexema);
            if (enOtroLugar == null) {
                reportar("E301", "El identificador '" + nombre.lexema
                        + "' no ha sido declarado.", nombre);
            } else {
                reportar("E303", "El identificador '" + nombre.lexema
                        + "' se usa antes de su declaración o fuera de su bloque (declarado en la linea: "
                        + enOtroLugar.getLinea() + ").", nombre);
            }
            return null;
        }

        private void reportar(String codigo, String mensaje, Token posicion) {
            if (errores != null) {
                errores.agregar(codigo, ErrorType.SEMANTICO, mensaje, posicion.line, posicion.column);
            }
        }
    }

    /** Tipo resultante de "izquierda op derecha", o null si la operación no es válida. */
    private static Type tipoBinario(TokenType operador, Type izquierda, Type derecha) {
        boolean numericos = esNumerico(izquierda) && esNumerico(derecha);

        return switch (operador) {
            case SUMA, RESTA, MULTI, MODULO -> !numericos ? null
                    : (izquierda == Type.INT && derecha == Type.INT ? Type.INT : Type.FLOAT);
            case DIVISION -> numericos ? Type.FLOAT : null;
            case MENOR, MENOR_IGUAL, MAYOR, MAYOR_IGUAL -> numericos ? Type.BOOL : null;
            case COMPARACION_IGUAL, DIFERENTE -> izquierda == derecha || numericos ? Type.BOOL : null;
            case AND_LOGICO, OR_LOGICO ->
                    izquierda == Type.BOOL && derecha == Type.BOOL ? Type.BOOL : null;
            default -> null;
        };
    }

    private static boolean esNumerico(Type tipo) {
        return tipo == Type.INT || tipo == Type.FLOAT;
    }

    /** Un tipo desconocido (null) se acepta para no generar errores en cascada. */
    private static boolean asignable(Type valor, Type destino) {
        return valor == null || destino == null || valor == destino
                || (valor == Type.INT && destino == Type.FLOAT);
    }

    /** Tipo de una anotación: los tokens INT, FLOAT... se llaman igual que los valores de Type. */
    private static Type tipoAnotado(Token tipo) {
        return Type.valueOf(tipo.tipo.name());
    }

    private static String nombreTipo(Type tipo) {
        return tipo.name().toLowerCase();
    }

    /** Token más a la izquierda de una expresión, para ubicar el error. */
    private static Token primerToken(Expresion expresion) {
        return switch (expresion) {
            case NodoLiteral literal -> literal.valor();
            case NodoVariable variable -> variable.nombre();
            case NodoAgrupacion grupo -> primerToken(grupo.expresion());
            case NodoUnario unario -> unario.operador();
            case NodoBinario binario -> primerToken(binario.izquierda());
        };
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
}

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
 *   f(a1..an)        n parámetros, cada ai asignable al suyo -> tipo de retorno de f
 *   return e         e asignable al tipo de retorno de la función
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

        // Funciones elevadas (hoisting): se registran antes que cualquier sentencia,
        // así se pueden llamar antes de su declaración (y de forma recursiva)
        for (Sentencia node : nodes) {
            if (node instanceof NodoFuncion funcion) {
                List<Type> parametros = funcion.parametros().stream()
                        .map(p -> tipoAnotado(p.tipo()))
                        .toList();
                declarar(symbolsTable, new Symbol(
                        funcion.nombre().lexema,
                        tipoRetorno(funcion),
                        SymbolCategory.FUNCION,
                        funcion.nombre().line,
                        funcion.nombre().column,
                        parametros), errorHandler);
            }
        }

        for(Sentencia node: nodes) {

            switch (node) {

                //VARIABLE DECLARATION
                case NodoDeclaracion declaration -> {
                    // El tipo anotado manda; si no hay, se infiere del valor
                    // (sin reportar errores: eso lo hace la fase 4)
                    Type type = declaration.tipo() != null
                            ? tipoAnotado(declaration.tipo())
                            : new Verificador(symbolsTable, null).tipo(declaration.valor());

                    declarar(symbolsTable, new Symbol(
                            declaration.identificador().lexema,
                            type,
                            declaration.palabraClave().tipo == TokenType.CONST
                                    ? SymbolCategory.CONSTANTE
                                    : SymbolCategory.VARIABLE,
                            declaration.identificador().line,
                            declaration.identificador().column), errorHandler);
                }

                //FUNCIÓN: ya registrada arriba; parámetros y cuerpo comparten un ámbito
                case NodoFuncion funcion -> {
                    SymbolTable ambito = symbolsTable.abrirAmbito();
                    for (NodoParametro parametro : funcion.parametros()) {
                        declarar(ambito, new Symbol(
                                parametro.nombre().lexema,
                                tipoAnotado(parametro.tipo()),
                                SymbolCategory.PARAMETRO,
                                parametro.nombre().line,
                                parametro.nombre().column), errorHandler);
                    }
                    analyzeSymbols(funcion.cuerpo().sentencias(), ambito, errorHandler);
                }

                //RETURN y LLAMADA: no declaran símbolos
                case NodoReturn ret -> { }
                case NodoLlamada llamada -> { }

                //IF: cada rama es un ámbito propio
                case NodoIf nodeIf -> {
                    analyzeSymbols(nodeIf.bloqueTrue().sentencias(), symbolsTable.abrirAmbito(), errorHandler);
                    analyzeSymbols(nodeIf.bloqueFalse().sentencias(), symbolsTable.abrirAmbito(), errorHandler);
                }

                //WHILE: el cuerpo es un ámbito propio
                case NodoWhile nodeWhile ->
                        analyzeSymbols(nodeWhile.cuerpo().sentencias(), symbolsTable.abrirAmbito(), errorHandler);

                //PRINT, ASIGNACIÓN, BREAK y CONTINUE: no declaran símbolos
                case NodoImprimir print -> { }
                case NodoAsignacion asignacion -> { }
                case NodoSalto salto -> { }

                //BLOQUE: registra sus declaraciones en un ámbito nuevo
                case NodoBloque bloque -> analyzeSymbols(bloque.sentencias(), symbolsTable.abrirAmbito(), errorHandler);
            }
        }
    }

    /**
     * Inserta el símbolo en el ámbito o, si el nombre ya es visible, reporta E302.
     *
     * @param ambito       ámbito donde se declara
     * @param symbol       símbolo a declarar
     * @param errorHandler destino del error
     */
    private static void declarar(SymbolTable ambito, Symbol symbol, ErrorHandler errorHandler) {
        if (!ambito.insertar(symbol)) {
            Symbol previous = ambito.buscar(symbol.getNombre());

            errorHandler.agregar(
                "E302",
                ErrorType.SEMANTICO,
                "El identificador '"
                + symbol.getNombre()
                + "' ya fue declarado (primera declaracion en la linea: "
                + previous.getLinea() + ").",
                symbol.getLinea(),
                symbol.getColumna());
        }
    }

    /**
     * Fase 4: uso de identificadores (E301, E303, E305), división entre cero (E304),
     * tipos (E306, E307, E308, E312), break/continue fuera de un ciclo (E313),
     * llamadas (E314, E315) y return (E316, E317, E318).
     * @param nodes sentencias del programa
     * @param symbolsTable tabla global, ya llenada por {@link #analyzeSymbols}
     * @param errorHandler destino de los errores
     */
    public static void analyzeSemantic(
        List<Sentencia> nodes,
        SymbolTable symbolsTable,
        ErrorHandler errorHandler
    ) {
        Verificador verificador = new Verificador(symbolsTable, errorHandler);

        // Funciones elevadas: son visibles desde el inicio del programa
        for (Sentencia node : nodes) {
            if (node instanceof NodoFuncion funcion) {
                verificador.declarados.add(symbolsTable.buscar(funcion.nombre().lexema));
            }
        }
        verificador.sentencias(nodes);
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

        /** Ciclos que contienen el punto actual: 0 significa que break/continue son E313. */
        private int ciclos = 0;

        /** Función cuyo cuerpo se está verificando, o null en el nivel superior (E316). */
        private NodoFuncion funcionActual;

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
         * @param bloque cuerpo de un if o de un while, o bloque independiente
         */
        private void bloque(NodoBloque bloque) {
            SymbolTable padre = actual;
            actual = siguienteAmbito();
            sentencias(bloque.sentencias());
            actual = padre;
        }

        /**
         * @return el siguiente ámbito hijo del actual que abrió la fase 3
         */
        private SymbolTable siguienteAmbito() {
            return pendientes.computeIfAbsent(actual, t -> t.getHijos().iterator()).next();
        }

        /**
         * Verifica una función: los parámetros son visibles en todo el cuerpo,
         * que comparte su ámbito. Desde el cuerpo solo se ven las globales
         * declaradas antes de la función (las demás aún no están en
         * {@link #declarados}: E303).
         *
         * @param funcion la función a verificar
         */
        private void funcion(NodoFuncion funcion) {
            SymbolTable padre = actual;
            actual = siguienteAmbito();
            for (NodoParametro parametro : funcion.parametros()) {
                declarados.add(actual.buscar(parametro.nombre().lexema));
            }

            funcionActual = funcion;
            sentencias(funcion.cuerpo().sentencias());
            funcionActual = null;
            actual = padre;

            Type retorno = tipoRetorno(funcion);
            if (retorno != Type.VOID && !siempreRetorna(funcion.cuerpo())) {
                reportar("E318", "La función '" + funcion.nombre().lexema + "' debe devolver un valor "
                        + nombreTipo(retorno) + " en todos los caminos.", funcion.nombre());
            }
        }

        /**
         * Verifica un return: debe estar en una función (E316), llevar valor
         * solo si la función no es void (E317) y ese valor debe ser asignable
         * al tipo de retorno (E307).
         *
         * @param ret el return a verificar
         */
        private void retorno(NodoReturn ret) {
            Type valor = ret.valor() == null ? null : tipo(ret.valor());

            if (funcionActual == null) {
                reportar("E316", "'return' solo puede usarse dentro de una función.", ret.palabraClave());
                return;
            }

            String nombre = funcionActual.nombre().lexema;
            Type esperado = tipoRetorno(funcionActual);
            if (esperado == Type.VOID && ret.valor() != null) {
                reportar("E317", "La función '" + nombre + "' es void: su return no lleva valor.",
                        ret.palabraClave());
            } else if (esperado != Type.VOID && ret.valor() == null) {
                reportar("E317", "La función '" + nombre + "' debe devolver un valor "
                        + nombreTipo(esperado) + ".", ret.palabraClave());
            } else if (ret.valor() != null && !asignable(valor, esperado)) {
                reportar("E307", "La función '" + nombre + "' debe devolver " + nombreTipo(esperado)
                        + ", no " + nombreTipo(valor) + ".", ret.palabraClave());
            }
        }

        /**
         * Tipo de una llamada: comprueba que se llame a una función (E301, E314),
         * con el número de argumentos (E315) y los tipos (E307) correctos.
         *
         * @param llamada la llamada a verificar
         * @return el tipo de retorno (VOID si no devuelve nada), o null si hubo error
         */
        private Type llamada(NodoLlamada llamada) {
            // Los argumentos se verifican siempre: pueden tener sus propios errores
            List<Type> argumentos = llamada.argumentos().stream().map(this::tipo).toList();
            Token nombre = llamada.nombre();
            Symbol funcion = actual.buscar(nombre.lexema);

            if (funcion == null) {
                reportar("E301", "La función '" + nombre.lexema + "' no ha sido declarada.", nombre);
                return null;
            }
            if (funcion.getCategoria() != SymbolCategory.FUNCION) {
                reportar("E314", "'" + nombre.lexema + "' no es una función: no se puede llamar.", nombre);
                return null;
            }

            List<Type> parametros = funcion.getParametros();
            if (parametros.size() != argumentos.size()) {
                reportar("E315", "La función '" + nombre.lexema + "' espera " + parametros.size()
                        + " argumento(s), pero recibió " + argumentos.size() + ".", nombre);
            } else {
                for (int i = 0; i < parametros.size(); i++) {
                    if (!asignable(argumentos.get(i), parametros.get(i))) {
                        reportar("E307", "El argumento " + (i + 1) + " de '" + nombre.lexema
                                + "' debe ser " + nombreTipo(parametros.get(i)) + ", no "
                                + nombreTipo(argumentos.get(i)) + ".",
                                primerToken(llamada.argumentos().get(i)));
                    }
                }
            }
            return funcion.getTipo();
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
                        } else if (anotado == null && valor == Type.VOID) {
                            reportar("E312", "No se puede inferir el tipo de '"
                                    + declaracion.identificador().lexema + "': el valor es void.",
                                    declaracion.identificador());
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
                    } else if (symbol.getCategoria() == SymbolCategory.FUNCION) {
                        reportar("E305", "No se puede asignar a la función '" + nombre.lexema + "'.", nombre);
                    } else if (!asignable(valor, symbol.getTipo())) {
                        reportar("E307", "No se puede asignar un valor " + nombreTipo(valor)
                                + " a '" + nombre.lexema + "', que es de tipo "
                                + nombreTipo(symbol.getTipo()) + ".", nombre);
                    }
                }

                case NodoIf nodoIf -> {
                    condicion(nodoIf.condicion(), "if");
                    bloque(nodoIf.bloqueTrue());
                    bloque(nodoIf.bloqueFalse());
                }

                case NodoWhile nodoWhile -> {
                    condicion(nodoWhile.condicion(), "while");
                    ciclos++;
                    bloque(nodoWhile.cuerpo());
                    ciclos--;
                }

                case NodoSalto salto -> {
                    if (ciclos == 0) {
                        reportar("E313", "'" + salto.palabraClave().lexema
                                + "' solo puede usarse dentro de un ciclo.", salto.palabraClave());
                    }
                }

                case NodoImprimir print -> {
                    if (tipo(print.expresion()) == Type.VOID) {
                        reportar("E306", "No se puede imprimir un valor void.", primerToken(print.expresion()));
                    }
                }

                case NodoBloque b -> bloque(b);

                case NodoFuncion f -> funcion(f);

                case NodoReturn ret -> retorno(ret);

                // Llamada como sentencia: el valor de retorno se descarta
                case NodoLlamada llamada -> llamada(llamada);
            }
        }

        /**
         * Comprueba que la condición de un if o un while sea bool (E308).
         *
         * @param expresion condición a comprobar
         * @param sentencia nombre de la sentencia, para el mensaje ("if", "while")
         */
        private void condicion(Expresion expresion, String sentencia) {
            Type tipo = tipo(expresion);
            if (tipo != null && tipo != Type.BOOL) {
                reportar("E308", "La condición del " + sentencia + " debe ser bool, no "
                        + nombreTipo(tipo) + ".", primerToken(expresion));
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
                    if (symbol != null && symbol.getCategoria() == SymbolCategory.FUNCION) {
                        reportar("E306", "Las funciones no son valores: falta '(...)' para llamar a '"
                                + variable.nombre().lexema + "'.", variable.nombre());
                        yield null;
                    }
                    yield symbol == null ? null : symbol.getTipo();
                }

                case NodoAgrupacion grupo -> tipo(grupo.expresion());

                case NodoLlamada llamada -> llamada(llamada);

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
            case COMPARACION_IGUAL, DIFERENTE ->
                    izquierda != Type.VOID && (izquierda == derecha || numericos) ? Type.BOOL : null;
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

    /**
     * @param funcion función declarada
     * @return su tipo de retorno; VOID si no lo anota o anota 'void'
     */
    private static Type tipoRetorno(NodoFuncion funcion) {
        return funcion.tipoRetorno() == null ? Type.VOID : tipoAnotado(funcion.tipoRetorno());
    }

    /**
     * Predicado conservador R de grammar.md 12.5: true si la sentencia termina
     * con return en todo camino. No evalúa condiciones, así que un while nunca cuenta.
     *
     * @param sentencia sentencia a analizar
     * @return true si siempre retorna
     */
    private static boolean siempreRetorna(Sentencia sentencia) {
        return switch (sentencia) {
            case NodoReturn ret -> true;
            case NodoBloque bloque -> bloque.sentencias().stream().anyMatch(SemanticAnalyzer::siempreRetorna);
            // Sin else, la rama falsa es un bloque vacío: nunca retorna
            case NodoIf nodoIf -> siempreRetorna(nodoIf.bloqueTrue()) && siempreRetorna(nodoIf.bloqueFalse());
            default -> false;
        };
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
            case NodoLlamada llamada -> llamada.nombre();
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

            case NodoLlamada llamada -> null;

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

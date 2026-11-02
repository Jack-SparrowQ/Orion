package com.compiler.util;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.compiler.parser.ast.*;
import com.compiler.semantic.Type;

public class JSGenerator {

    // Consume los escapes por pares para no confundir el "\0" de "\\0" (barra y un cero)
    private static final Pattern ESCAPE_O_CR = Pattern.compile("\\\\.|\r");

    // Ayudantes que necesitan los accesos a elementos y las concatenaciones. JS devolvería
    // undefined fuera de rango y "anull" al concatenar null, pero en Orion ambos deben detener
    // el programa; y .length y s[i] de JS cuentan unidades UTF-16, mientras for...of recorre
    // caracteres Unicode. Orion no admite '$' en un identificador: estos nombres no pueden
    // chocar con los del programa.
    private static final String AYUDANTES = """
            function $elementos(v) {
                return typeof v === "string" ? Array.from(v) : v;
            }
            function $indice(e, i) {
                if (!(i >= 0 && i < e.length)) {
                    throw new RangeError("Indice fuera de rango: " + i + " (longitud " + e.length + ")");
                }
                return i;
            }
            function $en(v, i) {
                const e = $elementos(v);
                return e[$indice(e, i)];
            }
            function $longitud(v) {
                return $elementos(v).length;
            }
            function $asignar(v, i, valor) {
                v[$indice(v, i)] = valor(v[i]);
            }
            function $concatenar(a, b) {
                if (a == null || b == null) {
                    throw new TypeError("No se puede concatenar una cadena null");
                }
                return a + b;
            }
            """;

    /** Tipo de cada expresión, calculado por el análisis semántico. */
    private final Map<Expresion, Type> tipos;

    /** Se activa al generar un uso de los ayudantes: los demás programas no los llevan. */
    private boolean usaAyudantes;

    /**
     * @param tipos tipo de cada expresión, por identidad de nodo
     *              (ver {@link com.compiler.semantic.SemanticAnalyzer#analyzeSemantic})
     */
    public JSGenerator(Map<Expresion, Type> tipos) {
        this.tipos = tipos;
    }

    public String generar(List<Sentencia> programa) {
        usaAyudantes = false;
        StringBuilder codigoJS = new StringBuilder();

        // Recorremos la lista de sentencias del programa
        for (Sentencia nodo : programa) {
            codigoJS.append(visitarNodo(nodo, 0));
        }

        return usaAyudantes ? AYUDANTES + codigoJS : codigoJS.toString();
    }

    // El método central que decide qué código JavaScript generar dependiendo del nodo
    private String visitarNodo(Sentencia nodo, int nivelIndentacion) {
        String indentacion = crearIndentacion(nivelIndentacion);

        return switch (nodo) {
            // 1. Traducir una Declaración (let x = 5;). JS no tiene tipos: la anotación se descarta.
            case NodoDeclaracion dec -> indentacion + dec.palabraClave().lexema + " "
                    + dec.identificador().lexema
                    + (dec.valor() == null ? "" : " = " + generarExpresion(dec.valor())) + ";\n";

            // Traducir una Asignación (x = 5;)
            case NodoAsignacion asig -> indentacion + asig.identificador().lexema
                    + " = " + generarExpresion(asig.valor()) + ";\n";

            // Traducir una asignación a un elemento. El valor va en una función para evaluarlo
            // después de comprobar el índice, y solo una vez aunque sea compuesta.
            case NodoAsignacionElemento asig -> {
                usaAyudantes = true;
                String valor = generarExpresion(asig.valor());
                String funcion;
                if (asig.operador() == null) {
                    funcion = "() => " + valor;
                } else if (tipos.get(asig.valor()) == Type.STRING) { // '+=' es el único compuesto de cadenas
                    funcion = "($v) => " + concatenacion("$v", valor);
                } else {
                    funcion = "($v) => $v " + asig.operador().lexema + " (" + valor + ")";
                }
                yield indentacion + "$asignar(" + generarExpresion(asig.destino().objeto()) + ", "
                        + generarExpresion(asig.destino().indice()) + ", " + funcion + ");\n";
            }

            // 2. Traducir una Impresión (print(x) -> console.log(x))
            case NodoImprimir imp -> indentacion + "console.log(" + argumentosDePrint(imp.argumentos()) + ");\n";

            // 3. Traducir un IF
            case NodoIf nIf -> {
                StringBuilder jsIf = new StringBuilder();
                jsIf.append(indentacion).append("if (").append(generarExpresion(nIf.condicion())).append(") {\n");

                // Las sentencias del bloque se visitan con un nivel más de indentación
                for (Sentencia sentenciaInterna : nIf.bloqueTrue().sentencias()) {
                    jsIf.append(visitarNodo(sentenciaInterna, nivelIndentacion + 1));
                }

                jsIf.append(indentacion).append("}");

                if (!nIf.bloqueFalse().sentencias().isEmpty()) {
                    jsIf.append(" else {\n");
                    for (Sentencia sentenciaInterna : nIf.bloqueFalse().sentencias()) {
                        jsIf.append(visitarNodo(sentenciaInterna, nivelIndentacion + 1));
                    }
                    jsIf.append(indentacion).append("}");
                }

                jsIf.append("\n");
                yield jsIf.toString();
            }

            // 4. Traducir un bloque independiente: JS también tiene bloques con ámbito propio
            case NodoBloque bloque -> {
                StringBuilder jsBloque = new StringBuilder(indentacion + "{\n");
                for (Sentencia sentenciaInterna : bloque.sentencias()) {
                    jsBloque.append(visitarNodo(sentenciaInterna, nivelIndentacion + 1));
                }
                yield jsBloque.append(indentacion).append("}\n").toString();
            }

            // 5. Traducir un WHILE: misma forma en JS
            case NodoWhile nWhile -> {
                StringBuilder jsWhile = new StringBuilder();
                jsWhile.append(indentacion).append("while (").append(generarExpresion(nWhile.condicion())).append(") {\n");
                for (Sentencia sentenciaInterna : nWhile.cuerpo().sentencias()) {
                    jsWhile.append(visitarNodo(sentenciaInterna, nivelIndentacion + 1));
                }
                yield jsWhile.append(indentacion).append("}\n").toString();
            }

            // Traducir un FOR: for...of evalúa la expresión una vez y recorre por carácter Unicode
            case NodoFor nFor -> {
                StringBuilder jsFor = new StringBuilder();
                jsFor.append(indentacion).append("for (const ").append(nFor.variable().lexema)
                        .append(" of ").append(generarExpresion(nFor.iterable())).append(") {\n");
                for (Sentencia sentenciaInterna : nFor.cuerpo().sentencias()) {
                    jsFor.append(visitarNodo(sentenciaInterna, nivelIndentacion + 1));
                }
                yield jsFor.append(indentacion).append("}\n").toString();
            }

            // 6. break y continue son iguales en JS
            case NodoSalto salto -> indentacion + salto.palabraClave().lexema + ";\n";

            // 7. Traducir una función: JS no tiene tipos, así que se descartan
            case NodoFuncion fn -> {
                StringBuilder jsFn = new StringBuilder(indentacion).append("function ")
                        .append(fn.nombre().lexema).append("(");
                for (int i = 0; i < fn.parametros().size(); i++) {
                    jsFn.append(i == 0 ? "" : ", ").append(fn.parametros().get(i).nombre().lexema);
                }
                jsFn.append(") {\n");
                for (Sentencia sentenciaInterna : fn.cuerpo().sentencias()) {
                    jsFn.append(visitarNodo(sentenciaInterna, nivelIndentacion + 1));
                }
                yield jsFn.append(indentacion).append("}\n").toString();
            }

            case NodoReturn ret -> indentacion + "return"
                    + (ret.valor() == null ? "" : " " + generarExpresion(ret.valor())) + ";\n";

            // Llamada como sentencia (f(x);)
            case NodoLlamada llamada -> indentacion + generarExpresion(llamada) + ";\n";
        };
    }

    // Método auxiliar para que el código JS generado se vea bonito
    private String crearIndentacion(int nivel) {
        return "    ".repeat(Math.max(0, nivel)); // 4 espacios por nivel
    }

    // Con más de un argumento, console.log toma el primero como formato: un texto con
    // "%d" se consumiría al siguiente argumento. "%s" hace que ese texto se escriba tal cual.
    private String argumentosDePrint(List<Expresion> argumentos) {
        String lista = String.join(", ", argumentos.stream().map(this::generarExpresion).toList());
        if (argumentos.size() < 2) {
            return lista;
        }
        Type primero = tipos.get(argumentos.get(0));
        return primero == Type.STRING || primero == Type.CHAR ? "\"%s\", " + lista : lista;
    }

    private String concatenacion(String izquierda, String derecha) {
        usaAyudantes = true;
        return "$concatenar(" + izquierda + ", " + derecha + ")";
    }

    // Un literal de Orion ya es válido en JS, salvo dos casos que aquí cambian de forma:
    // "\01" sería un escape octal (en Orion es NUL y '1') y un CR crudo cortaría la cadena.
    private static String literalJS(String lexema) {
        return ESCAPE_O_CR.matcher(lexema).replaceAll(m -> switch (m.group()) {
            case "\\0" -> "\\\\x00";
            case "\r" -> "\\\\r";
            default -> Matcher.quoteReplacement(m.group());
        });
    }

    // Traduce una expresión (recursivamente) a su texto en JavaScript.
    // La precedencia de Orion es la misma que la de JS, así que no se
    // reordena nada: solo se conservan los paréntesis que escribió el programador.
    private String generarExpresion(Expresion expr) {
        return switch (expr) {
            case NodoLiteral l -> literalJS(l.valor().lexema);

            case NodoVariable v -> v.nombre().lexema;

            // shortcut: '<' entre char compara unidades UTF-16, no códigos Unicode: difiere de
            // grammar.md solo entre emojis y U+E000-U+FFFF; compararlos con codePointAt si importa.
            case NodoBinario b -> {
                String izquierda = generarExpresion(b.izquierda());
                String derecha = generarExpresion(b.derecha());
                // El único '+' que da string es la concatenación
                yield tipos.get(b) == Type.STRING
                        ? concatenacion(izquierda, derecha)
                        : izquierda + " " + b.operador().lexema + " " + derecha;
            }

            case NodoUnario u -> {
                String operando = generarExpresion(u.operando());
                // Evita que "- -x" se convierta en "--x", que en JS es un decremento
                if (u.operador().lexema.equals("-") && operando.startsWith("-")) {
                    operando = " " + operando;
                }
                yield u.operador().lexema + operando;
            }

            case NodoAgrupacion g -> "(" + generarExpresion(g.expresion()) + ")";

            case NodoLlamada llamada -> llamada.nombre().lexema + "("
                    + String.join(", ", llamada.argumentos().stream().map(this::generarExpresion).toList()) + ")";

            case NodoArreglo arreglo -> "["
                    + String.join(", ", arreglo.elementos().stream().map(this::generarExpresion).toList()) + "]";

            case NodoIndice indice -> {
                usaAyudantes = true;
                yield "$en(" + generarExpresion(indice.objeto()) + ", " + generarExpresion(indice.indice()) + ")";
            }

            case NodoPropiedad propiedad -> {
                usaAyudantes = true;
                yield "$longitud(" + generarExpresion(propiedad.objeto()) + ")";
            }
        };
    }
}

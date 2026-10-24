package com.compiler.util;

import java.util.List;

import com.compiler.parser.ast.*;

public class JSGenerator {

    public String generar(List<Sentencia> programa) {
        StringBuilder codigoJS = new StringBuilder();

        // Recorremos la lista de sentencias del programa
        for (Sentencia nodo : programa) {
            codigoJS.append(visitarNodo(nodo, 0));
        }

        return codigoJS.toString();
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

            // 2. Traducir una Impresión (print(x) -> console.log(x))
            case NodoImprimir imp -> indentacion + "console.log(" + generarExpresion(imp.expresion()) + ");\n";

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

    // Traduce una expresión (recursivamente) a su texto en JavaScript.
    // La precedencia de Orion es la misma que la de JS, así que no se
    // reordena nada: solo se conservan los paréntesis que escribió el programador.
    private String generarExpresion(Expresion expr) {
        return switch (expr) {
            case NodoLiteral l -> l.valor().lexema;

            case NodoVariable v -> v.nombre().lexema;

            case NodoBinario b -> generarExpresion(b.izquierda()) + " " + b.operador().lexema
                    + " " + generarExpresion(b.derecha());

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
        };
    }
}

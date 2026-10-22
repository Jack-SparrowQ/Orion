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
            // 1. Traducir una Declaración (let x = 5;)
            case NodoDeclaracion dec -> indentacion + dec.palabraClave().lexema + " "
                    + dec.identificador().lexema + " = " + generarExpresion(dec.valor()) + ";\n";

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
        };
    }
}

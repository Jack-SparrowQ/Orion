package com.compiler.util;

import java.util.List;

import com.compiler.parser.*;
import com.compiler.parser.Nodes.NodoAgrupacion;
import com.compiler.parser.Nodes.NodoBinario;
import com.compiler.parser.Nodes.NodoDeclaracion;
import com.compiler.parser.Nodes.NodoExpresionSimple;
import com.compiler.parser.Nodes.NodoIf;
import com.compiler.parser.Nodes.NodoImprimir;
import com.compiler.parser.Nodes.NodoUnario;
import com.compiler.parser.Nodes.NodoAgrupacion;

public class JSGenerator {

    public String generar(List<ElementoAST> programa) {
        StringBuilder codigoJS = new StringBuilder();
        
        // Recorremos la lista de sentencias del programa
        for (ElementoAST nodo : programa) {
            codigoJS.append(visitarNodo(nodo, 0));
        }
        
        return codigoJS.toString();
    }

    // El método central que decide qué código JavaScript generar dependiendo del nodo
    private String visitarNodo(ElementoAST nodo, int nivelIndentacion) {
        String indentacion = crearIndentacion(nivelIndentacion);

        // 1. Traducir una Declaración (let x = 5;)
        if (nodo instanceof NodoDeclaracion) {
            NodoDeclaracion dec = (NodoDeclaracion) nodo;
            return indentacion + dec.palabraClave.lexema + " " + 
                   dec.identificador.lexema + " = " + dec.valorNumero.lexema + ";\n";
        }
        
        // 2. Traducir una Impresión (print(x) -> console.log(x))
        if (nodo instanceof NodoImprimir) {
            NodoImprimir imp = (NodoImprimir) nodo;
            String valor = generarExpresion(imp.expresion); // Por ahora es simple
            return indentacion + "console.log(" + valor + ");\n";
        }

        // 3. Traducir un IF
        if (nodo instanceof NodoIf) {
            NodoIf nIf = (NodoIf) nodo;
            String condicion = generarExpresion(nIf.condicion);
            
            StringBuilder jsIf = new StringBuilder();
            jsIf.append(indentacion).append("if (").append(condicion).append(") {\n");
            
            // Si el bloque del if tiene cosas adentro, las visitamos recursivamente
            // aumentándole un nivel a la indentación
            if (nIf.bloqueTrue != null) {
                for (ElementoAST sentenciaInterna : nIf.bloqueTrue.sentencias) {
                    jsIf.append(visitarNodo(sentenciaInterna, nivelIndentacion + 1));
                }
            }
            
            jsIf.append(indentacion).append("}\n");
            return jsIf.toString();
        }

        return "";
    }

    // Método auxiliar para que el código JS generado se vea bonito
    private String crearIndentacion(int nivel) {
        return "    ".repeat(Math.max(0, nivel)); // 4 espacios por nivel
    }

    // Traduce una expresión (recursivamente) a su texto en JavaScript.
    // La precedencia de Orion es la misma que la de JS, así que no se
    // reordena nada: solo se conservan los paréntesis que escribió el programador.
    private String generarExpresion(ElementoAST expr) {
        if (expr instanceof NodoExpresionSimple) {
            return ((NodoExpresionSimple) expr).valor.lexema;
        }

        if (expr instanceof NodoBinario) {
            NodoBinario b = (NodoBinario) expr;
            return generarExpresion(b.izquierda) + " " + b.operador.lexema
                    + " " + generarExpresion(b.derecha);
        }

        if (expr instanceof NodoUnario) {
            NodoUnario u = (NodoUnario) expr;
            String operando = generarExpresion(u.operando);
            // Evita que "- -x" se convierta en "--x", que en JS es un decremento
            if (u.operador.lexema.equals("-") && operando.startsWith("-")) {
                operando = " " + operando;
            }
            return u.operador.lexema + operando;
        }

        if (expr instanceof NodoAgrupacion) {
            return "(" + generarExpresion(((NodoAgrupacion) expr).expresion) + ")";
        }

        throw new IllegalArgumentException(
                "Expresión desconocida: " + expr.getClass().getSimpleName());
    }
}
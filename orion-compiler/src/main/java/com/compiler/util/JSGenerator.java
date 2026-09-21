package main.java.com.compiler.util;

import java.util.List;

import main.java.com.compiler.parser.*;

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
            String valor = ((NodoExpresionSimple) imp.expresion).valor.lexema; // Por ahora es simple
            return indentacion + "console.log(" + valor + ");\n";
        }

        // 3. Traducir un IF
        if (nodo instanceof NodoIf) {
            NodoIf nIf = (NodoIf) nodo;
            String condicion = ((NodoExpresionSimple) nIf.condicion).valor.lexema;
            
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
}
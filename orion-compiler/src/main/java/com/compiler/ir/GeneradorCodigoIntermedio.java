package com.compiler.codegen;

import java.util.ArrayList;
import java.util.List;

import com.compiler.parser.Nodes.*;
import com.compiler.parser.ElementoAST;

// Traduce el AST a código de tres direcciones (lista de cuádruplas).
public class GeneradorCodigoIntermedio {

    private final List<Cuadrupla> codigo = new ArrayList<>();
    private int contadorTemporales = 0;
    private int contadorEtiquetas = 0;

    public List<Cuadrupla> generar(List<ElementoAST> programa) {
        codigo.clear();
        contadorTemporales = 0;
        contadorEtiquetas = 0;

        for (ElementoAST nodo : programa) {
            generarSentencia(nodo);
        }
        return new ArrayList<>(codigo);
    }

    // Texto listo para mostrar o guardar (las etiquetas sin sangría)
    public static String aTexto(List<Cuadrupla> codigo) {
        StringBuilder sb = new StringBuilder();
        for (Cuadrupla c : codigo) {
            if (!c.op.equals("label")) {
                sb.append("    ");
            }
            sb.append(c).append("\n");
        }
        return sb.toString();
    }

    // ---------------- SENTENCIAS ----------------

    private void generarSentencia(ElementoAST nodo) {

        if (nodo instanceof NodoDeclaracion) {
            NodoDeclaracion dec = (NodoDeclaracion) nodo;
            String valor = generarExpresion(dec.valorNumero
            );
            emitir("=", valor, null, dec.identificador.lexema);
        }
        else if (nodo instanceof NodoImprimir) {
            String valor = generarExpresion(((NodoImprimir) nodo).expresion);
            emitir("print", valor, null, null);
        }
        else if (nodo instanceof NodoIf) {
            NodoIf nIf = (NodoIf) nodo;

            String condicion = generarExpresion(nIf.condicion);
            String etiquetaFin = nuevaEtiqueta();

            emitir("ifFalse", condicion, null, etiquetaFin);
            for (ElementoAST interna : nIf.bloqueTrue.sentencias) {
                generarSentencia(interna);
            }
            emitir("label", null, null, etiquetaFin);
        }
        else {
            throw new IllegalArgumentException(
                    "Sentencia desconocida: " + nodo.getClass().getSimpleName());
        }
    }

    // ---------------- EXPRESIONES ----------------

    // Genera las cuádruplas de la expresión y devuelve dónde queda su valor:
    // un literal, una variable o un temporal (t1, t2, ...).
    private String generarExpresion(ElementoAST expr) {

        if (expr instanceof NodoExpresionSimple) {
            return ((NodoExpresionSimple) expr).valor.lexema;
        }

        if (expr instanceof NodoBinario) {
            NodoBinario b = (NodoBinario) expr;
            String izquierda = generarExpresion(b.izquierda);
            String derecha = generarExpresion(b.derecha);
            String temporal = nuevoTemporal(); // después de los hijos
            emitir(b.operador.lexema, izquierda, derecha, temporal);
            return temporal;
        }

        if (expr instanceof NodoUnario) {
            NodoUnario u = (NodoUnario) expr;
            String operando = generarExpresion(u.operando);
            String temporal = nuevoTemporal();
            emitir(u.operador.lexema, operando, null, temporal);
            return temporal;
        }

        if (expr instanceof NodoAgrupacion) {
            // Los paréntesis ya quedaron reflejados en la estructura del árbol
            return generarExpresion(((NodoAgrupacion) expr).expresion);
        }

        throw new IllegalArgumentException(
                "Expresión desconocida: " + expr.getClass().getSimpleName());
    }

    // ---------------- AUXILIARES ----------------

    private void emitir(String op, String arg1, String arg2, String resultado) {
        codigo.add(new Cuadrupla(op, arg1, arg2, resultado));
    }

    private String nuevoTemporal() {
        return "t" + (++contadorTemporales);
    }

    private String nuevaEtiqueta() {
        return "L" + (++contadorEtiquetas);
    }
}

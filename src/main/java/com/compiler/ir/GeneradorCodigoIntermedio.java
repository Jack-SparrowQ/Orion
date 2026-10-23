package com.compiler.ir;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.compiler.lexer.TokenType;
import com.compiler.parser.ast.*;

// Traduce el AST a código de tres direcciones (lista de cuádruplas).
public class GeneradorCodigoIntermedio {

    private final List<Cuadrupla> codigo = new ArrayList<>();
    private int contadorTemporales = 0;
    private int contadorEtiquetas = 0;

    /**
     * Etiquetas de los ciclos abiertos, el más interno arriba:
     * [0] = inicio (destino de continue), [1] = fin (destino de break).
     */
    private final Deque<String[]> ciclos = new ArrayDeque<>();

    public List<Cuadrupla> generar(List<Sentencia> programa) {
        codigo.clear();
        ciclos.clear();
        contadorTemporales = 0;
        contadorEtiquetas = 0;

        for (Sentencia nodo : programa) {
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

    private void generarSentencia(Sentencia nodo) {

        switch (nodo) {
            case NodoDeclaracion dec -> {
                // "let x: T;" no produce código: solo reserva el nombre
                if (dec.valor() != null) {
                    String valor = generarExpresion(dec.valor());
                    emitir("=", valor, null, dec.identificador().lexema);
                }
            }
            case NodoAsignacion asig -> {
                String valor = generarExpresion(asig.valor());
                emitir("=", valor, null, asig.identificador().lexema);
            }
            case NodoImprimir imp -> {
                String valor = generarExpresion(imp.expresion());
                emitir("print", valor, null, null);
            }
            case NodoIf nIf -> {
                String condicion = generarExpresion(nIf.condicion());
                List<Sentencia> sino = nIf.bloqueFalse().sentencias();

                if (sino.isEmpty()) {
                    //     ifFalse c goto Lfin ; then ; Lfin:
                    String etiquetaFin = nuevaEtiqueta();
                    emitir("ifFalse", condicion, null, etiquetaFin);
                    nIf.bloqueTrue().sentencias().forEach(this::generarSentencia);
                    emitir("label", null, null, etiquetaFin);
                } else {
                    //     ifFalse c goto Lelse ; then ; goto Lfin ; Lelse: ; else ; Lfin:
                    String etiquetaElse = nuevaEtiqueta();
                    String etiquetaFin = nuevaEtiqueta();
                    emitir("ifFalse", condicion, null, etiquetaElse);
                    nIf.bloqueTrue().sentencias().forEach(this::generarSentencia);
                    emitir("goto", null, null, etiquetaFin);
                    emitir("label", null, null, etiquetaElse);
                    sino.forEach(this::generarSentencia);
                    emitir("label", null, null, etiquetaFin);
                }
            }
            case NodoWhile nWhile -> {
                //     Linicio: ; ifFalse c goto Lfin ; cuerpo ; goto Linicio ; Lfin:
                // La condición se genera después de Linicio: se reevalúa en cada vuelta
                String etiquetaInicio = nuevaEtiqueta();
                String etiquetaFin = nuevaEtiqueta();
                emitir("label", null, null, etiquetaInicio);
                String condicion = generarExpresion(nWhile.condicion());
                emitir("ifFalse", condicion, null, etiquetaFin);

                ciclos.push(new String[] { etiquetaInicio, etiquetaFin });
                nWhile.cuerpo().sentencias().forEach(this::generarSentencia);
                ciclos.pop();

                emitir("goto", null, null, etiquetaInicio);
                emitir("label", null, null, etiquetaFin);
            }
            // break salta al fin del ciclo más interno; continue, a su inicio.
            // El análisis semántico ya garantizó que hay un ciclo abierto (E313).
            case NodoSalto salto -> emitir("goto", null, null,
                    ciclos.peek()[salto.palabraClave().tipo == TokenType.BREAK ? 1 : 0]);
            // El código de tres direcciones no tiene ámbitos: el bloque se aplana
            case NodoBloque bloque -> bloque.sentencias().forEach(this::generarSentencia);
        }
    }

    // ---------------- EXPRESIONES ----------------

    // Genera las cuádruplas de la expresión y devuelve dónde queda su valor:
    // un literal, una variable o un temporal (t1, t2, ...).
    private String generarExpresion(Expresion expr) {

        return switch (expr) {
            case NodoLiteral l -> l.valor().lexema;

            case NodoVariable v -> v.nombre().lexema;

            case NodoBinario b -> {
                String izquierda = generarExpresion(b.izquierda());
                String derecha = generarExpresion(b.derecha());
                String temporal = nuevoTemporal(); // después de los hijos
                emitir(b.operador().lexema, izquierda, derecha, temporal);
                yield temporal;
            }

            case NodoUnario u -> {
                String operando = generarExpresion(u.operando());
                String temporal = nuevoTemporal();
                emitir(u.operador().lexema, operando, null, temporal);
                yield temporal;
            }

            // Los paréntesis ya quedaron reflejados en la estructura del árbol
            case NodoAgrupacion g -> generarExpresion(g.expresion());
        };
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

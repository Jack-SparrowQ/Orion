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
     * [0] = destino de continue, [1] = fin (destino de break).
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

    // Texto listo para mostrar o guardar (etiquetas y delimitadores de función sin sangría)
    public static String aTexto(List<Cuadrupla> codigo) {
        StringBuilder sb = new StringBuilder();
        for (Cuadrupla c : codigo) {
            if (!c.op.equals("label") && !c.op.equals("func") && !c.op.equals("endfunc")) {
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
            case NodoAsignacionElemento asig -> {
                // Primero el destino: sus subíndices se evalúan una vez y antes que el valor
                String objeto = generarExpresion(asig.destino().objeto());
                String indice = generarExpresion(asig.destino().indice());
                if (asig.operador() == null) {
                    emitir("[]=", objeto, indice, generarExpresion(asig.valor()));
                } else {
                    String anterior = nuevoTemporal();
                    emitir("[]", objeto, indice, anterior);
                    String derecha = generarExpresion(asig.valor());
                    String resultado = nuevoTemporal();
                    emitir(asig.operador().lexema, anterior, derecha, resultado);
                    emitir("[]=", objeto, indice, resultado);
                }
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
            case NodoFor nFor -> {
                //     e = iterable ; i = 0 ; n = len e
                //     Linicio: ; c = i < n ; ifFalse c goto Lfin ; x = e[i] ; cuerpo
                //     Lsiguiente: ; i = i + 1 ; goto Linicio ; Lfin:
                // La copia en un temporal evalúa el iterable una sola vez: el cuerpo puede reasignar la variable
                String iterable = nuevoTemporal();
                emitir("=", generarExpresion(nFor.iterable()), null, iterable);
                String indice = nuevoTemporal();
                emitir("=", "0", null, indice);
                String longitud = nuevoTemporal();
                emitir("len", iterable, null, longitud);

                String etiquetaInicio = nuevaEtiqueta();
                String etiquetaSiguiente = nuevaEtiqueta();
                String etiquetaFin = nuevaEtiqueta();
                emitir("label", null, null, etiquetaInicio);
                String condicion = nuevoTemporal();
                emitir("<", indice, longitud, condicion);
                emitir("ifFalse", condicion, null, etiquetaFin);
                emitir("[]", iterable, indice, nFor.variable().lexema);

                // continue debe pasar por el incremento: saltar a Linicio repetiría el elemento
                ciclos.push(new String[] { etiquetaSiguiente, etiquetaFin });
                nFor.cuerpo().sentencias().forEach(this::generarSentencia);
                ciclos.pop();

                emitir("label", null, null, etiquetaSiguiente);
                emitir("+", indice, "1", indice);
                emitir("goto", null, null, etiquetaInicio);
                emitir("label", null, null, etiquetaFin);
            }
            // break salta al fin del ciclo más interno; continue, a su siguiente iteración.
            // El análisis semántico ya garantizó que hay un ciclo abierto (E313).
            case NodoSalto salto -> emitir("goto", null, null,
                    ciclos.peek()[salto.palabraClave().tipo == TokenType.BREAK ? 1 : 0]);
            // El código de tres direcciones no tiene ámbitos: el bloque se aplana
            case NodoBloque bloque -> bloque.sentencias().forEach(this::generarSentencia);
            //     func f, n ; cuerpo ; return ; endfunc
            // Se emite en su lugar: func/endfunc delimitan una definición, no código
            // que se ejecute al pasar por ahí. Los parámetros se usan por su nombre.
            case NodoFuncion fn -> {
                emitir("func", fn.nombre().lexema, String.valueOf(fn.parametros().size()), null);
                fn.cuerpo().sentencias().forEach(this::generarSentencia);
                // Retorno implícito al final (las void pueden terminar sin return)
                if (!codigo.get(codigo.size() - 1).op.equals("return")) {
                    emitir("return", null, null, null);
                }
                emitir("endfunc", null, null, null);
            }
            case NodoReturn ret -> emitir("return",
                    ret.valor() == null ? null : generarExpresion(ret.valor()), null, null);
            // Llamada como sentencia: el valor de retorno se descarta
            case NodoLlamada llamada -> generarLlamada(llamada, false);
        }
    }

    /**
     * Emite param por cada argumento (ya evaluados, en orden) y luego el call.
     *
     * @param llamada   la llamada
     * @param usarValor true si el valor de retorno se usa (se guarda en un temporal)
     * @return el temporal con el valor de retorno, o null si se descarta
     */
    private String generarLlamada(NodoLlamada llamada, boolean usarValor) {
        // Se evalúan todos antes del primer param: un argumento puede contener otra llamada
        List<String> argumentos = llamada.argumentos().stream().map(this::generarExpresion).toList();
        argumentos.forEach(a -> emitir("param", a, null, null));
        String temporal = usarValor ? nuevoTemporal() : null; // después de los hijos
        emitir("call", llamada.nombre().lexema, String.valueOf(argumentos.size()), temporal);
        return temporal;
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

            case NodoLlamada llamada -> generarLlamada(llamada, true);

            case NodoArreglo arreglo -> {
                // Se evalúan todos antes de crearlo: un elemento puede ser otro arreglo
                List<String> elementos = arreglo.elementos().stream().map(this::generarExpresion).toList();
                String temporal = nuevoTemporal();
                emitir("array", String.valueOf(elementos.size()), null, temporal);
                for (int i = 0; i < elementos.size(); i++) {
                    emitir("[]=", temporal, String.valueOf(i), elementos.get(i));
                }
                yield temporal;
            }

            case NodoIndice i -> {
                String objeto = generarExpresion(i.objeto());
                String indice = generarExpresion(i.indice());
                String temporal = nuevoTemporal();
                emitir("[]", objeto, indice, temporal);
                yield temporal;
            }

            case NodoPropiedad p -> {
                String objeto = generarExpresion(p.objeto());
                String temporal = nuevoTemporal();
                emitir("len", objeto, null, temporal);
                yield temporal;
            }
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

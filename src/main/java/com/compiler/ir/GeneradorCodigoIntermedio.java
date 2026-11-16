package com.compiler.ir;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.compiler.lexer.TokenType;
import com.compiler.parser.ast.*;
import com.compiler.semantic.SemanticAnalyzer;
import com.compiler.semantic.Type;

// Traduce el AST a funciones IR y codigo principal con cuerpos de cuadruplas.
public class GeneradorCodigoIntermedio {

    private List<Cuadrupla> codigo = new ArrayList<>();
    private final List<FuncionIR> funciones = new ArrayList<>();
    private int contadorTemporales = 0;
    private int contadorEtiquetas = 0;
    private final Deque<Map<String, String>> ambitos = new ArrayDeque<>();
    private final Map<String, Integer> declaraciones = new HashMap<>();
    private final Map<String, Type> tiposIR = new HashMap<>();
    private Map<Expresion, Type> tiposExpresiones;

    /**
     * Etiquetas de los ciclos abiertos, el más interno arriba:
     * [0] = destino de continue, [1] = fin (destino de break).
     */
    private final Deque<String[]> ciclos = new ArrayDeque<>();

    public ProgramaIR generar(List<Sentencia> programa, Map<Expresion, Type> tipos) {
        codigo = new ArrayList<>();
        funciones.clear();
        ciclos.clear();
        ambitos.clear();
        declaraciones.clear();
        tiposIR.clear();
        tiposExpresiones = tipos;
        ambitos.push(new HashMap<>());
        contadorTemporales = 0;
        contadorEtiquetas = 0;

        for (Sentencia nodo : programa) {
            generarSentencia(nodo);
        }
        return new ProgramaIR(funciones, codigo, tiposIR);
    }

    // Texto listo para mostrar o guardar (etiquetas y delimitadores de función sin sangría)
    public static String aTexto(ProgramaIR programa) {
        StringBuilder sb = new StringBuilder();
        for (FuncionIR funcion : programa.funciones()) {
            sb.append("func ").append(funcion.nombre()).append(", ").append(funcion.parametros().size());
            if (!funcion.parametros().isEmpty()) {
                sb.append(", [").append(String.join(", ", funcion.parametros())).append("]");
            }
            sb.append("\n");
            agregarTexto(sb, funcion.cuerpo());
            sb.append("endfunc\n");
        }
        agregarTexto(sb, programa.principal());
        return sb.toString();
    }

    private static void agregarTexto(StringBuilder sb, List<Cuadrupla> codigo) {
        for (Cuadrupla c : codigo) {
            if (!c.op.equals("label")) {
                sb.append("    ");
            }
            sb.append(c).append("\n");
        }
    }

    // ---------------- SENTENCIAS ----------------

    private void generarSentencia(Sentencia nodo) {

        switch (nodo) {
            case NodoDeclaracion dec -> {
                // "let x: T;" no produce código: solo reserva el nombre
                Type tipo = dec.tipo() != null ? SemanticAnalyzer.tipoAnotado(dec.tipo()) : tipoExpresion(dec.valor());
                String nombre = declarar(dec.identificador().lexema, tipo);
                if (dec.valor() != null) {
                    String valor = generarExpresion(dec.valor());
                    emitir("=", valor, null, nombre);
                }
            }
            case NodoAsignacion asig -> {
                String valor = generarExpresion(asig.valor());
                emitir("=", valor, null, resolver(asig.identificador().lexema));
            }
            case NodoAsignacionElemento asig -> {
                // Primero el destino: sus subíndices se evalúan una vez y antes que el valor
                String objeto = generarExpresion(asig.destino().objeto());
                String indice = generarExpresion(asig.destino().indice());
                if (asig.operador() == null) {
                    emitir("[]=", objeto, indice, generarExpresion(asig.valor()));
                } else {
                    Type tipoElemento = tipoExpresion(asig.destino().objeto()).elemento();
                    String anterior = nuevoTemporal(tipoElemento);
                    emitir("[]", objeto, indice, anterior);
                    String derecha = generarExpresion(asig.valor());
                    String resultado = nuevoTemporal(tipoElemento);
                    emitir(operacion(asig.operador().tipo, asig.operador().lexema, tipoElemento), anterior, derecha, resultado);
                    emitir("[]=", objeto, indice, resultado);
                }
            }
            case NodoImprimir imp -> {
                // Se evalúan todos antes del primer param: un argumento puede contener una llamada
                List<String> valores = imp.argumentos().stream().map(this::generarExpresion).toList();
                if (valores.size() == 1) {
                    emitir("print", valores.get(0), null, null);
                } else {
                    valores.forEach(v -> emitir("param", v, null, null));
                    emitir("printn", String.valueOf(valores.size()), null, null);
                }
            }
            case NodoIf nIf -> {
                String condicion = generarExpresion(nIf.condicion());
                List<Sentencia> sino = nIf.bloqueFalse().sentencias();

                if (sino.isEmpty()) {
                    //     ifFalse c goto Lfin ; then ; Lfin:
                    String etiquetaFin = nuevaEtiqueta();
                    emitir("ifFalse", condicion, null, etiquetaFin);
                    generarBloque(nIf.bloqueTrue());
                    emitir("label", null, null, etiquetaFin);
                } else {
                    //     ifFalse c goto Lelse ; then ; goto Lfin ; Lelse: ; else ; Lfin:
                    String etiquetaElse = nuevaEtiqueta();
                    String etiquetaFin = nuevaEtiqueta();
                    emitir("ifFalse", condicion, null, etiquetaElse);
                    generarBloque(nIf.bloqueTrue());
                    emitir("goto", null, null, etiquetaFin);
                    emitir("label", null, null, etiquetaElse);
                    generarBloque(nIf.bloqueFalse());
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
                generarBloque(nWhile.cuerpo());
                ciclos.pop();

                emitir("goto", null, null, etiquetaInicio);
                emitir("label", null, null, etiquetaFin);
            }
            case NodoFor nFor -> {
                //     e = iterable ; i = 0 ; n = len e
                //     Linicio: ; c = i < n ; ifFalse c goto Lfin ; x = e[i] ; cuerpo
                //     Lsiguiente: ; i = i + 1 ; goto Linicio ; Lfin:
                // La copia en un temporal evalúa el iterable una sola vez: el cuerpo puede reasignar la variable
                Type tipoIterable = tipoExpresion(nFor.iterable());
                String iterable = nuevoTemporal(tipoIterable);
                emitir("=", generarExpresion(nFor.iterable()), null, iterable);
                String indice = nuevoTemporal(Type.INT);
                emitir("=", "0", null, indice);
                String longitud = nuevoTemporal(Type.INT);
                emitir("len", iterable, null, longitud);

                String etiquetaInicio = nuevaEtiqueta();
                String etiquetaSiguiente = nuevaEtiqueta();
                String etiquetaFin = nuevaEtiqueta();
                emitir("label", null, null, etiquetaInicio);
                String condicion = nuevoTemporal(Type.BOOL);
                emitir("<", indice, longitud, condicion);
                emitir("ifFalse", condicion, null, etiquetaFin);
                ambitos.push(new HashMap<>());
                Type tipoElemento = tipoIterable == Type.STRING ? Type.CHAR : tipoIterable.elemento();
                emitir("[]", iterable, indice, declarar(nFor.variable().lexema, tipoElemento));

                // continue debe pasar por el incremento: saltar a Linicio repetiría el elemento
                ciclos.push(new String[] { etiquetaSiguiente, etiquetaFin });
                nFor.cuerpo().sentencias().forEach(this::generarSentencia);
                ambitos.pop();
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
            // El bloque se aplana, pero sus declaraciones conservan nombres IR distintos.
            case NodoBloque bloque -> generarBloque(bloque);
            // Se recorre en su lugar para conservar visibilidad y numeracion global.
            // El cuerpo se guarda aparte; parametros y cuerpo comparten ambito.
            case NodoFuncion fn -> {
                ambitos.push(new HashMap<>());
                List<String> parametros = fn.parametros().stream()
                        .map(p -> declarar(p.nombre().lexema, SemanticAnalyzer.tipoAnotado(p.tipo()))).toList();
                List<Cuadrupla> principal = codigo;
                codigo = new ArrayList<>();
                try {
                    fn.cuerpo().sentencias().forEach(this::generarSentencia);
                    // Retorno implicito al final, tambien para una funcion vacia.
                    if (codigo.isEmpty() || !codigo.get(codigo.size() - 1).op.equals("return")) {
                        emitir("return", null, null, null);
                    }
                    funciones.add(new FuncionIR(fn.nombre().lexema, parametros, codigo));
                } finally {
                    codigo = principal;
                    ambitos.pop();
                }
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
        String temporal = usarValor ? nuevoTemporal(tipoExpresion(llamada)) : null; // después de los hijos
        emitir("call", llamada.nombre().lexema, String.valueOf(argumentos.size()), temporal);
        return temporal;
    }

    // ---------------- EXPRESIONES ----------------

    // Genera las cuádruplas de la expresión y devuelve dónde queda su valor:
    // un literal, una variable o un temporal ($t1, $t2, ...).
    private String generarExpresion(Expresion expr) {

        return switch (expr) {
            case NodoLiteral l -> l.valor().lexema;

            case NodoVariable v -> resolver(v.nombre().lexema);

            case NodoBinario b -> {
                String izquierda = generarExpresion(b.izquierda());
                if (b.operador().tipo == TokenType.AND_LOGICO || b.operador().tipo == TokenType.OR_LOGICO) {
                    String temporal = nuevoTemporal(Type.BOOL);
                    emitir("=", izquierda, null, temporal);
                    String etiquetaFin;
                    if (b.operador().tipo == TokenType.AND_LOGICO) {
                        etiquetaFin = nuevaEtiqueta();
                        emitir("ifFalse", temporal, null, etiquetaFin);
                    } else {
                        String etiquetaDerecha = nuevaEtiqueta();
                        etiquetaFin = nuevaEtiqueta();
                        emitir("ifFalse", temporal, null, etiquetaDerecha);
                        emitir("goto", null, null, etiquetaFin);
                        emitir("label", null, null, etiquetaDerecha);
                    }
                    // El salto debe preceder a todo el codigo del operando derecho.
                    String derecha = generarExpresion(b.derecha());
                    emitir("=", derecha, null, temporal);
                    emitir("label", null, null, etiquetaFin);
                    yield temporal;
                }
                String derecha = generarExpresion(b.derecha());
                Type tipo = tipoExpresion(b);
                String temporal = nuevoTemporal(tipo); // después de los hijos
                emitir(operacion(b.operador().tipo, b.operador().lexema, tipo), izquierda, derecha, temporal);
                yield temporal;
            }

            case NodoUnario u -> {
                String operando = generarExpresion(u.operando());
                String temporal = nuevoTemporal(tipoExpresion(u));
                emitir(u.operador().lexema, operando, null, temporal);
                yield temporal;
            }

            // Los paréntesis ya quedaron reflejados en la estructura del árbol
            case NodoAgrupacion g -> generarExpresion(g.expresion());

            case NodoLlamada llamada -> generarLlamada(llamada, true);

            case NodoArreglo arreglo -> {
                // Se evalúan todos antes de crearlo: un elemento puede ser otro arreglo
                List<String> elementos = arreglo.elementos().stream().map(this::generarExpresion).toList();
                String temporal = nuevoTemporal(tipoExpresion(arreglo));
                emitir("array", String.valueOf(elementos.size()), null, temporal);
                for (int i = 0; i < elementos.size(); i++) {
                    emitir("[]=", temporal, String.valueOf(i), elementos.get(i));
                }
                yield temporal;
            }

            case NodoIndice i -> {
                String objeto = generarExpresion(i.objeto());
                String indice = generarExpresion(i.indice());
                String temporal = nuevoTemporal(tipoExpresion(i));
                emitir("[]", objeto, indice, temporal);
                yield temporal;
            }

            case NodoPropiedad p -> {
                String objeto = generarExpresion(p.objeto());
                String temporal = nuevoTemporal(tipoExpresion(p));
                emitir("len", objeto, null, temporal);
                yield temporal;
            }
        };
    }

    // ---------------- AUXILIARES ----------------

    private void generarBloque(NodoBloque bloque) {
        ambitos.push(new HashMap<>());
        bloque.sentencias().forEach(this::generarSentencia);
        ambitos.pop();
    }

    private String declarar(String nombre, Type tipo) {
        int numero = declaraciones.getOrDefault(nombre, 0);
        declaraciones.put(nombre, numero + 1);
        String nombreIR = numero == 0 ? nombre : nombre + "$" + numero;
        ambitos.peek().put(nombre, nombreIR);
        tiposIR.put(nombreIR, Objects.requireNonNull(tipo, "Tipo ausente para " + nombreIR));
        return nombreIR;
    }

    private String resolver(String nombre) {
        for (Map<String, String> ambito : ambitos) {
            String nombreIR = ambito.get(nombre);
            if (nombreIR != null) return nombreIR;
        }
        // Las pruebas aisladas de IR pueden omitir declaraciones; semántica las exige en la CLI.
        return nombre;
    }

    private void emitir(String op, String arg1, String arg2, String resultado) {
        codigo.add(new Cuadrupla(op, arg1, arg2, resultado));
    }

    private Type tipoExpresion(Expresion expresion) {
        return Objects.requireNonNull(tiposExpresiones.get(expresion), "Tipo ausente para " + expresion);
    }

    private String operacion(TokenType operador, String lexema, Type tipo) {
        return operador == TokenType.SUMA && tipo == Type.STRING ? "concat" : lexema;
    }

    private String nuevoTemporal(Type tipo) {
        String nombre = "$t" + (++contadorTemporales);
        tiposIR.put(nombre, Objects.requireNonNull(tipo, "Tipo ausente para " + nombre));
        return nombre;
    }

    private String nuevaEtiqueta() {
        return "$L" + (++contadorEtiquetas);
    }
}

package com.compiler.util;

import java.util.ArrayList;
import java.util.List;

import com.compiler.parser.ast.*;

/**
 * Muestra el AST como un árbol, con ramas +-- / `-- y colores por clase de nodo:
 * <pre>
 * Programa
 * `-- While
 *     +-- Condicion
 *     |   `-- Binario &lt;
 *     |       +-- Variable i
 *     |       `-- Literal 3
 *     `-- Cuerpo
 *         `-- Imprimir
 *             `-- Variable i
 * </pre>
 * Las ramas son ASCII a propósito: los caracteres de caja (├ └ │) se ven
 * como "ÔööÔöÇÔöÇ" en consolas de Windows con la página de códigos 850.
 * Las expresiones también se despliegan como subárboles, así se ve la
 * precedencia y la asociatividad que decidió el parser.
 */
public class ASTPrinter {

    /** Sin colores si la variable de entorno NO_COLOR está definida (https://no-color.org). */
    private static final boolean COLORES = System.getenv("NO_COLOR") == null;

    // Un color por clase de nodo
    private static final String SENTENCIA = ConsoleColors.CYAN;
    private static final String SECCION = ConsoleColors.GRAY;
    private static final String OPERADOR = ConsoleColors.YELLOW;
    private static final String LITERAL = ConsoleColors.GREEN;
    private static final String NOMBRE = ConsoleColors.MAGENTA;

    /**
     * Nodo de la vista: lo que se imprime en una línea y sus hijos.
     *
     * @param tipo    clase de nodo (Declaracion, Binario...)
     * @param color   color del tipo
     * @param detalle texto que sigue al tipo (nombre, operador, valor), puede ser ""
     * @param hijos   subárboles, en orden
     */
    private record Arbol(String tipo, String color, String detalle, List<Arbol> hijos) {

        Arbol(String tipo, String color, String detalle, Arbol... hijos) {
            this(tipo, color, detalle, List.of(hijos));
        }
    }

    /**
     * Imprime el programa completo en la salida estándar.
     *
     * @param nodos   sentencias del programa
     * @param prefijo texto antepuesto a cada línea (sangría externa, normalmente "")
     */
    public static void printAST(List<Sentencia> nodos, String prefijo) {
        String raiz = pintar(nodos.isEmpty() ? "Programa (vacio)" : "Programa", ConsoleColors.BLUE);
        System.out.println(prefijo + raiz);
        imprimirHijos(sentencias(nodos), prefijo);
    }

    // ---------------- IMPRESIÓN ----------------

    /**
     * Imprime cada hijo con su rama: `-- el último, +-- los demás. Debajo de un
     * hijo que no es el último la línea vertical | continua hasta su hermano.
     *
     * @param hijos   nodos a imprimir
     * @param prefijo ramas heredadas de los ancestros
     */
    private static void imprimirHijos(List<Arbol> hijos, String prefijo) {
        for (int i = 0; i < hijos.size(); i++) {
            boolean ultimo = i == hijos.size() - 1;
            Arbol hijo = hijos.get(i);

            String etiqueta = pintar(hijo.tipo(), hijo.color())
                    + (hijo.detalle().isEmpty() ? "" : " " + hijo.detalle());
            System.out.println(prefijo + pintar(ultimo ? "`-- " : "+-- ", SECCION) + etiqueta);

            imprimirHijos(hijo.hijos(), prefijo + pintar(ultimo ? "    " : "|   ", SECCION));
        }
    }

    private static String pintar(String texto, String color) {
        return COLORES ? color + texto + ConsoleColors.RESET : texto;
    }

    // ---------------- SENTENCIAS ----------------

    private static List<Arbol> sentencias(List<Sentencia> nodos) {
        List<Arbol> arboles = new ArrayList<>();
        for (Sentencia nodo : nodos) {
            arboles.add(sentencia(nodo));
        }
        return arboles;
    }

    /** Agrupa sentencias bajo un rótulo (Cuerpo, Entonces, Sino...). */
    private static Arbol seccion(String nombre, List<Sentencia> nodos) {
        return new Arbol(nombre, SECCION, nodos.isEmpty() ? "(vacio)" : "", sentencias(nodos));
    }

    private static Arbol sentencia(Sentencia nodo) {
        return switch (nodo) {

            case NodoDeclaracion d -> {
                String detalle = d.palabraClave().lexema + " " + pintar(d.identificador().lexema, NOMBRE)
                        + (d.tipo() == null ? "" : ": " + d.tipo().lexema);
                yield d.valor() == null
                        ? new Arbol("Declaracion", SENTENCIA, detalle)
                        : new Arbol("Declaracion", SENTENCIA, detalle, expresion(d.valor()));
            }

            case NodoAsignacion a -> new Arbol("Asignacion", SENTENCIA,
                    pintar(a.identificador().lexema, NOMBRE), expresion(a.valor()));

            case NodoImprimir imp -> new Arbol("Imprimir", SENTENCIA, "", expresion(imp.expresion()));

            case NodoIf nIf -> {
                List<Arbol> hijos = new ArrayList<>();
                hijos.add(new Arbol("Condicion", SECCION, "", expresion(nIf.condicion())));
                hijos.add(seccion("Entonces", nIf.bloqueTrue().sentencias()));
                if (!nIf.bloqueFalse().sentencias().isEmpty()) {
                    hijos.add(seccion("Sino", nIf.bloqueFalse().sentencias()));
                }
                yield new Arbol("If", SENTENCIA, "", hijos);
            }

            case NodoWhile w -> new Arbol("While", SENTENCIA, "",
                    new Arbol("Condicion", SECCION, "", expresion(w.condicion())),
                    seccion("Cuerpo", w.cuerpo().sentencias()));

            case NodoSalto s -> new Arbol(capitalizar(s.palabraClave().lexema), SENTENCIA, "");

            case NodoBloque b -> new Arbol("Bloque", SENTENCIA, b.sentencias().isEmpty() ? "(vacio)" : "",
                    sentencias(b.sentencias()));

            case NodoFuncion f -> {
                List<String> parametros = new ArrayList<>();
                for (NodoParametro p : f.parametros()) {
                    parametros.add(pintar(p.nombre().lexema, NOMBRE) + ": " + p.tipo().lexema);
                }
                String firma = pintar(f.nombre().lexema, NOMBRE) + "(" + String.join(", ", parametros) + ")"
                        + ": " + (f.tipoRetorno() == null ? "void" : f.tipoRetorno().lexema);
                yield new Arbol("Funcion", SENTENCIA, firma, sentencias(f.cuerpo().sentencias()));
            }

            case NodoReturn r -> r.valor() == null
                    ? new Arbol("Return", SENTENCIA, "")
                    : new Arbol("Return", SENTENCIA, "", expresion(r.valor()));

            case NodoLlamada l -> llamada(l);
        };
    }

    // ---------------- EXPRESIONES ----------------

    private static Arbol expresion(Expresion expresion) {
        return switch (expresion) {
            case NodoLiteral l -> new Arbol("Literal", LITERAL, l.valor().lexema);
            case NodoVariable v -> new Arbol("Variable", NOMBRE, v.nombre().lexema);
            case NodoBinario b -> new Arbol("Binario", OPERADOR, b.operador().lexema,
                    expresion(b.izquierda()), expresion(b.derecha()));
            case NodoUnario u -> new Arbol("Unario", OPERADOR, u.operador().lexema, expresion(u.operando()));
            case NodoAgrupacion g -> new Arbol("Agrupacion", OPERADOR, "( )", expresion(g.expresion()));
            case NodoLlamada l -> llamada(l);
        };
    }

    /** Llamada: los argumentos son sus hijos, en orden. */
    private static Arbol llamada(NodoLlamada llamada) {
        List<Arbol> argumentos = new ArrayList<>();
        for (Expresion argumento : llamada.argumentos()) {
            argumentos.add(expresion(argumento));
        }
        String detalle = pintar(llamada.nombre().lexema, NOMBRE) + (argumentos.isEmpty() ? "()" : "");
        return new Arbol("Llamada", SENTENCIA, detalle, argumentos);
    }

    private static String capitalizar(String palabra) {
        return Character.toUpperCase(palabra.charAt(0)) + palabra.substring(1);
    }
}

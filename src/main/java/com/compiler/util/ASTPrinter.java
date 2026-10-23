package com.compiler.util;

import java.util.List;

import com.compiler.parser.ast.NodoAsignacion;
import com.compiler.parser.ast.NodoBloque;
import com.compiler.parser.ast.NodoDeclaracion;
import com.compiler.parser.ast.NodoIf;
import com.compiler.parser.ast.NodoImprimir;
import com.compiler.parser.ast.NodoSalto;
import com.compiler.parser.ast.NodoWhile;
import com.compiler.parser.ast.Sentencia;

/**
 * Clase destinada para la visualizacion
 * de AST
 */
public class ASTPrinter {

    /**
     * Metodo para mostrar el Abstract Sintactic Tree
     * @param nodos
     * @param prefijo
     */
    public static void printAST(
            List<Sentencia> nodos,
            String prefijo) {

        for (Sentencia nodo : nodos) {

            switch (nodo) {

                case NodoDeclaracion d -> System.out.println(
                        prefijo
                                + "├── [Declaración Variable]: "
                                + d.palabraClave().lexema
                                + " "
                                + d.identificador().lexema
                                + (d.tipo() == null ? "" : ": " + d.tipo().lexema)
                                + (d.valor() == null ? "" : " = " + d.valor())
                );

                case NodoAsignacion a -> System.out.println(
                        prefijo
                                + "├── [Asignación]: "
                                + a.identificador().lexema
                                + " = "
                                + a.valor()
                );

                case NodoIf nIf -> {
                    System.out.println(
                            prefijo
                                    + "├── Condition: "
                                    + nIf.condicion()
                    );

                    System.out.println(
                            prefijo
                                    + "│   └── Cuerpo del Bloque:"
                    );

                    printAST(
                            nIf.bloqueTrue().sentencias(),
                            prefijo + "│       "
                    );

                    if (!nIf.bloqueFalse().sentencias().isEmpty()) {
                        System.out.println(
                                prefijo
                                        + "│   └── Bloque Else:"
                        );

                        printAST(
                                nIf.bloqueFalse().sentencias(),
                                prefijo + "│       "
                        );
                    }
                }

                case NodoImprimir imp -> System.out.println(
                        prefijo
                                + "├── [Instrucción Imprimir]: "
                                + imp.expresion()
                );

                case NodoBloque b -> {
                    System.out.println(prefijo + "├── [Bloque]:");
                    printAST(b.sentencias(), prefijo + "│   ");
                }

                case NodoWhile w -> {
                    System.out.println(prefijo + "├── [While] Condition: " + w.condicion());
                    System.out.println(prefijo + "│   └── Cuerpo del Ciclo:");
                    printAST(w.cuerpo().sentencias(), prefijo + "│       ");
                }

                case NodoSalto s -> System.out.println(
                        prefijo + "├── [" + s.palabraClave().lexema + "]");
            }
        }
    }
}

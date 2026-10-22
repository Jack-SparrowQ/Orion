package com.compiler.util;

import java.util.List;

import com.compiler.parser.ast.NodoDeclaracion;
import com.compiler.parser.ast.NodoIf;
import com.compiler.parser.ast.NodoImprimir;
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
                                + " = "
                                + d.valor()
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
            }
        }
    }
}

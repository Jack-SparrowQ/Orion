package com.compiler.util;

import java.util.List;

import com.compiler.parser.ElementoAST;
import com.compiler.parser.Nodes.NodoDeclaracion;
import com.compiler.parser.Nodes.NodoIf;
import com.compiler.parser.Nodes.NodoImprimir;

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
            List<ElementoAST> nodos,
            String prefijo) {

        for (ElementoAST nodo : nodos) {

            // --------------------------------------------------------
            // DECLARACIÓN
            // --------------------------------------------------------

            if (nodo instanceof NodoDeclaracion) {

                NodoDeclaracion d =
                        (NodoDeclaracion) nodo;

                System.out.println(
                        prefijo
                                + "├── [Declaración Variable]: "
                                + d.palabraClave.lexema
                                + " "
                                + d.identificador.lexema
                                + " = "
                                + d.valorNumero
                );
            }


            // --------------------------------------------------------
            // IF
            // --------------------------------------------------------

            else if (nodo instanceof NodoIf) {

                NodoIf nIf =
                        (NodoIf) nodo;

                System.out.println(
                        prefijo
                                + "├── Condition: "
                                + nIf.condicion
                );


                System.out.println(
                        prefijo
                                + "│   └── Cuerpo del Bloque:"
                );


                printAST(
                        nIf.bloqueTrue.sentencias,
                        prefijo + "│       "
                );
            }


            // --------------------------------------------------------
            // PRINT
            // --------------------------------------------------------

            else if (nodo instanceof NodoImprimir) {

                NodoImprimir imp =
                        (NodoImprimir) nodo;


                System.out.println(
                        prefijo
                                + "├── [Instrucción Imprimir]: "
                                + imp.expresion
                );
            }
        }
    }
}

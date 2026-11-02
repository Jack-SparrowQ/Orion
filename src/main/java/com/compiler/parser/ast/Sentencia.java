package com.compiler.parser.ast;

/**
 * Sentencia del programa. Al ser sellada, un switch sobre Sentencia que
 * olvide un tipo de nodo no compila.
 */
public sealed interface Sentencia permits NodoDeclaracion, NodoAsignacion, NodoAsignacionElemento, NodoImprimir, NodoIf, NodoBloque,
        NodoWhile, NodoFor, NodoSalto, NodoFuncion, NodoReturn, NodoLlamada {
}

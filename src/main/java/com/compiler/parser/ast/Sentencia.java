package com.compiler.parser.ast;

/**
 * Sentencia del programa. Al ser sellada, un switch sobre Sentencia que
 * olvide un tipo de nodo no compila.
 */
public sealed interface Sentencia permits NodoDeclaracion, NodoAsignacion, NodoImprimir, NodoIf, NodoBloque {
}

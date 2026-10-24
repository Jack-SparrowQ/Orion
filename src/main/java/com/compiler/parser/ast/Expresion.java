package com.compiler.parser.ast;

/**
 * Expresión. Al ser sellada, un switch sobre Expresion que olvide un tipo
 * de nodo no compila.
 */
public sealed interface Expresion
        permits NodoLiteral, NodoVariable, NodoBinario, NodoUnario, NodoAgrupacion, NodoLlamada {
}

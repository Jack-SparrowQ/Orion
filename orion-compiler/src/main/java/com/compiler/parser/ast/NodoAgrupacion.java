package com.compiler.parser.ast;

/** Expresión entre paréntesis escrita por el programador: (a + b) */
public record NodoAgrupacion(Expresion expresion) implements Expresion {

    @Override
    public String toString() {
        return "group(" + expresion + ")";
    }
}

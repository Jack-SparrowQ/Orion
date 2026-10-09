package com.compiler.parser.Nodes;

import com.compiler.parser.ElementoAST;

// Expresión entre paréntesis escrita por el programador: (a + b)
public class NodoAgrupacion extends ElementoAST {
    public final ElementoAST expresion;

    public NodoAgrupacion(ElementoAST expresion) {
        this.expresion = expresion;
    }

    @Override
    public String toString() {
        return "group(" + expresion + ")";
    }
}

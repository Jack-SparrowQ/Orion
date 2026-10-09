package com.compiler.parser.Nodes;

import com.compiler.parser.ElementoAST;

public class NodoImprimir extends ElementoAST {
    public final ElementoAST expresion; // Lo que va adentro de los paréntesis

    public NodoImprimir(ElementoAST expresion) {
        this.expresion = expresion;
    }
}

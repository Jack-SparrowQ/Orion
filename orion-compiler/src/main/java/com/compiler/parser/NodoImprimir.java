package com.compiler.parser;

public class NodoImprimir extends ElementoAST {
    public final ElementoAST expresion; // Lo que va adentro de los paréntesis

    public NodoImprimir(ElementoAST expresion) {
        this.expresion = expresion;
    }
}

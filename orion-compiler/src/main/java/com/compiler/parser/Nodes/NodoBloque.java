package com.compiler.parser.Nodes;

import java.util.List;

import com.compiler.parser.ElementoAST;

public class NodoBloque extends ElementoAST {
    public final List<ElementoAST> sentencias;

    public NodoBloque(List<ElementoAST> sentencias) {
        this.sentencias = sentencias;
    }
}

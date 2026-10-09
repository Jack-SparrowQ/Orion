package com.compiler.parser.Nodes;

import com.compiler.parser.ElementoAST;

public class NodoIf extends ElementoAST {
    public final ElementoAST condicion;
    public final NodoBloque bloqueTrue;

    public NodoIf(ElementoAST condicion, NodoBloque bloqueTrue) {
        this.condicion = condicion;
        this.bloqueTrue = bloqueTrue;
    }
}

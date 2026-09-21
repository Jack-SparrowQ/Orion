package main.java.com.compiler.parser;

import java.util.List;

public class NodoBloque extends ElementoAST {
    public final List<ElementoAST> sentencias;

    public NodoBloque(List<ElementoAST> sentencias) {
        this.sentencias = sentencias;
    }
}

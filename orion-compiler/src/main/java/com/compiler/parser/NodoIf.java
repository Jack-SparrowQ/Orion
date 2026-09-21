package main.java.com.compiler.parser;

public class NodoIf extends ElementoAST {
    public final ElementoAST condicion;
    public final NodoBloque bloqueTrue;

    public NodoIf(ElementoAST condicion, NodoBloque bloqueTrue) {
        this.condicion = condicion;
        this.bloqueTrue = bloqueTrue;
    }
}

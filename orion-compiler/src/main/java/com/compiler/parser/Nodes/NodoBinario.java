package com.compiler.parser.Nodes;

import com.compiler.lexer.Token;
import com.compiler.parser.ElementoAST;

public class NodoBinario extends ElementoAST{

    public final ElementoAST izquierda;
    public final Token operador;
    public final ElementoAST derecha;

    public NodoBinario(ElementoAST izquierda, Token operador, ElementoAST derecha) {
        this.izquierda = izquierda;
        this.operador = operador;
        this.derecha = derecha;
    }

    @Override 
    public String toString() {
        return "(" + izquierda + " " + operador.lexema + " " + derecha + ")";
    }
    
}

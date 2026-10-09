package com.compiler.parser.Nodes;

import com.compiler.lexer.Token;
import com.compiler.parser.ElementoAST;

// Valor simple: número, identificador, true o false
public class NodoExpresionSimple extends ElementoAST {
    public final Token valor;
    public NodoExpresionSimple(Token valor) { this.valor = valor; }

    @Override 
    public String toString() {
        return valor.lexema;
    }
}
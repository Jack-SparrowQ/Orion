package com.compiler.parser;

import com.compiler.lexer.Token;

public class NodoExpresionSimple extends ElementoAST {
    public final Token valor;
    public NodoExpresionSimple(Token valor) { this.valor = valor; }
}
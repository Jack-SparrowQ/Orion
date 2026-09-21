package main.java.com.compiler.parser;

import main.java.com.compiler.lexer.Token;

public class NodoExpresionSimple extends ElementoAST {
    public final Token valor;
    public NodoExpresionSimple(Token valor) { this.valor = valor; }
}
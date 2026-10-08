package com.compiler.lexer;

public class Token {
    public final TokenType tipo;
    public final String lexema;

    public Token(TokenType tipo, String lexema) {
        this.tipo = tipo;
        this.lexema = lexema;
    }

    public TokenType getTokenType() { return tipo;}

    @Override
    public String toString() {
        return "[" + tipo + " : '" + lexema + "']";
    }
}
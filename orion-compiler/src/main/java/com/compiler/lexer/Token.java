package com.compiler.lexer;

public class Token {
    public final TokenType tipo;
    public final String lexema;
    public final int line;
    public final int column;

    public Token(
            TokenType tipo, 
            String lexema,
            int line,
            int column
            ) {
        this.tipo = tipo;
        this.lexema = lexema;
        this.line = line;
        this.column = column;
    }

    public TokenType getTokenType() { return tipo;}

    @Override
    public String toString() {
        return "[" + tipo + " : '" + lexema + "' @" + line + ":" + column + "']";
    }
}
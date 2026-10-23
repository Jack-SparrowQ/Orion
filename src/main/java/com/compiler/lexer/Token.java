package com.compiler.lexer;

/**
 * 
 * Token
 */
public class Token {
    public final TokenType tipo; //The type of token (e.g., NUMBER, IDENTIFIER)
    public final String lexema;  // The actual text/characters from input (e.g., "5", "x")
    public final int line;       // The line number in the source code where the token was found
    public final int column;     // The column number in the source code where the token was found

    /**
     * Constructor to create a token.
     * @param tipo
     * @param lexema
     * @param line
     * @param column
     */
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

    // Displays the token as text when printed
    @Override
    public String toString() {
        return String.format("Token(%s, \"%s\", %d:%d)",
                            tipo, lexema, line, column);
    }
}
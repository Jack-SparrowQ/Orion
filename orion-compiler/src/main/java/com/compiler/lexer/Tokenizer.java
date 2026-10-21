package com.compiler.lexer;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Carlos Rivera
 * LexerAnalyzer
 */
public class Tokenizer {
    
    private List<Token> tokensList;
    private Lexer lexer;
    private Token token;

    /**
     * LexerAnalyzer constructor.
     * @param lexer
     */
    public Tokenizer(Lexer lexer) {
        this.lexer = lexer;
        
        tokensList = new ArrayList<>();
    }

    /**
     * Metodo principal encargado de recorrer
     * los caracteres y agregarlos a la lista de tokes.
     */
    public void run() {

        do {

            token = lexer.siguienteToken();
            tokensList.add(token);

        } while (token.tipo != TokenType.EOF);
    }
    
    /**
     * Metodo que retorna el numero de tokens.
     * @return
     */
    public int getNumTokens() {
        return tokensList.size();
    }

    /**
     * Metodo encargado de devolver informacion del Token.
     * @param token
     * @return String of Token information (type, lex, line & colum).
     */
    public static String getTokenInformation(Token token) {
        return String.format(
            "Token: %-15s Lexema: '%s' [%d:%d]",
            token.tipo,
            token.lexema,
            token.line,
            token.column
        );
    }

    public List<Token> getTokensList() {
        return  tokensList;
    }

}

package com.compiler.parser.ast;

import com.compiler.lexer.Token;

/**
 * nombre ":" tipo ” un parametro de funcion. El tipo es obligatorio.
 *
 * @param nombre nombre del parametro
 * @param tipo   tipo anotado (int, float, char, string o bool)
 */
public record NodoParametro(Token nombre, Token tipo) {

    @Override
    public String toString() {
        return nombre.lexema + ": " + tipo.lexema;
    }
}

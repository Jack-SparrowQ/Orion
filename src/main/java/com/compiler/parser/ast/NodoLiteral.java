package com.compiler.parser.ast;

import com.compiler.lexer.Token;

/** Valor literal: número, cadena, carácter, true, false o null. */
public record NodoLiteral(Token valor) implements Expresion {

    @Override
    public String toString() {
        return valor.lexema;
    }
}

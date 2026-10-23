package com.compiler.parser.ast;

import com.compiler.lexer.Token;

/** Valor literal: número, true o false. */
public record NodoLiteral(Token valor) implements Expresion {

    @Override
    public String toString() {
        return valor.lexema;
    }
}

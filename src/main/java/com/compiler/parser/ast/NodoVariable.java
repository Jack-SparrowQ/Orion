package com.compiler.parser.ast;

import com.compiler.lexer.Token;

/** Uso de una variable por su nombre. */
public record NodoVariable(Token nombre) implements Expresion {

    @Override
    public String toString() {
        return nombre.lexema;
    }
}

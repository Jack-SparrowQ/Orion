package com.compiler.parser.ast;

import com.compiler.lexer.Token;

/** izquierda operador derecha: a + b, a < b, a && b... */
public record NodoBinario(Expresion izquierda, Token operador, Expresion derecha) implements Expresion {

    @Override
    public String toString() {
        return "(" + izquierda + " " + operador.lexema + " " + derecha + ")";
    }
}

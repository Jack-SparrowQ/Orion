package com.compiler.parser.ast;

import com.compiler.lexer.Token;

/** Operación con un solo operando: !activo, -5 */
public record NodoUnario(Token operador, Expresion operando) implements Expresion {

    @Override
    public String toString() {
        return "(" + operador.lexema + operando + ")";
    }
}

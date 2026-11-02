package com.compiler.parser.ast;

import com.compiler.lexer.Token;

/**
 * objeto "." nombre. La única propiedad que existe es 'length' (lo comprueba
 * el análisis semántico: E311).
 *
 * @param objeto expresión de la que se lee la propiedad
 * @param nombre nombre de la propiedad
 */
public record NodoPropiedad(Expresion objeto, Token nombre) implements Expresion {

    @Override
    public String toString() {
        return objeto + "." + nombre.lexema;
    }
}

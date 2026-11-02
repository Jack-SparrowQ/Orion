package com.compiler.parser.ast;

import com.compiler.lexer.Token;

/**
 * objeto "[" indice "]"
 *
 * @param objeto   arreglo o cadena que se indexa
 * @param corchete el '[', para ubicar errores
 * @param indice   posición, contando desde 0
 */
public record NodoIndice(Expresion objeto, Token corchete, Expresion indice) implements Expresion {

    @Override
    public String toString() {
        return objeto + "[" + indice + "]";
    }
}

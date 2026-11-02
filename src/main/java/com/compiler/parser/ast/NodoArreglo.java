package com.compiler.parser.ast;

import java.util.List;
import java.util.stream.Collectors;

import com.compiler.lexer.Token;

/**
 * "[" [ expresion { "," expresion } ] "]"
 *
 * @param corchete  el '[' de apertura, para ubicar errores
 * @param elementos elementos en orden; vacío en '[]'
 */
public record NodoArreglo(Token corchete, List<Expresion> elementos) implements Expresion {

    @Override
    public String toString() {
        return elementos.stream().map(Expresion::toString).collect(Collectors.joining(", ", "[", "]"));
    }
}

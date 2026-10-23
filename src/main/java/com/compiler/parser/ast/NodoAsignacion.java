package com.compiler.parser.ast;

import com.compiler.lexer.Token;

/**
 * identificador "=" valor ";"
 * @param identificador la variable que recibe el valor (ya declarada)
 * @param valor la expresión asignada
 */
public record NodoAsignacion(Token identificador, Expresion valor) implements Sentencia {
}

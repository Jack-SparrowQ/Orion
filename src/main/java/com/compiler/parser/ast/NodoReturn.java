package com.compiler.parser.ast;

import com.compiler.lexer.Token;

/**
 * "return" [ valor ] ";"
 *
 * @param palabraClave token 'return'; ubica los errores E316/E317
 * @param valor        expresionn devuelta, o null en "return;"
 */
public record NodoReturn(Token palabraClave, Expresion valor) implements Sentencia {
}

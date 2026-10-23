package com.compiler.parser.ast;

import com.compiler.lexer.Token;

/**
 * ("let" | "const") identificador "=" valor ";"
 * @param palabraClave 'let' o 'const'
 * @param identificador el nombre de la variable (ej. 'x')
 * @param valor la expresión asignada
 */
public record NodoDeclaracion(Token palabraClave, Token identificador, Expresion valor) implements Sentencia {
}

package com.compiler.parser.ast;

import com.compiler.lexer.Token;

/**
 * ("let" | "const") identificador [ ":" tipo ] [ "=" valor ] ";"
 * El parser garantiza que al menos uno de tipo/valor existe, y que una const tiene valor.
 * @param palabraClave 'let' o 'const'
 * @param identificador el nombre de la variable (ej. 'x')
 * @param tipo el nombre de tipo anotado (ej. 'int'), o null si se infiere del valor
 * @param valor la expresión asignada, o null en "let x: T;"
 */
public record NodoDeclaracion(Token palabraClave, Token identificador, Token tipo, Expresion valor)
        implements Sentencia {
}

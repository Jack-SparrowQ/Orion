package com.compiler.parser.ast;

import com.compiler.lexer.Token;

/**
 * "for" "(" variable "in" iterable ")" bloque
 *
 * @param variable nombre de la variable de iteración, de solo lectura
 * @param iterable expresión que se recorre; se evalúa una sola vez
 * @param cuerpo   bloque que se repite por cada elemento
 */
public record NodoFor(Token variable, Expresion iterable, NodoBloque cuerpo) implements Sentencia {
}

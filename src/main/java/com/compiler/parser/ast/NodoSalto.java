package com.compiler.parser.ast;

import com.compiler.lexer.Token;

/**
 * "break" ";" o "continue" ";". Ambas saltan dentro del ciclo más interno:
 * break a su salida y continue a su siguiente iteración.
 *
 * @param palabraClave token BREAK o CONTINUE; también ubica el error E313
 */
public record NodoSalto(Token palabraClave) implements Sentencia {
}

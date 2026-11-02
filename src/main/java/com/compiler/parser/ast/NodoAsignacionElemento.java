package com.compiler.parser.ast;

import com.compiler.lexer.Token;

/**
 * destino ("=" | "+=" | "-=" | "*=" | "/=" | "%=") valor ";" con destino v[i].
 * A diferencia de NodoAsignacion, la forma compuesta no se reduce a v[i] = v[i] + e:
 * los subíndices del destino deben evaluarse una sola vez.
 *
 * @param destino  elemento que recibe el valor; su raíz es una variable (lo garantiza el parser)
 * @param operador operador binario de la asignación compuesta ('+' en '+='), o null en '='
 * @param valor    expresión asignada
 */
public record NodoAsignacionElemento(NodoIndice destino, Token operador, Expresion valor) implements Sentencia {
}

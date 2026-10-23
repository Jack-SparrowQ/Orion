package com.compiler.parser.ast;

/**
 * "if" "(" condicion ")" bloque ( "else" bloque )?
 * @param bloqueFalse rama else; vacía si no hay 'else'
 */
public record NodoIf(Expresion condicion, NodoBloque bloqueTrue, NodoBloque bloqueFalse) implements Sentencia {
}

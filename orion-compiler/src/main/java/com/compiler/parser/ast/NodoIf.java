package com.compiler.parser.ast;

/** "if" "(" condicion ")" bloque */
public record NodoIf(Expresion condicion, NodoBloque bloqueTrue) implements Sentencia {
}

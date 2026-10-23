package com.compiler.parser.ast;

/** "print" "(" expresion ")" ";" */
public record NodoImprimir(Expresion expresion) implements Sentencia {
}

package com.compiler.parser.ast;

import java.util.List;

/**
 * "print" "(" [ expresion { "," expresion } ] ")" ";"
 *
 * @param argumentos valores a escribir, separados por un espacio; vacío en print()
 */
public record NodoImprimir(List<Expresion> argumentos) implements Sentencia {
}

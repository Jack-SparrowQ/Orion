package com.compiler.parser.ast;

/**
 * "while" "(" condicion ")" bloque
 *
 * @param condicion expresión bool que se evalúa antes de cada iteración
 * @param cuerpo    bloque que se repite mientras la condición sea verdadera
 */
public record NodoWhile(Expresion condicion, NodoBloque cuerpo) implements Sentencia {
}

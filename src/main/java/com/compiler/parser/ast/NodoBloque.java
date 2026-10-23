package com.compiler.parser.ast;

import java.util.List;

/**
 * Bloque de sentencias: "{" sentencias "}".
 * Es el cuerpo de un if y también una sentencia independiente; en ambos
 * casos abre un ámbito nuevo para las variables que declara.
 *
 * @param sentencias sentencias del bloque, en orden de aparición
 */
public record NodoBloque(List<Sentencia> sentencias) implements Sentencia {
}

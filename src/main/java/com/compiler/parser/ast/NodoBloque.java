package com.compiler.parser.ast;

import java.util.List;

/** "{" sentencias "}" */
public record NodoBloque(List<Sentencia> sentencias) {
}

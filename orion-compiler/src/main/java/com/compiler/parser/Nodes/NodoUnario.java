package com.compiler.parser.Nodes;

import com.compiler.lexer.Token;
import com.compiler.parser.ElementoAST;

// Operación con un solo operando: !activo, -5
public class NodoUnario extends ElementoAST {
    public final Token operador;
    public final ElementoAST operando;

    public NodoUnario(Token operador, ElementoAST operando) {
        this.operador = operador;
        this.operando = operando;
    }

    @Override
    public String toString() {
        return "(" + operador.lexema + operando + ")";
    }
}
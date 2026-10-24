package com.compiler.parser.ast;

import java.util.List;

import com.compiler.lexer.Token;

/**
 * nombre "(" [ argumentos ] ")" ” llamada a una funcion.
 * Es expresion (usa el valor de retorno) y tambion sentencia ("f(x);").
 *
 * @param nombre     funcion llamada; tambien ubica los errores E314/E315
 * @param argumentos argumentos en orden
 */
public record NodoLlamada(Token nombre, List<Expresion> argumentos) implements Expresion, Sentencia {

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(nombre.lexema).append("(");
        for (int i = 0; i < argumentos.size(); i++) {
            sb.append(i == 0 ? "" : ", ").append(argumentos.get(i));
        }
        return sb.append(")").toString();
    }
}

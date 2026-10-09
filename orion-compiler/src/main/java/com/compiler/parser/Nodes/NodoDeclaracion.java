package com.compiler.parser.Nodes;

import com.compiler.lexer.Token;
import com.compiler.parser.ElementoAST;

public class NodoDeclaracion extends ElementoAST {
    public final Token palabraClave; // 'let' o 'const'
    public final Token identificador; // El nombre de la variable (ej. 'x')
    public final ElementoAST valorNumero;   // El valor asignado (ej. '5')

    public NodoDeclaracion(Token palabraClave, Token identificador, ElementoAST valorNumero) {
        this.palabraClave = palabraClave;
        this.identificador = identificador;
        this.valorNumero = valorNumero;
    }

    
}

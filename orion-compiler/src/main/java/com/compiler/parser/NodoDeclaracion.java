package main.java.com.compiler.parser;

import main.java.com.compiler.lexer.Token;

public class NodoDeclaracion extends ElementoAST {
    public final Token palabraClave; // 'let' o 'const'
    public final Token identificador; // El nombre de la variable (ej. 'x')
    public final Token valorNumero;   // El valor asignado (ej. '5')

    public NodoDeclaracion(Token palabraClave, Token identificador, Token valorNumero) {
        this.palabraClave = palabraClave;
        this.identificador = identificador;
        this.valorNumero = valorNumero;
    }

    
}

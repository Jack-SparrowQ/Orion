package com.compiler.parser;

import java.util.ArrayList;
import java.util.List;

import com.compiler.error.ErrorHandler;
import com.compiler.error.ErrorType;
import com.compiler.lexer.Token;
import com.compiler.lexer.TokenType;
import com.compiler.parser.Nodes.NodoAgrupacion;
import com.compiler.parser.Nodes.NodoBinario;
import com.compiler.parser.Nodes.NodoBloque;
import com.compiler.parser.Nodes.NodoDeclaracion;
import com.compiler.parser.Nodes.NodoExpresionSimple;
import com.compiler.parser.Nodes.NodoIf;
import com.compiler.parser.Nodes.NodoImprimir;
import com.compiler.parser.Nodes.NodoUnario;

public class Parser {

    private final ErrorHandler gestorErrores;
    private final List<Token> tokens;
    private int actual = 0; // Puntero al token bajo análisis

    // Excepción interna: solo sirve para abortar el parseo.
    // El mensaje del error ya queda guardado en el ErrorHandler.
    private static class ParseError extends RuntimeException {}

    public Parser(
            List<Token> tokens,
            ErrorHandler gestorErrores) {

        this.gestorErrores = gestorErrores;
        this.tokens = tokens;
    }

     // Registra un error sintáctico en la posición del token dado
    private ParseError error(Token token, String code, String message) {
        gestorErrores.agregar(code, ErrorType.SINTACTICO, message, token.line, token.column);
        return new ParseError();
    }

     // Texto legible para mostrar el token encontrado
     private String describir(Token token) {
        return token.tipo == TokenType.EOF ? "End of the file": "'" + token.lexema + "'";
     }

    // --- MÉTODOS DE APOYO Y NAVEGACIÓN ---

    // Mira el token actual sin consumirlo
    private Token peek() {
        return tokens.get(actual);
    }

    // Verifica si ya terminamos de leer todos los tokens
    private boolean isAtEnd() {
        return peek().tipo == TokenType.EOF;
    }

    // Avanza al siguiente token y devuelve el anterior
    private Token avanzar() {
        if (!isAtEnd()) actual++;
        return tokens.get(actual - 1);
    }

    // Devuelve el último token consumido
    private Token anterior() {
        return tokens.get(actual - 1);
    }

    // Comprueba si el token actual es de un tipo específico
    private boolean verificar(TokenType tipo) {
        if (isAtEnd()) return false;
        return peek().tipo == tipo;
    }

    // Si el token actual coincide con alguno de los tipos, lo consume y avanza
    private boolean coinciden(TokenType... tipos) {
        for (TokenType tipo : tipos) {
            if (verificar(tipo)) {
                avanzar();
                return true;
            }
        }
        return false;
    }

    // OBLIGA a que el token actual sea del tipo esperado. Si no, lanza ERROR SINTÁCTICO.
    private Token consumir(TokenType tipo, String mensajeError) {
        
        if(verificar(tipo)) {
            return avanzar();
        }

        // Aquí capturamos el clásico error de compilación
        Token tokenError = peek();

        throw error(tokenError, "E201",
         mensajeError + "found "+ describir(tokenError) + ".");
    }
    
    //Metodo principal
    public List<ElementoAST> parsear() {
        List<ElementoAST> program = new ArrayList<>();
        try {
            while (!isAtEnd()) {
                program.add(sentencia());
            }
            return program;
        } catch (ParseError e) {
            // El error ya fue registrado en el ErrorHandler (modo fail-fast).
        }

        return program;
    }

    // 2. El Enrutador: Decide qué regla gramatical usar viendo el token actual
    private ElementoAST sentencia() {
        if (verificar(TokenType.LET) || verificar(TokenType.CONST)) {
            return declaracionVariable(); 
        }
        if (coinciden(TokenType.PRINT)) {
            return declaracionImprimir();
        }
        if (coinciden(TokenType.IF)) {
            return declaracionIf();
        }
        
        // Si no es ninguna de las anteriores, lanzamos error (por ahora)
        throw error(peek(), "E2002", "Instruccion no reconocida: "+ describir(peek()) + ".");
    }

    // Regla: declaracion -> ("let" | "const") IDENTIFICADOR "=" expresion ";"
    private ElementoAST declaracionVariable() {
        // 1. Como ya verificamos en el paso anterior, avanzamos de forma segura y guardamos si fue 'let' o 'const'
        Token palabraClave = avanzar(); 
        
        // 2. Obligatoriamente debe seguir el nombre de la variable
        Token identificador = consumir(TokenType.IDENTIFICADOR, "Se esperaba el nombre de la variable.");
        
        // 3. Obligatoriamente debe seguir un signo '='
        consumir(TokenType.IGUAL, "Se esperaba '=' después del nombre de la variable.");
        
        // 4. Obligatoriamente debe seguir un número literal
        ElementoAST valor = expresion();        
        // 5. Obligatoriamente debe cerrar con ';'
        consumir(TokenType.PUNTO_Y_COMA, "Se esperaba ';' al final de la sentencia.");
        
        return new NodoDeclaracion(palabraClave, identificador, valor);
    }

    // Regla: "print" "(" expresion ")" ";"
    private ElementoAST declaracionImprimir() {
        consumir(TokenType.PARENTESIS_IZQ, "Se esperaba '(' después de 'print'.");
        
        ElementoAST valorAImprimir = expresion(); // Leemos lo que está adentro
        
        consumir(TokenType.PARENTESIS_DER, "Se esperaba ')' después del valor a imprimir.");
        consumir(TokenType.PUNTO_Y_COMA, "Se esperaba ';' al final de la instrucción print.");
        
        return new NodoImprimir(valorAImprimir);
    }

    // Regla: "if" "(" expresion ")" "{" sentencias "}"
    private ElementoAST declaracionIf() {
        consumir(TokenType.PARENTESIS_IZQ, "Se esperaba '(' después de 'if'.");
        
        ElementoAST condicion = expresion(); // Leemos la condición
        
        consumir(TokenType.PARENTESIS_DER, "Se esperaba ')' después de la condición del if.");
        
        // El 'if' requiere un bloque de código
        consumir(TokenType.LLAVE_IZQ, "Se esperaba '{' antes del bloque del if.");
        NodoBloque cuerpo = parsearBloque();
        
        return new NodoIf(condicion, cuerpo);
    }

    // Regla auxiliar para leer todo lo que está dentro de { }
    private NodoBloque parsearBloque() {
        List<ElementoAST> sentenciasInternas = new ArrayList<>();
        
        // Seguimos leyendo sentencias hasta encontrar la llave derecha '}' o el EOF
        while (!verificar(TokenType.LLAVE_DER) && !isAtEnd()) {
            sentenciasInternas.add(sentencia());
        }
        
        consumir(TokenType.LLAVE_DER, "Se esperaba '}' al final del bloque.");
        return new NodoBloque(sentenciasInternas);
    }

    // ================================================================
// EXPRESIONES (de menor a mayor precedencia, igual que en JavaScript)
// ================================================================

// expresion -> logicoOr
private ElementoAST expresion() {
    return logicoOr();
}

// logicoOr -> logicoAnd ( "||" logicoAnd )*
private ElementoAST logicoOr() {
    ElementoAST izquierda = logicoAnd();
    while (coinciden(TokenType.OR_LOGICO)) {
        Token operador = anterior();
        ElementoAST derecha = logicoAnd();
        izquierda = new NodoBinario(izquierda, operador, derecha);
    }
    return izquierda;
}

// logicoAnd -> igualdad ( "&&" igualdad )*
private ElementoAST logicoAnd() {
    ElementoAST izquierda = igualdad();
    while (coinciden(TokenType.AND_LOGICO)) {
        Token operador = anterior();
        ElementoAST derecha = igualdad();
        izquierda = new NodoBinario(izquierda, operador, derecha);
    }
    return izquierda;
}

// igualdad -> comparacion ( ("==" | "!=") comparacion )*
private ElementoAST igualdad() {
    ElementoAST izquierda = comparacion();
    while (coinciden(TokenType.COMPARACION_IGUAL, TokenType.DIFERENTE)) {
        Token operador = anterior();
        ElementoAST derecha = comparacion();
        izquierda = new NodoBinario(izquierda, operador, derecha);
    }
    return izquierda;
}

// comparacion -> termino ( ("<" | "<=" | ">" | ">=") termino )*
private ElementoAST comparacion() {
    ElementoAST izquierda = termino();
    while (coinciden(TokenType.MENOR, TokenType.MENOR_IGUAL,
                     TokenType.MAYOR, TokenType.MAYOR_IGUAL)) {
        Token operador = anterior();
        ElementoAST derecha = termino();
        izquierda = new NodoBinario(izquierda, operador, derecha);
    }
    return izquierda;
}

// termino -> factor ( ("+" | "-") factor )*
private ElementoAST termino() {
    ElementoAST izquierda = factor();
    while (coinciden(TokenType.SUMA, TokenType.RESTA)) {
        Token operador = anterior();
        ElementoAST derecha = factor();
        izquierda = new NodoBinario(izquierda, operador, derecha);
    }
    return izquierda;
}

// factor -> unario ( ("*" | "/" | "%") unario )*
private ElementoAST factor() {
    ElementoAST izquierda = unario();
    while (coinciden(TokenType.MULTI, TokenType.DIVISION, TokenType.MODULO)) {
        Token operador = anterior();
        ElementoAST derecha = unario();
        izquierda = new NodoBinario(izquierda, operador, derecha);
    }
    return izquierda;
}

// unario -> ("!" | "-") unario | primario
private ElementoAST unario() {
    if (coinciden(TokenType.NEGACION_LOGICA, TokenType.RESTA)) {
        Token operador = anterior();
        ElementoAST operando = unario();
        return new NodoUnario(operador, operando);
    }
    return primario();
}

// primario -> NUMERO | IDENTIFICADOR | "true" | "false" | "(" expresion ")"
private ElementoAST primario() {
    if (coinciden(TokenType.NUMERO, TokenType.IDENTIFICADOR,
                  TokenType.TRUE, TokenType.FALSE)) {
        return new NodoExpresionSimple(anterior());
    }

    if (coinciden(TokenType.PARENTESIS_IZQ)) {
        ElementoAST interior = expresion();
        consumir(TokenType.PARENTESIS_DER, "Se esperaba ')' después de la expresión.");
        return new NodoAgrupacion(interior);
    }

    throw error(peek(), "E203",
            "Se esperaba una expresión (número, variable, true/false o '('). Se encontró "
                    + describir(peek()) + ".");
}

    private void sincronizar() {
        while (!isAtEnd()) {
            if(verificar(TokenType.PUNTO_Y_COMA)) {
                avanzar();
                return;
            }
            avanzar();
        }
    }
}
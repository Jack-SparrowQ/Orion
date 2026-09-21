package main.java.com.compiler.parser;

import java.util.ArrayList;
import java.util.List;
import main.java.com.compiler.lexer.Token;
import main.java.com.compiler.lexer.TokenType;

public class Parser {
    private final List<Token> tokens;
    private int actual = 0; // Puntero al token bajo análisis

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
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
        if (verificar(tipo)) return avanzar();
        
        // Aquí capturamos el clásico error de compilación
        Token tokenError = peek();
        throw new RuntimeException("Error Sintáctico: " + mensajeError + 
                " Encontrado '" + tokenError.lexema + "' en el token nro " + actual);
    }
    
    //Metodo principal
    public List<ElementoAST> parsear() {
        List<ElementoAST> program = new ArrayList<>();
        try {
            while (!isAtEnd()) {
                program.add(sentencia());
            }
            return program;
        } catch (RuntimeException e) {
            System.err.println(e.getMessage());
            return null;
        }
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
        throw new RuntimeException("Error Sintáctico: Instrucción no reconocida en el token " + peek().lexema);
    }

    // Regla: declaracion -> ("let" | "const") IDENTIFICADOR "=" NUMERO ";"
    private ElementoAST declaracionVariable() {
        // 1. Como ya verificamos en el paso anterior, avanzamos de forma segura y guardamos si fue 'let' o 'const'
        Token palabraClave = avanzar(); 
        
        // 2. Obligatoriamente debe seguir el nombre de la variable
        Token identificador = consumir(TokenType.IDENTIFICADOR, "Se esperaba el nombre de la variable.");
        
        // 3. Obligatoriamente debe seguir un signo '='
        consumir(TokenType.IGUAL, "Se esperaba '=' después del nombre de la variable.");
        
        // 4. Obligatoriamente debe seguir un número literal
        Token valor = consumir(TokenType.NUMERO, "Se esperaba un valor numérico.");
        
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

    // Regla temporal para las expresiones (matemáticas o variables)
    private ElementoAST expresion() {
        // Por ahora, para no complicarlo, asumimos que la expresión es solo un número o un identificador
        if (coinciden(TokenType.NUMERO, TokenType.IDENTIFICADOR)) {
            return new NodoExpresionSimple(tokens.get(actual - 1));
        }
        throw new RuntimeException("Error Sintáctico: Se esperaba una expresión (número o variable).");
    }
}
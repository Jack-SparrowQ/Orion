package com.compiler.lexer;

import java.util.HashMap;
import java.util.Map;

import com.compiler.error.ErrorHandler;
import com.compiler.error.ErrorType;

public class Lexer {
    private final String codigoFuente;
    private int posicionActual = 0; // Nuestro puntero
    private int line = 1;           // Poscion actual
    private int column = 1;
    private int startLine = 1;      // posicion donde empezo  el token en curso
    private int startColumn = 1;

    // Diccionario de búsqueda rápida para palabras reservadas
    private static final Map<String, TokenType> PALABRAS_RESERVADAS;

    private final ErrorHandler gestorErrores;

    static {
        PALABRAS_RESERVADAS = new HashMap<>();
        // Control y flujo
        PALABRAS_RESERVADAS.put("if", TokenType.IF);
        PALABRAS_RESERVADAS.put("else", TokenType.ELSE);
        PALABRAS_RESERVADAS.put("while", TokenType.WHILE);
        PALABRAS_RESERVADAS.put("for", TokenType.FOR);
        PALABRAS_RESERVADAS.put("in", TokenType.IN);
        PALABRAS_RESERVADAS.put("break", TokenType.BREAK);
        PALABRAS_RESERVADAS.put("continue", TokenType.CONTINUE);
        PALABRAS_RESERVADAS.put("return", TokenType.RETURN);
        
        // Declaraciones
        PALABRAS_RESERVADAS.put("let", TokenType.LET);
        PALABRAS_RESERVADAS.put("const", TokenType.CONST);
        PALABRAS_RESERVADAS.put("fn", TokenType.FN);
        
        // Valores lógicos y especiales
        PALABRAS_RESERVADAS.put("true", TokenType.TRUE);
        PALABRAS_RESERVADAS.put("false", TokenType.FALSE);
        PALABRAS_RESERVADAS.put("null", TokenType.NULL);
        PALABRAS_RESERVADAS.put("void", TokenType.VOID);
        
        // Módulos
        PALABRAS_RESERVADAS.put("import", TokenType.IMPORT);
        PALABRAS_RESERVADAS.put("from", TokenType.FROM);
        PALABRAS_RESERVADAS.put("export", TokenType.EXPORT);
        
        // Otros
        PALABRAS_RESERVADAS.put("print", TokenType.PRINT);
    }

    public Lexer(String codigoFuente,ErrorHandler gestorErrores) {
        this.codigoFuente = codigoFuente;
        this.gestorErrores = gestorErrores;
    }

    // Método de apoyo para ver el carácter actual sin avanzar
    private char caracterActual() {
        if (posicionActual >= codigoFuente.length()) return '\0'; // Fin de cadena
        return codigoFuente.charAt(posicionActual);
    }

    // Método de apoyo para avanzar el puntero
    private void avanzar() {
        if(caracterActual() == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
        posicionActual++;
    }

    // La función principal de tu analizador léxico
    public Token siguienteToken() {
        while (posicionActual < codigoFuente.length()) {
            char c = caracterActual();

            startLine = line;
            startColumn = column;

            // 1. Ignorar espacios en blanco y saltos de línea
            if (Character.isWhitespace(c)) {
                avanzar();
                continue;
            }

            // 2. Detectar Operadores (Ejemplo con el símbolo '=')
            switch (c) {
                case '+':
                    avanzar();
                    if (match('=')) {
                        // Si el siguiente fue '=', es un '+='
                        return createToken(TokenType.SUMA_Y_ASIGNACION, "+=");
                    }
                    return createToken(TokenType.SUMA, "+");
                case '-':
                    avanzar();
                    if (match('=')) {
                        return createToken(TokenType.RESTA_Y_ASIGNACION, "-=");
                    }
                    return createToken(TokenType.RESTA, "-");
                case '*':
                    avanzar();
                    if (match('=')) {
                        return createToken(TokenType.MULTI_Y_ASIGNACION, "*=");
                    }
                    return createToken(TokenType.MULTI, "*");
                case '/':
                    avanzar();
                    if (match('=')) {
                        return createToken(TokenType.DIVISION_Y_ASIGNACION, "/=");
                    }
                    return createToken(TokenType.DIVISION, "/");
                case '%':
                    avanzar();
                    if (match('=')) {
                        return createToken(TokenType.MODULO_Y_ASIGNACION, "%=");
                    }
                    return createToken(TokenType.MODULO, "%");
                case '(':
                    avanzar();
                    return createToken(TokenType.PARENTESIS_IZQ, "(");
                case ')':
                    avanzar();
                    return createToken(TokenType.PARENTESIS_DER, ")");
                case '{':
                    avanzar();
                    return createToken(TokenType.LLAVE_IZQ, "{");
                case '}':
                    avanzar();
                    return createToken(TokenType.LLAVE_DER, "}");
                case '[':
                    avanzar();
                    return createToken(TokenType.CORCHETE_DER, "[");
                case ']':
                    avanzar();
                    return createToken(TokenType.CORCHETE_IZQ, "]");
                case ';':
                    avanzar();
                    return createToken(TokenType.PUNTO_Y_COMA, ";");
                case '.':
                    avanzar();
                    return createToken(TokenType.PUNTO, ".");

                case '!':
                    avanzar(); // Consumimos el '!'
                    if (match('=')) {
                        // Si el siguiente fue '=', encontramos un '!='
                        return createToken(TokenType.DIFERENTE, "!=");
                    }
                    return createToken(TokenType.NEGACION_LOGICA, "!");
                    
                case '=':
                    avanzar(); // Consumimos el primer '='
                    if (match('=')) {
                        // Si el siguiente fue '=', es un '=='
                        return createToken(TokenType.COMPARACION_IGUAL, "==");
                    }
                    // Si no, era un simple '=' de asignación
                    return createToken(TokenType.IGUAL, "=");
                    
                case '<':
                    avanzar(); // Consumimos el '<'
                    if (match('=')) {
                        return createToken(TokenType.MENOR_IGUAL, "<=");
                    }
                    return createToken(TokenType.MENOR, "<");
                    
                case '>':
                    avanzar(); // Consumimos el '>'
                    if (match('=')) {
                        return createToken(TokenType.MAYOR_IGUAL, ">=");
                    }
                    return createToken(TokenType.MAYOR, ">");

                default:
                    break;
            }

            // Detectar Operadores logicos
            switch (c) {
                case '&':
                    avanzar();
                    if(match('&')) { 
                        return createToken(TokenType.AND_LOGICO, "&&"); 
                    }
                    gestorErrores.agregar("E101", ErrorType.LEXICO, "Error Léxico: Se esperaba '&' después de '&'", startLine, startColumn);
                    continue;
                case '|':
                    avanzar();
                    if(match('|')) {
                        return createToken(TokenType.OR_LOGICO, "||");
                    }
                    gestorErrores.agregar("E102",
                    ErrorType.LEXICO, "Operador incompleto: se esperaba '||'", startLine, startColumn);
                    continue;

                default:
                    break;
            }

            // 3. Detectar Números
            if (Character.isDigit(c)) {
                StringBuilder numero = new StringBuilder();
                while (posicionActual < codigoFuente.length() && Character.isDigit(caracterActual())) {
                    numero.append(caracterActual());
                    avanzar();
                }
                return createToken(TokenType.NUMERO, numero.toString());
            }

            // 4. DETECTAR PALABRAS (Identificadores o Palabras Reservadas)
            // Un identificador suele empezar con una letra o guion bajo (_)
            if (Character.isLetter(c) || c == '_') {
                StringBuilder buffer = new StringBuilder();
                
                // Mientras sean letras, números o guiones bajos, forma parte de la palabra
                while (posicionActual < codigoFuente.length() && 
                      (Character.isLetterOrDigit(caracterActual()) || caracterActual() == '_')) {
                    buffer.append(caracterActual());
                    avanzar();
                }
                
                String palabra = buffer.toString();
                
                // Verificamos si es una palabra reservada de Orion
                TokenType tipoAsignado = PALABRAS_RESERVADAS.get(palabra);
                if (tipoAsignado != null) {
                    return createToken(tipoAsignado, palabra);
                }
                
                // Si no está en el mapa, es un identificador del usuario (ej. nombre de variable)
                return createToken(TokenType.IDENTIFICADOR, palabra);
            }
            
            // Si llegamos aquí, hay un carácter que nuestro lenguaje no reconoce
            gestorErrores.agregar("E100", 
                ErrorType.LEXICO, 
                "Carácter no reconocido '" + c + "'.",
                startLine, startColumn);

            avanzar(); //We skip the invalid character; otherwise, this would cause an infinite loop.
        }

        // Si el bucle termina, llegamos al final del archivo
        return new Token(TokenType.EOF, "",startLine,startColumn);
    }

    //Metodo lookahead
    private boolean match(char esperado) {
        // Si ya llegamos al final del archivo, no puede coincidir
        if (posicionActual >= codigoFuente.length()) return false;
        
        // Si el carácter actual no es el que esperamos, no avanzamos
        if (codigoFuente.charAt(posicionActual) != esperado) return false;
        
        // ¡Coincidió! Avanzamos el puntero (consumimos el segundo carácter)
        avanzar();
        return true;
    }

    private Token createToken(TokenType type, String lexema) {
        return new Token(type, lexema, startLine, startColumn);
    }

}
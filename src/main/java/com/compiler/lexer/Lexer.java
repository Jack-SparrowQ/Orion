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

    // Lo que puede seguir a '\' dentro de un literal
    private static final String ESCAPES = "ntr0\\\"'";

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

        // Nombres de tipo
        PALABRAS_RESERVADAS.put("int", TokenType.INT);
        PALABRAS_RESERVADAS.put("float", TokenType.FLOAT);
        PALABRAS_RESERVADAS.put("char", TokenType.CHAR);
        PALABRAS_RESERVADAS.put("string", TokenType.STRING);
        PALABRAS_RESERVADAS.put("bool", TokenType.BOOL);
        
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

    // Método de apoyo para ver el carácter siguiente al actual sin avanzar
    private char siguienteCaracter() {
        if (posicionActual + 1 >= codigoFuente.length()) return '\0';
        return codigoFuente.charAt(posicionActual + 1);
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
                    if (match('/')) {
                        // Comentario de línea: se descarta hasta el salto de línea
                        while (posicionActual < codigoFuente.length() && caracterActual() != '\n') {
                            avanzar();
                        }
                        continue;
                    }
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
                    return createToken(TokenType.CORCHETE_IZQ, "[");
                case ']':
                    avanzar();
                    return createToken(TokenType.CORCHETE_DER, "]");
                case ';':
                    avanzar();
                    return createToken(TokenType.PUNTO_Y_COMA, ";");
                case ':':
                    avanzar();
                    return createToken(TokenType.DOS_PUNTOS, ":");
                case ',':
                    avanzar();
                    return createToken(TokenType.COMA, ",");
                case '.':
                    avanzar();
                    return createToken(TokenType.PUNTO, ".");

                case '"':
                case '\'':
                    return literalDeTexto(c);

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
                    gestorErrores.agregar("E101", ErrorType.LEXICO, "Operador incompleto: se esperaba '&&'.", startLine, startColumn);
                    continue;
                case '|':
                    avanzar();
                    if(match('|')) {
                        return createToken(TokenType.OR_LOGICO, "||");
                    }
                    gestorErrores.agregar("E102",
                    ErrorType.LEXICO, "Operador incompleto: se esperaba '||'.", startLine, startColumn);
                    continue;

                default:
                    break;
            }

            // 3. Detectar Números
            if (esDigito(c)) {
                StringBuilder numero = new StringBuilder();
                while (posicionActual < codigoFuente.length() && esDigito(caracterActual())) {
                    numero.append(caracterActual());
                    avanzar();
                }
                // Parte decimal: solo si al '.' le sigue un dígito ("3." sigue siendo 3 y '.')
                if (caracterActual() == '.' && esDigito(siguienteCaracter())) {
                    numero.append('.');
                    avanzar();
                    while (posicionActual < codigoFuente.length() && esDigito(caracterActual())) {
                        numero.append(caracterActual());
                        avanzar();
                    }
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
        // Posición real del final (no la del inicio del último token).
        return new Token(TokenType.EOF, "", line, column);
    }

    /**
     * Lee una CADENA o un CARACTER (grammar.md 3.6). El lexema conserva el texto
     * fuente, con comillas y escapes, para que el literal siga ocupando una línea
     * en --tokens, --ast y el TAC. Tras un error el token se devuelve igual: el
     * compilador se detiene al acabar esta fase y así se informan todos los errores.
     *
     * @param comilla '"' para una cadena, '\'' para un carácter
     * @return el token del literal, cerrado o no
     */
    private Token literalDeTexto(char comilla) {
        int inicio = posicionActual;
        int contenido = 0; // caracteres Unicode o escapes: 'x' debe tener exactamente uno
        avanzar();

        while (!enFinDeLinea() && caracterActual() != comilla) {
            if (caracterActual() == '\\') {
                int columnaEscape = column;
                avanzar();
                if (enFinDeLinea()) {
                    break; // la barra no escapa nada: es un literal sin cerrar (E103)
                }
                if (ESCAPES.indexOf(caracterActual()) < 0) {
                    gestorErrores.agregar("E104", ErrorType.LEXICO,
                            "Secuencia de escape no válida: '\\" + Character.toString(codigoFuente.codePointAt(posicionActual)) + "'.",
                            line, columnaEscape);
                }
            }
            avanzarCaracterUnicode();
            contenido++;
        }

        boolean esCadena = comilla == '"';
        if (enFinDeLinea()) {
            gestorErrores.agregar("E103", ErrorType.LEXICO,
                    (esCadena ? "Cadena" : "Carácter") + " sin cerrar: falta " + comilla + " antes del fin de línea.",
                    startLine, startColumn);
        } else {
            avanzar(); // comilla de cierre
            if (!esCadena && contenido != 1) {
                gestorErrores.agregar("E107", ErrorType.LEXICO,
                        "Un literal de carácter debe contener exactamente un carácter.",
                        startLine, startColumn);
            }
        }

        return createToken(esCadena ? TokenType.CADENA : TokenType.CARACTER,
                codigoFuente.substring(inicio, posicionActual));
    }

    // FinDeLinea en grammar.md es solo LF: un CR puede formar parte de un literal
    private boolean enFinDeLinea() {
        return posicionActual >= codigoFuente.length() || caracterActual() == '\n';
    }

    // Avanza un carácter Unicode completo (dos char de Java si está fuera del BMP, como un emoji)
    private void avanzarCaracterUnicode() {
        for (int i = Character.charCount(codigoFuente.codePointAt(posicionActual)); i > 0; i--) {
            avanzar();
        }
    }

    // Solo dígitos ASCII: Character.isDigit acepta también '٣', '৩'... que luego no se pueden convertir a número.
    private static boolean esDigito(char c) {
        return c >= '0' && c <= '9';
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
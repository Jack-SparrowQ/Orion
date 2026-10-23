package com.compiler.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.compiler.error.ErrorHandler;
import com.compiler.error.ErrorType;
import com.compiler.lexer.Token;
import com.compiler.lexer.TokenType;
import com.compiler.parser.ast.*;

public class Parser {

    // Operador binario equivalente a cada asignación compuesta (+= -> +)
    private static final Map<TokenType, TokenType> OPERADOR_COMPUESTO = Map.of(
            TokenType.SUMA_Y_ASIGNACION, TokenType.SUMA,
            TokenType.RESTA_Y_ASIGNACION, TokenType.RESTA,
            TokenType.MULTI_Y_ASIGNACION, TokenType.MULTI,
            TokenType.DIVISION_Y_ASIGNACION, TokenType.DIVISION,
            TokenType.MODULO_Y_ASIGNACION, TokenType.MODULO);

    private final ErrorHandler gestorErrores;
    private final List<Token> tokens;
    private int actual = 0; // Puntero al token bajo análisis

    // Excepción interna: aborta la sentencia actual hasta sentenciaORecuperar().
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
        return token.tipo == TokenType.EOF ? "el fin del archivo" : "'" + token.lexema + "'";
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

    // Mira el token posterior al actual (el EOF si no hay más)
    private Token siguiente() {
        return tokens.get(Math.min(actual + 1, tokens.size() - 1));
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
         mensajeError + " Se encontró " + describir(tokenError) + ".");
    }
    
    //Metodo principal
    public List<Sentencia> parsear() {
        List<Sentencia> program = new ArrayList<>();
        while (!isAtEnd()) {
            sentenciaORecuperar(false).ifPresent(program::add);
        }
        return program;
    }

    // Parsea una sentencia. Si tiene un error (ya registrado en el ErrorHandler),
    // salta hasta la siguiente sentencia para seguir buscando errores.
    private Optional<Sentencia> sentenciaORecuperar(boolean enBloque) {
        try {
            return Optional.of(sentencia());
        } catch (ParseError e) {
            sincronizar(enBloque);
            return Optional.empty();
        }
    }

    // 2. El Enrutador: Decide qué regla gramatical usar viendo el token actual
    private Sentencia sentencia() {
        if (verificar(TokenType.LET) || verificar(TokenType.CONST)) {
            return declaracionVariable(); 
        }
        if (coinciden(TokenType.PRINT)) {
            return declaracionImprimir();
        }
        if (coinciden(TokenType.IF)) {
            return declaracionIf();
        }
        // Un identificador solo empieza sentencia si le sigue '=' (asignación)
        if (verificar(TokenType.IDENTIFICADOR)
                && (siguiente().tipo == TokenType.IGUAL || OPERADOR_COMPUESTO.containsKey(siguiente().tipo))) {
            return asignacion();
        }

        // Si no es ninguna de las anteriores, lanzamos error (por ahora)
        throw error(peek(), "E202", "Instruccion no reconocida: " + describir(peek()) + ".");
    }

    // Regla: declaracion -> "let" IDENTIFICADOR ( ":" tipo [ "=" expresion ] | "=" expresion ) ";"
    //                    | "const" IDENTIFICADOR [ ":" tipo ] "=" expresion ";"
    private Sentencia declaracionVariable() {
        // 1. Como ya verificamos en el paso anterior, avanzamos de forma segura y guardamos si fue 'let' o 'const'
        Token palabraClave = avanzar();

        // 2. Obligatoriamente debe seguir el nombre de la variable
        Token identificador = consumir(TokenType.IDENTIFICADOR, "Se esperaba el nombre de la variable.");

        // 3. Anotación de tipo opcional: ':' decide (LL(1))
        Token tipo = coinciden(TokenType.DOS_PUNTOS) ? tipo() : null;

        // 4. El '=' es obligatorio si no hay tipo (no habría de dónde inferirlo) o si es const
        Expresion valor = null;
        if (tipo == null) {
            consumir(TokenType.IGUAL, "Se esperaba ':' o '=' después del nombre de la variable.");
            valor = expresion();
        } else if (palabraClave.tipo == TokenType.CONST) {
            consumir(TokenType.IGUAL, "Una constante debe inicializarse: se esperaba '='.");
            valor = expresion();
        } else if (coinciden(TokenType.IGUAL)) {
            valor = expresion();
        }

        // 5. Obligatoriamente debe cerrar con ';'
        consumir(TokenType.PUNTO_Y_COMA, "Se esperaba ';' al final de la sentencia.");

        return new NodoDeclaracion(palabraClave, identificador, tipo, valor);
    }

    // Regla: tipo -> "int" | "float" | "char" | "string" | "bool"
    // 'void' no está: solo es válido como tipo de retorno.
    // shortcut: sin arreglos (T[]); añadir el bucle { "[" "]" } cuando existan los literales de arreglo.
    private Token tipo() {
        if (coinciden(TokenType.INT, TokenType.FLOAT, TokenType.CHAR, TokenType.STRING, TokenType.BOOL)) {
            return anterior();
        }
        throw error(peek(), "E201",
                "Se esperaba un tipo (int, float, char, string o bool). Se encontró " + describir(peek()) + ".");
    }

    // Regla: asignacion -> IDENTIFICADOR "=" expresion ";"
    //                   | IDENTIFICADOR ("+=" | "-=" | "*=" | "/=" | "%=") expresion ";"
    private Sentencia asignacion() {
        Token identificador = avanzar();
        Token operador = avanzar(); // '=' o compuesto (ya comprobado en sentencia())

        Expresion valor = expresion();
        consumir(TokenType.PUNTO_Y_COMA, "Se esperaba ';' al final de la asignación.");

        TokenType binario = OPERADOR_COMPUESTO.get(operador.tipo);
        if (binario != null) {
            // a += b se traduce a a = a + b: las demás fases no necesitan un nodo nuevo.
            // Los paréntesis conservan el orden: a *= b + 1 es a = a * (b + 1).
            if (valor instanceof NodoBinario) {
                valor = new NodoAgrupacion(valor);
            }
            Token op = new Token(binario, operador.lexema.substring(0, 1), operador.line, operador.column);
            valor = new NodoBinario(new NodoVariable(identificador), op, valor);
        }

        return new NodoAsignacion(identificador, valor);
    }

    // Regla: "print" "(" expresion ")" ";"
    private Sentencia declaracionImprimir() {
        consumir(TokenType.PARENTESIS_IZQ, "Se esperaba '(' despues de 'print'.");
        
        Expresion valorAImprimir = expresion(); // Leemos lo que está adentro
        
        consumir(TokenType.PARENTESIS_DER, "Se esperaba ')' despues del valor a imprimir.");
        consumir(TokenType.PUNTO_Y_COMA, "Se esperaba ';' al final de la instrucción print.");
        
        return new NodoImprimir(valorAImprimir);
    }

    // Regla: "if" "(" expresion ")" bloque ( "else" ( bloque | if ) )?
    private Sentencia declaracionIf() {
        consumir(TokenType.PARENTESIS_IZQ, "Se esperaba '(' despues de 'if'.");

        Expresion condicion = expresion(); // Leemos la condición

        consumir(TokenType.PARENTESIS_DER, "Se esperaba ')' despues de la condicion del if.");

        // El 'if' requiere un bloque de código
        consumir(TokenType.LLAVE_IZQ, "Se esperaba '{' antes del bloque del if.");
        NodoBloque cuerpo = parsearBloque();

        // Sin 'else', la rama falsa es un bloque vacío
        NodoBloque sino = new NodoBloque(List.of());
        if (coinciden(TokenType.ELSE)) {
            if (coinciden(TokenType.IF)) {
                // else if: el if anidado es la única sentencia de la rama falsa
                sino = new NodoBloque(List.of(declaracionIf()));
            } else {
                consumir(TokenType.LLAVE_IZQ, "Se esperaba '{' después de 'else'.");
                sino = parsearBloque();
            }
        }

        return new NodoIf(condicion, cuerpo, sino);
    }

    // Regla auxiliar para leer todo lo que está dentro de { }
    private NodoBloque parsearBloque() {
        List<Sentencia> sentenciasInternas = new ArrayList<>();
        
        // Seguimos leyendo sentencias hasta encontrar la llave derecha '}' o el EOF
        while (!verificar(TokenType.LLAVE_DER) && !isAtEnd()) {
            sentenciaORecuperar(true).ifPresent(sentenciasInternas::add);
        }
        
        consumir(TokenType.LLAVE_DER, "Se esperaba '}' al final del bloque.");
        return new NodoBloque(sentenciasInternas);
    }

    // ================================================================
    // EXPRESIONES (de menor a mayor precedencia, igual que en JavaScript)
    // ================================================================

    // expresion -> logicoOr
    private Expresion expresion() {
        return logicoOr();
    }

    // logicoOr -> logicoAnd ( "||" logicoAnd )*
    private Expresion logicoOr() {
        Expresion izquierda = logicoAnd();
        while (coinciden(TokenType.OR_LOGICO)) {
            Token operador = anterior();
            Expresion derecha = logicoAnd();
            izquierda = new NodoBinario(izquierda, operador, derecha);
        }
        return izquierda;
    }

    // logicoAnd -> igualdad ( "&&" igualdad )*
    private Expresion logicoAnd() {
        Expresion izquierda = igualdad();
        while (coinciden(TokenType.AND_LOGICO)) {
            Token operador = anterior();
            Expresion derecha = igualdad();
            izquierda = new NodoBinario(izquierda, operador, derecha);
        }
        return izquierda;
    }

    // igualdad -> comparacion ( ("==" | "!=") comparacion )*
    private Expresion igualdad() {
        Expresion izquierda = comparacion();
        while (coinciden(TokenType.COMPARACION_IGUAL, TokenType.DIFERENTE)) {
            Token operador = anterior();
            Expresion derecha = comparacion();
            izquierda = new NodoBinario(izquierda, operador, derecha);
        }
        return izquierda;
    }

    // comparacion -> termino ( ("<" | "<=" | ">" | ">=") termino )*
    private Expresion comparacion() {
        Expresion izquierda = termino();
        while (coinciden(TokenType.MENOR, TokenType.MENOR_IGUAL,
                         TokenType.MAYOR, TokenType.MAYOR_IGUAL)) {
            Token operador = anterior();
            Expresion derecha = termino();
            izquierda = new NodoBinario(izquierda, operador, derecha);
        }
        return izquierda;
    }

    // termino -> factor ( ("+" | "-") factor )*
    private Expresion termino() {
        Expresion izquierda = factor();
        while (coinciden(TokenType.SUMA, TokenType.RESTA)) {
            Token operador = anterior();
            Expresion derecha = factor();
            izquierda = new NodoBinario(izquierda, operador, derecha);
        }
        return izquierda;
    }

    // factor -> unario ( ("*" | "/" | "%") unario )*
    private Expresion factor() {
        Expresion izquierda = unario();
        while (coinciden(TokenType.MULTI, TokenType.DIVISION, TokenType.MODULO)) {
            Token operador = anterior();
            Expresion derecha = unario();
            izquierda = new NodoBinario(izquierda, operador, derecha);
        }
        return izquierda;
    }

    // unario -> ("!" | "-") unario | primario
    private Expresion unario() {
        if (coinciden(TokenType.NEGACION_LOGICA, TokenType.RESTA)) {
            Token operador = anterior();
            Expresion operando = unario();
            return new NodoUnario(operador, operando);
        }
        return primario();
    }

    // primario -> NUMERO | IDENTIFICADOR | "true" | "false" | "(" expresion ")"
    private Expresion primario() {
        if (coinciden(TokenType.NUMERO, TokenType.TRUE, TokenType.FALSE)) {
            return new NodoLiteral(anterior());
        }

        if (coinciden(TokenType.IDENTIFICADOR)) {
            return new NodoVariable(anterior());
        }

        if (coinciden(TokenType.PARENTESIS_IZQ)) {
            Expresion interior = expresion();
            consumir(TokenType.PARENTESIS_DER, "Se esperaba ')' después de la expresión.");
            return new NodoAgrupacion(interior);
        }

        throw error(peek(), "E203",
                "Se esperaba una expresión (número, variable, true/false o '('). Se encontró "
                        + describir(peek()) + ".");
    }

    // Modo pánico: descarta tokens hasta pasar un ';' o hasta el inicio de otra
    // sentencia (o el '}' que cierra el bloque). Consume al menos un token para
    // no quedar en un bucle infinito, salvo el '}' del bloque actual, que debe
    // quedar para cerrarlo (el bucle de parsearBloque termina en él).
    // Si descarta un '{' (p. ej. error en la cabecera de un if), descarta el
    // bloque entero hasta su '}' para no analizar su cuerpo fuera de contexto.
    private void sincronizar(boolean enBloque) {
        if (enBloque && verificar(TokenType.LLAVE_DER)) {
            return;
        }

        int llavesAbiertas = 0;
        do {
            Token descartado = avanzar();

            if (descartado.tipo == TokenType.LLAVE_IZQ) {
                llavesAbiertas++;
            } else if (descartado.tipo == TokenType.LLAVE_DER && llavesAbiertas > 0
                    && --llavesAbiertas == 0) {
                return;
            }

            if (llavesAbiertas == 0) {
                if (descartado.tipo == TokenType.PUNTO_Y_COMA) {
                    return;
                }
                switch (peek().tipo) {
                    case LET, CONST, PRINT, IF, LLAVE_DER -> {
                        return;
                    }
                    default -> { }
                }
            }
        } while (!isAtEnd());
    }
}
package com.compiler.lexer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.compiler.error.CompilationError;
import com.compiler.error.ErrorHandler;

public class LexerTest {

    private final ErrorHandler errores = new ErrorHandler();

    private List<Token> lex(String codigo) {
        Tokenizer tokenizer = new Tokenizer(new Lexer(codigo, errores));
        tokenizer.run();
        return tokenizer.getTokensList();
    }

    /** Tipos de los tokens, sin el EOF final. */
    private String tipos(String codigo) {
        List<Token> tokens = lex(codigo);
        assertEquals(TokenType.EOF, tokens.get(tokens.size() - 1).tipo);
        return tokens.subList(0, tokens.size() - 1).stream()
                .map(t -> t.tipo.name())
                .collect(Collectors.joining(" "));
    }

    private String codigosDeError() {
        return errores.obtenerErrores().stream()
                .map(CompilationError::toString)
                .map(e -> e.substring(0, e.indexOf('[')))
                .collect(Collectors.joining(" "));
    }

    @Test
    void declaracion() {
        assertEquals("LET IDENTIFICADOR IGUAL NUMERO PUNTO_Y_COMA", tipos("let x = 42;"));
        assertEquals("", codigosDeError());
    }

    @Test
    void operadoresDeDosCaracteres() {
        assertEquals("COMPARACION_IGUAL DIFERENTE MENOR_IGUAL MAYOR_IGUAL AND_LOGICO OR_LOGICO "
                + "SUMA_Y_ASIGNACION RESTA_Y_ASIGNACION MULTI_Y_ASIGNACION DIVISION_Y_ASIGNACION MODULO_Y_ASIGNACION",
                tipos("== != <= >= && || += -= *= /= %="));
    }

    @Test
    void operadoresDeUnCaracter() {
        assertEquals("IGUAL MENOR MAYOR NEGACION_LOGICA SUMA RESTA MULTI DIVISION MODULO PUNTO",
                tipos("= < > ! + - * / % ."));
    }

    @Test
    void corchetes() {
        assertEquals("CORCHETE_IZQ CORCHETE_DER", tipos("[ ]"));
    }

    @Test
    void palabrasReservadasEIdentificadores() {
        assertEquals("IF ELSE PRINT TRUE FALSE IDENTIFICADOR IDENTIFICADOR",
                tipos("if else print true false iffy _x1"));
    }

    @Test
    void lexemaYPosicion() {
        List<Token> tokens = lex("let a = 10;\n  print(a);");
        Token diez = tokens.get(3);
        assertEquals("10", diez.lexema);
        assertEquals(1, diez.line);
        assertEquals(9, diez.column);
        Token print = tokens.get(5);
        assertEquals(TokenType.PRINT, print.tipo);
        assertEquals(2, print.line);
        assertEquals(3, print.column);
    }

    @Test
    void caracterNoReconocido_seSaltaYContinua() {
        assertEquals("LET IDENTIFICADOR IGUAL PUNTO_Y_COMA", tipos("let a = @;"));
        assertEquals("E100", codigosDeError());
    }

    @Test
    void ampersandYBarraSueltos() {
        assertEquals("IDENTIFICADOR IDENTIFICADOR", tipos("a & b"));
        assertEquals("E101", codigosDeError());
        assertEquals("IDENTIFICADOR IDENTIFICADOR", tipos("a | b"));
        assertEquals("E101 E102", codigosDeError());
    }

    /** Solo dígitos ASCII forman números: '٣' (árabe-índico) es un carácter no reconocido. */
    @Test
    void digitosNoAscii() {
        assertEquals("NUMERO", tipos("٣1"));
        assertEquals("E100", codigosDeError());
    }

    @Test
    void comentarioDeLinea() {
        assertEquals("LET IDENTIFICADOR IGUAL NUMERO PUNTO_Y_COMA PRINT",
                tipos("let a = 1; // a / 2 = @\nprint"));
        assertEquals("", codigosDeError());
        assertEquals("", tipos("// solo un comentario"));
    }

    @Test
    void numerosDecimales() {
        List<Token> tokens = lex("3.14 10 2.0");
        assertEquals("3.14", tokens.get(0).lexema);
        assertEquals("10", tokens.get(1).lexema);
        assertEquals("2.0", tokens.get(2).lexema);
        // Un '.' sin dígitos detrás no forma parte del número
        assertEquals("NUMERO PUNTO", tipos("3."));
        assertEquals("NUMERO PUNTO IDENTIFICADOR", tipos("3.x"));
    }

    @Test
    void entradaVacia() {
        assertEquals("", tipos(""));
    }
}

package com.compiler.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.compiler.error.CompilationError;
import com.compiler.error.ErrorHandler;
import com.compiler.lexer.Lexer;
import com.compiler.lexer.Tokenizer;
import com.compiler.parser.Nodes.NodoDeclaracion;
import com.compiler.parser.Nodes.NodoIf;
import com.compiler.parser.Nodes.NodoImprimir;

public class ParserTest {

    private final ErrorHandler errores = new ErrorHandler();

    private List<ElementoAST> parse(String codigo) {
        Tokenizer tokenizer = new Tokenizer(new Lexer(codigo, errores));
        tokenizer.run();
        return new Parser(tokenizer.getTokensList(), errores).parsear();
    }

    /** Forma parentizada de la expresión de un print. */
    private String expr(String expresion) {
        List<ElementoAST> programa = parse("print(" + expresion + ");");
        assertEquals("", errores(), "errores inesperados");
        return ((NodoImprimir) programa.get(0)).expresion.toString();
    }

    private String errores() {
        return errores.obtenerErrores().stream()
                .map(CompilationError::toString)
                .collect(Collectors.joining("\n"));
    }

    @Test
    void precedencia() {
        assertEquals("(a + (b * 2))", expr("a + b * 2"));
        assertEquals("((a * b) + 2)", expr("a * b + 2"));
        assertEquals("((a < b) == (c > d))", expr("a < b == c > d"));
        assertEquals("(a || (b && c))", expr("a || b && c"));
    }

    @Test
    void asociatividadIzquierda() {
        assertEquals("((a - b) - c)", expr("a - b - c"));
        assertEquals("((a / b) % c)", expr("a / b % c"));
    }

    @Test
    void unariosYAgrupacion() {
        assertEquals("(-(!a))", expr("-!a"));
        assertEquals("(group((a + b)) * c)", expr("(a + b) * c"));
        assertEquals("true", expr("true"));
    }

    @Test
    void sentencias() {
        List<ElementoAST> programa = parse("let a = 1; const b = a; if (a > 0) { print(a); print(b); }");
        assertEquals("", errores());
        assertEquals(3, programa.size());
        assertInstanceOf(NodoDeclaracion.class, programa.get(0));
        assertInstanceOf(NodoDeclaracion.class, programa.get(1));
        NodoIf nodoIf = assertInstanceOf(NodoIf.class, programa.get(2));
        assertEquals(2, nodoIf.bloqueTrue.sentencias.size());
    }

    @Test
    void falta_igual() {
        parse("let x 5;");
        assertEquals("E201[SINTACTICO] Línea 1, columna 7: Se esperaba '=' después del nombre de la variable."
                + " Se encontró '5'.", errores());
    }

    @Test
    void falta_puntoYComaAlFinal() {
        parse("let a = 1");
        assertEquals("E201[SINTACTICO] Línea 1, columna 10: Se esperaba ';' al final de la sentencia."
                + " Se encontró el fin del archivo.", errores());
    }

    @Test
    void falta_expresion() {
        parse("let c = 10 + ;");
        assertEquals(1, errores.obtenerErrores().size());
        assertEquals("E203", errores.obtenerErrores().get(0).toString().substring(0, 4));
    }

    @Test
    void bloqueSinCerrar() {
        parse("if (a) { print(a);");
        assertEquals(1, errores.obtenerErrores().size());
        assertEquals(true, errores().contains("Se esperaba '}' al final del bloque."), errores());
    }
}

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
import com.compiler.parser.ast.NodoDeclaracion;
import com.compiler.parser.ast.NodoIf;
import com.compiler.parser.ast.NodoImprimir;
import com.compiler.parser.ast.Sentencia;

public class ParserTest {

    private final ErrorHandler errores = new ErrorHandler();

    private List<Sentencia> parse(String codigo) {
        Tokenizer tokenizer = new Tokenizer(new Lexer(codigo, errores));
        tokenizer.run();
        return new Parser(tokenizer.getTokensList(), errores).parsear();
    }

    /** Forma parentizada de la expresión de un print. */
    private String expr(String expresion) {
        List<Sentencia> programa = parse("print(" + expresion + ");");
        assertEquals("", errores(), "errores inesperados");
        return ((NodoImprimir) programa.get(0)).expresion().toString();
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
        List<Sentencia> programa = parse("let a = 1; const b = a; if (a > 0) { print(a); print(b); }");
        assertEquals("", errores());
        assertEquals(3, programa.size());
        assertInstanceOf(NodoDeclaracion.class, programa.get(0));
        assertInstanceOf(NodoDeclaracion.class, programa.get(1));
        NodoIf nodoIf = assertInstanceOf(NodoIf.class, programa.get(2));
        assertEquals(2, nodoIf.bloqueTrue().sentencias().size());
    }

    @Test
    void ifSinElse_ramaFalsaVacia() {
        NodoIf nodoIf = (NodoIf) parse("if (a) { print(a); }").get(0);
        assertEquals(0, nodoIf.bloqueFalse().sentencias().size());
    }

    @Test
    void ifElseIfElse() {
        List<Sentencia> programa = parse("if (a) { print(1); } else if (b) { print(2); } else { print(3); print(4); }");
        assertEquals("", errores());
        assertEquals(1, programa.size());
        NodoIf exterior = (NodoIf) programa.get(0);
        // else if: el if anidado es la única sentencia de la rama falsa
        NodoIf anidado = assertInstanceOf(NodoIf.class, exterior.bloqueFalse().sentencias().get(0));
        assertEquals("b", anidado.condicion().toString());
        assertEquals(2, anidado.bloqueFalse().sentencias().size());
    }

    @Test
    void elseSinLlave() {
        parse("if (a) { print(a); } else print(b);");
        assertEquals("E201@1", codigosYLineas());
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

    // ------------------------------------------------------------
    // Recuperación de errores (modo pánico)
    // ------------------------------------------------------------

    /** Códigos y líneas de los errores, uno por línea: "E201@1". */
    private String codigosYLineas() {
        return errores.obtenerErrores().stream()
                .map(e -> e.toString().substring(0, e.toString().indexOf('['))
                        + "@" + e.toString().replaceAll(".*Línea (\\d+).*", "$1"))
                .collect(Collectors.joining(" "));
    }

    @Test
    void reportaUnErrorPorSentencia() {
        List<Sentencia> programa = parse("let = 1;\nprint(;\nlet c = ;\nlet ok = 2;");
        assertEquals("E201@1 E203@2 E203@3", codigosYLineas());
        // La sentencia válida después de los errores sigue en el árbol
        assertEquals(1, programa.size());
    }

    @Test
    void errorDentroDeBloque_noRompeElBloque() {
        List<Sentencia> programa = parse("if (a) {\n let = 1;\n print(a);\n}\nprint(b);");
        assertEquals("E201@2", codigosYLineas());
        NodoIf nodoIf = assertInstanceOf(NodoIf.class, programa.get(0));
        assertEquals(1, nodoIf.bloqueTrue().sentencias().size());
        assertEquals(2, programa.size());
    }

    /** El error está justo en la '}' de cierre: no debe consumirse ni provocar otro error. */
    @Test
    void errorEnLaLlaveDeCierre() {
        parse("if (a) { let x = }\nprint(a);");
        assertEquals("E203@1", codigosYLineas());
    }

    /** Error en la cabecera del if: se descarta el bloque entero, sin errores en cascada. */
    @Test
    void errorEnCabeceraDeIf_saltaElBloque() {
        List<Sentencia> programa = parse("if (a {\n print(a);\n}\nprint(;\nprint(b);");
        assertEquals("E201@1 E203@4", codigosYLineas());
        assertEquals(1, programa.size());
    }

    @Test
    void llaveSuelta_noBuclea() {
        parse("}\nprint(a);\n}");
        assertEquals("E202@1 E202@3", codigosYLineas());
    }

    @Test
    void bloqueSinCerrar() {
        parse("if (a) { print(a);");
        assertEquals(1, errores.obtenerErrores().size());
        assertEquals(true, errores().contains("Se esperaba '}' al final del bloque."), errores());
    }
}

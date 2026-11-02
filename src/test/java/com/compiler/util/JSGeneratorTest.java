package com.compiler.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.compiler.error.ErrorHandler;
import com.compiler.lexer.Lexer;
import com.compiler.lexer.Tokenizer;
import com.compiler.parser.Parser;
import com.compiler.parser.ast.Sentencia;

public class JSGeneratorTest {

    /** JavaScript de un programa sin errores. */
    private String js(String codigo) {
        ErrorHandler errores = new ErrorHandler();
        Tokenizer tokenizer = new Tokenizer(new Lexer(codigo, errores));
        tokenizer.run();
        List<Sentencia> programa = new Parser(tokenizer.getTokensList(), errores).parsear();
        assertEquals(List.of(), errores.obtenerErrores());
        return new JSGenerator().generar(programa);
    }

    @Test
    void forIn() {
        assertEquals("""
                function f(s) {
                    for (const c of s + "!") {
                        if (c == 'a') {
                            continue;
                        }
                        console.log(c);
                    }
                }
                """, js("fn f(s: string) { for (c in s + \"!\") { if (c == 'a') { continue; } print(c); } }"));
    }

    @Test
    void literalesSeCopianTalCual() {
        assertEquals("let s = \"a\\n\\\"b\" + 'c';\n", js("let s = \"a\\n\\\"b\" + 'c';"));
    }

    /** "\01" en JS sería octal; en Orion es NUL seguido de '1'. */
    @Test
    void nulSeEscribeEnHexadecimal() {
        assertEquals("console.log(\"\\x001\");\n", js("print(\"\\01\");"));
    }

    /** "\\0" es una barra y un cero: no contiene el escape \0. */
    @Test
    void barraEscapadaSeguidaDeCero_noCambia() {
        assertEquals("console.log(\"\\\\0\");\n", js("print(\"\\\\0\");"));
    }

    /** Un CR crudo es válido en Orion, pero en JS terminaría la cadena. */
    @Test
    void crCrudoSeEscapa() {
        assertEquals("console.log(\"a\\rb\");\n", js("print(\"a\rb\");"));
    }
}

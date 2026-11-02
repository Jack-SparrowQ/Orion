package com.compiler.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.compiler.error.ErrorHandler;
import com.compiler.lexer.Lexer;
import com.compiler.lexer.Tokenizer;
import com.compiler.parser.Parser;
import com.compiler.parser.ast.Sentencia;
import com.compiler.semantic.SemanticAnalyzer;
import com.compiler.semantic.SymbolTable;

public class JSGeneratorTest {

    /**
     * JavaScript de un programa sintácticamente válido. Los errores semánticos se ignoran:
     * varias pruebas usan nombres sin declarar, y una expresión de tipo desconocido se copia tal cual.
     */
    private String js(String codigo) {
        ErrorHandler errores = new ErrorHandler();
        Tokenizer tokenizer = new Tokenizer(new Lexer(codigo, errores));
        tokenizer.run();
        List<Sentencia> programa = new Parser(tokenizer.getTokensList(), errores).parsear();
        assertEquals(List.of(), errores.obtenerErrores());

        SymbolTable tabla = new SymbolTable();
        SemanticAnalyzer.analyzeSymbols(programa, tabla, errores);
        return new JSGenerator(SemanticAnalyzer.analyzeSemantic(programa, tabla, errores)).generar(programa);
    }

    @Test
    void concatenacion_usaElAyudante() {
        String codigo = js("let s = \"a\";\nprint(s + \"b\" + s);");
        assertTrue(codigo.startsWith("function $elementos"), codigo);
        assertTrue(codigo.endsWith("console.log($concatenar($concatenar(s, \"b\"), s));\n"), codigo);
    }

    @Test
    void concatenacion_conParentesis() {
        assertTrue(js("let s = \"a\";\nprint(s + (s + \"b\"));")
                .endsWith("console.log($concatenar(s, ($concatenar(s, \"b\"))));\n"));
    }

    /** s += e se reduce a s = s + e, así que también pasa por el ayudante. */
    @Test
    void concatenacionCompuesta() {
        assertTrue(js("let s = \"a\";\ns += \"b\" + s;").endsWith("s = $concatenar(s, ($concatenar(\"b\", s)));\n"));
    }

    @Test
    void concatenacionCompuestaEnElemento() {
        assertTrue(js("let v = [\"a\"];\nv[0] += \"b\";")
                .endsWith("$asignar(v, 0, ($v) => $concatenar($v, \"b\"));\n"));
    }

    /** Sumas, restas y compuestas numéricas no cambian ni arrastran los ayudantes. */
    @Test
    void sumaNumerica_noEsConcatenacion() {
        assertEquals("let n = 1 + 2.5;\nn = n + 1;\n", js("let n = 1 + 2.5;\nn += 1;"));
        assertTrue(js("let v = [1.5];\nv[0] += 2;").endsWith("$asignar(v, 0, ($v) => $v + (2));\n"));
    }

    /** Dos expresiones iguales son nodos distintos: cada una conserva su propio tipo. */
    @Test
    void expresionesIguales_enAmbitosDistintos() {
        String codigo = js("""
                fn f(a: string, b: string): string { return a + b; }
                fn g(a: int, b: int): int { return a + b; }
                """);
        assertTrue(codigo.contains("return $concatenar(a, b);"), codigo);
        assertTrue(codigo.contains("return a + b;"), codigo);
    }

    /** Un programa sin accesos a elementos no lleva los ayudantes. */
    @Test
    void arregloSinAccesos_sinAyudantes() {
        assertEquals("let v = [1, [2, 3], []];\n", js("let v = [1, [2, 3], []];"));
    }

    @Test
    void accesosUsanAyudantes() {
        String codigo = js("print(m[0][i].length);");
        assertTrue(codigo.startsWith("function $elementos"), codigo);
        assertTrue(codigo.endsWith("console.log($longitud($en($en(m, 0), i)));\n"), codigo);
    }

    @Test
    void asignacionAElemento() {
        assertTrue(js("m[0][i] = a + 1;").endsWith("$asignar($en(m, 0), i, () => a + 1);\n"));
    }

    /** El paréntesis conserva el orden: v[0] *= b + 1 es v[0] * (b + 1). */
    @Test
    void asignacionCompuestaAElemento() {
        assertTrue(js("v[0] *= b + 1;").endsWith("$asignar(v, 0, ($v) => $v * (b + 1));\n"));
    }

    @Test
    void nullSeCopiaTalCual() {
        assertEquals("let s = null;\nconsole.log(s == null);\n", js("let s: string = null;\nprint(s == null);"));
    }

    @Test
    void forIn() {
        assertEquals("""
                function f(s) {
                    for (const c of s) {
                        if (c == 'a') {
                            continue;
                        }
                        console.log(c);
                    }
                }
                """, js("fn f(s: string) { for (c in s) { if (c == 'a') { continue; } print(c); } }"));
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

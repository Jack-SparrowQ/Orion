package com.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.compiler.cli.Options.Mode;

public class CompilatorTest {
    
    private static String lines(String... lines) {
        return String.join("\n", lines) + "\n";
    }

 
    /** E100: caracter no reconocido. */
    @Test
    void errorLexico_caracterNoReconocido() {
        ResultadoCompilacion r = ResultadoCompilacion.ejecutar(lines("let a = @;"));
        GoldenFile.verificar("error_lexico", r.informe());
    }
 
    /** E203: falta una expresion. */
    @Test
    void errorSintactico_expresionFaltante() {
        ResultadoCompilacion r = ResultadoCompilacion.ejecutar(lines("let c = 10 + ;"));
        GoldenFile.verificar("error_sintactico_expresion", r.informe());
    }
 
    /** E202: instrucción no reconocida. */
    @Test
    void errorSintactico_instruccionNoReconocida() {
        ResultadoCompilacion r = ResultadoCompilacion.ejecutar(lines("let a = 1;", "5;"));
        GoldenFile.verificar("error_sintactico_instruccion", r.informe());
    }

 
    /** E301 en print. Regresion de F1: el error se reportaba dos veces. */
    @Test
    void errorSemantico_printNoDeclarado() {
        ResultadoCompilacion r = ResultadoCompilacion.ejecutar(lines("print(x);"));
        assertEquals(1, ResultadoCompilacion.contar(r.stderr, "E301"), "ocurrencias de E301");
        GoldenFile.verificar("error_semantico_print_no_declarado", r.informe());
    }
 
    // /** Caracteriza F2: usar una variable antes de declararla NO es error. */
    // @Test
    // void usoAntesDeDeclarar_noGeneraError() {
    //     ResultadoCompilacion r = ResultadoCompilacion.ejecutar(lines("let a = b;", "let b = 1;"));
    //     // Caracteriza F2 (defecto conocido): no se reporta ningun error semantico.
    //     assertFalse(r.stdout.contains("E301"), "no debe haber E301");
    //     assertFalse(r.archivos.containsKey("program.js"), "se genera program.js");
    //     GoldenFile.verificar("uso_antes_de_declarar", r.informe());
    // }
 
    /** E301 en la condicion de un if. */
    @Test
    void errorSemantico_condicionIfNoDeclarada() {
        ResultadoCompilacion r = ResultadoCompilacion.ejecutar(lines("if (z > 0) {", " print(1);", "}"));
        GoldenFile.verificar("error_semantico_condicion_if", r.informe());
    }


    // ------------------------------------------------------------
    // Modos de compilacion (programa "hello" de docs/CLI.md)
    // ------------------------------------------------------------

    private static final String HELLO = lines("let a = 10;", "let b = 3;", "print(a + b * 2);");

    /** Modo por defecto: solo genera JavaScript (ya no escribe el TAC). */
    @Test
    void compilar_generaSoloJavaScript() {
        ResultadoCompilacion r = ResultadoCompilacion.ejecutar(HELLO);
        assertEquals(0, r.exitCode);
        assertEquals("", r.stdout);
        assertEquals("orion: generated programa.js\n", r.stderr);
        assertEquals(java.util.Set.of("programa.js"), r.archivos.keySet());
    }

    @Test
    void emitIr_generaSoloTac() {
        ResultadoCompilacion r = ResultadoCompilacion.ejecutar(HELLO, Mode.EMIT_IR);
        assertEquals(0, r.exitCode);
        assertEquals(java.util.Set.of("programa.tac"), r.archivos.keySet());
        assertTrue(r.archivos.get("programa.tac").contains("print t2"), r.archivos.toString());
    }

    @Test
    void check_noGeneraArchivos() {
        ResultadoCompilacion r = ResultadoCompilacion.ejecutar(HELLO, Mode.CHECK);
        assertEquals(0, r.exitCode);
        assertEquals("", r.stdout);
        assertTrue(r.archivos.isEmpty());
    }

    @Test
    void tokens_imprimeTokensEnStdout() {
        ResultadoCompilacion r = ResultadoCompilacion.ejecutar(HELLO, Mode.TOKENS);
        assertEquals(0, r.exitCode);
        assertTrue(r.stdout.startsWith("Token: LET"), r.stdout);
        assertTrue(r.stdout.contains("Token: EOF"), r.stdout);
        assertEquals("", r.stderr);
        assertTrue(r.archivos.isEmpty());
    }

    /** Con error lexico: tokens reconocidos por stdout, diagnosticos por stderr. */
    @Test
    void tokens_conErrorLexico() {
        ResultadoCompilacion r = ResultadoCompilacion.ejecutar(lines("let a = @;"), Mode.TOKENS);
        assertEquals(1, r.exitCode);
        assertTrue(r.stdout.contains("Token: LET"), r.stdout);
        assertTrue(r.stderr.contains("E100"), r.stderr);
    }

    @Test
    void ast_imprimeArbolEnStdout() {
        ResultadoCompilacion r = ResultadoCompilacion.ejecutar(HELLO, Mode.AST);
        assertEquals(0, r.exitCode);
        assertEquals(3, ResultadoCompilacion.contar(r.stdout, "├──"), r.stdout);
        assertTrue(r.archivos.isEmpty());
    }

    // ------------------------------------------------------------
    // if / else if / else
    // ------------------------------------------------------------

    private static final String IF_ELSE = lines(
            "let a = 1;",
            "if (a > 0) {",
            "    print(a);",
            "} else if (a < 0) {",
            "    print(-a);",
            "} else {",
            "    print(0);",
            "}");

    @Test
    void ifElse_generaJavaScript() {
        ResultadoCompilacion r = ResultadoCompilacion.ejecutar(IF_ELSE);
        assertEquals(0, r.exitCode, r.stderr);
        assertEquals(lines(
                "let a = 1;",
                "if (a > 0) {",
                "    console.log(a);",
                "} else {",
                "    if (a < 0) {",
                "        console.log(-a);",
                "    } else {",
                "        console.log(0);",
                "    }",
                "}"), r.archivos.get("programa.js"));
    }

    @Test
    void ifElse_generaTac() {
        ResultadoCompilacion r = ResultadoCompilacion.ejecutar(IF_ELSE, Mode.EMIT_IR);
        assertEquals(lines(
                "    a = 1",
                "    t1 = a > 0",
                "    ifFalse t1 goto L1",
                "    print a",
                "    goto L2",
                "L1:",
                "    t2 = a < 0",
                "    ifFalse t2 goto L3",
                "    t3 = -a",
                "    print t3",
                "    goto L4",
                "L3:",
                "    print 0",
                "L4:",
                "L2:"), r.archivos.get("programa.tac"));
    }

    @Test
    void errorSemanticoEnElse() {
        ResultadoCompilacion r = ResultadoCompilacion.ejecutar(lines("if (true) { } else { print(z); }"));
        assertEquals(1, r.exitCode);
        assertTrue(r.stderr.contains("E301[SEMANTICO] Línea 1, columna 28"), r.stderr);
    }

    // ------------------------------------------------------------
    // Asignacion
    // ------------------------------------------------------------

    private static final String ASIGNACION = lines("let a = 1;", "a = a + 2;", "print(a);");

    @Test
    void asignacion_generaJavaScriptYTac() {
        ResultadoCompilacion js = ResultadoCompilacion.ejecutar(ASIGNACION);
        assertEquals(0, js.exitCode, js.stderr);
        assertEquals(lines("let a = 1;", "a = a + 2;", "console.log(a);"), js.archivos.get("programa.js"));

        ResultadoCompilacion tac = ResultadoCompilacion.ejecutar(ASIGNACION, Mode.EMIT_IR);
        assertEquals(lines("    a = 1", "    t1 = a + 2", "    a = t1", "    print a"),
                tac.archivos.get("programa.tac"));
    }

    @Test
    void asignacion_aConstante_E305() {
        ResultadoCompilacion r = ResultadoCompilacion.ejecutar(lines("const k = 1;", "k = 2;"));
        assertEquals(1, r.exitCode);
        assertTrue(r.stderr.contains("E305[SEMANTICO] Línea 2, columna 1: No se puede reasignar la constante 'k'"),
                r.stderr);
    }

    @Test
    void asignacion_aNoDeclarada_E301() {
        ResultadoCompilacion r = ResultadoCompilacion.ejecutar(lines("x = 2;"));
        assertEquals(1, r.exitCode);
        assertTrue(r.stderr.contains("E301[SEMANTICO] Línea 1, columna 1"), r.stderr);
    }

    /** Si no se puede escribir el artefacto: error por stderr y codigo 3. */
    @Test
    void fallaEscritura_devuelveErrorIo() {
        ResultadoCompilacion r = ResultadoCompilacion.ejecutar(HELLO, Mode.COMPILE, "programa.js");
        assertEquals(3, r.exitCode);
        assertTrue(r.stderr.contains("orion: error: cannot write"), r.stderr);
    }
}

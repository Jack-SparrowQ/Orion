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
        ResultadoCompilacion r = ResultadoCompilacion.ejecutar(lines("let a = b;", "b = 1;"));
        GoldenFile.verificar("error_sintactico_instruccion", r.informe());
    }

 
    /** E301 en print. Caracteriza F1: el error se reporta DOS veces. */
    @Test
    void errorSemantico_printNoDeclarado() {
        ResultadoCompilacion r = ResultadoCompilacion.ejecutar(lines("print(x);"));
        // Caracteriza F1 (defecto conocido): el mismo E301 aparece dos veces.
        assertEquals(2, ResultadoCompilacion.contar(r.stderr, "E301"), "ocurrencias de E301");
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

    /** Si no se puede escribir el artefacto: error por stderr y codigo 3. */
    @Test
    void fallaEscritura_devuelveErrorIo() {
        ResultadoCompilacion r = ResultadoCompilacion.ejecutar(HELLO, Mode.COMPILE, "programa.js");
        assertEquals(3, r.exitCode);
        assertTrue(r.stderr.contains("orion: error: cannot write"), r.stderr);
    }
}

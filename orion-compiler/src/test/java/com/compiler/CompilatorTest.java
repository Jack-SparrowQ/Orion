package com.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

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
 
    /** Instruccion no reconocida. Caracteriza F8: el codigo es E2002 (4 digitos). */
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
        assertEquals(2, ResultadoCompilacion.contar(r.stdout, "E301"), "ocurrencias de E301");
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
    // Politica de fallo de escritura de archivos
    // (se comprueban propiedades, no el texto completo, porque el
    //  mensaje de IOException depende del sistema operativo)
    // ------------------------------------------------------------
 
    /** Si no se puede escribir el TAC: aviso por stderr y la compilacion continua. */
    // @Test
    // void fallaEscrituraTac_noEsFatal() {
    //     ResultadoCompilacion r = ResultadoCompilacion.ejecutar(
    //             lines("let a = 1;", "print(a);"), "programa.tac");
 
    //     assertTrue(r.stderr.contains("[AVISO] No se pudo generar programa.tac:"), "aviso del TAC");
    //     assertTrue(r.archivos.containsKey("program.js"), "se sigue generando program.js");
    //     assertTrue(r.stdout.contains("FINALIZADA CORRECTAMENTE"), "la compilacion termina");
    // }
 
    // /** Si no se puede escribir el JS: error por stderr y la compilacion se detiene. */
    // @Test
    // void fallaEscrituraJs_esFatal() {
    //     ResultadoCompilacion r = ResultadoCompilacion.ejecutar(
    //             lines("let a = 1;", "print(a);"), "program.js");
 
    //     assertTrue(r.stderr.contains("[ERROR] No se pudo generar el archivo."), "error del JS");
    //     assertFalse(r.stdout.contains("FINALIZADA CORRECTAMENTE"), "no llega al banner final");
    // }
}

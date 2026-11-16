package com.compiler.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.compiler.ir.Cuadrupla;
import com.compiler.ir.ProgramaIR;
import com.compiler.semantic.Type;

class GeneradorJSTest {
    @Test
    void cuerpoSinEtiquetas_esLinealSinAyudantesNiDespachador() {
        ProgramaIR programa = new ProgramaIR(List.of(), List.of(
                new Cuadrupla("+", "1", "2", "$t1"),
                new Cuadrupla("=", "$t1", null, "a"),
                new Cuadrupla("print", "a", null, null)), Map.of("a", Type.INT, "$t1", Type.INT));
        assertEquals("""
                let $t1, a;
                $t1 = 1 + 2;
                a = $t1;
                console.log(a);
                """, new GeneradorJS().generar(programa));
    }

    @Test
    void programaVacio_noEmiteDeclaracionesNiDespachador() {
        assertEquals("", new GeneradorJS().generar(new ProgramaIR(List.of(), List.of(), Map.of())));
    }

    @Test
    void printn_conVariableCharConservaElFormatoLiteral() {
        ProgramaIR programa = new ProgramaIR(List.of(), List.of(
                new Cuadrupla("param", "c", null, null),
                new Cuadrupla("param", "1", null, null),
                new Cuadrupla("printn", "2", null, null)), Map.of("c", Type.CHAR));
        assertEquals("let c;\nconsole.log(\"%s\", c, 1);\n", new GeneradorJS().generar(programa));
    }

    @Test
    void saltoSinEtiqueta_noGeneraUnDespachadorInvalido() {
        ProgramaIR programa = new ProgramaIR(List.of(), List.of(new Cuadrupla("goto", null, null, "$L1")), Map.of());
        assertThrows(IllegalArgumentException.class, () -> new GeneradorJS().generar(programa));
    }
}

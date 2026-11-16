package com.compiler;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.compiler.cli.Options.Mode;

class OptimizacionTest {
    @Test
    void puntoFijoEliminaRamaYPropagaEntreBloquesQueQuedanUnidos() {
        String fuente = "let x = 1; if (false) { x = 2; print(x); } print(x + 3);";
        ResultadoCompilacion js = ResultadoCompilacion.ejecutar(fuente);
        assertEquals(0, js.exitCode, js.stderr);
        assertEquals("console.log(4);\n", js.archivos.get("programa.js"));
        ResultadoCompilacion ir = ResultadoCompilacion.ejecutar(fuente, Mode.EMIT_IR);
        assertEquals(0, ir.exitCode, ir.stderr);
        assertEquals("    print 4\n", ir.archivos.get("programa.tac"));
    }

    @Test
    void condicionMuertaDesapareceTrasQuitarSaltoAlSiguiente() {
        ResultadoCompilacion r = ResultadoCompilacion.ejecutar("fn f(p: int) { if (p > 0) {} print(1); } f(2);");
        assertEquals(0, r.exitCode, r.stderr);
        String js = r.archivos.get("programa.js");
        assertFalse(js.contains("switch"), js);
        assertFalse(js.contains("$t"), js);
        assertFalse(js.contains(" > "), js);
        assertTrue(js.contains("console.log(1)"), js);
    }
}

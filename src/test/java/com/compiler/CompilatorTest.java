package com.compiler;

import org.junit.jupiter.api.Test;

/** Referencias JS existentes: regenerar solo ante un cambio deliberado del generador. */
class CompilatorTest {
    @Test
    void valido() {
        GoldenFile.verificar("valido", ResultadoCompilacion.ejecutar("""
                let a = 10;
                let b = 3;
                let c = a + b * 2;
                const ok = true;
                print(c);
                if (a > 0) { print(a); }
                """).informe());
    }

    @Test
    void ifVariasSentencias() {
        GoldenFile.verificar("if_varias_sentencias", ResultadoCompilacion.ejecutar("""
                let a = 1;
                if (a > 0) { let b = 2; print(b); print(a); }
                """).informe());
    }

    @Test
    void vacio() {
        GoldenFile.verificar("vacio", ResultadoCompilacion.ejecutar("").informe());
    }
}

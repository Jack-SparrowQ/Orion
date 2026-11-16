package com.compiler.ir.opt;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.compiler.ir.Cuadrupla;
import com.compiler.ir.FuncionIR;
import com.compiler.ir.GrafoFlujo;
import com.compiler.ir.ProgramaIR;
import com.compiler.semantic.Type;

class SimplificacionSaltosTest {
    private Cuadrupla c(String op, String a, String b, String r) {
        return new Cuadrupla(op, a, b, r);
    }

    private List<Cuadrupla> simplificar(Cuadrupla... codigo) {
        return new SimplificacionSaltos().apply(new ProgramaIR(List.of(), List.of(codigo), Map.of())).principal();
    }

    @Test
    void eliminaInalcanzableTrasReturnIncluidosSusEfectosYMetadatos() {
        Cuadrupla retorno = c("return", null, null, null);
        FuncionIR f = new FuncionIR("f", List.of(), List.of("x"), List.of(retorno,
                c("call", "otra", "0", "x"), c("label", null, null, "huerfana"), c("print", "x", null, null)));
        ProgramaIR salida = new SimplificacionSaltos().apply(new ProgramaIR(List.of(f), List.of(), Map.of("x", Type.INT)));
        assertEquals(List.of(retorno), salida.funciones().getFirst().cuerpo());
        assertTrue(salida.funciones().getFirst().locales().isEmpty());
        assertTrue(salida.tipos().isEmpty());
    }

    @Test
    void eliminaSaltoAlSiguienteYEtiquetaHuerfana() {
        for (String op : List.of("goto", "ifFalse")) {
            Cuadrupla imprimir = c("print", "1", null, null);
            assertEquals(List.of(imprimir), simplificar(c(op, "cond", null, "L"),
                    c("label", null, null, "L"), imprimir));
        }
    }

    @Test
    void eliminaCodigoSaltado() {
        Cuadrupla imprimir = c("print", "2", null, null);
        assertEquals(List.of(imprimir), simplificar(c("goto", null, null, "L"),
                c("print", "1", null, null), c("label", null, null, "L"), imprimir));
    }

    @Test
    void redirigeCadenaCondicionalSinPerderLaRamaDeCaida() {
        var codigo = simplificar(c("ifFalse", "cond", null, "L1"), c("print", "1", null, null),
                c("goto", null, null, "fin"), c("label", null, null, "L1"), c("goto", null, null, "L2"),
                c("label", null, null, "L2"), c("goto", null, null, "destino"),
                c("label", null, null, "destino"), c("print", "2", null, null),
                c("label", null, null, "fin"), c("print", "3", null, null));
        assertEquals("destino", codigo.getFirst().resultado);
        assertTrue(codigo.stream().noneMatch(c -> "L1".equals(c.resultado) || "L2".equals(c.resultado)));
        assertEquals(List.of("1", "2", "3"), codigo.stream().filter(c -> c.op.equals("print")).map(c -> c.arg1).toList());
        assertDoesNotThrow(() -> GrafoFlujo.construir(codigo));
    }

    @Test
    void ciclosDeSaltosVaciosTerminanYConservanElCiclo() {
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            var codigo = simplificar(c("goto", null, null, "L1"), c("label", null, null, "L1"),
                    c("goto", null, null, "L2"), c("label", null, null, "L2"), c("goto", null, null, "L1"),
                    c("print", "1", null, null));
            assertTrue(codigo.stream().anyMatch(c -> c.op.equals("goto")));
            assertTrue(codigo.stream().noneMatch(c -> c.op.equals("print")));
            assertDoesNotThrow(() -> GrafoFlujo.construir(codigo));
            var propio = simplificar(c("label", null, null, "L"), c("goto", null, null, "L"));
            assertEquals(List.of("label", "goto"), propio.stream().map(c -> c.op).toList());
        });
    }

    @Test
    void conservaBloquesConEfectosAlResolverCadenas() {
        var codigo = simplificar(c("goto", null, null, "L"), c("label", null, null, "L"),
                c("call", "f", "0", null), c("goto", null, null, "fin"),
                c("label", null, null, "fin"), c("print", "1", null, null));
        assertEquals(List.of("call", "print"), codigo.stream().map(c -> c.op).toList());
    }

    @Test
    void eliminaEtiquetaSinReferenciasYAdmiteVacio() {
        assertTrue(simplificar(c("label", null, null, "L")).isEmpty());
        assertTrue(simplificar().isEmpty());
    }
}

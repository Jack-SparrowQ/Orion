package com.compiler.ir.opt;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import com.compiler.ir.*;
import com.compiler.semantic.Type;

class PlegadoConstantesTest {
    private Cuadrupla c(String op, String a, String b, String r) {
        return new Cuadrupla(op, a, b, r);
    }

    private List<Cuadrupla> optimizar(Cuadrupla... codigo) {
        return new PlegadoConstantes().apply(new ProgramaIR(List.of(), List.of(codigo), Map.of())).principal();
    }

    @TestFactory
    Stream<DynamicTest> operadores() {
        String[][] casos = {
            {"+", "2", "3", "5"}, {"-", "2", "3", "-1"}, {"*", "2", "3", "6"},
            {"/", "7", "2", "3.5"}, {"%", "-7", "3", "-1"}, {"**", "2", "3", "8"},
            {"**", "2", "53", "9007199254740992"},
            {"/", "1", "3", "0.3333333333333333"}, {"+", "0.1", "0.2", "0.30000000000000004"},
            {"-", "5", null, "-5"}, {"!", "true", null, "false"},
            {"==", "2", "2.0", "true"}, {"!=", "2", "3", "true"},
            {"<", "2", "3", "true"}, {"<=", "2", "2", "true"},
            {">", "2", "3", "false"}, {">=", "3", "3", "true"},
            {"==", "true", "false", "false"}, {"!=", "true", "false", "true"},
            {"concat", "\"a\\n\"", "\"b\"", "\"a\\nb\""},
            {"concat", "\"\\0\"", "\"1\"", "\"\\u00001\""}
        };
        return Stream.of(casos).map(x -> DynamicTest.dynamicTest(String.join(" ", x[0], x[1]), () -> {
            Cuadrupla resultado = optimizar(c(x[0], x[1], x[2], "r")).getFirst();
            assertEquals("=", resultado.op);
            assertEquals(x[3], resultado.arg1);
            assertEquals("r", resultado.resultado);
        }));
    }

    @TestFactory
    Stream<DynamicTest> operacionesQueNoSePliegan() {
        String[][] casos = {
            {"**", "2", "0.5"},
            {"/", "1", "0"}, {"%", "1", "0"}, {"**", "2", "1024"},
            {"*", "9007199254740992", "2"}, {"/", "1", "10000000"},
            {"-", "0", null}, {"*", "-1", "0"}, {"concat", "null", "\"x\""},
            {"==", "'a'", "'a'"}, {"<", "\"a\"", "\"b\""}, {"==", "\"a\"", "\"a\""}
        };
        return Stream.of(casos).map(x -> DynamicTest.dynamicTest(x[0] + " " + x[1], () ->
                assertEquals(x[0], optimizar(c(x[0], x[1], x[2], "r")).getFirst().op)));
    }

    @Test
    void propagaHastaReasignacionIncluidaLecturaDeArreglo() {
        List<Cuadrupla> codigo = optimizar(c("=", "3", null, "x"), c("+", "x", "2", "$t1"),
                c("=", "$t1", null, "y"), c("[]", "v", "0", "x"), c("print", "x", null, null),
                c("[]=", "v", "1", "y"));
        assertEquals("5", codigo.get(1).arg1);
        assertEquals("5", codigo.get(2).arg1);
        assertEquals("x", codigo.get(4).arg1);
        assertEquals("5", codigo.get(5).resultado);
        assertEquals(6, codigo.size());
    }

    @Test
    void llamadaInvalidaGlobalesYResultadoPeroConservaLocalesParametrosYTemporales() {
        List<Cuadrupla> cuerpo = List.of(c("=", "1", null, "g"), c("=", "2", null, "local"),
                c("=", "3", null, "$t1"), c("=", "4", null, "p"),
                c("call", "f", "0", "destino"), c("print", "g", null, null),
                c("print", "local", null, null), c("print", "$t1", null, null), c("return", "p", null, null));
        FuncionIR funcion = new FuncionIR("f", List.of("p"), List.of("local", "$t1", "destino"), cuerpo);
        ProgramaIR entrada = new ProgramaIR(List.of(funcion), cuerpo, Map.of("g", Type.INT));
        ProgramaIR salida = new PlegadoConstantes().apply(entrada);
        var codigo = salida.funciones().getFirst().cuerpo();
        assertEquals("g", codigo.get(5).arg1);
        assertEquals("2", codigo.get(6).arg1);
        assertEquals("3", codigo.get(7).arg1);
        assertEquals("4", codigo.get(8).arg1);
        assertEquals("local", salida.principal().get(6).arg1);
        assertEquals("3", salida.principal().get(7).arg1);
        assertEquals(entrada.tipos(), salida.tipos());
        assertEquals(funcion.locales(), salida.funciones().getFirst().locales());
        var redefinido = optimizar(c("=", "5", null, "$t1"), c("call", "f", "0", "$t1"),
                c("print", "$t1", null, null));
        assertEquals("$t1", redefinido.getLast().arg1);
    }

    @Test
    void noCruzaEtiquetasYNiFuncionesYResuelveCondiciones() {
        var codigo = optimizar(c("=", "true", null, "x"), c("ifFalse", "x", null, "L"),
                c("print", "x", null, null), c("label", null, null, "L"), c("print", "x", null, null));
        assertEquals(4, codigo.size());
        assertEquals("x", codigo.get(1).arg1);
        assertEquals("x", codigo.getLast().arg1);
        assertEquals("goto", optimizar(c("ifFalse", "false", null, "L"),
                c("label", null, null, "L")).getFirst().op);
        assertEquals(Type.INT, new ProgramaIR(List.of(), List.of(), Map.of()).tipo("-5"));
        assertEquals(Type.FLOAT, new ProgramaIR(List.of(), List.of(), Map.of()).tipo("-0.25"));
    }
}

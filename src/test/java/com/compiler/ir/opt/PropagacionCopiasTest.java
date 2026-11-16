package com.compiler.ir.opt;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.compiler.error.ErrorHandler;
import com.compiler.ir.Cuadrupla;
import com.compiler.ir.FuncionIR;
import com.compiler.ir.GeneradorCodigoIntermedio;
import com.compiler.ir.ProgramaIR;
import com.compiler.lexer.Lexer;
import com.compiler.lexer.Tokenizer;
import com.compiler.parser.Parser;
import com.compiler.semantic.SemanticAnalyzer;
import com.compiler.semantic.SymbolTable;
import com.compiler.semantic.Type;

class PropagacionCopiasTest {
    private Cuadrupla c(String op, String a, String b, String r) {
        return new Cuadrupla(op, a, b, r);
    }

    private List<Cuadrupla> optimizar(Cuadrupla... codigo) {
        return new PropagacionCopias().apply(new ProgramaIR(List.of(), List.of(codigo), Map.of())).principal();
    }

    @Test
    void propagaCopiasYLasEncadenaSinEliminarAsignaciones() {
        var codigo = optimizar(c("=", "y", null, "x"), c("=", "x", null, "z"),
                c("+", "x", "z", "r"), c("param", "z", null, null), c("return", "x", null, null));
        assertEquals(5, codigo.size());
        assertEquals("y", codigo.get(1).arg1);
        assertEquals("y", codigo.get(2).arg1);
        assertEquals("y", codigo.get(2).arg2);
        assertEquals("y", codigo.get(3).arg1);
        assertEquals("y", codigo.get(4).arg1);
    }

    @Test
    void redefinirCualquieraDeLosExtremosInvalidaLaCopia() {
        for (String redefinida : List.of("x", "y")) {
            var codigo = optimizar(c("=", "y", null, "x"), c("=", "1", null, redefinida),
                    c("print", "x", null, null));
            assertEquals("x", codigo.getLast().arg1);
        }
    }

    @Test
    void lecturaDeArregloYCallTambienRedefinenElDestino() {
        for (String op : List.of("[]", "call", "len", "array", "concat")) {
            var codigo = optimizar(c("=", "y", null, "x"), c(op, "v", "0", "x"),
                    c("print", "x", null, null));
            assertEquals("x", codigo.getLast().arg1);
        }
    }

    @Test
    void escrituraDeArregloLeeLosTresOperandosYNoRedefineSuValor() {
        var codigo = optimizar(c("=", "v", null, "a"), c("=", "i", null, "j"),
                c("=", "valor", null, "x"), c("[]=", "a", "j", "x"), c("print", "x", null, null));
        Cuadrupla escritura = codigo.get(3);
        assertEquals("v", escritura.arg1);
        assertEquals("i", escritura.arg2);
        assertEquals("valor", escritura.resultado);
        assertEquals("valor", codigo.getLast().arg1);
    }

    @Test
    void llamadaInvalidaSiCualquieraDeLosExtremosEsGlobal() {
        for (String[] extremos : new String[][] {{"x", "$t1"}, {"$t1", "g"}, {"x", "g"}}) {
            var codigo = optimizar(c("=", extremos[1], null, extremos[0]), c("call", "f", "0", null),
                    c("print", extremos[0], null, null));
            assertEquals(extremos[0], codigo.getLast().arg1);
        }
        var temporales = optimizar(c("=", "$t1", null, "$t2"), c("call", "f", "0", null),
                c("print", "$t2", null, null));
        assertEquals("$t1", temporales.getLast().arg1);
    }

    @Test
    void llamadaConservaCopiasEntreLocalesYParametros() {
        FuncionIR funcion = new FuncionIR("f", List.of("p"), List.of("x"), List.of(
                c("=", "p", null, "x"), c("call", "otra", "0", null), c("return", "x", null, null)));
        var salida = new PropagacionCopias().apply(new ProgramaIR(List.of(funcion), List.of(), Map.of()));
        assertEquals("p", salida.funciones().getFirst().cuerpo().getLast().arg1);
    }

    @Test
    void copiaDeSeguridadGlobalSobreviveALlamadaEnProgramaReal() {
        ProgramaIR entrada = ir("""
                let g = 1;
                fn f(): int { g = 9; return 2; }
                fn calcular(): int { return g + f(); }
                print(calcular());
                """);
        ProgramaIR salida = new PropagacionCopias().apply(new PlegadoConstantes().apply(entrada));
        var codigo = salida.funciones().get(1).cuerpo();
        int llamada = -1;
        for (int i = 0; i < codigo.size(); i++) if (codigo.get(i).op.equals("call")) llamada = i;
        assertTrue(llamada > 0);
        Cuadrupla copia = codigo.get(llamada - 1);
        assertEquals("=", copia.op);
        assertEquals("g", copia.arg1);
        assertTrue(copia.resultado.startsWith("$t"));
        assertEquals(copia.resultado, codigo.get(llamada + 1).arg1);
        assertEquals("+", codigo.get(llamada + 1).op);
    }

    @Test
    void noCruzaBloquesNiCuerpos() {
        var codigo = optimizar(c("=", "y", null, "x"), c("label", null, null, "L"),
                c("print", "x", null, null));
        assertEquals("x", codigo.getLast().arg1);
        FuncionIR funcion = new FuncionIR("f", List.of(), List.of(), List.of(c("return", "x", null, null)));
        var salida = new PropagacionCopias().apply(new ProgramaIR(List.of(funcion),
                List.of(c("=", "y", null, "x")), Map.of()));
        assertEquals("x", salida.funciones().getFirst().cuerpo().getFirst().arg1);
    }

    @Test
    void eliminaAutocopiasSinInvalidarRelacionesValidas() {
        var codigo = optimizar(c("=", "y", null, "x"), c("=", "x", null, "y"),
                c("=", "x", null, "x"), c("print", "x", null, null));
        assertEquals("y", codigo.getLast().arg1);
        assertTrue(codigo.stream().noneMatch(c -> c.op.equals("=") && c.resultado.equals(c.arg1)));
        assertTrue(optimizar(c("=", "x", null, "x")).isEmpty());
    }

    @Test
    void fusionaTemporalContiguoYActualizaMetadatos() {
        FuncionIR funcion = new FuncionIR("f", List.of("a", "b"), List.of("$t1", "x"), List.of(
                c("+", "a", "b", "$t1"), c("=", "$t1", null, "x"), c("return", "x", null, null)));
        ProgramaIR entrada = new ProgramaIR(List.of(funcion), List.of(),
                Map.of("a", Type.INT, "b", Type.INT, "$t1", Type.INT, "x", Type.INT));
        ProgramaIR salida = new PropagacionCopias().apply(entrada);
        var codigo = salida.funciones().getFirst().cuerpo();
        assertEquals(2, codigo.size());
        assertEquals("+", codigo.getFirst().op);
        assertEquals("x", codigo.getFirst().resultado);
        assertEquals(List.of("x"), salida.funciones().getFirst().locales());
        assertFalse(salida.tipos().containsKey("$t1"));
        assertEquals(Type.INT, salida.tipo("x"));
        assertTrue(entrada.tipos().containsKey("$t1"));
    }

    @Test
    void fusionConservaOperacionConEfectos() {
        for (String op : List.of("call", "[]", "len", "concat", "array")) {
            var codigo = optimizar(c(op, "a", "0", "$t1"), c("=", "$t1", null, "x"));
            assertEquals(1, codigo.size());
            assertEquals(op, codigo.getFirst().op);
            assertEquals("x", codigo.getFirst().resultado);
        }
    }

    @Test
    void noFusionaConDosUsosNiUsoRepetidoEnUnaInstruccion() {
        var codigo = optimizar(c("+", "a", "b", "$t1"), c("=", "$t1", null, "x"),
                c("print", "$t1", null, null));
        assertEquals(3, codigo.size());
        assertEquals("$t1", codigo.getFirst().resultado);
        var repetido = optimizar(c("+", "a", "b", "$t1"), c("+", "$t1", "$t1", "x"));
        assertEquals("$t1", repetido.getFirst().resultado);
    }

    @Test
    void cuentaUsosEnTodoElCuerpoYRespetaAdyacencia() {
        var entreBloques = optimizar(c("+", "a", "b", "$t1"), c("=", "$t1", null, "x"),
                c("label", null, null, "L"), c("print", "$t1", null, null));
        assertEquals("$t1", entreBloques.getFirst().resultado);
        var noInmediato = optimizar(c("+", "a", "b", "$t1"), c("print", "a", null, null),
                c("=", "$t1", null, "x"));
        assertEquals(3, noInmediato.size());
        var separado = optimizar(c("+", "a", "b", "$t1"), c("label", null, null, "L"),
                c("=", "$t1", null, "x"));
        assertEquals(3, separado.size());
    }

    @Test
    void noFusionaTemporalDeCortocircuitoConDosDefiniciones() {
        var redefinido = optimizar(c("+", "a", "b", "$t1"), c("=", "$t1", null, "x"),
                c("-", "a", "b", "$t1"));
        assertEquals(3, redefinido.size());
        assertEquals("$t1", redefinido.getFirst().resultado);
        ProgramaIR salida = new PropagacionCopias().apply(ir("fn f(a: bool, b: bool): bool { let x = a && b; return x; }"));
        var codigo = salida.funciones().getFirst().cuerpo();
        assertEquals(2, codigo.stream().filter(c -> c.op.equals("=") && "$t1".equals(c.resultado)).count());
        assertTrue(codigo.stream().anyMatch(c -> c.op.equals("=") && "x".equals(c.resultado)
                && "$t1".equals(c.arg1)));
    }

    @Test
    void escrituraDeArregloNoEsDefinicionFusionablePeroSiCuentaComoUso() {
        var codigo = optimizar(c("[]=", "v", "0", "$t1"), c("=", "$t1", null, "x"));
        assertEquals(2, codigo.size());
        assertEquals("$t1", codigo.getFirst().resultado);
        var uso = optimizar(c("+", "a", "b", "$t1"), c("=", "$t1", null, "x"),
                c("[]=", "v", "0", "$t1"));
        assertEquals("$t1", uso.getFirst().resultado);
    }

    @Test
    void noPropagaLiteralesNiNombresDeFuncionOEtiquetas() {
        for (String literal : List.of("1", "-2", "0.5", "true", "false", "null", "\"hola\"", "'a'", "void 0")) {
            var codigo = optimizar(c("=", literal, null, "x"), c("print", "x", null, null));
            assertEquals("x", codigo.getLast().arg1);
        }
        var codigo = optimizar(c("=", "otra", null, "f"), c("call", "f", "0", null),
                c("label", null, null, "f"), c("goto", null, null, "f"));
        assertEquals("f", codigo.get(1).arg1);
        assertEquals("f", codigo.getLast().resultado);
    }

    private ProgramaIR ir(String fuente) {
        ErrorHandler errores = new ErrorHandler();
        Tokenizer tokenizer = new Tokenizer(new Lexer(fuente, errores));
        tokenizer.run();
        var ast = new Parser(tokenizer.getTokensList(), errores).parsear();
        SymbolTable tabla = new SymbolTable();
        SemanticAnalyzer.analyzeSymbols(ast, tabla, errores);
        var tipos = SemanticAnalyzer.analyzeSemantic(ast, tabla, errores);
        assertEquals(List.of(), errores.obtenerErrores());
        return new GeneradorCodigoIntermedio().generar(ast, tipos);
    }
}

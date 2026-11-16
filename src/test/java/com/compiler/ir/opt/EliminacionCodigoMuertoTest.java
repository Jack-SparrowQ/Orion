package com.compiler.ir.opt;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.compiler.ir.Cuadrupla;
import com.compiler.ir.FuncionIR;
import com.compiler.ir.GeneradorCodigoIntermedio;
import com.compiler.ir.ProgramaIR;
import com.compiler.error.ErrorHandler;
import com.compiler.lexer.Lexer;
import com.compiler.lexer.Tokenizer;
import com.compiler.parser.Parser;
import com.compiler.semantic.SemanticAnalyzer;
import com.compiler.semantic.SymbolTable;
import com.compiler.semantic.Type;

class EliminacionCodigoMuertoTest {
    @Test
    void temporalMuertoDelPrincipalNoEsGlobalAunqueHayaUnaLlamadaEnElWhile() {
        String fuente = """
                let rondas = 1 + 2;
                fn ganar() { rondas = 0; }
                while (rondas > 0) { ganar(); }
                """;
        ErrorHandler errores = new ErrorHandler();
        Tokenizer tokenizer = new Tokenizer(new Lexer(fuente, errores));
        tokenizer.run();
        var ast = new Parser(tokenizer.getTokensList(), errores).parsear();
        SymbolTable tabla = new SymbolTable();
        SemanticAnalyzer.analyzeSymbols(ast, tabla, errores);
        var tipos = SemanticAnalyzer.analyzeSemantic(ast, tabla, errores);
        assertEquals(List.of(), errores.obtenerErrores());
        ProgramaIR entrada = new PlegadoConstantes().apply(new GeneradorCodigoIntermedio().generar(ast, tipos));
        Cuadrupla temporalMuerto = entrada.principal().stream().filter(c -> c.op.equals("=")
                && c.resultado.startsWith("$t") && "3".equals(c.arg1)).findFirst().orElseThrow();
        ProgramaIR salida = new EliminacionCodigoMuerto().apply(entrada);
        assertFalse(salida.principal().stream().anyMatch(c -> temporalMuerto.resultado.equals(c.resultado)));
        assertFalse(salida.tipos().containsKey(temporalMuerto.resultado));
        assertTrue(salida.principal().stream().anyMatch(c -> c.op.equals("=")
                && "rondas".equals(c.resultado) && "3".equals(c.arg1)));
        assertTrue(salida.principal().stream().anyMatch(c -> c.op.equals("call") && "ganar".equals(c.arg1)));
        assertTrue(salida.funciones().getFirst().cuerpo().stream().anyMatch(c -> c.op.equals("=")
                && "rondas".equals(c.resultado) && "0".equals(c.arg1)));
    }

    private Cuadrupla c(String op, String a, String b, String r) {
        return new Cuadrupla(op, a, b, r);
    }

    private List<Cuadrupla> eliminar(Cuadrupla... codigo) {
        return new EliminacionCodigoMuerto().apply(new ProgramaIR(List.of(), List.of(codigo), Map.of())).principal();
    }

    @Test
    void eliminaDefinicionesPurasMuertasYConservaLaUltimaAsignacionLeida() {
        var codigo = eliminar(c("=", "1", null, "x"), c("+", "x", "2", "$t1"),
                c("=", "3", null, "x"), c("print", "x", null, null));
        assertEquals(2, codigo.size());
        assertEquals("3", codigo.getFirst().arg1);
    }

    @Test
    void vivacidadUneLasRamas() {
        Cuadrupla primera = c("=", "1", null, "x");
        Cuadrupla segunda = c("=", "2", null, "x");
        var codigo = eliminar(c("=", "0", null, "x"), c("ifFalse", "cond", null, "L1"),
                primera, c("goto", null, null, "L2"), c("label", null, null, "L1"), segunda,
                c("label", null, null, "L2"), c("print", "x", null, null));
        assertEquals(7, codigo.size());
        assertTrue(codigo.containsAll(List.of(primera, segunda)));
    }

    @Test
    void asignacionUsadaSoloEnLaSiguienteVueltaSigueViva() {
        Cuadrupla siguienteVuelta = c("=", "y", null, "x");
        var codigo = eliminar(c("=", "0", null, "x"), c("label", null, null, "inicio"),
                c("ifFalse", "cond", null, "fin"), c("print", "x", null, null), siguienteVuelta,
                c("goto", null, null, "inicio"), c("label", null, null, "fin"));
        assertTrue(codigo.contains(siguienteVuelta));
        assertEquals(7, codigo.size());
    }

    @Test
    void llamadaLeeGlobalesAunqueNoSeanUsadasExplicitamenteDespues() {
        Cuadrupla asignacion = c("=", "5", null, "g");
        Cuadrupla llamada = c("call", "f", "0", "$t1");
        ProgramaIR salida = new EliminacionCodigoMuerto().apply(new ProgramaIR(List.of(),
                List.of(asignacion, llamada), Map.of("g", Type.INT, "$t1", Type.INT)));
        assertEquals(List.of(asignacion, llamada), salida.principal());
        assertEquals(Type.INT, salida.tipo("g"));
    }

    @Test
    void globalesVivasEnReturnYEnCaidaAlFinalDeFuncion() {
        for (boolean retorno : List.of(true, false)) {
            Cuadrupla asignacion = c("=", "5", null, "g");
            List<Cuadrupla> cuerpo = retorno ? List.of(asignacion, c("return", null, null, null)) : List.of(asignacion);
            FuncionIR f = new FuncionIR("f", List.of("p"), List.of("local"), cuerpo);
            ProgramaIR salida = new EliminacionCodigoMuerto().apply(new ProgramaIR(List.of(f), List.of(),
                    Map.of("g", Type.INT, "p", Type.INT, "local", Type.INT)));
            assertTrue(salida.funciones().getFirst().cuerpo().contains(asignacion));
            assertEquals(List.of("p"), salida.funciones().getFirst().parametros());
        }
    }

    @Test
    void asignacionesLocalesYParametrosMuertosNoSonGlobalesImplicitas() {
        FuncionIR f = new FuncionIR("f", List.of("p"), List.of("x"), List.of(
                c("=", "1", null, "x"), c("=", "2", null, "p"),
                c("call", "otra", "0", null), c("return", null, null, null)));
        ProgramaIR salida = new EliminacionCodigoMuerto().apply(new ProgramaIR(List.of(f), List.of(),
                Map.of("x", Type.INT, "p", Type.INT)));
        assertEquals(List.of("call", "return"), salida.funciones().getFirst().cuerpo().stream().map(c -> c.op).toList());
        assertTrue(salida.funciones().getFirst().locales().isEmpty());
        assertTrue(salida.tipos().isEmpty());
    }

    @Test
    void conservaTodasLasOperacionesConEfectosOQuePuedenFallar() {
        for (String op : List.of("call", "[]", "len", "concat", "array")) {
            Cuadrupla efecto = c(op, "v", "0", "$t1");
            assertEquals(List.of(efecto), eliminar(efecto), op);
        }
        Cuadrupla escritura = c("[]=", "v", "i", "x");
        var codigo = eliminar(c("=", "7", null, "x"), escritura, c("param", "x", null, null),
                c("printn", "1", null, null));
        assertEquals(4, codigo.size());
        assertSame(escritura, codigo.get(1));
    }

    @Test
    void eliminaTodosLosOperadoresPurosSinUso() {
        for (String op : List.of("=", "+", "-", "*", "/", "%", "**", "==", "!=", "<", "<=", ">", ">=", "!")) {
            assertTrue(eliminar(c(op, "a", "b", "$t1")).isEmpty(), op);
        }
    }

    @Test
    void limpiaTiposYLocalesSinQuitarLosNombresUsadosEnOtroCuerpo() {
        FuncionIR f = new FuncionIR("f", List.of(), List.of("$t1", "x"), List.of(
                c("=", "1", null, "$t1"), c("call", "otra", "0", "x"), c("return", null, null, null)));
        ProgramaIR salida = new EliminacionCodigoMuerto().apply(new ProgramaIR(List.of(f),
                List.of(c("print", "g", null, null)), Map.of("$t1", Type.INT, "x", Type.INT, "g", Type.INT)));
        assertEquals(List.of("x"), salida.funciones().getFirst().locales());
        assertEquals(Map.of("x", Type.INT, "g", Type.INT), salida.tipos());
    }

    @Test
    void cuerpoVacioProduceProgramaVacio() {
        assertTrue(eliminar().isEmpty());
    }
}

package com.compiler.ir;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import com.compiler.error.ErrorHandler;
import com.compiler.lexer.Lexer;
import com.compiler.lexer.Tokenizer;
import com.compiler.parser.Parser;
import com.compiler.semantic.SemanticAnalyzer;
import com.compiler.semantic.SymbolTable;

class GrafoFlujoTest {
    @Test
    void ifSinElse() {
        GrafoFlujo grafo = principal("if (true) { print(1); } print(2);");
        comprobar(grafo, List.of(2, 1), List.of(2), List.of());
        assertEquals("""
                B0 -> B2, B1
                    ifFalse true goto $L1
                B1 -> B2
                    print 1
                B2 ->
                $L1:
                    print 2
                """, grafo.aTexto());
    }

    @Test
    void ifConElse() {
        GrafoFlujo grafo = principal("if (true) { print(1); } else { print(2); } print(3);");
        comprobar(grafo, List.of(2, 1), List.of(3), List.of(3), List.of());
        assertEquals(List.of(1, 2, 2, 2), grafo.bloques().stream().map(b -> b.instrucciones().size()).toList());
    }

    @Test
    void whileConBreakContinueYCodigoDespuesDeGoto() {
        GrafoFlujo grafo = principal("while (true) { continue; print(1); break; } print(2);");
        comprobar(grafo, List.of(4, 1), List.of(0), List.of(4), List.of(0), List.of());
        assertEquals(List.of(), grafo.bloques().get(2).predecesores());
        assertEquals(List.of(), grafo.bloques().get(3).predecesores());
        assertEquals("print 1", grafo.bloques().get(2).instrucciones().getFirst().toString());
    }

    @Test
    void forIn() {
        GrafoFlujo grafo = principal("for (c in \"ab\") { print(c); }");
        comprobar(grafo, List.of(1), List.of(4, 2), List.of(3), List.of(1), List.of());
        assertEquals(List.of(3, 3, 2, 3, 1), grafo.bloques().stream().map(b -> b.instrucciones().size()).toList());
    }

    @Test
    void cortocircuitoAndYOr() {
        comprobar(principal("print(true && false);"), List.of(2, 1), List.of(2), List.of());
        comprobar(principal("print(true || false);"), List.of(2, 1), List.of(3), List.of(3), List.of());
    }

    @Test
    void despuesDeReturnSeConservaSinPredecesores() {
        GrafoFlujo grafo = GrafoFlujo.construir(ir("fn f() { return; print(1); }").funciones().getFirst().cuerpo());
        comprobar(grafo, List.of(), List.of());
        assertTrue(grafo.bloques().get(1).predecesores().isEmpty());
        assertEquals("print 1", grafo.bloques().get(1).instrucciones().getFirst().toString());
    }

    @Test
    void vacioYLineal() {
        GrafoFlujo vacio = GrafoFlujo.construir(List.of());
        assertTrue(vacio.bloques().isEmpty());
        assertTrue(vacio.aplanar().isEmpty());
        assertEquals("", vacio.aTexto());
        GrafoFlujo lineal = principal("print(1); print(2);");
        comprobar(lineal, List.of());
        assertEquals(2, lineal.bloques().getFirst().instrucciones().size());
    }

    @Test
    void etiquetasInexistentesInclusoEnCodigoInalcanzable() {
        for (String op : List.of("goto", "ifFalse")) {
            assertThrows(IllegalArgumentException.class, () -> GrafoFlujo.construir(List.of(
                    new Cuadrupla("return", null, null, null),
                    new Cuadrupla(op, "true", null, "$ausente"))));
        }
    }

    @Test
    void recordsCopianSusListas() {
        List<Cuadrupla> codigo = new ArrayList<>(ir("print(1);").principal());
        List<Integer> sucesores = new ArrayList<>(List.of(0));
        List<Integer> predecesores = new ArrayList<>(List.of(0));
        BloqueBasico bloque = new BloqueBasico(0, codigo, sucesores, predecesores);
        List<BloqueBasico> bloques = new ArrayList<>(List.of(bloque));
        GrafoFlujo grafo = new GrafoFlujo(bloques);
        codigo.clear(); sucesores.clear(); predecesores.clear(); bloques.clear();
        assertEquals(1, bloque.instrucciones().size());
        assertEquals(List.of(0), bloque.sucesores());
        assertEquals(List.of(0), bloque.predecesores());
        assertEquals(List.of(bloque), grafo.bloques());
        assertThrows(UnsupportedOperationException.class, () -> bloque.instrucciones().clear());
        assertThrows(UnsupportedOperationException.class, () -> bloque.sucesores().clear());
        assertThrows(UnsupportedOperationException.class, () -> bloque.predecesores().clear());
        assertThrows(UnsupportedOperationException.class, () -> grafo.bloques().clear());
    }

    @TestFactory
    List<DynamicTest> idaYVueltaDelCorpus() throws Exception {
        Path corpus = Path.of(getClass().getResource("/programas").toURI());
        try (Stream<Path> archivos = Files.list(corpus)) {
            List<Path> fuentes = archivos.filter(p -> p.toString().endsWith(".orion")).sorted().toList();
            assertFalse(fuentes.isEmpty());
            return fuentes.stream().map(fuente -> DynamicTest.dynamicTest(fuente.getFileName().toString(), () -> {
                ProgramaIR programa = ir(Files.readString(fuente));
                assertEquals(programa.principal(), GrafoFlujo.construir(programa.principal()).aplanar());
                for (FuncionIR funcion : programa.funciones()) {
                    assertEquals(funcion.cuerpo(), GrafoFlujo.construir(funcion.cuerpo()).aplanar(), funcion.nombre());
                }
            })).toList();
        }
    }

    @SafeVarargs
    private void comprobar(GrafoFlujo grafo, List<Integer>... sucesores) {
        assertEquals(sucesores.length, grafo.bloques().size(), grafo.aTexto());
        for (int id = 0; id < sucesores.length; id++) {
            BloqueBasico bloque = grafo.bloques().get(id);
            assertEquals(id, bloque.id());
            assertEquals(sucesores[id], bloque.sucesores(), grafo.aTexto());
            List<Integer> esperados = new ArrayList<>();
            for (int origen = 0; origen < sucesores.length; origen++) {
                if (sucesores[origen].contains(id)) esperados.add(origen);
            }
            assertEquals(esperados, bloque.predecesores(), "Predecesores de B" + id);
        }
    }

    private GrafoFlujo principal(String fuente) {
        return GrafoFlujo.construir(ir(fuente).principal());
    }

    private ProgramaIR ir(String fuente) {
        ErrorHandler errores = new ErrorHandler();
        Tokenizer tokenizer = new Tokenizer(new Lexer(fuente, errores));
        tokenizer.run();
        var programa = new Parser(tokenizer.getTokensList(), errores).parsear();
        SymbolTable tabla = new SymbolTable();
        SemanticAnalyzer.analyzeSymbols(programa, tabla, errores);
        var tipos = SemanticAnalyzer.analyzeSemantic(programa, tabla, errores);
        assertEquals(List.of(), errores.obtenerErrores());
        return new GeneradorCodigoIntermedio().generar(programa, tipos);
    }
}

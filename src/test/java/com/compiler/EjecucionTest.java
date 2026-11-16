package com.compiler;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.compiler.error.ErrorHandler;
import com.compiler.lexer.Lexer;
import com.compiler.lexer.Tokenizer;
import com.compiler.parser.Parser;
import com.compiler.semantic.SemanticAnalyzer;
import com.compiler.semantic.SymbolTable;
import com.compiler.util.JSGenerator;

/** Corpus de programas completos: fuente -> compilador -> JavaScript -> node. */
class EjecucionTest {
    @TempDir
    Path temporal;

    @BeforeAll
    static void comprobarNode() throws Exception {
        Process proceso;
        try {
            proceso = new ProcessBuilder("node", "--version")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException e) {
            assumeTrue(false, "node no esta disponible en PATH: " + e.getMessage());
            return;
        }
        try {
            assertTrue(proceso.waitFor(10, TimeUnit.SECONDS), "node --version excedio 10 s");
            assertEquals(0, proceso.exitValue(), "node --version fallo");
        } finally {
            if (proceso.isAlive()) proceso.destroyForcibly().waitFor();
        }
    }

    @TestFactory
    List<DynamicTest> ejecutarCorpus() throws Exception {
        return fuentes().stream().map(p -> DynamicTest.dynamicTest(p.getFileName().toString(),
                () -> ejecutar(p))).toList();
    }

    @TestFactory
    List<DynamicTest> compararASTeIR() throws Exception {
        return fuentes().stream().map(p -> DynamicTest.dynamicTest(p.getFileName().toString(),
                () -> comparar(p))).toList();
    }

    private List<Path> fuentes() throws Exception {
        Path corpus = Path.of(EjecucionTest.class.getResource("/programas").toURI());
        try (Stream<Path> archivos = Files.list(corpus)) {
            List<Path> programas = archivos.filter(p -> p.toString().endsWith(".orion")).sorted().toList();
            assertFalse(programas.isEmpty(), "El corpus debe contener programas");
            return programas;
        }
    }

    private void ejecutar(Path fuente) throws Exception {
        String nombre = fuente.getFileName().toString().replaceFirst("\\.orion$", "");
        String esperado = Files.readString(fuente.resolveSibling(nombre + ".salida"), StandardCharsets.UTF_8);
        Path error = fuente.resolveSibling(nombre + ".error");
        ResultadoCompilacion compilacion = ResultadoCompilacion.ejecutar(Files.readString(fuente));
        assertEquals(0, compilacion.exitCode, compilacion.stderr);
        String js = compilacion.archivos.get("programa.js");
        assertNotNull(js, "El compilador debe generar programa.js");
        Ejecucion ejecucion = ejecutarJS(nombre, js);
        assertAll(nombre,
                () -> assertEquals(esperado, ejecucion.stdout(), "stdout"),
                () -> verificarError(error, ejecucion));
    }

    private void comparar(Path fuente) throws Exception {
        String nombre = fuente.getFileName().toString().replaceFirst("\\.orion$", "");
        String codigo = Files.readString(fuente, StandardCharsets.UTF_8);
        Ejecucion desdeAST = ejecutarJS(nombre + "-ast", generarAST(codigo));
        ResultadoCompilacion compilacion = ResultadoCompilacion.ejecutar(codigo);
        assertEquals(0, compilacion.exitCode, compilacion.stderr);
        Ejecucion desdeIR = ejecutarJS(nombre + "-ir", compilacion.archivos.get("programa.js"));
        Path error = fuente.resolveSibling(nombre + ".error");
        assertAll(nombre,
                () -> assertEquals(desdeAST.codigo(), desdeIR.codigo(), "codigo de salida AST vs IR"),
                () -> assertEquals(desdeAST.stdout(), desdeIR.stdout(), "stdout AST vs IR"),
                () -> verificarError(error, desdeAST),
                () -> verificarError(error, desdeIR));
    }

    private String generarAST(String codigo) {
        ErrorHandler errores = new ErrorHandler();
        Tokenizer tokenizer = new Tokenizer(new Lexer(codigo, errores));
        tokenizer.run();
        var ast = new Parser(tokenizer.getTokensList(), errores).parsear();
        SymbolTable tabla = new SymbolTable();
        SemanticAnalyzer.analyzeSymbols(ast, tabla, errores);
        var tipos = SemanticAnalyzer.analyzeSemantic(ast, tabla, errores);
        assertEquals(List.of(), errores.obtenerErrores());
        return new JSGenerator(tipos).generar(ast);
    }

    @Test
    void asignacionFueraDeRango_fallaAntesDeEvaluarElValor() throws Exception {
        String codigo = """
                let v = [1];
                fn valor(): int { print("no debe imprimirse"); return 2; }
                print("antes");
                v[2] = valor();
                """;
        Ejecucion desdeAST = ejecutarJS("rango-ast", generarAST(codigo));
        ResultadoCompilacion compilacion = ResultadoCompilacion.ejecutar(codigo);
        assertEquals(0, compilacion.exitCode, compilacion.stderr);
        Ejecucion desdeIR = ejecutarJS("rango-ir", compilacion.archivos.get("programa.js"));
        assertEquals(desdeAST.codigo(), desdeIR.codigo());
        for (Ejecucion ejecucion : List.of(desdeAST, desdeIR)) {
            assertNotEquals(0, ejecucion.codigo());
            assertEquals("antes\n", ejecucion.stdout());
            assertTrue(ejecucion.stderr().contains("RangeError: Indice fuera de rango: 2 (longitud 1)"), ejecucion.stderr());
        }
    }

    @Test
    void arregloNoDependeDelIdentificadorArrayDelUsuario() throws Exception {
        String codigo = "let Array = 7; let v = [1, 2]; print(Array, v[1]);";
        ResultadoCompilacion compilacion = ResultadoCompilacion.ejecutar(codigo);
        assertEquals(0, compilacion.exitCode, compilacion.stderr);
        Ejecucion desdeIR = ejecutarJS("nombre-Array-ir", compilacion.archivos.get("programa.js"));
        Ejecucion desdeAST = ejecutarJS("nombre-Array-ast", generarAST(codigo));
        assertEquals(0, desdeIR.codigo(), desdeIR.stderr());
        assertEquals(0, desdeAST.codigo(), desdeAST.stderr());
        assertEquals("7 2\n", desdeIR.stdout());
        assertEquals(desdeAST.stdout(), desdeIR.stdout());
    }

    @Test
    void declaracionSinInicializador_seReiniciaEnCadaIteracion() throws Exception {
        String codigo = """
                let i = 0;
                while (i < 2) {
                    let dato: int;
                    if (i == 0) { dato = 7; }
                    print(dato);
                    i += 1;
                }
                """;
        ResultadoCompilacion compilacion = ResultadoCompilacion.ejecutar(codigo);
        assertEquals(0, compilacion.exitCode, compilacion.stderr);
        Ejecucion desdeIR = ejecutarJS("reinicio-ir", compilacion.archivos.get("programa.js"));
        Ejecucion desdeAST = ejecutarJS("reinicio-ast", generarAST(codigo));
        assertEquals(0, desdeIR.codigo(), desdeIR.stderr());
        assertEquals(0, desdeAST.codigo(), desdeAST.stderr());
        assertEquals("7\nundefined\n", desdeIR.stdout());
        assertEquals(desdeAST.stdout(), desdeIR.stdout());
    }

    private void verificarError(Path error, Ejecucion ejecucion) throws IOException {
        if (Files.exists(error)) {
            assertNotEquals(0, ejecucion.codigo(), "Se esperaba error de ejecucion");
            String fragmento = Files.readString(error, StandardCharsets.UTF_8).stripTrailing();
            assertFalse(fragmento.isEmpty(), "El error esperado no debe estar vacio");
            assertTrue(ejecucion.stderr().contains(fragmento), ejecucion.stderr());
        } else {
            assertEquals(0, ejecucion.codigo(), ejecucion.stderr());
            assertEquals("", ejecucion.stderr(), "stderr inesperado");
        }
    }

    private record Ejecucion(int codigo, String stdout, String stderr) { }

    private Ejecucion ejecutarJS(String nombre, String js) throws Exception {
        Path directorio = Files.createDirectory(temporal.resolve(nombre));
        Path programa = Files.writeString(directorio.resolve("programa.js"), js, StandardCharsets.UTF_8);
        Path stdout = directorio.resolve("stdout.txt");
        Path stderr = directorio.resolve("stderr.txt");
        // Archivos separados evitan bloquear las tuberias y conservan los dos canales.
        Process proceso = new ProcessBuilder("node", programa.toAbsolutePath().toString())
                .redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start();
        try {
            assertTrue(proceso.waitFor(10, TimeUnit.SECONDS), nombre + ": node excedio 10 s");
            String salida = Files.readString(stdout, StandardCharsets.UTF_8);
            String errores = Files.readString(stderr, StandardCharsets.UTF_8);
            return new Ejecucion(proceso.exitValue(), salida, errores);
        } finally {
            if (proceso.isAlive()) proceso.destroyForcibly().waitFor();
        }
    }
}

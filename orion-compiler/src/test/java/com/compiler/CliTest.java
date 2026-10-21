package com.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Comportamiento de Compiler.run: ayuda, version, lectura de archivos y rutas de salida. */
public class CliTest {

    @TempDir
    Path dir;

    private Path fuente(String nombre, String codigo) throws IOException {
        return Files.writeString(dir.resolve(nombre), codigo);
    }

    @Test
    void sinArgumentos_muestraAyudaYCodigo2() {
        ResultadoCompilacion r = ResultadoCompilacion.ejecutarCli();
        assertEquals(2, r.exitCode);
        assertTrue(r.stderr.startsWith("Usage: orion"), r.stderr);
    }

    @Test
    void help_enStdout() {
        ResultadoCompilacion r = ResultadoCompilacion.ejecutarCli("--help");
        assertEquals(0, r.exitCode);
        assertTrue(r.stdout.startsWith("Usage: orion"), r.stdout);
        assertEquals("", r.stderr);
    }

    @Test
    void version_sinJar() {
        ResultadoCompilacion r = ResultadoCompilacion.ejecutarCli("-V");
        assertEquals(0, r.exitCode);
        assertEquals("orion (development version)\n", r.stdout);
    }

    @Test
    void errorDeUso_formato() {
        ResultadoCompilacion r = ResultadoCompilacion.ejecutarCli("--target", "js");
        assertEquals(2, r.exitCode);
        assertEquals("orion: error: unknown option '--target'\nTry 'orion --help'.\n", r.stderr);
    }

    @Test
    void archivoInexistente_codigo3() {
        ResultadoCompilacion r = ResultadoCompilacion.ejecutarCli(dir.resolve("no.orion").toString());
        assertEquals(3, r.exitCode);
        assertTrue(r.stderr.startsWith("orion: error: cannot read"), r.stderr);
    }

    @Test
    void salidaIgualALaEntrada_codigo2() throws IOException {
        Path src = fuente("a.orion", "let a = 1;\n");
        ResultadoCompilacion r = ResultadoCompilacion.ejecutarCli(src.toString(), "-o", src.toString());
        assertEquals(2, r.exitCode);
        assertEquals("let a = 1;\n", Files.readString(src));
    }

    @Test
    void output_creaDirectoriosPadre() throws IOException {
        Path src = fuente("hello.orion", "let a = 10;\nprint(a);\n");
        Path out = dir.resolve("build/sub/hello.js");
        ResultadoCompilacion r = ResultadoCompilacion.ejecutarCli(src.toString(), "-o", out.toString(), "--quiet");
        assertEquals(0, r.exitCode);
        assertEquals("", r.stderr);
        assertTrue(Files.readString(out).contains("console.log(a)"));
    }

    @Test
    void emitIr_aStdout() throws IOException {
        Path src = fuente("hello.orion", "let a = 10;\nlet b = 3;\nprint(a + b * 2);\n");
        ResultadoCompilacion r = ResultadoCompilacion.ejecutarCli("--emit-ir", src.toString(), "-o", "-");
        assertEquals(0, r.exitCode);
        assertTrue(r.stdout.contains("t1 = b * 2"), r.stdout);
        assertEquals("", r.stderr);
    }

    @Test
    void verbose_trazaEnStderr() throws IOException {
        Path src = fuente("hello.orion", "let a = 10;\nprint(a);\n");
        ResultadoCompilacion r = ResultadoCompilacion.ejecutarCli("--check", "--verbose", src.toString());
        assertEquals(0, r.exitCode);
        assertEquals("", r.stdout);
        assertTrue(r.stderr.contains("SYMBOLS TABLE"), r.stderr);
    }

    @Test
    void erroresDelFuente_codigo1() throws IOException {
        Path src = fuente("bad.orion", "print(x);\n");
        assertEquals(1, ResultadoCompilacion.ejecutarCli("--check", src.toString()).exitCode);
    }
}

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
import org.junit.jupiter.api.io.TempDir;

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
        Path corpus = Path.of(EjecucionTest.class.getResource("/programas").toURI());
        try (Stream<Path> archivos = Files.list(corpus)) {
            List<Path> programas = archivos.filter(p -> p.toString().endsWith(".orion")).sorted().toList();
            assertFalse(programas.isEmpty(), "El corpus debe contener programas");
            return programas.stream().map(p -> DynamicTest.dynamicTest(p.getFileName().toString(),
                    () -> ejecutar(p))).toList();
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
            assertAll(nombre,
                    () -> assertEquals(esperado, salida, "stdout"),
                    () -> {
                        if (Files.exists(error)) {
                            assertNotEquals(0, proceso.exitValue(), "Se esperaba error de ejecucion");
                            String fragmento = Files.readString(error, StandardCharsets.UTF_8).stripTrailing();
                            assertFalse(fragmento.isEmpty(), "El error esperado no debe estar vacio");
                            assertTrue(errores.contains(fragmento), errores);
                        } else {
                            assertEquals(0, proceso.exitValue(), errores);
                            assertEquals("", errores, "stderr inesperado");
                        }
                    });
        } finally {
            if (proceso.isAlive()) proceso.destroyForcibly().waitFor();
        }
    }
}

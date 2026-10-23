package com.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Compara una salida con su archivo de referencia en src/test/resources/golden.
 *
 * Para regenerar las referencias A PROPOSITO (despues de un cambio de
 * comportamiento deliberado y revisado):
 *
 *     mvn test -Dgolden.update=true
 *
 * y revisa con git diff que solo cambia lo que querias cambiar.
 */
final class GoldenFile {

    private GoldenFile() {
    }

    static void verificar(String nombre, String actual) {
        String ruta = "golden/" + nombre + ".txt";

        if (Boolean.getBoolean("golden.update")) {
            escribir(Path.of("src/test/resources", ruta), actual);
            return;
        }

        try (InputStream in = GoldenFile.class.getClassLoader().getResourceAsStream(ruta)) {
            if (in == null) {
                fail("Falta la referencia " + ruta
                        + ". Generala con: mvn test -Dgolden.update=true");
            }
            String esperado = ResultadoCompilacion.normalizar(
                    new String(in.readAllBytes(), StandardCharsets.UTF_8));
            assertEquals(esperado, actual, "La salida cambio respecto a " + ruta);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void escribir(Path destino, String contenido) {
        try {
            Files.createDirectories(destino.getParent());
            Files.writeString(destino, contenido, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

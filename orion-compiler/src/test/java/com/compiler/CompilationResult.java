package com.compiler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Ejecuta Main.compilar sobre un codigo fuente y captura todo lo observable:
 * lo que sale por consola (stdout y stderr) y los archivos generados.
 */
final class ResultadoCompilacion {

    final String stdout;
    final String stderr;
    /** nombre de archivo generado -> contenido (ordenado por nombre). */
    final Map<String, String> archivos;

    private ResultadoCompilacion(String stdout, String stderr, Map<String, String> archivos) {
        this.stdout = stdout;
        this.stderr = stderr;
        this.archivos = archivos;
    }

    /**
     * @param codigoOrion codigo fuente a compilar
     * @param directoriosPrevios nombres de directorios que se crean antes de compilar
     *        (sirven para provocar fallos de escritura: un directorio con el nombre
     *        del archivo de salida impide crear el archivo)
     */
    static ResultadoCompilacion ejecutar(String codigoOrion, String... directoriosPrevios) {
        Path directorio = null;
        PrintStream outOriginal = System.out;
        PrintStream errOriginal = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        try {
            directorio = Files.createTempDirectory("orion-test");
            for (String nombre : directoriosPrevios) {
                Files.createDirectory(directorio.resolve(nombre));
            }

            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
            try {
                Main.compile(codigoOrion, directorio);
            } finally {
                System.setOut(outOriginal);
                System.setErr(errOriginal);
            }

            return new ResultadoCompilacion(
                    normalizar(out.toString(StandardCharsets.UTF_8)),
                    normalizar(err.toString(StandardCharsets.UTF_8)),
                    leerArchivos(directorio));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            borrar(directorio);
        }
    }

    /** Texto completo y estable que se compara con la referencia (golden). */
    String informe() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== STDOUT ===\n").append(stdout);
        sb.append("=== STDERR ===\n").append(stderr);
        sb.append("=== FILES ===\n");
        if (archivos.isEmpty()) {
            sb.append("(ninguno)\n");
        }
        for (Map.Entry<String, String> a : archivos.entrySet()) {
            sb.append("--- ").append(a.getKey()).append(" ---\n")
              .append(a.getValue()).append("\n");
        }
        return sb.toString();
    }

    static int contar(String texto, String fragmento) {
        int n = 0;
        for (int i = texto.indexOf(fragmento); i >= 0; i = texto.indexOf(fragmento, i + 1)) {
            n++;
        }
        return n;
    }

    static String normalizar(String texto) {
        return texto.replace("\r\n", "\n");
    }

    private static Map<String, String> leerArchivos(Path directorio) throws IOException {
        Map<String, String> resultado = new TreeMap<>();
        try (Stream<Path> lista = Files.list(directorio)) {
            for (Path p : (Iterable<Path>) lista::iterator) {
                if (Files.isRegularFile(p)) {
                    // Main escribe con FileWriter (charset por defecto): se lee igual.
                    String contenido = new String(Files.readAllBytes(p), Charset.defaultCharset());
                    resultado.put(p.getFileName().toString(), normalizar(contenido));
                }
            }
        }
        return resultado;
    }

    private static void borrar(Path directorio) {
        if (directorio == null) {
            return;
        }
        try (Stream<Path> arbol = Files.walk(directorio)) {
            arbol.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            // Limpieza de un directorio temporal: no debe romper la prueba.
        }
    }
}

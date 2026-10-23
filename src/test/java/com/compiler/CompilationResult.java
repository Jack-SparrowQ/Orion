package com.compiler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntSupplier;
import java.util.stream.Stream;

import com.compiler.cli.Options.Mode;

/**
 * Ejecuta Compiler.compile (o Compiler.run) y captura todo lo observable:
 * lo que sale por consola (stdout y stderr), el codigo de salida y los
 * archivos generados.
 */
final class ResultadoCompilacion {

    final int exitCode;
    final String stdout;
    final String stderr;
    /** nombre de archivo generado -> contenido (ordenado por nombre). */
    final Map<String, String> archivos;

    private ResultadoCompilacion(int exitCode, String stdout, String stderr, Map<String, String> archivos) {
        this.exitCode = exitCode;
        this.stdout = stdout;
        this.stderr = stderr;
        this.archivos = archivos;
    }

    /** Compila en el modo por defecto (genera programa.js). */
    static ResultadoCompilacion ejecutar(String codigoOrion) {
        return ejecutar(codigoOrion, Mode.COMPILE);
    }

    /**
     * @param codigoOrion codigo fuente a compilar
     * @param modo modo de compilacion; el artefacto se llama programa.js o programa.tac
     * @param directoriosPrevios nombres de directorios que se crean antes de compilar
     *        (sirven para provocar fallos de escritura: un directorio con el nombre
     *        del archivo de salida impide crear el archivo)
     */
    static ResultadoCompilacion ejecutar(String codigoOrion, Mode modo, String... directoriosPrevios) {
        Path directorio = null;
        try {
            directorio = Files.createTempDirectory("orion-test");
            for (String nombre : directoriosPrevios) {
                Files.createDirectory(directorio.resolve(nombre));
            }

            Path salida = directorio.resolve(modo == Mode.EMIT_IR ? "programa.tac" : "programa.js");
            ResultadoCompilacion r = capturar(
                    () -> Compiler.compile(codigoOrion, modo, salida, false, false), directorio);

            // La ruta temporal cambia en cada ejecucion: se quita para que el informe sea estable.
            String prefijo = directorio + File.separator;
            return new ResultadoCompilacion(r.exitCode, r.stdout.replace(prefijo, ""),
                    r.stderr.replace(prefijo, ""), r.archivos);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            borrar(directorio);
        }
    }

    /** Ejecuta la linea de comandos completa (no lee archivos generados). */
    static ResultadoCompilacion ejecutarCli(String... args) {
        return capturar(() -> Compiler.run(args), null);
    }

    private static ResultadoCompilacion capturar(IntSupplier accion, Path directorio) {
        PrintStream outOriginal = System.out;
        PrintStream errOriginal = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode;
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        try {
            exitCode = accion.getAsInt();
        } finally {
            System.setOut(outOriginal);
            System.setErr(errOriginal);
        }

        try {
            return new ResultadoCompilacion(
                    exitCode,
                    normalizar(out.toString(StandardCharsets.UTF_8)),
                    normalizar(err.toString(StandardCharsets.UTF_8)),
                    directorio == null ? Map.of() : leerArchivos(directorio));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Texto completo y estable que se compara con la referencia (golden). */
    String informe() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== EXIT CODE ===\n").append(exitCode).append("\n");
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
                    String contenido = Files.readString(p, StandardCharsets.UTF_8);
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

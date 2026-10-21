package com.compiler.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.compiler.cli.Options.Mode;
import com.compiler.cli.Options.UsageException;

public class OptionsTest {

    private static String usageError(String... args) {
        return assertThrows(UsageException.class, () -> Options.parse(args)).getMessage();
    }

    @Test
    void soloArchivo_modoCompilar() throws UsageException {
        Options o = Options.parse(new String[] {"hello.orion"});
        assertEquals(Optional.of(Path.of("hello.orion")), o.getInputFile());
        assertEquals(Mode.COMPILE, o.getMode());
        assertEquals(Optional.empty(), o.getOutputPath());
        assertFalse(o.isVerbose() || o.isQuiet() || o.isHelp() || o.isVersion());
    }

    @Test
    void opcionesAntesYDespuesDelArchivo() throws UsageException {
        Options o = Options.parse(new String[] {"--emit-ir", "hello.orion", "-o", "build/x.tac", "--verbose"});
        assertEquals(Mode.EMIT_IR, o.getMode());
        assertEquals(Optional.of(Path.of("build/x.tac")), o.getOutputPath());
        assertTrue(o.isVerbose());
    }

    @Test
    void outputConIgual() throws UsageException {
        assertEquals(Optional.of(Path.of("out.js")),
                Options.parse(new String[] {"--output=out.js", "a.orion"}).getOutputPath());
    }

    @Test
    void outputGuionEsStdout() throws UsageException {
        assertEquals(Optional.of(Path.of("-")),
                Options.parse(new String[] {"a.orion", "-o", "-"}).getOutputPath());
    }

    @Test
    void helpTienePrioridadSobreTodo() throws UsageException {
        assertTrue(Options.parse(new String[] {"--bogus", "-V", "-h"}).isHelp());
    }

    @Test
    void versionIgnoraElResto() throws UsageException {
        assertTrue(Options.parse(new String[] {"--bogus", "--version"}).isVersion());
    }

    @Test
    void errores() {
        assertEquals("missing input file", usageError("--check"));
        assertEquals("only one input file is allowed", usageError("a.orion", "b.orion"));
        assertEquals("unknown option '--target'", usageError("--target", "js", "a.orion"));
        assertEquals("unknown option '-hV'", usageError("-hV", "a.orion"));
        assertEquals("option '-o' requires a path", usageError("a.orion", "-o"));
        assertEquals("option '--output' requires a path", usageError("a.orion", "--output="));
        assertEquals("option '--check' does not take a value", usageError("--check=yes", "a.orion"));
        assertEquals("options '--verbose' and '--quiet' cannot be used together",
                usageError("--verbose", "--quiet", "a.orion"));
        assertEquals("options '--check' and '--emit-ir' cannot be used together",
                usageError("--check", "--emit-ir", "a.orion"));
        assertEquals("options '--tokens' and '--ast' cannot be used together",
                usageError("--tokens", "--ast", "a.orion"));
        assertEquals("options '-o' and '--check' cannot be used together",
                usageError("--check", "-o", "x", "a.orion"));
        assertEquals("options '-o' and '--tokens' cannot be used together",
                usageError("-o", "x", "--tokens", "a.orion"));
    }

    @Test
    void modoRepetidoEsValido() throws UsageException {
        assertEquals(Mode.AST, Options.parse(new String[] {"--ast", "--ast", "a.orion"}).getMode());
    }
}

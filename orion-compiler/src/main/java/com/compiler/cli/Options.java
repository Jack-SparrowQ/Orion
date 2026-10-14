package com.compiler.cli;

import java.nio.file.Path;
import java.util.Optional;

/**
 * 
 * Options
 */
public final class Options {
    
    public enum Mode {
        COMPILE,
        CHECK,
        AST,
        TOKENS,
        EMIT_IR
    }

    private final Optional<Path> inputFile;
    private final Optional<Path> outputPath;
    private final Mode mode;

    private final boolean verbose;
    private final boolean quiet;
    private final boolean help;
    private final boolean version;

    /**
     * Constructor.
     * @param inputFile
     * @param outputPath
     * @param mode
     * @param verbose
     * @param quiet
     * @param help
     * @param version
     */
    public Options(
        Optional<Path> inputFile,
        Optional<Path> outputPath,
        Mode mode,
        boolean verbose,
        boolean quiet,
        boolean help,
        boolean version
    ) {
        this.inputFile = requireOptional(inputFile, "inputFile");
        this.outputPath = requireOptional(outputPath, "outputPath");

        if(mode == null) {
            throw new IllegalArgumentException("Mode cannot be null.");
        }

        this.mode = mode;
        this.verbose = verbose;
        this.quiet = quiet;
        this.help = help;
        this.version = version;
    }

    private static <T> Optional<T> requireOptional(
            Optional<T> value,
            String name) {

        if (value == null) {
            throw new IllegalArgumentException(
                    name + " cannot be null. Use Optional.empty() instead.");
        }

        return value;
    }

    public Optional<Path> getInputFile() {
        return inputFile;
    }

    public Optional<Path> getOutputPath() {
        return outputPath;
    }

    public Mode getMode() {
        return mode;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public boolean isQuiet() {
        return quiet;
    }

    public boolean isHelp() {
        return help;
    }

    public boolean isVersion() {
        return version;
    }

}

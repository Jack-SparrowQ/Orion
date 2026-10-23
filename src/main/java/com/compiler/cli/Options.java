package com.compiler.cli;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
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
        EMIT_IR;

        /** Command-line flag for this mode, e.g. EMIT_IR -> --emit-ir. */
        public String flag() {
            return "--" + name().toLowerCase().replace('_', '-');
        }

        /** Modes that write an artifact (and therefore accept -o). */
        public boolean writesArtifact() {
            return this == COMPILE || this == EMIT_IR;
        }
    }

    /** Invalid command line: the CLI prints the message and exits with code 2. */
    public static final class UsageException extends Exception {
        public UsageException(String message) {
            super(message);
        }
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

    /**
     * Parses the command line: orion [options] <file>.
     * @param args command-line arguments.
     * @return the parsed options.
     * @throws UsageException if the command line is invalid.
     */
    public static Options parse(String[] args) throws UsageException {

        // --help and --version take precedence: the remaining arguments are ignored.
        if (Arrays.stream(args).anyMatch(a -> a.equals("-h") || a.equals("--help"))) {
            return new Options(Optional.empty(), Optional.empty(), Mode.COMPILE, false, false, true, false);
        }
        if (Arrays.stream(args).anyMatch(a -> a.equals("-V") || a.equals("--version"))) {
            return new Options(Optional.empty(), Optional.empty(), Mode.COMPILE, false, false, false, true);
        }

        Path input = null;
        Path output = null;
        Mode mode = null;
        boolean verbose = false;
        boolean quiet = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            String value = null;

            if (arg.startsWith("--") && arg.contains("=")) {
                value = arg.substring(arg.indexOf('=') + 1);
                arg = arg.substring(0, arg.indexOf('='));
            }

            if (value != null && !arg.equals("--output")) {
                throw new UsageException("option '" + arg + "' does not take a value");
            }

            switch (arg) {
                case "--verbose" -> verbose = true;
                case "--quiet" -> quiet = true;
                case "--check" -> mode = setMode(mode, Mode.CHECK);
                case "--emit-ir" -> mode = setMode(mode, Mode.EMIT_IR);
                case "--tokens" -> mode = setMode(mode, Mode.TOKENS);
                case "--ast" -> mode = setMode(mode, Mode.AST);
                case "-o", "--output" -> {
                    if (value == null) {
                        if (i + 1 >= args.length) {
                            throw new UsageException("option '" + arg + "' requires a path");
                        }
                        value = args[++i];
                    }
                    if (value.isEmpty()) {
                        throw new UsageException("option '" + arg + "' requires a path");
                    }
                    output = toPath(value);
                }
                default -> {
                    if (arg.startsWith("-")) {
                        throw new UsageException("unknown option '" + arg + "'");
                    }
                    if (input != null) {
                        throw new UsageException("only one input file is allowed");
                    }
                    input = toPath(arg);
                }
            }
        }

        if (mode == null) {
            mode = Mode.COMPILE;
        }
        if (verbose && quiet) {
            throw new UsageException("options '--verbose' and '--quiet' cannot be used together");
        }
        if (output != null && !mode.writesArtifact()) {
            throw new UsageException("options '-o' and '" + mode.flag() + "' cannot be used together");
        }
        if (input == null) {
            throw new UsageException("missing input file");
        }

        return new Options(Optional.of(input), Optional.ofNullable(output), mode, verbose, quiet, false, false);
    }

    private static Mode setMode(Mode current, Mode requested) throws UsageException {
        if (current != null && current != requested) {
            throw new UsageException("options '" + current.flag() + "' and '"
                    + requested.flag() + "' cannot be used together");
        }
        return requested;
    }

    private static Path toPath(String value) throws UsageException {
        try {
            return Path.of(value);
        } catch (InvalidPathException e) {
            throw new UsageException("invalid path '" + value + "'");
        }
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

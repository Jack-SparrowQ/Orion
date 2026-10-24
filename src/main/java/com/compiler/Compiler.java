package com.compiler;

import java.util.List;
import java.util.Optional;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import com.compiler.cli.Options;
import com.compiler.cli.Options.Mode;
import com.compiler.error.ErrorHandler;
import com.compiler.ir.Cuadrupla;
import com.compiler.ir.GeneradorCodigoIntermedio;
import com.compiler.lexer.*;
import com.compiler.parser.*;
import com.compiler.parser.ast.Sentencia;
import com.compiler.semantic.*;
import com.compiler.util.ASTPrinter;
import com.compiler.util.JSGenerator;

import com.compiler.util.ConsoleColors;

public class Compiler {

        static final int EXIT_OK = 0;
        static final int EXIT_SOURCE_ERRORS = 1;
        static final int EXIT_USAGE = 2;
        static final int EXIT_IO = 3;

        private static final String HELP = """
                Usage: orion [options] <file>

                Compile an Orion program to JavaScript.

                Modes (only one at a time):
                  (none)        Compile and generate <name>.js
                  --check       Validate without generating files
                  --emit-ir     Generate <name>.tac (three-address code)
                  --tokens      Display tokens
                  --ast         Display the abstract syntax tree

                Options:
                  -o, --output <path>   Output path ('-' for stdout)
                      --verbose         Display process details
                      --quiet           Suppress informational messages
                  -h, --help            Display this help
                  -V, --version         Display the compiler version

                Exit codes: 0 success, 1 source errors, 2 usage error, 3 I/O error.
                """;

        /** Phase trace: stderr with --verbose, discarded otherwise. */
        private static PrintStream info = System.err;


    public static void main(String[] args) {
        System.exit(run(args));
    }

    /**
     * Runs the command line: orion [options] <file>.
     * @param args command-line arguments.
     * @return the process exit code.
     */
    static int run(String[] args) {

        if (args.length == 0) {
            System.err.print(HELP);
            return EXIT_USAGE;
        }

        Options options;
        try {
            options = Options.parse(args);
        } catch (Options.UsageException e) {
            return usageError(e.getMessage());
        }

        if (options.isHelp()) {
            System.out.print(HELP);
            return EXIT_OK;
        }

        if (options.isVersion()) {
            // Only present when running from the packaged JAR (manifest entry).
            String version = Compiler.class.getPackage().getImplementationVersion();
            System.out.println(version == null ? "orion (development version)" : "orion " + version);
            return EXIT_OK;
        }

        Path input = options.getInputFile().get();
        Mode mode = options.getMode();
        Path output = options.getOutputPath().orElseGet(() -> defaultOutput(input, mode));

        String orionCode;
        try {
            orionCode = Files.readString(input, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            System.err.println("orion: error: cannot read '" + input + "': file not found");
            return EXIT_IO;
        } catch (IOException e) {
            System.err.println("orion: error: cannot read '" + input + "': " + e.getMessage());
            return EXIT_IO;
        }

        try {
            if (mode.writesArtifact() && !isStdout(output)
                    && Files.exists(output) && Files.isSameFile(input, output)) {
                return usageError("output file '" + output + "' would overwrite the input file");
            }
        } catch (IOException e) {
            System.err.println("orion: error: cannot access '" + output + "': " + e.getMessage());
            return EXIT_IO;
        }

        return compile(orionCode, mode, output, options.isVerbose(), options.isQuiet());
    }

    /**
     * Runs the compilation phases up to the one the mode asks for.
     * @param orionCode source code.
     * @param mode where to stop and what to produce.
     * @param output artifact path ("-" for stdout); ignored by modes without artifact.
     * @param verbose show the phase trace on stderr.
     * @param quiet suppress informational messages.
     * @return the process exit code.
     */
    static int compile(String orionCode, Mode mode, Path output, boolean verbose, boolean quiet) {

        info = verbose ? System.err : new PrintStream(OutputStream.nullOutputStream());

        showHeader(orionCode);

        ErrorHandler errorHandler  = new ErrorHandler();

        Optional<List<Token>> tokens = lexicalAnalyzer(
                orionCode, errorHandler, mode == Mode.TOKENS ? System.out : info);

        if(tokens.isEmpty()) {
            return EXIT_SOURCE_ERRORS;
        }

        if(mode == Mode.TOKENS) { return EXIT_OK; }

        Optional<List<Sentencia>> ASTTree = analizeSyntax(tokens.get(), errorHandler);

        if(ASTTree.isEmpty()) { return EXIT_SOURCE_ERRORS; }

        if(mode == Mode.AST) {
            ASTPrinter.printAST(ASTTree.get(), "");
            return EXIT_OK;
        }

        SymbolTable symbolsTable = construirSymbolsTable(ASTTree.get(), errorHandler);

        if(!analizarSemantica(ASTTree.get(), symbolsTable, errorHandler)) {
            return EXIT_SOURCE_ERRORS;
        }

        if(mode == Mode.CHECK) {
            if(!quiet) {
                System.err.println("orion: no errors found");
            }
            return EXIT_OK;
        }

        String tacCode = generateIntermediateCode(ASTTree.get());

        String artifact = mode == Mode.EMIT_IR ? tacCode : generarJavaScript(ASTTree.get());

        int exitCode = writeArtifact(artifact, output, quiet);

        if(exitCode == EXIT_OK) {
            mostrarFinal();
        }

        return exitCode;
    }



    // ================================================================
    // MOSTRAR ERRORES
    // ================================================================

    /**
     * Si la fase dejó errores en el gestor, los muestra y devuelve true
     * para que Compiler() aborte la compilación.
     * @param errorHandler
     * @param phase
     * @return
     */
    private static boolean reportarSiHayErrores(
        ErrorHandler errorHandler,
        String phase
    ) {
        if(!errorHandler.hayErrores()) {
                return false;
        }

        System.err.println();
        System.err.println(
                "Se encontraron errores durante el analisis " + phase + "."
        );

        errorHandler.mostrarErrores();

        System.err.println();
        System.err.println(ConsoleColors.RED + "[COMPILACION ABORTADA]" + ConsoleColors.RESET);

        return true;
    }

    private static int usageError(String message) {
        System.err.println("orion: error: " + message);
        System.err.println("Try 'orion --help'.");
        return EXIT_USAGE;
    }

    // ================================================================
    // ARCHIVOS DE SALIDA
    // ================================================================

    /** <basename>.js (or .tac with --emit-ir) in the current working directory. */
    private static Path defaultOutput(Path input, Mode mode) {
        String name = input.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return Path.of(base + (mode == Mode.EMIT_IR ? ".tac" : ".js"));
    }

    private static boolean isStdout(Path output) {
        return output.toString().equals("-");
    }

    /**
     * Escribe el artefacto en la ruta indicada (creando los directorios
     * padre) o en stdout si la ruta es "-".
     * @return EXIT_OK, o EXIT_IO si no se pudo escribir (error ya mostrado).
     */
    private static int writeArtifact(String content, Path output, boolean quiet) {
        if (isStdout(output)) {
            System.out.print(content.endsWith("\n") ? content : content + "\n");
            return EXIT_OK;
        }

        try {
            Files.createDirectories(output.toAbsolutePath().getParent());
            Files.writeString(output, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("orion: error: cannot write '" + output + "': " + e.getMessage());
            return EXIT_IO;
        }

        if (!quiet) {
            System.err.println("orion: generated " + output);
        }
        return EXIT_OK;
    }

    //COMPILATION PHASES.

    /**
     * Just show a simple simple header.
     * @param orionCode
     */
    private static void showHeader(String orionCode) {
        info.println("ORION Compiler.\n");
        info.println("Source Code: \n");
        info.println(orionCode);
    }

    /**
     * Lexical Analysis.
     * @param orionCode
     * @param errorHandler
     * @param tokensOut where the recognized tokens are listed.
     * @return the tokens, or empty if there were lexical errors.
     */
    private static Optional<List<Token>> lexicalAnalyzer(
        String orionCode,
        ErrorHandler errorHandler,
        PrintStream tokensOut
    ) {

        info.println("=== 1. LEXICAL ANALYSIS PHASE ===");

        Lexer lexer = new Lexer(orionCode, errorHandler);

        Tokenizer tokenizer = new Tokenizer(lexer);

        tokenizer.run();

        for (Token token : tokenizer.getTokensList()) {
            tokensOut.println(Tokenizer.getTokenInformation(token));
        }

        if (reportarSiHayErrores(errorHandler, "LEXICO")) {
            return Optional.empty();
        }

        info.println(
                "Total de tokens reconocidos: "
                        + tokenizer.getNumTokens()
        );

        info.println();

        return Optional.of(tokenizer.getTokensList());
    }

    /**
     * Syntax Analysis.
     * @param tokensList
     * @param errorHandler
     * @return the AST, or empty if there were syntax errors.
     */
    private static Optional<List<Sentencia>> analizeSyntax(
        List<Token> tokensList,
        ErrorHandler errorHandler
    ) {
        info.println("=== 2. FASE DE ANALISIS SINTACTICO ===");

        Parser parser = new Parser(
                tokensList,
                errorHandler
        );

        List<Sentencia> arbolAST = parser.parsear();

        if (reportarSiHayErrores(errorHandler, "SINTACTICO")) {
            return Optional.empty();
        }

        info.println(
                ConsoleColors.GREEN + "✓ Análisis SINTACTICO completado correctamente." + ConsoleColors.RESET
        );

        info.println(
                "Número de nodos principales: "
                        + arbolAST.size()
        );

        info.println();

        return Optional.of(arbolAST);
    }

    /**
     * Phase 3: Symbol table construction.
     * Does not abort: errors detected are reported at the end of Phase 4.
     * @param arbolAST
     * @param errorHandler
     * @return symbols table.
     */
    private static SymbolTable construirSymbolsTable(
        List<Sentencia> arbolAST,
        ErrorHandler errorHandler
    ) {
        info.println("=== 3. SYMBOLS TABLE ===");

        SymbolTable symbolsTable = new SymbolTable();

        SemanticAnalyzer.analyzeSymbols(
                arbolAST,
                symbolsTable,
                errorHandler
        );

        symbolsTable.mostrar(info);

        info.println();

        return symbolsTable;
    }

    /**
     * Phase 4: Semantic Analysis.
     * @param arbolAST
     * @param symbolsTable
     * @param errorHandler
     * @return true if there are no errors; false if there were.
     */
    private static boolean analizarSemantica(
        List<Sentencia> arbolAST,
        SymbolTable symbolsTable,
        ErrorHandler errorHandler
    ) {
        info.println("=== 4. ANALISIS SEMANTICO ===");

        SemanticAnalyzer.analyzeSemantic(
                arbolAST,
                symbolsTable,
                errorHandler
        );

        if (reportarSiHayErrores(errorHandler, "SEMANTICO")) {
            return false;
        }

        info.println(
                ConsoleColors.GREEN + "Semantic Analysis is done." + ConsoleColors.RESET
        );

        info.println();

        return true;
    }

    /**
     * Phase 5: Intermediate code generation (TAC and quadruples)
     * @param arbolAST
     * @return the three-address code as text.
     */
    private static String generateIntermediateCode(List<Sentencia> arbolAST) {
        info.println("=== 5. GENERACION DE CODIGO INTERMEDIO ===");

        GeneradorCodigoIntermedio generadorIR = new GeneradorCodigoIntermedio();
        List<Cuadrupla> codigoIntermedio = generadorIR.generar(arbolAST);
        String textoTAC = GeneradorCodigoIntermedio.aTexto(codigoIntermedio);

        info.println();
        info.println("Codigo de tres direcciones:");
        info.println("-----------------------------------------");
        info.println(textoTAC);
        info.println("-----------------------------------------");
        info.println();

        info.println("Cuadruplas (op, arg1, arg2, resultado):");
        for (int i = 0; i < codigoIntermedio.size(); i++) {
            info.println(String.format("%3d  %s", i, codigoIntermedio.get(i).comoTupla()));
        }
        info.println();

        return textoTAC;
    }

    /**
     * Phase 6: JS code generation.
     * @param arbolAST list of AST elements.
     * @return JavaScript code.
     */
    private static String generarJavaScript(List<Sentencia> arbolAST) {
        info.println("=== 6. GENERACION DE CODIGO JAVASCRIPT ===");

        JSGenerator generador = new JSGenerator();

        String JavaScriptCode =
                generador.generar(arbolAST);

        info.println();
        info.println(
                "CODIGO JavaScript generado:"
        );

        info.println("-----------------------------------------");
        info.println(JavaScriptCode);
        info.println("-----------------------------------------");

        info.println();

        return JavaScriptCode;
    }

    private static void mostrarFinal() {
        info.println();
        info.println(ConsoleColors.GREEN + "Compilation completed successfully." + ConsoleColors.RESET);
    }
}

package com.compiler;

import java.util.List;
import java.util.Optional;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

import com.compiler.error.ErrorHandler;
import com.compiler.ir.Cuadrupla;
import com.compiler.ir.GeneradorCodigoIntermedio;
import com.compiler.lexer.*;
import com.compiler.parser.*;
import com.compiler.semantic.*;
import com.compiler.util.ASTPrinter;
import com.compiler.util.JSGenerator;

import com.compiler.util.ConsoleColors;

public class Compiler {

        private static final String TAC_FILE = "programa.tac";
        private static final String JS_FILE = "program.js";

        private static final String ORION_CODE = """
                let a = 1;
                let b = 2;
                let c = 3;
                let d = 4;

                let e = a + a * (a-c) + (b - c) * d;
                print(e);
                """;


    public static void main(String[] args) {
        compile(ORION_CODE, Path.of(""));
    }

    static void compile(String orionCode, Path output) {

        showHeader(orionCode);

        ErrorHandler errorHandler  = new ErrorHandler();

        Optional<List<Token>> tokens = lexicalAnalyzer(orionCode, errorHandler);

        if(tokens.isEmpty()) {
            return;
        }

        Optional<List<ElementoAST>> ASTTree = analizeSyntax(tokens.get(), errorHandler);

        if(ASTTree.isEmpty()) { return; }

        mostrarAST(ASTTree.get());

        SymbolTable symbolsTable = construirSymbolsTable(ASTTree.get(), errorHandler);

        if(!analizarSemantica(ASTTree.get(), symbolsTable, errorHandler)) {
            return;
        }

        generateIntermediateCode(ASTTree.get(), output);

        String jsCode = generarJavaScript(ASTTree.get());

        if(!escribirJavaScript(jsCode, output)) {
            return;
        }

        mostrarFinal();
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
                "Se encontraron errores durante el análisis " + phase + "."
        );

        errorHandler.mostrarErrores();

        System.err.println();
        System.err.println(ConsoleColors.RED + "[COMPILACIÓN ABORTADA]" + ConsoleColors.RESET);

        return true;
    }

    /**
     * Escribe el contenido en la ruta indicada y cierra el archivo.
     * Cada llamador decide qué hacer si falla (la política no vive aquí).
     * @param route
     * @param content
     * @throws IOException
     */
    private static void writeFile(
        String route,
        String content
    ) throws IOException {
        try (FileWriter writer = new FileWriter(route)) {
                writer.write(content);
        }
    }

    //COMPILATION PHASES.

    /**
     * Just show a simple simple header.
     * @param orionCode
     */
    private static void showHeader(String orionCode) {
        System.out.println("ORION Compiler.\n");
        System.out.println("Source Code: \n");
        System.out.println(orionCode);
    }

    /**
     * Lexical Analysis.
     * @param orionCode
     * @param errorHandler
     * @return the tokens, or empty if there were lexical errors.
     */
    private static Optional<List<Token>> lexicalAnalyzer(
        String orionCode,
        ErrorHandler errorHandler
    ) {

        System.out.println("1. LEXICAL ANALYSIS PHASE");

        Lexer lexer = new Lexer(orionCode, errorHandler);

        Tokenizer tokenizer = new Tokenizer(lexer);

        tokenizer.run();

        if (reportarSiHayErrores(errorHandler, "lexico")) {
            return Optional.empty();
        }

        System.out.println(
                "Total de tokens reconocidos: "
                        + tokenizer.getNumTokens()
        );

        System.out.println();

        return Optional.of(tokenizer.getTokensList());
    }

    /**
     * Syntax Analysis.
     * @param tokensList
     * @param errorHandler
     * @return the AST, or empty if there were syntax errors.
     */
    private static Optional<List<ElementoAST>> analizeSyntax(
        List<Token> tokensList,
        ErrorHandler errorHandler
    ) {
        System.out.println("=== 2. FASE DE ANÁLISIS SINTÁCTICO ===");

        Parser parser = new Parser(
                tokensList,
                errorHandler
        );

        List<ElementoAST> arbolAST = parser.parsear();

        if (reportarSiHayErrores(errorHandler, "sintactic")) {
            return Optional.empty();
        }

        System.out.println(
                ConsoleColors.RED + "✓ Análisis sintáctico completado correctamente." + ConsoleColors.GREEN
        );

        System.out.println(
                "Número de nodos principales: "
                        + arbolAST.size()
        );

        System.out.println();

        return Optional.of(arbolAST);
    }

    /** Fase 3: visualización del AST. */
    private static void mostrarAST(List<ElementoAST> arbolAST) {
        System.out.println("=== 3. ÁRBOL DE SINTAXIS ABSTRACTA (AST) ===");

        ASTPrinter.printAST(arbolAST, "");

        System.out.println();
    }

    /**
     * Phase 4: Symbol table construction. 
     * Does not abort: errors detected are reported at the end of Phase 5.
     * @param arbolAST
     * @param errorHandler
     * @return symbols table.
     */
    private static SymbolTable construirSymbolsTable(
        List<ElementoAST> arbolAST,
        ErrorHandler errorHandler
    ) {
        System.out.println("=== 4. SYMBOLS TABLE ===");

        SymbolTable symbolsTable = new SymbolTable();

        SemanticAnalyzer.analyzeSymbols(
                arbolAST,
                symbolsTable,
                errorHandler
        );

        //symbolsTable.mostrar();

        System.out.println();

        return symbolsTable;
    }

    /**
     * Phase 5: Semantic Analysis.
     * @param arbolAST
     * @param symbolsTable
     * @param errorHandler
     * @return true if there are no errors; false if there were.
     */
    private static boolean analizarSemantica(
        List<ElementoAST> arbolAST,
        SymbolTable symbolsTable,
        ErrorHandler errorHandler
    ) {
        System.out.println("=== 5. ANÁLISIS SEMÁNTICO ===");

        SemanticAnalyzer.analyzeSemantic(
                arbolAST,
                symbolsTable,
                errorHandler
        );

        if (reportarSiHayErrores(errorHandler, "Semantic")) {
            return false;
        }

        System.out.println(
                ConsoleColors.RED + "✓ Semantic Analysis is done." + ConsoleColors.GREEN
        );

        System.out.println();

        return true;
    }

    /**
     * Phase 6: Intermediate code generation (TAC and quadruples)
     * @param arbolAST
     * @param output
     */
    private static void generateIntermediateCode(
        List<ElementoAST> arbolAST,
        Path output
    ) {
        System.out.println("=== 6. GENERACIÓN DE CÓDIGO INTERMEDIO ===");

        GeneradorCodigoIntermedio generadorIR = new GeneradorCodigoIntermedio();
        List<Cuadrupla> codigoIntermedio = generadorIR.generar(arbolAST);
        String textoTAC = GeneradorCodigoIntermedio.aTexto(codigoIntermedio);

        System.out.println();
        System.out.println("Código de tres direcciones:");
        System.out.println("-----------------------------------------");
        System.out.println(textoTAC);
        System.out.println("-----------------------------------------");
        System.out.println();

        System.out.println("Cuádruplas (op, arg1, arg2, resultado):");
        for (int i = 0; i < codigoIntermedio.size(); i++) {
            System.out.println(String.format("%3d  %s", i, codigoIntermedio.get(i).comoTupla()));
        }
        System.out.println();

        try {
            writeFile(output.resolve(TAC_FILE).toString(), textoTAC);
            System.out.println("✓ Archivo generado correctamente: " + TAC_FILE);
        } catch (IOException e) {
            // No es fatal: el resto de la compilación continúa
            System.err.println("[AVISO] No se pudo generar " + TAC_FILE + ": " + e.getMessage());
        }
        System.out.println();
    }

    /**
     * Phase 7: JS code generation.
     * @param arbolAST list of AST elements.
     * @return JavaScript code.
     */
    private static String generarJavaScript(List<ElementoAST> arbolAST) {
        System.out.println("=== 6. GENERACIÓN DE CÓDIGO JAVASCRIPT ===");

        JSGenerator generador = new JSGenerator();

        String JavaScriptCode =
                generador.generar(arbolAST);

        System.out.println();
        System.out.println(
                "Código JavaScript generado:"
        );

        System.out.println("-----------------------------------------");
        System.out.println(JavaScriptCode);
        System.out.println("-----------------------------------------");

        System.out.println();

        return JavaScriptCode;
    }

    /**
     * Fase 8: escritura del archivo JavaScript de salida.
     * @return true si se escribió; false si falló (error ya mostrado), lo que aborta.
     */
    private static boolean escribirJavaScript(
        String JavaScriptCode,
        Path output
    ) {
        System.out.println("=== 7. GENERACIÓN DEL ARCHIVO DE SALIDA ===");

        try {

            writeFile(output.resolve(JS_FILE).toString(), JavaScriptCode);

            System.out.println(
                    ConsoleColors.RED + "Archivo generado correctamente: "
                            + JS_FILE + ConsoleColors.RESET
            );

            return true;

        } catch (IOException e) {

            System.err.println(
                    ConsoleColors.RED + "[ERROR] No se pudo generar el archivo." + ConsoleColors.RESET
            );

            System.err.println(
                    "Detalle: " + e.getMessage()
            );

            return false;
        }
    }

    private static void mostrarFinal() {
        System.out.println();
        System.out.println(ConsoleColors.GREEN + "Compilation completed successfully." + ConsoleColors.RESET);
    }
}
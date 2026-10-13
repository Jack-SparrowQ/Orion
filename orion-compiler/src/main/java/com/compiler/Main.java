package com.compiler;

import java.util.List;
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

public class Main {

        private static final String TAC_FILE = "programa.tac";
        private static final String JS_FILE = "program.js";

        private static final String ORION_CODE = """
                let a = ;
                """;


    public static void main(String[] args) {
        compile(ORION_CODE, Path.of(""));
    }

    static void compile(String orionCode, Path output) {

        System.out.println("=================================================");
        System.out.println("              COMPILADOR ORION");
        System.out.println("=================================================");
        System.out.println();

        System.out.println("Código fuente:");
        System.out.println("-----------------------------------------");
        System.out.println(orionCode);
        System.out.println("-----------------------------------------");
        System.out.println();


        // ============================================================
        // 1. ANÁLISIS LÉXICO
        // ============================================================

        System.out.println("=== 1. FASE DE ANÁLISIS LÉXICO ===");

        ErrorHandler gestorErrores = new ErrorHandler();
        Lexer lexer = new Lexer(
                orionCode,
                gestorErrores
        );

        Tokenizer lexerAnalyzer = new Tokenizer(lexer);

        lexerAnalyzer.run();

        if(reportarSiHayErrores(gestorErrores, "lexico")) {
                return;
        }

        System.out.println(
                "Total de tokens reconocidos: "
                        + lexerAnalyzer.getNumTokens()
        );

        System.out.println();


        // ============================================================
        // 2. ANÁLISIS SINTÁCTICO
        // ============================================================

        System.out.println("=== 2. FASE DE ANÁLISIS SINTÁCTICO ===");

        List<Token> listaTokens = lexerAnalyzer.getTokensList();

        Parser parser = new Parser(
                listaTokens,
                gestorErrores
        );

        List<ElementoAST> arbolAST = parser.parsear();

        /*
         * Si el Parser devuelve null significa que ocurrió
         * algún error sintáctico.
         */
        if (reportarSiHayErrores(gestorErrores, "sintactic")) {
            return;
        }

        System.out.println(
                "✓ Análisis sintáctico completado correctamente."
        );

        System.out.println(
                "Número de nodos principales: "
                        + arbolAST.size()
        );

        System.out.println();


        // ============================================================
        // 3. VISUALIZACIÓN DEL AST
        // ============================================================

        System.out.println("=== 3. ÁRBOL DE SINTAXIS ABSTRACTA (AST) ===");

        ASTPrinter.printAST(arbolAST, "");

        System.out.println();


        // ============================================================
        // 4. TABLA DE SÍMBOLOS
        // ============================================================

        System.out.println("=== 4. CONSTRUCCIÓN DE TABLA DE SÍMBOLOS ===");

        SymbolTable tablaSimbolos = new SymbolTable();

        SemanticAnalyzer.analyzeSymbols(
                arbolAST, 
                tablaSimbolos, 
                gestorErrores
        );

        tablaSimbolos.mostrar();

        System.out.println();


        // ============================================================
        // 5. ANÁLISIS SEMÁNTICO
        // ============================================================

        System.out.println("=== 5. ANÁLISIS SEMÁNTICO ===");

        SemanticAnalyzer.analyzeSemantic(
                arbolAST, 
                tablaSimbolos, 
                gestorErrores
        );

        if (reportarSiHayErrores(gestorErrores, "Semantic")) {
            return;
        }

        System.out.println(
                "✓ Análisis semántico completado correctamente."
        );

        System.out.println();

        // ============================================================
        // 6. GENERACIÓN DE CÓDIGO INTERMEDIO
        // ============================================================

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
                writeFile(TAC_FILE, textoTAC);
                System.out.println("✓ Archivo generado correctamente: programa.tac");
        } catch (IOException e) {
        // No es fatal: el resto de la compilación continúa
                System.err.println("[AVISO] No se pudo generar programa.tac: " + e.getMessage());
        }
        System.out.println();


        // ============================================================
        // 7. GENERACIÓN DE CÓDIGO JS
        // ============================================================

        System.out.println("=== 6. GENERACIÓN DE CÓDIGO JAVASCRIPT ===");

        JSGenerator generador = new JSGenerator();

        String codigoJavaScript =
                generador.generar(arbolAST);

        System.out.println();
        System.out.println(
                "Código JavaScript generado:"
        );

        System.out.println("-----------------------------------------");
        System.out.println(codigoJavaScript);
        System.out.println("-----------------------------------------");

        System.out.println();


        // ============================================================
        // 8. GENERACIÓN DEL ARCHIVO
        // ============================================================

        System.out.println("=== 7. GENERACIÓN DEL ARCHIVO DE SALIDA ===");

        try {

            writeFile(JS_FILE, codigoJavaScript);

            System.out.println(
                    "Archivo generado correctamente: "
                            + JS_FILE
            );

        } catch (IOException e) {

            System.err.println(
                    "[ERROR] No se pudo generar el archivo."
            );

            System.err.println(
                    "Detalle: " + e.getMessage()
            );

            return;
        }


        // ============================================================
        // FIN
        // ============================================================

        System.out.println();
        System.out.println("=================================================");
        System.out.println("       COMPILACIÓN FINALIZADA CORRECTAMENTE");
        System.out.println("=================================================");
    }



    // ================================================================
    // MOSTRAR ERRORES
    // ================================================================

    /**
     * Si la fase dejó errores en el gestor, los muestra y devuelve true
     * para que main() aborte la compilación.
     * @param gestorErrores
     * @param phase
     * @return
     */
    private static boolean reportarSiHayErrores(
        ErrorHandler gestorErrores,
        String phase
    ) {
        if(!gestorErrores.hayErrores()) {
                return false;
        }

        System.err.println();
        System.err.println(
                "Se encontraron errores durante el análisis " + phase + "."
        );

        gestorErrores.mostrarErrores();

        System.err.println();
        System.err.println("[COMPILACIÓN ABORTADA]");

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
}
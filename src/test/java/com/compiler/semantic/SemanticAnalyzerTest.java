package com.compiler.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.compiler.error.ErrorHandler;
import com.compiler.lexer.Lexer;
import com.compiler.lexer.Tokenizer;
import com.compiler.parser.Parser;
import com.compiler.parser.ast.Sentencia;

public class SemanticAnalyzerTest {

    private ErrorHandler errores;
    private SymbolTable tabla;

    /** Analiza el programa (desde cero) y devuelve los códigos de error: "E301 E306". */
    private String analizar(String codigo) {
        errores = new ErrorHandler();
        tabla = new SymbolTable();
        Tokenizer tokenizer = new Tokenizer(new Lexer(codigo, errores));
        tokenizer.run();
        List<Sentencia> programa = new Parser(tokenizer.getTokensList(), errores).parsear();
        assertEquals(List.of(), errores.obtenerErrores(), "el programa de prueba debe ser sintácticamente válido");

        SemanticAnalyzer.analyzeSymbols(programa, tabla, errores);
        SemanticAnalyzer.analyzeSemantic(programa, tabla, errores);

        return errores.obtenerErrores().stream()
                .map(e -> e.toString().substring(0, e.toString().indexOf('[')))
                .collect(Collectors.joining(" "));
    }

    private Type tipoDe(String variable) {
        return tabla.buscar(variable).getTipo();
    }

    // ------------------------------------------------------------
    // Inferencia de tipos
    // ------------------------------------------------------------

    @Test
    void inferenciaDeTipos() {
        assertEquals("", analizar("""
                let i = 1;
                let f = 2.5;
                let suma = i + 1;
                let mixta = i + f;
                let division = i / 2;
                let resto = i % 2;
                let negado = -f;
                let cmp = i > f;
                let logica = cmp && !false;
                let igual = i == f;
                """));
        assertEquals(Type.INT, tipoDe("i"));
        assertEquals(Type.FLOAT, tipoDe("f"));
        assertEquals(Type.INT, tipoDe("suma"));
        assertEquals(Type.FLOAT, tipoDe("mixta"));
        assertEquals(Type.FLOAT, tipoDe("division")); // en JS, 1 / 2 es 0.5
        assertEquals(Type.INT, tipoDe("resto"));
        assertEquals(Type.FLOAT, tipoDe("negado"));
        assertEquals(Type.BOOL, tipoDe("cmp"));
        assertEquals(Type.BOOL, tipoDe("logica"));
        assertEquals(Type.BOOL, tipoDe("igual"));
    }

    // ------------------------------------------------------------
    // E306: operandos incompatibles
    // ------------------------------------------------------------

    @Test
    void operandosIncompatibles() {
        assertEquals("E306", analizar("let x = 1 + true;"));
    }

    @Test
    void operadoresUnariosIncompatibles() {
        assertEquals("E306 E306", analizar("let a = !5;\nlet b = -true;"));
    }

    @Test
    void logicosYComparacionesIncompatibles() {
        assertEquals("E306 E306 E306", analizar("let a = 1 && 2;\nlet b = 1 == true;\nlet c = true < false;"));
    }

    /** Un error no provoca otros en cascada: x tiene tipo desconocido. */
    @Test
    void sinErroresEnCascada() {
        assertEquals("E301", analizar("let x = y + 1;\nlet z = x * true;\nprint(-x);"));
    }

    // ------------------------------------------------------------
    // E307: asignación incompatible
    // ------------------------------------------------------------

    @Test
    void asignacionIncompatible() {
        assertEquals("E307 E307", analizar("let i = 1;\ni = 2.5;\nlet b = true;\nb = 1;"));
    }

    @Test
    void asignacionConPromocionIntAFloat() {
        assertEquals("", analizar("let f = 1.5;\nf = 2;\nf = f * 2;"));
    }

    // ------------------------------------------------------------
    // E308: condición no booleana
    // ------------------------------------------------------------

    @Test
    void condicionNoBooleana() {
        assertEquals("E308", analizar("let n = 1;\nif (n) { print(n); }"));
        assertEquals("E308 E308", analizar("if (1 + 1) { } else if (2.5) { }"));
    }

    @Test
    void condicionBooleana() {
        assertEquals("", analizar("let n = 1;\nif (n > 0 && true) { print(n); } else { print(0); }"));
    }

    // ------------------------------------------------------------
    // E303: uso antes de la declaración o fuera de su bloque
    // ------------------------------------------------------------

    @Test
    void usoAntesDeDeclarar() {
        assertEquals("E303", analizar("let a = b;\nlet b = 1;"));
    }

    @Test
    void autoreferenciaEnLaDeclaracion() {
        assertEquals("E303", analizar("let a = a + 1;"));
    }

    @Test
    void usoFueraDelBloque() {
        assertEquals("E303", analizar("if (true) { let t = 1; }\nprint(t);"));
    }

    /** Un bloque independiente también abre un ámbito. */
    @Test
    void usoFueraDelBloqueIndependiente() {
        assertEquals("E303", analizar("{ let t = 1; print(t); }\nprint(t);"));
    }

    // ------------------------------------------------------------
    // Funciones
    // ------------------------------------------------------------

    /** Funciones elevadas: se pueden llamar antes de declararlas y de forma recursiva. */
    @Test
    void funcionesElevadasYRecursivas() {
        assertEquals("", analizar("""
                print(fact(5));
                fn fact(n: int): int {
                    if (n <= 1) { return 1; } else { return n * fact(n - 1); }
                }
                let r = fact(3) + 1;
                """));
        assertEquals(Type.INT, tipoDe("r"));
        assertEquals(SymbolCategory.FUNCION, tabla.buscar("fact").getCategoria());
    }

    @Test
    void erroresDeLlamada() {
        assertEquals("E301", analizar("g();"));
        assertEquals("E314", analizar("let x = 1;\nx();"));
        assertEquals("E315", analizar("fn f(a: int) { }\nf();"));
        assertEquals("E307", analizar("fn f(a: int) { }\nf(true);"));
    }

    @Test
    void erroresDeReturn() {
        assertEquals("E316", analizar("return;"));
        assertEquals("E317", analizar("fn f() { return 1; }"));
        assertEquals("E317", analizar("fn f(): int { return; }"));
        assertEquals("E307", analizar("fn f(): int { return true; }"));
        assertEquals("", analizar("fn f(): float { return 1; }"));
    }

    /** Predicado R: un if sin else o un while no garantizan el return. */
    @Test
    void retornoEnTodosLosCaminos() {
        assertEquals("E318", analizar("fn f(a: bool): int { if (a) { return 1; } }"));
        assertEquals("E318", analizar("fn f(): int { while (true) { return 1; } }"));
        assertEquals("", analizar("fn f(a: bool): int { if (a) { return 1; } return 2; }"));
    }

    /** Desde una función solo se ven las globales declaradas antes de ella. */
    @Test
    void globalesVisiblesDesdeFunciones() {
        assertEquals("", analizar("let a = 1;\nfn f(): int { return a; }"));
        assertEquals("E303", analizar("fn f(): int { return b; }\nlet b = 1;"));
    }

    /** Parámetros y cuerpo comparten ámbito; funciones y variables comparten nombres. */
    @Test
    void nombresDeFuncionesYParametros() {
        assertEquals("E302", analizar("fn f(a: int) { let a = 2; }"));
        assertEquals("E302", analizar("fn f() { }\nlet f = 1;"));
        assertEquals("", analizar("fn f(a: int) { }\nfn g(a: bool) { }"));
    }

    /** Un valor void no se imprime ni se usa para inferir un tipo; una función no es un valor. */
    @Test
    void usosInvalidosDeFunciones() {
        assertEquals("E306", analizar("fn f() { }\nprint(f());"));
        assertEquals("E312", analizar("fn f() { }\nlet x = f();"));
        assertEquals("E306", analizar("fn f() { }\nprint(f);"));
    }

    /** La condición del while debe ser bool. */
    @Test
    void condicionDelWhileNoBooleana() {
        assertEquals("E308", analizar("while (1) { }"));
    }

    /** break/continue fuera de un ciclo es E313; dentro de un if dentro del ciclo es válido. */
    @Test
    void saltosFueraYDentroDeUnCiclo() {
        assertEquals("E313 E313", analizar("break;\nif (true) { continue; }"));
        assertEquals("", analizar("let i = 0;\nwhile (i < 3) { i += 1; if (i == 2) { continue; } break; }"));
    }

    /** El cuerpo del while es un ámbito: sus variables no se ven después del ciclo. */
    @Test
    void usoFueraDelCuerpoDelWhile() {
        assertEquals("E303", analizar("while (false) { let t = 1; }\nprint(t);"));
    }

    /** Bloques hermanos pueden repetir un nombre, cada uno con su propio tipo. */
    @Test
    void bloquesHermanosRepitenNombre() {
        assertEquals("", analizar("if (true) { let x = 1; print(x + 1); } else { let x = true; print(!x); }"));
    }

    /** Declarar un nombre ya visible en un ámbito exterior es ocultarlo: E302. */
    @Test
    void ocultarVariableExterior() {
        assertEquals("E302", analizar("let n = 1;\nif (true) { let n = 2; }"));
    }

    /** Tras cerrar el bloque, su nombre queda libre para el ámbito exterior. */
    @Test
    void redeclararTrasCerrarElBloque() {
        assertEquals("", analizar("{ let a = 1; print(a); }\nlet a = true;\nprint(!a);"));
        assertEquals(Type.BOOL, tipoDe("a"));
    }

    @Test
    void bloqueVeLasVariablesExteriores() {
        assertEquals("", analizar("let a = 1;\nif (a > 0) { let b = a + 1; print(b); }\nprint(a);"));
    }

    // ------------------------------------------------------------
    // Otras reglas
    // ------------------------------------------------------------

    @Test
    void reglasExistentes() {
        assertEquals("E301", analizar("print(x);"));
        assertEquals("E302", analizar("let a = 1;\nlet a = 2;"));
        assertEquals("E304", analizar("let a = 1;\nprint(a / (2 - 2));"));
        assertEquals("E305", analizar("const k = 1;\nk = 2;"));
    }

    // ------------------------------------------------------------
    // Anotación de tipo
    // ------------------------------------------------------------

    @Test
    void anotacionDeTipo_defineElTipo() {
        assertEquals("", analizar("let a: float = 1;\nlet b: string;\nconst c: bool = true;"));
        assertEquals(Type.FLOAT, tipoDe("a")); // int se promueve: no se infiere int
        assertEquals(Type.STRING, tipoDe("b"));
        assertEquals(Type.BOOL, tipoDe("c"));
    }

    @Test
    void anotacionDeTipo_valorIncompatible() {
        assertEquals("E307 E307", analizar("let a: int = 2.5;\nconst b: bool = 1;"));
    }

    /** Sin valor inicial, la variable acepta luego asignaciones de su tipo. */
    @Test
    void anotacionDeTipo_sinValor() {
        assertEquals("E307", analizar("let x: int;\nx = 3;\nx = true;"));
    }
}

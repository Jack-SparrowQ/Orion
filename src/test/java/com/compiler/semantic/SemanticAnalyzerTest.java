package com.compiler.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

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
    // Cadenas y caracteres
    // ------------------------------------------------------------

    @Test
    void cadenasYCaracteres_tipos() {
        assertEquals("", analizar("""
                let s = "a" + "b";
                let c = 'x';
                let orden = c < 'y';
                let igual = s == "ab";
                let anotada: string = s;
                let d: char = c;
                s += "c";
                fn eco(t: string): string { return t; }
                print(eco("hola"));
                print(c);
                """));
        assertEquals(Type.STRING, tipoDe("s"));
        assertEquals(Type.CHAR, tipoDe("c"));
        assertEquals(Type.BOOL, tipoDe("orden"));
        assertEquals(Type.BOOL, tipoDe("igual"));
    }

    /** Sin conversiones implícitas: char y string tampoco se mezclan. */
    @Test
    void cadenasYCaracteres_operacionesInvalidas() {
        assertEquals("E306", analizar("let x = \"n=\" + 5;"));
        assertEquals("E306", analizar("let x = \"a\" + 'b';"));
        assertEquals("E306", analizar("let x = 'a' + 'b';"));
        assertEquals("E306", analizar("let x = \"a\" - \"b\";"));
        assertEquals("E306", analizar("let x = \"a\" < \"b\";"));
        assertEquals("E306", analizar("let x = \"a\" == 'a';"));
        assertEquals("E306", analizar("let x = -\"a\";"));
    }

    @Test
    void cadenasYCaracteres_asignacionIncompatible() {
        assertEquals("E307", analizar("let e: int = \"texto\";"));
        assertEquals("E307", analizar("let c: char = \"a\";"));
        assertEquals("E307", analizar("let s: string = 'a';"));
    }

    @Test
    void cadenaComoCondicion_E308() {
        assertEquals("E308", analizar("if (\"a\") { }"));
    }

    // ------------------------------------------------------------
    // for ... in
    // ------------------------------------------------------------

    @Test
    void forIn_variableEsChar() {
        assertEquals("", analizar("""
                let s = "ab";
                for (c in s) {
                    let d: char = c;
                    print(c < 'b');
                    if (c == 'a') { continue; }
                    break;
                }
                """));
        assertEquals("E307", analizar("for (c in \"ab\") { let i: int = c; }"));
        assertEquals("E306", analizar("for (c in \"ab\") { let t = c + \"x\"; }"));
    }

    @Test
    void forIn_recorreCualquierExpresionString() {
        assertEquals("", analizar("""
                fn nombre(): string { return "x"; }
                fn f(p: string) {
                    for (a in p) { print(a); }
                    for (b in p + "z") { print(b); }
                    for (c in nombre()) { print(c); }
                }
                """));
    }

    @Test
    void forIn_noIterable_E319() {
        assertEquals("E319", analizar("for (x in 5) { }"));
        assertEquals("E319", analizar("for (x in 2.5) { }"));
        assertEquals("E319", analizar("for (x in true) { }"));
        assertEquals("E319", analizar("for (x in 'a') { }"));
        assertEquals("E319", analizar("fn f() { }\nfor (x in f()) { }"));
    }

    /** Un iterable con error propio no repite el error en E319 ni en el cuerpo. */
    @Test
    void forIn_sinErroresEnCascada() {
        assertEquals("E301", analizar("for (x in nada) { let i: int = x; }"));
        assertEquals("E319", analizar("for (x in 5) { let i: int = x; let c: char = x; }"));
    }

    @Test
    void forIn_variableDeSoloLectura_E305() {
        assertEquals("E305", analizar("for (c in \"a\") { c = 'b'; }"));
        // c += 'b' es c = c + 'b': además, char + char no es una operación válida
        assertEquals("E306 E305", analizar("for (c in \"a\") { c += 'b'; }"));
    }

    /** La variable es local al ciclo y el iterable pertenece al ámbito exterior. */
    @Test
    void forIn_ambito() {
        assertEquals("E303", analizar("for (c in \"a\") { }\nprint(c);"));
        assertEquals("E303", analizar("for (c in c) { }"));
        assertEquals("", analizar("for (c in \"a\") { }\nfor (c in \"b\") { }"));
    }

    @Test
    void forIn_sinOcultamiento_E302() {
        assertEquals("E302", analizar("let c = 1;\nfor (c in \"a\") { }"));
        assertEquals("E302", analizar("for (c in \"a\") { let c = 1; }"));
        assertEquals("E302", analizar("for (c in \"a\") { for (c in \"b\") { } }"));
        assertEquals("E302", analizar("fn f(p: int) { for (p in \"a\") { } }"));
        assertEquals("E302", analizar("fn c() { }\nfor (c in \"a\") { }"));
    }

    /** Reasignar lo que se recorre es válido: el iterable se evaluó antes de empezar. */
    @Test
    void forIn_reasignarElIterable() {
        assertEquals("", analizar("let s = \"ab\";\nfor (c in s) { s = \"x\"; }"));
    }

    /** R(for ... B) es falso: el cuerpo puede no ejecutarse nunca. */
    @Test
    void forIn_noGarantizaRetorno_E318() {
        assertEquals("E318", analizar("fn f(s: string): int { for (c in s) { return 1; } }"));
        assertEquals("", analizar("fn f(s: string): int { for (c in s) { return 1; } return 0; }"));
    }

    @Test
    void breakTrasUnFor_E313() {
        assertEquals("E313", analizar("for (c in \"a\") { }\nbreak;"));
    }

    @Test
    void forIn_categoriaEnLaTabla() {
        assertEquals("", analizar("for (c in \"a\") { }"));
        Symbol c = tabla.getHijos().get(0).buscar("c");
        assertEquals(SymbolCategory.ITERACION, c.getCategoria());
        assertEquals(Type.CHAR, c.getTipo());
    }

    // ------------------------------------------------------------
    // Arreglos
    // ------------------------------------------------------------

    /** Hay una sola instancia por tipo: el analizador compara con ==. */
    @Test
    void tiposDeArreglo_sonUnicos() {
        assertSame(Type.INT.arreglo(), Type.INT.arreglo());
        assertNotSame(Type.INT.arreglo(), Type.FLOAT.arreglo());
        assertSame(Type.INT, Type.INT.arreglo().elemento());
        assertEquals("INT[][]", Type.INT.arreglo().arreglo().toString());
    }

    @Test
    void arreglos_inferencia() {
        assertEquals("", analizar("""
                let a = [1, 2];
                let b = [1, 2.5];
                let c = ["x"];
                let d = ['a', 'b'];
                let e = [true];
                let m = [[1], [2, 3]];
                let conVacio = [[], [1]];
                let elemento = m[0][1];
                let fila = m[0];
                let largo = a.length;
                let letra = "hola"[0];
                let igual = a == [1];
                """));
        assertEquals(Type.INT.arreglo(), tipoDe("a"));
        assertEquals(Type.FLOAT.arreglo(), tipoDe("b"));
        assertEquals(Type.STRING.arreglo(), tipoDe("c"));
        assertEquals(Type.CHAR.arreglo(), tipoDe("d"));
        assertEquals(Type.BOOL.arreglo(), tipoDe("e"));
        assertEquals(Type.INT.arreglo().arreglo(), tipoDe("m"));
        assertEquals(Type.INT.arreglo().arreglo(), tipoDe("conVacio"));
        assertEquals(Type.INT, tipoDe("elemento"));
        assertEquals(Type.INT.arreglo(), tipoDe("fila"));
        assertEquals(Type.INT, tipoDe("largo"));
        assertEquals(Type.CHAR, tipoDe("letra"));
        assertEquals(Type.BOOL, tipoDe("igual"));
    }

    @Test
    void arreglos_anotacion() {
        assertEquals("", analizar("""
                let a: int[] = [1];
                let m: float[][] = [[1.5]];
                let v: string[];
                fn f(p: int[]): int[] { return p; }
                let r = f(a);
                """));
        assertEquals(Type.FLOAT.arreglo().arreglo(), tipoDe("m"));
        assertEquals(Type.STRING.arreglo(), tipoDe("v"));
        assertEquals(Type.INT.arreglo(), tipoDe("r"));
    }

    @Test
    void arregloVacio_tomaElTipoDelContexto() {
        assertEquals("", analizar("""
                let a: int[] = [];
                let m: int[][] = [[]];
                a = [];
                fn f(p: string[]): bool[] { return []; }
                let r = f([]);
                """));
    }

    @Test
    void arregloVacio_sinContexto_E312() {
        assertEquals("E312", analizar("let v = [];"));
        assertEquals("E312", analizar("let v = [[]];"));
        assertEquals("E312", analizar("print([]);"));
        assertEquals("E312", analizar("print([].length);"));
        assertEquals("E312", analizar("print([][0]);"));
        assertEquals("E312", analizar("for (x in []) { }"));
    }

    /** El tipo queda desconocido: el error se informa una vez, no en cada uso. */
    @Test
    void arregloVacio_sinErroresEnCascada() {
        assertEquals("E312", analizar("let v = [];\nv = [1];\nprint(v[0]);\nv[0] = 1;"));
    }

    @Test
    void arreglos_sinTipoComun_E307() {
        assertEquals("E307", analizar("let a = [1, \"x\"];"));
        assertEquals("E307", analizar("let a = [1, true];"));
        assertEquals("E307", analizar("let a = ['a', \"b\"];"));
        assertEquals("E307", analizar("let a = [[1], 2];"));
        assertEquals("E307", analizar("let a = [[1], [2.5]];")); // invariantes
    }

    @Test
    void arreglos_elementoVoid_E306() {
        assertEquals("E306", analizar("fn f() { }\nlet a = [f()];"));
    }

    @Test
    void arreglos_asignacionIncompatible_E307() {
        assertEquals("E307", analizar("let a: int[] = [1.5];"));
        assertEquals("E307", analizar("let a: int = [1];"));
        assertEquals("E307", analizar("let a: int[] = 1;"));
        assertEquals("E307", analizar("let a: int[] = \"x\";"));
        assertEquals("E307", analizar("let a: int[][] = [1];"));
        assertEquals("E307", analizar("let a: int = [];"));
        assertEquals("E307", analizar("fn f(p: int[]) { }\nf([\"x\"]);"));
        assertEquals("E307", analizar("fn f(): int[] { return 1; }"));
    }

    /** Los arreglos son invariantes: int no se promueve dentro de un arreglo. */
    @Test
    void arreglos_invariantes_E307() {
        assertEquals("E307", analizar("let a: float[] = [1, 2];"));
        assertEquals("E307", analizar("let a = [1];\nlet b: float[] = a;"));
        assertEquals("", analizar("let a: float[] = [1, 2.5];"));
    }

    @Test
    void arreglos_operadoresNoValidos_E306() {
        assertEquals("E306", analizar("let a = [1] + [2];"));
        assertEquals("E306", analizar("let a = [1] < [2];"));
        assertEquals("E306", analizar("let a = -[1];"));
        assertEquals("E306", analizar("let a = [1] == [1.5];"));
        assertEquals("E306", analizar("let a = [1] == 1;"));
    }

    @Test
    void indiceNoValido_E310() {
        assertEquals("E310", analizar("let a = [1];\nprint(a[1.5]);"));
        assertEquals("E310", analizar("let a = [1];\nprint(a[\"x\"]);"));
        assertEquals("E310", analizar("let a = [1];\nprint(a[true]);"));
        assertEquals("E310", analizar("let a = [1];\nprint(a[4 / 2]);")); // '/' siempre da float
        assertEquals("E310", analizar("let n = 1;\nprint(n[0]);"));
        assertEquals("E310", analizar("print('a'[0]);"));
        assertEquals("E310", analizar("fn f() { }\nprint(f()[0]);"));
        assertEquals("E310", analizar("let m = [[1]];\nprint(m[0][0][0]);"));
    }

    @Test
    void propiedadNoValida_E311() {
        assertEquals("E311", analizar("let a = [1];\nprint(a.size);"));
        assertEquals("E311", analizar("let n = 1;\nprint(n.length);"));
        assertEquals("E311", analizar("print(true.length);"));
    }

    @Test
    void indiceYPropiedad_sinErroresEnCascada() {
        assertEquals("E301", analizar("print(nada[0]);"));
        assertEquals("E301", analizar("print(nada.length);"));
        assertEquals("E301", analizar("let v = [1];\nprint(v[nada]);"));
    }

    @Test
    void asignacionAElemento_tipos() {
        assertEquals("", analizar("""
                let a = [1];
                let m = [[1.5]];
                let s = ["x"];
                a[0] = 2;
                a[0] += 1;
                a[0] %= 2;
                m[0][0] = 3;
                m[0] = [2.5];
                s[0] += "y";
                """));
        assertEquals("E307", analizar("let a = [1];\na[0] = \"x\";"));
        assertEquals("E307", analizar("let a = [1];\na[0] = 1.5;"));
        assertEquals("E307", analizar("let m = [[1]];\nm[0] = 1;"));
    }

    @Test
    void asignacionCompuestaAElemento() {
        assertEquals("E306", analizar("let a = [\"x\"];\na[0] -= \"y\";"));
        assertEquals("E306", analizar("let a = [1];\na[0] += \"y\";"));
        assertEquals("E307", analizar("let a = [1];\na[0] /= 2;")); // '/' da float
        assertEquals("", analizar("let a = [1.5];\na[0] /= 2;"));
        assertEquals("E304", analizar("let a = [1.5];\na[0] /= 0;"));
    }

    @Test
    void elementosDeSoloLectura_E305() {
        assertEquals("E305", analizar("const v = [1];\nv[0] = 2;"));
        assertEquals("E305", analizar("const m = [[1]];\nm[0][0] = 2;"));
        assertEquals("E305", analizar("let s = \"ab\";\ns[0] = 'c';"));
        assertEquals("E305", analizar("let m = [[1]];\nfor (f in m) { f[0] = 2; }"));
    }

    @Test
    void elementoDeVariableNoDeclarada_E301() {
        assertEquals("E301", analizar("v[0] = 1;"));
    }

    @Test
    void forIn_sobreArreglo() {
        assertEquals("", analizar("""
                let v = [1, 2];
                let suma = 0;
                for (x in v) { suma += x; }
                for (f in [[1.5]]) { for (y in f) { print(y * 2); } }
                """));
        assertEquals("E307", analizar("for (x in [1]) { let s: string = x; }"));
        assertEquals("E305", analizar("for (x in [1]) { x = 2; }"));
    }

    @Test
    void reglasDelEjemploDeGrammar() {
        assertEquals("", analizar("""
                fn sumaPares(v: int[]): int {
                    let total = 0;
                    for (x in v) {
                        if (x % 2 != 0) {
                            continue;
                        }
                        total += x;
                    }
                    return total;
                }
                const datos: int[] = [3, 8, 5, 10, 1];
                let resultado = sumaPares(datos);
                """));
    }

    // ------------------------------------------------------------
    // null
    // ------------------------------------------------------------

    @Test
    void null_enCadenasYArreglos() {
        assertEquals("", analizar("""
                let s: string = null;
                let v: int[] = null;
                let m: float[][] = null;
                const c: string = null;
                s = null;
                v = null;
                fn f(p: string, q: int[]): string[] { return null; }
                let r = f(null, null);
                fn g(): string { return null; }
                let cadenas = ["a", "b"];
                cadenas[0] = null;
                let filas = [[1]];
                filas[0] = null;
                """));
        assertEquals(Type.STRING, tipoDe("s"));
        assertEquals(Type.STRING.arreglo(), tipoDe("r"));
    }

    @Test
    void null_incompatible_E307() {
        assertEquals("E307", analizar("let a: int = null;"));
        assertEquals("E307", analizar("let a: float = null;"));
        assertEquals("E307", analizar("let a: bool = null;"));
        assertEquals("E307", analizar("let a: char = null;"));
        assertEquals("E307", analizar("let a = 1;\na = null;"));
        assertEquals("E307", analizar("fn f(p: int) { }\nf(null);"));
        assertEquals("E307", analizar("fn f(): int { return null; }"));
        assertEquals("E307", analizar("let a = [1];\na[0] = null;"));
    }

    @Test
    void null_comparaciones() {
        assertEquals("", analizar("""
                let s = "a";
                let v = [1];
                let a = s == null;
                let b = null != v;
                let c = null == null;
                let d = [] == null;
                if (s != null) { print(s); }
                """));
        assertEquals(Type.BOOL, tipoDe("a"));
        assertEquals(Type.BOOL, tipoDe("b"));
        assertEquals("E306", analizar("let a = 1 == null;"));
        assertEquals("E306", analizar("let a = null != 'a';"));
        assertEquals("E306", analizar("let a = true == null;"));
        assertEquals("E306", analizar("fn f() { }\nlet a = f() == null;"));
    }

    @Test
    void null_operacionesNoValidas_E306() {
        assertEquals("E306", analizar("let a = null + \"x\";"));
        assertEquals("E306", analizar("let a = \"x\" + null;"));
        assertEquals("E306", analizar("let a = 1 + null;"));
        assertEquals("E306", analizar("let a = null < 1;"));
        assertEquals("E306", analizar("let a = -null;"));
        assertEquals("E306", analizar("let a = !null;"));
        assertEquals("E306", analizar("let a = null && true;"));
        assertEquals("E306", analizar("let s = \"a\";\ns += null;"));
    }

    @Test
    void null_sinTipoInferible_E312() {
        assertEquals("E312", analizar("let x = null;"));
        assertEquals("E312", analizar("const x = null;"));
        assertEquals("E312", analizar("let x = [null];"));
        assertEquals("E312", analizar("let x = [[null]];"));
        assertEquals("E312", analizar("let x = [null, null];"));
        assertEquals("E312", analizar("print([null]);"));
    }

    @Test
    void null_sinErroresEnCascada() {
        assertEquals("E312", analizar("let x = null;\nx = 1;\nprint(x + 1);\nprint(x[0]);"));
    }

    /** print acepta cualquier tipo salvo void; [] necesita contexto, null no. */
    @Test
    void null_seImprime() {
        assertEquals("", analizar("print(null);"));
    }

    @Test
    void null_noSeRecorreNiIndexa() {
        assertEquals("E319", analizar("for (x in null) { }"));
        assertEquals("E310", analizar("print(null[0]);"));
        assertEquals("E311", analizar("print(null.length);"));
    }

    @Test
    void null_noEsCondicion_E308() {
        assertEquals("E308", analizar("if (null) { }"));
        assertEquals("E308", analizar("while (null) { }"));
    }

    @Test
    void null_enArreglos_tipoComun() {
        assertEquals("", analizar("""
                let a = [null, "x"];
                let b = ["x", null, "y"];
                let c = [null, [1]];
                let d = [[1], null, []];
                let e = [[null], ["x"]];
                let f: string[] = [null];
                let g: string[] = [null, null];
                let h: int[][] = [null];
                let i: string[][] = [[null], null, []];
                """));
        assertEquals(Type.STRING.arreglo(), tipoDe("a"));
        assertEquals(Type.STRING.arreglo(), tipoDe("b"));
        assertEquals(Type.INT.arreglo().arreglo(), tipoDe("c"));
        assertEquals(Type.INT.arreglo().arreglo(), tipoDe("d"));
        assertEquals(Type.STRING.arreglo().arreglo(), tipoDe("e"));
    }

    @Test
    void null_enArreglos_incompatible_E307() {
        assertEquals("E307", analizar("let a = [1, null];"));
        assertEquals("E307", analizar("let a = [null, true];"));
        assertEquals("E307", analizar("let a = [null, 'c'];"));
        assertEquals("E307", analizar("let a = [[1], [null]];"));
        assertEquals("E307", analizar("let a: int[] = [null];"));
        assertEquals("E307", analizar("let a: bool[] = [null, null];"));
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

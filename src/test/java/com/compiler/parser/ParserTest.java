package com.compiler.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.compiler.error.CompilationError;
import com.compiler.error.ErrorHandler;
import com.compiler.lexer.Lexer;
import com.compiler.lexer.Tokenizer;
import com.compiler.parser.ast.NodoAsignacion;
import com.compiler.parser.ast.NodoAsignacionElemento;
import com.compiler.parser.ast.NodoBloque;
import com.compiler.parser.ast.NodoDeclaracion;
import com.compiler.parser.ast.NodoFor;
import com.compiler.parser.ast.NodoFuncion;
import com.compiler.parser.ast.NodoIf;
import com.compiler.parser.ast.NodoImprimir;
import com.compiler.parser.ast.NodoLlamada;
import com.compiler.parser.ast.NodoReturn;
import com.compiler.parser.ast.NodoSalto;
import com.compiler.parser.ast.NodoWhile;
import com.compiler.parser.ast.Sentencia;

public class ParserTest {

    private ErrorHandler errores = new ErrorHandler();

    private List<Sentencia> parse(String codigo) {
        Tokenizer tokenizer = new Tokenizer(new Lexer(codigo, errores));
        tokenizer.run();
        return new Parser(tokenizer.getTokensList(), errores).parsear();
    }

    /** Forma parentizada de la expresión de un print. */
    private String expr(String expresion) {
        List<Sentencia> programa = parse("print(" + expresion + ");");
        assertEquals("", errores(), "errores inesperados");
        return ((NodoImprimir) programa.get(0)).argumentos().get(0).toString();
    }

    private String errores() {
        return errores.obtenerErrores().stream()
                .map(CompilationError::toString)
                .collect(Collectors.joining("\n"));
    }

    @Test
    void printConVariosArgumentos() {
        NodoImprimir varios = (NodoImprimir) parse("print(1, a + 2, \"x\");").get(0);
        assertEquals("", errores());
        assertEquals("[1, (a + 2), \"x\"]", varios.argumentos().toString());

        NodoImprimir vacio = (NodoImprimir) parse("print();").get(0);
        assertEquals("", errores());
        assertEquals(0, vacio.argumentos().size());
    }

    @Test
    void printMalFormado() {
        assertEquals("E203", codigos("print(1,);"));
        assertEquals("E203", codigos("print(,1);"));
        assertEquals("E201", codigos("print(1 2);"));
        assertEquals("E201", codigos("print(1, 2"));
        assertEquals("E201", codigos("print(1, 2)"));
    }

    @Test
    void nullEsUnaExpresion() {
        assertEquals("null", expr("null"));
        assertEquals("(s == null)", expr("s == null"));
        assertEquals("[null, \"a\"]", expr("[null, \"a\"]"));
    }

    @Test
    void cadenasYCaracteres_sonExpresiones() {
        assertEquals("(\"a\" + \"b\")", expr("\"a\" + \"b\""));
        assertEquals("(x == 'c')", expr("x == 'c'"));
    }

    @Test
    void precedencia() {
        assertEquals("(a + (b * 2))", expr("a + b * 2"));
        assertEquals("((a * b) + 2)", expr("a * b + 2"));
        assertEquals("((a < b) == (c > d))", expr("a < b == c > d"));
        assertEquals("(a || (b && c))", expr("a || b && c"));
    }

    @Test
    void asociatividadIzquierda() {
        assertEquals("((a - b) - c)", expr("a - b - c"));
        assertEquals("((a / b) % c)", expr("a / b % c"));
    }

    @Test
    void unariosYAgrupacion() {
        assertEquals("(-(!a))", expr("-!a"));
        assertEquals("(group((a + b)) * c)", expr("(a + b) * c"));
        assertEquals("true", expr("true"));
    }

    @Test
    void sentencias() {
        List<Sentencia> programa = parse("let a = 1; const b = a; if (a > 0) { print(a); print(b); }");
        assertEquals("", errores());
        assertEquals(3, programa.size());
        assertInstanceOf(NodoDeclaracion.class, programa.get(0));
        assertInstanceOf(NodoDeclaracion.class, programa.get(1));
        NodoIf nodoIf = assertInstanceOf(NodoIf.class, programa.get(2));
        assertEquals(2, nodoIf.bloqueTrue().sentencias().size());
    }

    /** fn con parámetros tipados, tipo de retorno, return y llamada en una expresión. */
    @Test
    void funcion() {
        List<Sentencia> programa = parse("fn sumar(a: int, b: int): int { return a + b; }\nprint(sumar(1, 2));");
        assertEquals("", errores());
        NodoFuncion fn = assertInstanceOf(NodoFuncion.class, programa.get(0));
        assertEquals(2, fn.parametros().size());
        assertEquals("int", fn.tipoRetorno().lexema);
        assertInstanceOf(NodoReturn.class, fn.cuerpo().sentencias().get(0));
        assertEquals("sumar(1, 2)", ((NodoImprimir) programa.get(1)).argumentos().get(0).toString());
    }

    /** Sin ': tipo' la función es void; "return;" no lleva valor. */
    @Test
    void funcionVoidYReturnSinValor() {
        NodoFuncion fn = (NodoFuncion) parse("fn f() { return; }").get(0);
        assertEquals("", errores());
        assertNull(fn.tipoRetorno());
        assertNull(((NodoReturn) fn.cuerpo().sentencias().get(0)).valor());
    }

    /** Una llamada puede ser sentencia; otra expresión que empieza igual no (E205). */
    @Test
    void llamadaComoSentencia() {
        assertInstanceOf(NodoLlamada.class, parse("f(1, x);").get(0));
        assertEquals("", errores());
        assertEquals("E205", codigos("f(1) + 2;"));
    }

    /** fn solo en el nivel superior; los parámetros exigen tipo. */
    @Test
    void erroresDeFuncion() {
        assertEquals("E202", codigos("if (a) { fn f() { } }"));
        assertEquals("E201", codigos("fn f(a) { }"));
    }

    /** while con break y continue en el cuerpo. */
    @Test
    void whileConSaltos() {
        List<Sentencia> programa = parse("while (a) { break; continue; }");
        assertEquals("", errores());
        NodoWhile ciclo = assertInstanceOf(NodoWhile.class, programa.get(0));
        assertEquals(2, ciclo.cuerpo().sentencias().size());
        assertInstanceOf(NodoSalto.class, ciclo.cuerpo().sentencias().get(0));
    }

    @Test
    void forIn() {
        List<Sentencia> programa = parse("for (c in \"ab\" + s) { print(c); break; }");
        assertEquals("", errores());
        NodoFor ciclo = assertInstanceOf(NodoFor.class, programa.get(0));
        assertEquals("c", ciclo.variable().lexema);
        assertEquals("(\"ab\" + s)", ciclo.iterable().toString());
        assertEquals(2, ciclo.cuerpo().sentencias().size());
    }

    @Test
    void forIn_erroresDeCabecera() {
        assertEquals("E201", codigos("for c in s { }"));
        assertEquals("E201", codigos("for (in s) { }"));
        assertEquals("E201", codigos("for (c of s) { }"));
        assertEquals("E201", codigos("for (c in s { }"));
        assertEquals("E201", codigos("for (c in s) print(c);"));
        assertEquals("E203", codigos("for (c in) { }"));
    }

    /** El cuerpo de un for con la cabecera mal se descarta entero; lo que sigue se analiza. */
    @Test
    void forIn_seRecuperaTrasUnError() {
        assertEquals("E201", codigos("for c in s { 1; }\nlet a = 2;"));
        assertEquals("E201 E201", codigos("for c in s { 1; }\nlet = 2;"));
    }

    /** Un for de estilo C no existe en Orion. El modo pánico para en cada ';' de la cabecera: hay cascada. */
    @Test
    void forDeEstiloC_noEsValido() {
        assertEquals("E201 E205 E201", codigos("for (let i = 0; i < 3; i += 1) { }"));
    }

    /** break sin ';' es E201; el parser no exige estar en un ciclo (eso es E313). */
    @Test
    void breakSinPuntoYComa() {
        assertEquals("E201", codigos("break"));
        assertEquals("", codigos("break;"));
    }

    /** Un bloque suelto es una sentencia y puede anidarse. */
    @Test
    void bloqueIndependiente() {
        List<Sentencia> programa = parse("{ let a = 1; { print(a); } }");
        assertEquals("", errores());
        NodoBloque bloque = assertInstanceOf(NodoBloque.class, programa.get(0));
        assertEquals(2, bloque.sentencias().size());
        assertInstanceOf(NodoBloque.class, bloque.sentencias().get(1));
    }

    @Test
    void ifSinElse_ramaFalsaVacia() {
        NodoIf nodoIf = (NodoIf) parse("if (a) { print(a); }").get(0);
        assertEquals(0, nodoIf.bloqueFalse().sentencias().size());
    }

    @Test
    void ifElseIfElse() {
        List<Sentencia> programa = parse("if (a) { print(1); } else if (b) { print(2); } else { print(3); print(4); }");
        assertEquals("", errores());
        assertEquals(1, programa.size());
        NodoIf exterior = (NodoIf) programa.get(0);
        // else if: el if anidado es la única sentencia de la rama falsa
        NodoIf anidado = assertInstanceOf(NodoIf.class, exterior.bloqueFalse().sentencias().get(0));
        assertEquals("b", anidado.condicion().toString());
        assertEquals(2, anidado.bloqueFalse().sentencias().size());
    }

    @Test
    void elseSinLlave() {
        parse("if (a) { print(a); } else print(b);");
        assertEquals("E201@1", codigosYLineas());
    }

    @Test
    void asignacion() {
        List<Sentencia> programa = parse("a = b * 2;");
        assertEquals("", errores());
        NodoAsignacion asignacion = assertInstanceOf(NodoAsignacion.class, programa.get(0));
        assertEquals("a", asignacion.identificador().lexema);
        assertEquals("(b * 2)", asignacion.valor().toString());
    }

    @Test
    void asignacionCompuesta_seTraduceASimple() {
        List<Sentencia> programa = parse("a += 1;\na -= b;\na *= b + 1;\na /= 2;\na %= 3;");
        assertEquals("", errores());
        String valores = programa.stream()
                .map(s -> ((NodoAsignacion) s).valor().toString())
                .collect(Collectors.joining(" | "));
        assertEquals("(a + 1) | (a - b) | (a * group((b + 1))) | (a / 2) | (a % 3)", valores);
    }

    /** Una expresión sin efecto no es una sentencia. */
    @Test
    void expresionSuelta_noEsSentencia() {
        parse("a + 1;\na = ;");
        assertEquals("E205@1 E203@2", codigosYLineas());
    }

    @Test
    void tipoArreglo() {
        NodoDeclaracion d = (NodoDeclaracion) parse("let m: int[][] = x;").get(0);
        assertEquals("", errores());
        assertEquals("int[][]", d.tipo().lexema);

        NodoFuncion f = (NodoFuncion) parse("fn f(v: string[]): bool[] { return x; }").get(0);
        assertEquals("", errores());
        assertEquals("v: string[]", f.parametros().get(0).toString());
        assertEquals("bool[]", f.tipoRetorno().lexema);
    }

    @Test
    void tipoArreglo_noValido() {
        assertEquals("E201", codigos("let a: int[3];"));
        assertEquals("E201", codigos("let a: void[];"));
        assertEquals("E201", codigos("fn f(): void[] { }"));
    }

    @Test
    void literalDeArreglo() {
        assertEquals("[]", expr("[]"));
        assertEquals("[1, (a + 2)]", expr("[1, a + 2]"));
        assertEquals("[[1], []]", expr("[[1], []]"));
    }

    @Test
    void literalDeArreglo_malFormado() {
        assertEquals("E203", codigos("let a = [1,];"));
        assertEquals("E201", codigos("let a = [1;"));
        assertEquals("E201", codigos("let a = [1 2];"));
    }

    /** Los sufijos se agrupan por la izquierda y ligan más que cualquier operador. */
    @Test
    void sufijos() {
        assertEquals("m[1][2]", expr("m[1][2]"));
        assertEquals("v[i].length", expr("v[i].length"));
        assertEquals("f()[0]", expr("f()[0]"));
        assertEquals("[1, 2][0]", expr("[1, 2][0]"));
        assertEquals("\"ab\".length", expr("\"ab\".length"));
        assertEquals("(a + (v[(i + 1)] * 2))", expr("a + v[i + 1] * 2"));
    }

    @Test
    void sufijos_malFormados() {
        assertEquals("E203", codigos("print(v[]);"));
        assertEquals("E201", codigos("print(v[1);"));
        assertEquals("E201", codigos("print(v.);"));
        assertEquals("E201", codigos("print(v.5);"));
    }

    @Test
    void asignacionAElemento() {
        List<Sentencia> programa = parse("m[i][j + 1] = 2;\nv[0] *= a + 1;");
        assertEquals("", errores());

        NodoAsignacionElemento simple = assertInstanceOf(NodoAsignacionElemento.class, programa.get(0));
        assertEquals("m[i][(j + 1)]", simple.destino().toString());
        assertNull(simple.operador());

        NodoAsignacionElemento compuesta = assertInstanceOf(NodoAsignacionElemento.class, programa.get(1));
        assertEquals("*", compuesta.operador().lexema);
        assertEquals("(a + 1)", compuesta.valor().toString());
    }

    @Test
    void destinoNoValido_E204() {
        assertEquals("E204", codigos("f() = 3;"));
        assertEquals("E204", codigos("f()[0] = 3;"));
        assertEquals("E204", codigos("v.length = 3;"));
        assertEquals("E204", codigos("v.length[0] = 3;"));
        assertEquals("E204", codigos("a + 1 = 3;"));
        assertEquals("", codigos("x = 1;\nv[0] = 1;\nm[0][1] = 1;"));
    }

    /** Tras E204 se descarta hasta el ';': lo siguiente se analiza con normalidad. */
    @Test
    void destinoNoValido_seRecupera() {
        parse("f() = 3;\nlet = 1;");
        assertEquals("E204@1 E201@2", codigosYLineas());
    }

    @Test
    void sufijoSinEfecto_E205() {
        assertEquals("E205", codigos("x;"));
        assertEquals("E205", codigos("v[0];"));
        assertEquals("E205", codigos("v.length;"));
        assertEquals("", codigos("f();"));
    }

    /** La asignación es una sentencia, no una expresión. */
    @Test
    void asignacionEncadenada_noEsValida() {
        assertEquals("E201", codigos("a = b = 1;"));
    }

    @Test
    void falta_igual() {
        parse("let x 5;");
        assertEquals("E201[SINTACTICO] Línea 1, columna 7: Se esperaba ':' o '=' después del nombre de la variable."
                + " Se encontró '5'.", errores());
    }

    @Test
    void falta_puntoYComaAlFinal() {
        parse("let a = 1");
        assertEquals("E201[SINTACTICO] Línea 1, columna 10: Se esperaba ';' al final de la sentencia."
                + " Se encontró el fin del archivo.", errores());
    }

    @Test
    void falta_expresion() {
        parse("let c = 10 + ;");
        assertEquals(1, errores.obtenerErrores().size());
        assertEquals("E203", errores.obtenerErrores().get(0).toString().substring(0, 4));
    }

    // ------------------------------------------------------------
    // Recuperación de errores (modo pánico)
    // ------------------------------------------------------------

    /** Códigos y líneas de los errores, uno por línea: "E201@1". */
    private String codigosYLineas() {
        return errores.obtenerErrores().stream()
                .map(e -> e.toString().substring(0, e.toString().indexOf('['))
                        + "@" + e.toString().replaceAll(".*Línea (\\d+).*", "$1"))
                .collect(Collectors.joining(" "));
    }

    @Test
    void reportaUnErrorPorSentencia() {
        List<Sentencia> programa = parse("let = 1;\nprint(;\nlet c = ;\nlet ok = 2;");
        assertEquals("E201@1 E203@2 E203@3", codigosYLineas());
        // La sentencia válida después de los errores sigue en el árbol
        assertEquals(1, programa.size());
    }

    @Test
    void errorDentroDeBloque_noRompeElBloque() {
        List<Sentencia> programa = parse("if (a) {\n let = 1;\n print(a);\n}\nprint(b);");
        assertEquals("E201@2", codigosYLineas());
        NodoIf nodoIf = assertInstanceOf(NodoIf.class, programa.get(0));
        assertEquals(1, nodoIf.bloqueTrue().sentencias().size());
        assertEquals(2, programa.size());
    }

    /** El error está justo en la '}' de cierre: no debe consumirse ni provocar otro error. */
    @Test
    void errorEnLaLlaveDeCierre() {
        parse("if (a) { let x = }\nprint(a);");
        assertEquals("E203@1", codigosYLineas());
    }

    /** Error en la cabecera del if: se descarta el bloque entero, sin errores en cascada. */
    @Test
    void errorEnCabeceraDeIf_saltaElBloque() {
        List<Sentencia> programa = parse("if (a {\n print(a);\n}\nprint(;\nprint(b);");
        assertEquals("E201@1 E203@4", codigosYLineas());
        assertEquals(1, programa.size());
    }

    @Test
    void llaveSuelta_noBuclea() {
        parse("}\nprint(a);\n}");
        assertEquals("E202@1 E202@3", codigosYLineas());
    }

    @Test
    void bloqueSinCerrar() {
        parse("if (a) { print(a);");
        assertEquals(1, errores.obtenerErrores().size());
        assertEquals(true, errores().contains("Se esperaba '}' al final del bloque."), errores());
    }

    /** Códigos de error de un programa: "E201 E201". */
    private String codigos(String codigo) {
        errores = new ErrorHandler();
        parse(codigo);
        return errores.obtenerErrores().stream()
                .map(e -> e.toString().substring(0, e.toString().indexOf('[')))
                .collect(Collectors.joining(" "));
    }

    @Test
    void anotacionDeTipo() {
        List<Sentencia> programa = parse("let a: int = 1; let b: float; const c: bool = true; let d = 2;");
        assertEquals("", errores());

        NodoDeclaracion a = (NodoDeclaracion) programa.get(0);
        assertEquals("int", a.tipo().lexema);
        assertEquals("1", a.valor().toString());

        NodoDeclaracion b = (NodoDeclaracion) programa.get(1);
        assertEquals("float", b.tipo().lexema);
        assertNull(b.valor());

        assertEquals("bool", ((NodoDeclaracion) programa.get(2)).tipo().lexema);
        assertNull(((NodoDeclaracion) programa.get(3)).tipo());
    }

    @Test
    void anotacionDeTipo_formasInvalidas() {
        assertEquals("E201", codigos("let x;"));           // sin tipo ni valor
        assertEquals("E201", codigos("const x: int;"));    // const sin valor
        assertEquals("E201", codigos("let x: void;"));     // void no es tipo de variable
        assertEquals("E201", codigos("let x: y = 1;"));    // y no es un tipo
        assertEquals("E201", codigos("let int = 1;"));     // los tipos son palabras reservadas
    }

    /** Tras un error en la anotación, el parser se recupera y sigue con la siguiente sentencia. */
    @Test
    void anotacionDeTipo_recuperacion() {
        List<Sentencia> programa = parse("let x: ; let y: int = 2;");
        assertEquals(1, errores.obtenerErrores().size());
        assertEquals(1, programa.size());
    }
}

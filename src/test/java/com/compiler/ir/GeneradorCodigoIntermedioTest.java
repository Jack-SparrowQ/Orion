package com.compiler.ir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import com.compiler.error.ErrorHandler;
import com.compiler.lexer.Lexer;
import com.compiler.lexer.Tokenizer;
import com.compiler.parser.Parser;
import com.compiler.parser.ast.Sentencia;
import com.compiler.semantic.SemanticAnalyzer;
import com.compiler.semantic.SymbolTable;
import com.compiler.semantic.Type;

public class GeneradorCodigoIntermedioTest {

    @Test
    void localesDeFuncion_incluyenVariablesForYTemporalesPeroNoParametrosNiGlobales() {
        ProgramaIR programa = ir("""
                let global = 1;
                fn f(p: int): int {
                    let x = p;
                    for (c in "ab") { let z = c; }
                    global += x;
                    return global;
                }
                """);
        FuncionIR funcion = programa.funciones().get(0);
        assertEquals(List.of("p"), funcion.parametros());
        assertEquals(List.of("x", "$t1", "$t2", "$t3", "$t4", "c", "z", "$t5"), funcion.locales());
        assertEquals(Type.INT, programa.tipo("global"));
    }

    @Test
    void concatSeDistingueDeSumaNumerica() {
        ProgramaIR programa = ir("print(\"a\" + \"b\", 1 + 2, 1 + 2.5);");
        assertEquals(List.of("concat", "+", "+", "param", "param", "param", "printn"),
                programa.principal().stream().map(c -> c.op).toList());
        assertEquals(Type.STRING, programa.tipo("$t1"));
        assertEquals(Type.INT, programa.tipo("$t2"));
        assertEquals(Type.FLOAT, programa.tipo("$t3"));
        assertEquals("(concat, \"a\", \"b\", $t1)", programa.principal().get(0).comoTupla());
    }

    @Test
    void asignacionCompuestaAElemento_emiteConcatYTiposDeLosTemporales() {
        ProgramaIR programa = ir("let v = [\"a\"]; v[0] += \"b\"; let f = [1.0]; f[0] += 2;");
        assertEquals(Type.STRING.arreglo(), programa.tipo("v"));
        assertEquals(Type.STRING, programa.tipo("$t2"));
        assertEquals(Type.STRING, programa.tipo("$t3"));
        assertEquals(Type.FLOAT, programa.tipo("$t5"));
        assertEquals(Type.FLOAT, programa.tipo("$t6"));
        assertTrue(GeneradorCodigoIntermedio.aTexto(programa).contains("$t3 = $t2 concat \"b\""));
        assertTrue(GeneradorCodigoIntermedio.aTexto(programa).contains("$t6 = $t5 + 2"));
    }

    @Test
    void variablesAnotadas_conservanTipoAunqueElValorSeaNullONoHayaInicializador() {
        ProgramaIR programa = ir("""
                let texto: string = null;
                let matriz: int[][] = null;
                let pendiente: float;
                let promovido: float = 1;
                print(texto, matriz);
                """);
        assertEquals(Type.STRING, programa.tipo("texto"));
        assertEquals(Type.INT.arreglo().arreglo(), programa.tipo("matriz"));
        assertEquals(Type.FLOAT, programa.tipo("pendiente"));
        assertEquals(Type.FLOAT, programa.tipo("promovido"));
        assertEquals(Type.NULL, programa.tipo("null"));
    }

    @Test
    void parametrosYVariablesRenombrados_conservanTiposPorDeclaracion() {
        ProgramaIR programa = ir("""
                fn primera(x: int[], m: string[][]) { print(x.length, m.length); }
                fn segunda(x: string) { print(x); }
                if (true) { let dato = 1; } else { let dato = "a"; }
                """);
        assertEquals(Type.INT.arreglo(), programa.tipo("x"));
        assertEquals(Type.STRING.arreglo().arreglo(), programa.tipo("m"));
        assertEquals(Type.STRING, programa.tipo("x$1"));
        assertEquals(Type.INT, programa.tipo("dato"));
        assertEquals(Type.STRING, programa.tipo("dato$1"));
    }

    @Test
    void for_tipadoDeVariablesYCopiaIndiceLongitudCondicion() {
        ProgramaIR programa = ir("for (c in \"ab\") {} for (n in [1, 2]) {}");
        assertEquals(Type.CHAR, programa.tipo("c"));
        assertEquals(Type.INT, programa.tipo("n"));
        assertEquals(Type.STRING, programa.tipo("$t1"));
        assertEquals(Type.INT, programa.tipo("$t2"));
        assertEquals(Type.INT, programa.tipo("$t3"));
        assertEquals(Type.BOOL, programa.tipo("$t4"));
        assertEquals(Type.INT.arreglo(), programa.tipo("$t5"));
        assertEquals(Type.INT.arreglo(), programa.tipo("$t6"));
        assertEquals(Type.INT, programa.tipo("$t7"));
        assertEquals(Type.INT, programa.tipo("$t8"));
        assertEquals(Type.BOOL, programa.tipo("$t9"));
    }

    @Test
    void llamadasYLogicos_tienenTiposDeExpresion() {
        ProgramaIR programa = ir("""
                fn texto(): string { return "a"; }
                fn visible(): bool { return true; }
                print(texto());
                print(false && visible(), true || visible());
                """);
        assertEquals(Type.STRING, programa.tipo("$t1"));
        for (int i = 2; i <= 5; i++) assertEquals(Type.BOOL, programa.tipo("$t" + i));
    }

    @Test
    void tipoReconoceLiteralesSinConfundirlosConNombres() {
        ProgramaIR programa = new ProgramaIR(List.of(), List.of(), Map.of("x", Type.FLOAT));
        assertEquals(Type.FLOAT, programa.tipo("x"));
        assertEquals(Type.INT, programa.tipo("10"));
        assertEquals(Type.FLOAT, programa.tipo("3.5"));
        assertEquals(Type.STRING, programa.tipo("\"t1\""));
        assertEquals(Type.STRING, programa.tipo("\"\""));
        assertEquals(Type.CHAR, programa.tipo("'a'"));
        assertEquals(Type.CHAR, programa.tipo("'\\n'"));
        assertEquals(Type.BOOL, programa.tipo("true"));
        assertEquals(Type.BOOL, programa.tipo("false"));
        assertEquals(Type.NULL, programa.tipo("null"));
        assertNull(programa.tipo("noDeclarado"));
        assertNull(programa.tipo(null));
    }

    @TestFactory
    List<DynamicTest> corpus_todosLosDestinosYParametrosTienenTipo() throws Exception {
        Path corpus = Path.of(getClass().getResource("/programas").toURI());
        try (Stream<Path> archivos = Files.list(corpus)) {
            List<Path> fuentes = archivos.filter(p -> p.toString().endsWith(".orion")).sorted().toList();
            assertTrue(!fuentes.isEmpty(), "El corpus no debe estar vacio");
            return fuentes.stream().map(fuente -> DynamicTest.dynamicTest(fuente.getFileName().toString(), () -> {
                ProgramaIR programa = ir(Files.readString(fuente, StandardCharsets.UTF_8));
                for (FuncionIR funcion : programa.funciones()) {
                    for (String parametro : funcion.parametros()) assertNotNull(programa.tipos().get(parametro), parametro);
                }
                List<Cuadrupla> instrucciones = Stream.concat(programa.principal().stream(),
                        programa.funciones().stream().flatMap(f -> f.cuerpo().stream())).toList();
                for (Cuadrupla c : instrucciones) {
                    switch (c.op) {
                        case "label", "goto", "ifFalse" -> { } // resultado es una etiqueta
                        case "[]=" -> assertNotNull(programa.tipos().get(c.arg1), c.toString());
                        default -> {
                            if (c.resultado != null) assertNotNull(programa.tipos().get(c.resultado), c.toString());
                        }
                    }
                }
            })).toList();
        }
    }

    @Test
    void programaIR_separaFuncionesConParametrosEstructurados() {
        ProgramaIR programa = ir("""
                print(primera(2));
                fn primera(x: int): int { return x; }
                print(3);
                fn segunda(x: int, y: int): int { return x + y; }
                """);
        assertEquals(List.of("primera", "segunda"),
                programa.funciones().stream().map(FuncionIR::nombre).toList());
        assertEquals(List.of("x"), programa.funciones().get(0).parametros());
        assertEquals(List.of("x$1", "y"), programa.funciones().get(1).parametros());
        assertEquals(List.of("param", "call", "print", "print"),
                programa.principal().stream().map(c -> c.op).toList());
        assertEquals("x", programa.funciones().get(0).cuerpo().get(0).arg1);
        assertEquals("x$1", programa.funciones().get(1).cuerpo().get(0).arg1);
        assertTrue(Stream.concat(programa.principal().stream(),
                programa.funciones().stream().flatMap(f -> f.cuerpo().stream()))
                .noneMatch(c -> c.op.equals("func") || c.op.equals("endfunc")));
    }

    @Test
    void texto_listaFuncionPosteriorAntesDelPrincipal() {
        assertEquals("""
                func doble, 1, [x]
                    $t2 = x * 2
                    return $t2
                endfunc
                    param 3
                    $t1 = call doble, 1
                    print $t1
                    print 4
                """, tac("""
                print(doble(3));
                fn doble(x: int): int { return x * 2; }
                print(4);
                """));
    }

    @Test
    void funcionesVacias_conservanReturnImplicito() {
        ProgramaIR programa = ir("fn primera() {} print(1); fn segunda() {}");
        assertEquals(2, programa.funciones().size());
        for (FuncionIR funcion : programa.funciones()) {
            assertTrue(funcion.parametros().isEmpty());
            assertEquals(1, funcion.cuerpo().size());
            assertEquals("return", funcion.cuerpo().get(0).op);
            assertEquals(null, funcion.cuerpo().get(0).arg1);
        }
        assertEquals(1, programa.principal().size());
    }

    @Test
    void numeracionSigueSiendoGlobalAunqueSeSeparenLosCuerpos() {
        ProgramaIR programa = ir("""
                if (true) { print(1 + 2); }
                fn primera(x: int): int { if (x > 0) { return x + 1; } return 0; }
                if (true) { print(3 + 4); }
                fn segunda(x: int): int { if (x > 0) { return x + 2; } return 0; }
                """);
        assertEquals(List.of("x"), programa.funciones().get(0).parametros());
        assertEquals(List.of("x$1"), programa.funciones().get(1).parametros());
        List<Cuadrupla> instrucciones = Stream.concat(programa.principal().stream(),
                programa.funciones().stream().flatMap(f -> f.cuerpo().stream())).toList();
        List<String> temporales = instrucciones.stream().map(c -> c.resultado)
                .filter(r -> r != null && r.startsWith("$t")).toList();
        assertEquals(6, temporales.size());
        assertEquals(6, temporales.stream().distinct().count());
        List<String> etiquetas = instrucciones.stream().filter(c -> c.op.equals("label"))
                .map(c -> c.resultado).toList();
        assertEquals(List.of("$L1", "$L3", "$L2", "$L4"), etiquetas);
    }

    @Test
    void records_copianListasYExponenListasInmutables() {
        List<String> parametros = new ArrayList<>(List.of("x"));
        List<Cuadrupla> cuerpo = new ArrayList<>(List.of(new Cuadrupla("return", "x", null, null)));
        List<String> locales = new ArrayList<>(List.of("$t1"));
        FuncionIR funcion = new FuncionIR("f", parametros, locales, cuerpo);
        List<FuncionIR> funciones = new ArrayList<>(List.of(funcion));
        List<Cuadrupla> principal = new ArrayList<>(List.of(new Cuadrupla("print", "1", null, null)));
        Map<String, Type> tipos = new HashMap<>(Map.of("x", Type.INT));
        ProgramaIR programa = new ProgramaIR(funciones, principal, tipos);
        parametros.clear();
        locales.clear();
        cuerpo.clear();
        funciones.clear();
        principal.clear();
        tipos.clear();
        assertEquals(List.of("x"), funcion.parametros());
        assertEquals(List.of("$t1"), funcion.locales());
        assertEquals(1, funcion.cuerpo().size());
        assertEquals(List.of(funcion), programa.funciones());
        assertEquals(1, programa.principal().size());
        assertEquals(Map.of("x", Type.INT), programa.tipos());
        assertThrows(UnsupportedOperationException.class, () -> funcion.parametros().clear());
        assertThrows(UnsupportedOperationException.class, () -> funcion.locales().clear());
        assertThrows(UnsupportedOperationException.class, () -> funcion.cuerpo().clear());
        assertThrows(UnsupportedOperationException.class, () -> programa.funciones().clear());
        assertThrows(UnsupportedOperationException.class, () -> programa.principal().clear());
        assertThrows(UnsupportedOperationException.class, () -> programa.tipos().clear());
    }

    @Test
    void localYGlobalPosterior_tienenNombresDistintos() {
        assertEquals("""
                func local, 0
                    valor = 3
                    return valor
                endfunc
                    valor$1 = 10
                    $t1 = call local, 0
                    print $t1
                    print valor$1
                """, tac("""
                fn local(): int { let valor = 3; return valor; }
                let valor = 10;
                print(local());
                print(valor);
                """));
    }

    @Test
    void funcionesDistintas_renombranParametrosYLocales() {
        assertEquals("""
                func primera, 1, [x]
                    dato = x
                    return dato
                endfunc
                func segunda, 1, [x$1]
                    dato$1 = x$1
                    return dato$1
                endfunc
                """, tac("""
                fn primera(x: int): int { let dato = x; return dato; }
                fn segunda(x: string): string { let dato = x; return dato; }
                """));
    }

    @Test
    void bloquesHermanos_separanDeclaracionesDeDistintoTipo() {
        assertEquals("""
                    ifFalse true goto $L1
                    dato = 7
                    print dato
                    goto $L2
                $L1:
                    dato$1 = "texto"
                    print dato$1
                $L2:
                """, tac("""
                if (true) { let dato = 7; print(dato); }
                else { let dato = "texto"; print(dato); }
                """));
    }

    @Test
    void ciclosDistintos_separanVariablesForYLocalesWhile() {
        String codigo = tac("""
                for (x in "a") { print(x); }
                for (x in "b") { print(x); }
                while (false) { let dato = 1; dato += 1; print(dato); }
                while (false) { let dato = 2; dato += 1; print(dato); }
                """);
        assertTrue(codigo.contains("    x = $t1[$t2]\n    print x\n"), codigo);
        assertTrue(codigo.contains("    x$1 = $t5[$t6]\n    print x$1\n"), codigo);
        assertTrue(codigo.contains("    dato = 1\n"), codigo);
        assertTrue(codigo.contains("    dato$1 = 2\n"), codigo);
        assertTrue(codigo.contains(" = dato + 1\n"), codigo);
        assertTrue(codigo.contains(" = dato$1 + 1\n"), codigo);
        assertTrue(codigo.contains("    print dato$1\n"), codigo);
    }

    @Test
    void sinRepeticiones_conservaNombresYGlobalVisible() {
        assertEquals("""
                func sumar, 1, [incremento]
                    $t1 = global + incremento
                    total = $t1
                    return total
                endfunc
                    global = 10
                    param 2
                    $t2 = call sumar, 1
                    print $t2
                """, tac("""
                let global = 10;
                fn sumar(incremento: int): int { let total = global + incremento; return total; }
                print(sumar(2));
                """));
    }

    @Test
    void recursion_usaParametroRenombradoSinCambiarNombreDeFuncion() {
        assertEquals("""
                func identidad, 1, [n]
                    return n
                endfunc
                func recursiva, 1, [n$1]
                    $t1 = n$1 <= 0
                    ifFalse $t1 goto $L1
                    return n$1
                $L1:
                    $t2 = n$1 - 1
                    param $t2
                    $t3 = call recursiva, 1
                    return $t3
                endfunc
                """, tac("""
                fn identidad(n: int): int { return n; }
                fn recursiva(n: int): int {
                    if (n <= 0) { return n; }
                    return recursiva(n - 1);
                }
                """));
    }

    @Test
    void bloquesIndependientes_reservanDeclaracionesSinInicializador() {
        assertEquals("""
                    x = void 0
                    x = 1
                    print x
                    x$1 = void 0
                    x$1 = 2
                    print x$1
                    x$2 = 3
                    print x$2
                """, tac("""
                { let x: int; x = 1; print(x); }
                { let x: int; x = 2; print(x); }
                let x = 3;
                print(x);
                """));
    }

    @Test
    void arreglosRenombrados_resuelvenLecturaAsignacionYArgumentos() {
        String codigo = tac("""
                { let v = [1]; }
                { let v = [2]; v[0] += 1; v[0] = 4; print(v.length, v[0]); usar(v); }
                """, "fn usar(p: int[]) {}");
        assertTrue(codigo.contains("    v$1 = $t2\n"), codigo);
        assertTrue(codigo.contains("    $t3 = v$1[0]\n"), codigo);
        assertTrue(codigo.contains("    v$1[0] = $t4\n"), codigo);
        assertTrue(codigo.contains("    v$1[0] = 4\n"), codigo);
        assertTrue(codigo.contains("    $t6 = len v$1\n"), codigo);
        assertTrue(codigo.contains("    $t7 = v$1[0]\n"), codigo);
        assertTrue(codigo.contains("    param v$1\n    call usar, 1\n"), codigo);
    }

    @Test
    void funcionResuelveGlobalRenombradaDeclaradaAntes() {
        assertEquals("""
                func primera, 0
                    x = 1
                    return x
                endfunc
                func segunda, 0
                    return x$1
                endfunc
                    x$1 = 2
                """, tac("""
                fn primera(): int { let x = 1; return x; }
                let x = 2;
                fn segunda(): int { return x; }
                """));
    }

    @Test
    void andLogico_cortocircuito() {
        assertEquals("""
                    $t1 = a
                    ifFalse $t1 goto $L1
                    $t1 = b
                $L1:
                    print $t1
                """, tac("print(a && b);", "let a = true; let b = false;"));
    }

    @Test
    void orLogico_cortocircuito() {
        assertEquals("""
                    $t1 = a
                    ifFalse $t1 goto $L1
                    goto $L2
                $L1:
                    $t1 = b
                $L2:
                    print $t1
                """, tac("print(a || b);", "let a = true; let b = false;"));
    }

    /** El AND derecho conserva su propio resultado y queda dentro de la rama del OR. */
    @Test
    void logicosAnidados_respetanPrecedencia() {
        assertEquals("""
                    $t1 = a
                    ifFalse $t1 goto $L1
                    goto $L2
                $L1:
                    $t2 = b
                    ifFalse $t2 goto $L3
                    $t2 = c
                $L3:
                    $t1 = $t2
                $L2:
                    print $t1
                """, tac("print(a || b && c);", "let a = true; let b = false; let c = true;"));
    }

    /** La llamada derecha se omite en ejecucion cuando el izquierdo es false. */
    @Test
    void andLogico_llamadaDespuesDelSalto() {
        assertEquals("""
                    $t1 = false
                    ifFalse $t1 goto $L1
                    $t2 = call f, 0
                    $t1 = $t2
                $L1:
                    print $t1
                """, tac("print(false && f());", "fn f(): bool { return true; }"));
    }

    /** Los nombres generados no pisan las variables del usuario t1 y L1. */
    @Test
    void temporalesYEtiquetas_noChocanConIdentificadores() {
        assertEquals("""
                    t1 = 7
                    L1 = 9
                    $t1 = L1 * 2
                    $t2 = t1 + $t1
                    print $t2
                    $t3 = t1 < L1
                    ifFalse $t3 goto $L1
                    $t4 = t1 + 1
                    t1 = $t4
                $L1:
                    param t1
                    param L1
                    printn 2
                """, tac("""
                let t1 = 7;
                let L1 = 9;
                print(t1 + L1 * 2);
                if (t1 < L1) { t1 += 1; }
                print(t1, L1);
                """));
    }

    /** Código de tres direcciones de un programa sin errores. */
    private String tac(String codigo) {
        return GeneradorCodigoIntermedio.aTexto(ir(codigo));
    }

    private String tac(String codigo, String contexto) {
        return GeneradorCodigoIntermedio.aTexto(ir(codigo, contexto));
    }

    private ProgramaIR ir(String codigo) {
        return ir(codigo, "");
    }

    /** El contexto tipa entradas externas de pruebas de fragmentos; solo el fragmento genera IR. */
    private ProgramaIR ir(String codigo, String contexto) {
        ErrorHandler errores = new ErrorHandler();
        List<Sentencia> programa = parsear(codigo, errores);
        List<Sentencia> completo = new ArrayList<>(parsear(contexto, errores));
        completo.addAll(programa);
        SymbolTable tabla = new SymbolTable();
        SemanticAnalyzer.analyzeSymbols(completo, tabla, errores);
        var tipos = SemanticAnalyzer.analyzeSemantic(completo, tabla, errores);
        assertEquals(List.of(), errores.obtenerErrores());
        return new GeneradorCodigoIntermedio().generar(programa, tipos);
    }

    private List<Sentencia> parsear(String codigo, ErrorHandler errores) {
        Tokenizer tokenizer = new Tokenizer(new Lexer(codigo, errores));
        tokenizer.run();
        List<Sentencia> programa = new Parser(tokenizer.getTokensList(), errores).parsear();
        assertEquals(List.of(), errores.obtenerErrores());
        return programa;
    }

    /** Los literales conservan sus comillas: "t1" no se confunde con el temporal $t1. */
    @Test
    void cadenasYCaracteres() {
        assertEquals("""
                    $t1 = "t1" concat "a b"
                    print $t1
                    print 'c'
                """, tac("print(\"t1\" + \"a b\");\nprint('c');"));
    }

    /** El exponente se calcula antes que la potencia que lo usa; la negación, al final. */
    @Test
    void potencia() {
        assertEquals("""
                    $t1 = 3 ** 2
                    $t2 = 2 ** $t1
                    $t3 = -$t2
                    print $t3
                """, tac("print(-2 ** 3 ** 2);"));
    }

    /** Con un argumento sigue siendo print x; printn no se confunde con imprimir el literal 2. */
    @Test
    void printSegunElNumeroDeArgumentos() {
        assertEquals("""
                    print 2
                    printn 0
                    $t1 = a + 1
                    param "x"
                    param $t1
                    printn 2
                """, tac("print(2);\nprint();\nprint(\"x\", a + 1);", "let a = 3;"));
    }

    /** Las llamadas dentro de los argumentos terminan antes del primer param del print. */
    @Test
    void printConLlamadaEnUnArgumento() {
        assertEquals("""
                    param 1
                    $t1 = call f, 1
                    param 2
                    param $t1
                    printn 2
                """, tac("print(2, f(1));", "fn f(x: int): int { return x; }"));
    }

    @Test
    void nullEsUnLiteral() {
        assertEquals("""
                    s = null
                    $t1 = s != null
                    print $t1
                """, tac("let s: string = null;\nprint(s != null);"));
    }

    /** Los elementos se evalúan antes de crear el arreglo, también los anidados. */
    @Test
    void literalDeArreglo() {
        assertEquals("""
                    $t1 = 1 + 2
                    $t2 = array 1
                    $t2[0] = $t1
                    $t3 = array 1
                    $t3[0] = 3
                    $t4 = array 2
                    $t4[0] = $t2
                    $t4[1] = $t3
                    m = $t4
                """, tac("let m = [[1 + 2], [3]];"));
    }

    @Test
    void indiceYLength() {
        assertEquals("""
                    $t1 = i + 1
                    $t2 = m[$t1]
                    $t3 = $t2[0]
                    $t4 = len v
                    $t5 = $t3 + $t4
                    print $t5
                """, tac("print(m[i + 1][0] + v.length);", "let m = [[1], [2]]; let i = 0; let v = [3];"));
    }

    @Test
    void asignacionAElemento() {
        assertEquals("""
                    $t1 = m[0]
                    $t2 = $t1[i]
                    $t3 = a + 1
                    $t1[i] = $t3
                """, tac("m[0][i] = a + 1;", "let m = [[1]]; let i = 0; let a = 2;"));
    }

    /** v[f()] += 1 llama a f una sola vez: el índice se reutiliza en la lectura y en la escritura. */
    @Test
    void asignacionCompuesta_evaluaElIndiceUnaVez() {
        assertEquals("""
                    $t1 = call f, 0
                    $t2 = v[$t1]
                    $t3 = $t2 + 1
                    v[$t1] = $t3
                """, tac("v[f()] += 1;", "let v = [1]; fn f(): int { return 0; }"));
    }

    /** El iterable se copia una vez; continue pasa por el incremento y break sale del ciclo. */
    @Test
    void forInConBreakYContinue() {
        assertEquals("""
                    $t1 = s
                    $t2 = 0
                    $t3 = len $t1
                $L1:
                    $t4 = $t2 < $t3
                    ifFalse $t4 goto $L3
                    c = $t1[$t2]
                    goto $L2
                    goto $L3
                $L2:
                    $t2 = $t2 + 1
                    goto $L1
                $L3:
                """, tac("for (c in s) { continue; break; }", "let s = \"ab\";"));
    }

    /** continue en un while dentro de un for es del while: la pila de ciclos los separa. */
    @Test
    void forIn_anidadoConWhile() {
        assertEquals("""
                    $t1 = "a"
                    $t2 = 0
                    $t3 = len $t1
                $L1:
                    $t4 = $t2 < $t3
                    ifFalse $t4 goto $L3
                    c = $t1[$t2]
                $L4:
                    ifFalse x goto $L5
                    goto $L4
                    goto $L4
                $L5:
                    goto $L2
                $L2:
                    $t2 = $t2 + 1
                    goto $L1
                $L3:
                """, tac("for (c in \"a\") { while (x) { continue; } continue; }", "let x = true;"));
    }

    /** La condición se reevalúa tras Linicio; continue salta a Linicio y break a Lfin. */
    @Test
    void whileConBreakYContinue() {
        assertEquals("""
                $L1:
                    $t1 = i < 3
                    ifFalse $t1 goto $L2
                    goto $L1
                    goto $L2
                    goto $L1
                $L2:
                """, tac("while (i < 3) { continue; break; }", "let i = 0;"));
    }

    /** Los argumentos se evalúan antes de los param; una void recibe return implícito. */
    @Test
    void funcionesYLlamadas() {
        assertEquals("""
                func sumar, 2, [a, b]
                    $t1 = a + b
                    return $t1
                endfunc
                func nada, 0
                    return
                endfunc
                    $t2 = 1 * 2
                    param $t2
                    param 3
                    $t3 = call sumar, 2
                    print $t3
                    call nada, 0
                """, tac("fn sumar(a: int, b: int): int { return a + b; }\nfn nada() { }\n"
                        + "print(sumar(1 * 2, 3));\nnada();"));
    }

    /** En ciclos anidados, break afecta solo al más interno. */
    @Test
    void breakEnCicloAnidado() {
        assertEquals("""
                $L1:
                    ifFalse a goto $L2
                $L3:
                    ifFalse b goto $L4
                    goto $L4
                    goto $L3
                $L4:
                    goto $L1
                $L2:
                """, tac("while (a) { while (b) { break; } }", "let a = true; let b = true;"));
    }
}

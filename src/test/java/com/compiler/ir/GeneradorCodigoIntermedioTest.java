package com.compiler.ir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.compiler.error.ErrorHandler;
import com.compiler.lexer.Lexer;
import com.compiler.lexer.Tokenizer;
import com.compiler.parser.Parser;
import com.compiler.parser.ast.Sentencia;

public class GeneradorCodigoIntermedioTest {

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
                    global = 10
                func sumar, 1, [incremento]
                    $t1 = global + incremento
                    total = $t1
                    return total
                endfunc
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
                    x = 1
                    print x
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
                """);
        assertTrue(codigo.contains("    v$1 = $t2\n"), codigo);
        assertTrue(codigo.contains("    $t3 = v$1[0]\n"), codigo);
        assertTrue(codigo.contains("    v$1[0] = $t4\n"), codigo);
        assertTrue(codigo.contains("    v$1[0] = 4\n"), codigo);
        assertTrue(codigo.contains("    $t5 = len v$1\n"), codigo);
        assertTrue(codigo.contains("    $t6 = v$1[0]\n"), codigo);
        assertTrue(codigo.contains("    param v$1\n    call usar, 1\n"), codigo);
    }

    @Test
    void funcionResuelveGlobalRenombradaDeclaradaAntes() {
        assertEquals("""
                func primera, 0
                    x = 1
                    return x
                endfunc
                    x$1 = 2
                func segunda, 0
                    return x$1
                endfunc
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
                """, tac("print(a && b);"));
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
                """, tac("print(a || b);"));
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
                """, tac("print(a || b && c);"));
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
                """, tac("print(false && f());"));
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
        ErrorHandler errores = new ErrorHandler();
        Tokenizer tokenizer = new Tokenizer(new Lexer(codigo, errores));
        tokenizer.run();
        List<Sentencia> programa = new Parser(tokenizer.getTokensList(), errores).parsear();
        assertEquals(List.of(), errores.obtenerErrores());
        return GeneradorCodigoIntermedio.aTexto(new GeneradorCodigoIntermedio().generar(programa));
    }

    /** Los literales conservan sus comillas: "t1" no se confunde con el temporal $t1. */
    @Test
    void cadenasYCaracteres() {
        assertEquals("""
                    $t1 = "t1" + "a b"
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
                """, tac("print(2);\nprint();\nprint(\"x\", a + 1);"));
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
                """, tac("print(2, f(1));"));
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
                    $t2[0] = 3
                    $t3 = array 2
                    $t3[0] = $t1
                    $t3[1] = $t2
                    m = $t3
                """, tac("let m = [1 + 2, [3]];"));
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
                """, tac("print(m[i + 1][0] + v.length);"));
    }

    @Test
    void asignacionAElemento() {
        assertEquals("""
                    $t1 = m[0]
                    $t2 = a + 1
                    $t1[i] = $t2
                """, tac("m[0][i] = a + 1;"));
    }

    /** v[f()] += 1 llama a f una sola vez: el índice se reutiliza en la lectura y en la escritura. */
    @Test
    void asignacionCompuesta_evaluaElIndiceUnaVez() {
        assertEquals("""
                    $t1 = call f, 0
                    $t2 = v[$t1]
                    $t3 = $t2 + 1
                    v[$t1] = $t3
                """, tac("v[f()] += 1;"));
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
                """, tac("for (c in s) { continue; break; }"));
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
                """, tac("for (c in \"a\") { while (x) { continue; } continue; }"));
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
                """, tac("while (i < 3) { continue; break; }"));
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
                """, tac("while (a) { while (b) { break; } }"));
    }
}

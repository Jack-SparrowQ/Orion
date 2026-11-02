package com.compiler.ir;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.compiler.error.ErrorHandler;
import com.compiler.lexer.Lexer;
import com.compiler.lexer.Tokenizer;
import com.compiler.parser.Parser;
import com.compiler.parser.ast.Sentencia;

public class GeneradorCodigoIntermedioTest {

    /** Código de tres direcciones de un programa sin errores. */
    private String tac(String codigo) {
        ErrorHandler errores = new ErrorHandler();
        Tokenizer tokenizer = new Tokenizer(new Lexer(codigo, errores));
        tokenizer.run();
        List<Sentencia> programa = new Parser(tokenizer.getTokensList(), errores).parsear();
        assertEquals(List.of(), errores.obtenerErrores());
        return GeneradorCodigoIntermedio.aTexto(new GeneradorCodigoIntermedio().generar(programa));
    }

    /** Los literales conservan sus comillas: "t1" no se confunde con el temporal t1. */
    @Test
    void cadenasYCaracteres() {
        assertEquals("""
                    t1 = "t1" + "a b"
                    print t1
                    print 'c'
                """, tac("print(\"t1\" + \"a b\");\nprint('c');"));
    }

    /** Con un argumento sigue siendo print x; printn no se confunde con imprimir el literal 2. */
    @Test
    void printSegunElNumeroDeArgumentos() {
        assertEquals("""
                    print 2
                    printn 0
                    t1 = a + 1
                    param "x"
                    param t1
                    printn 2
                """, tac("print(2);\nprint();\nprint(\"x\", a + 1);"));
    }

    /** Las llamadas dentro de los argumentos terminan antes del primer param del print. */
    @Test
    void printConLlamadaEnUnArgumento() {
        assertEquals("""
                    param 1
                    t1 = call f, 1
                    param 2
                    param t1
                    printn 2
                """, tac("print(2, f(1));"));
    }

    @Test
    void nullEsUnLiteral() {
        assertEquals("""
                    s = null
                    t1 = s != null
                    print t1
                """, tac("let s: string = null;\nprint(s != null);"));
    }

    /** Los elementos se evalúan antes de crear el arreglo, también los anidados. */
    @Test
    void literalDeArreglo() {
        assertEquals("""
                    t1 = 1 + 2
                    t2 = array 1
                    t2[0] = 3
                    t3 = array 2
                    t3[0] = t1
                    t3[1] = t2
                    m = t3
                """, tac("let m = [1 + 2, [3]];"));
    }

    @Test
    void indiceYLength() {
        assertEquals("""
                    t1 = i + 1
                    t2 = m[t1]
                    t3 = t2[0]
                    t4 = len v
                    t5 = t3 + t4
                    print t5
                """, tac("print(m[i + 1][0] + v.length);"));
    }

    @Test
    void asignacionAElemento() {
        assertEquals("""
                    t1 = m[0]
                    t2 = a + 1
                    t1[i] = t2
                """, tac("m[0][i] = a + 1;"));
    }

    /** v[f()] += 1 llama a f una sola vez: el índice se reutiliza en la lectura y en la escritura. */
    @Test
    void asignacionCompuesta_evaluaElIndiceUnaVez() {
        assertEquals("""
                    t1 = call f, 0
                    t2 = v[t1]
                    t3 = t2 + 1
                    v[t1] = t3
                """, tac("v[f()] += 1;"));
    }

    /** El iterable se copia una vez; continue pasa por el incremento y break sale del ciclo. */
    @Test
    void forInConBreakYContinue() {
        assertEquals("""
                    t1 = s
                    t2 = 0
                    t3 = len t1
                L1:
                    t4 = t2 < t3
                    ifFalse t4 goto L3
                    c = t1[t2]
                    goto L2
                    goto L3
                L2:
                    t2 = t2 + 1
                    goto L1
                L3:
                """, tac("for (c in s) { continue; break; }"));
    }

    /** continue en un while dentro de un for es del while: la pila de ciclos los separa. */
    @Test
    void forIn_anidadoConWhile() {
        assertEquals("""
                    t1 = "a"
                    t2 = 0
                    t3 = len t1
                L1:
                    t4 = t2 < t3
                    ifFalse t4 goto L3
                    c = t1[t2]
                L4:
                    ifFalse x goto L5
                    goto L4
                    goto L4
                L5:
                    goto L2
                L2:
                    t2 = t2 + 1
                    goto L1
                L3:
                """, tac("for (c in \"a\") { while (x) { continue; } continue; }"));
    }

    /** La condición se reevalúa tras Linicio; continue salta a Linicio y break a Lfin. */
    @Test
    void whileConBreakYContinue() {
        assertEquals("""
                L1:
                    t1 = i < 3
                    ifFalse t1 goto L2
                    goto L1
                    goto L2
                    goto L1
                L2:
                """, tac("while (i < 3) { continue; break; }"));
    }

    /** Los argumentos se evalúan antes de los param; una void recibe return implícito. */
    @Test
    void funcionesYLlamadas() {
        assertEquals("""
                func sumar, 2
                    t1 = a + b
                    return t1
                endfunc
                func nada, 0
                    return
                endfunc
                    t2 = 1 * 2
                    param t2
                    param 3
                    t3 = call sumar, 2
                    print t3
                    call nada, 0
                """, tac("fn sumar(a: int, b: int): int { return a + b; }\nfn nada() { }\n"
                        + "print(sumar(1 * 2, 3));\nnada();"));
    }

    /** En ciclos anidados, break afecta solo al más interno. */
    @Test
    void breakEnCicloAnidado() {
        assertEquals("""
                L1:
                    ifFalse a goto L2
                L3:
                    ifFalse b goto L4
                    goto L4
                    goto L3
                L4:
                    goto L1
                L2:
                """, tac("while (a) { while (b) { break; } }"));
    }
}

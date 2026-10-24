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

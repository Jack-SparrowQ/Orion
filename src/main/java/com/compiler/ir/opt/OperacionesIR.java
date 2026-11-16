package com.compiler.ir.opt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.compiler.ir.Cuadrupla;
import com.compiler.ir.FuncionIR;
import com.compiler.ir.ProgramaIR;
import com.compiler.semantic.Type;

/** Definiciones y lecturas del TAC compartidas por las pasadas. */
final class OperacionesIR {
    private OperacionesIR() { }

    static boolean nombre(String operando) {
        return operando != null && !Set.of("true", "false", "null").contains(operando)
                && operando.matches("[\\p{L}_$][\\p{L}\\p{N}_$]*");
    }

    static boolean define(Cuadrupla c) {
        return c.resultado != null && !Set.of("label", "goto", "ifFalse", "[]=").contains(c.op);
    }

    static boolean leeArg1(Cuadrupla c) {
        return !Set.of("call", "label", "goto", "printn", "array").contains(c.op);
    }

    static List<String> lecturas(Cuadrupla c) {
        List<String> nombres = new ArrayList<>();
        if (leeArg1(c) && nombre(c.arg1)) nombres.add(c.arg1);
        if (!c.op.equals("call") && nombre(c.arg2)) nombres.add(c.arg2);
        if (c.op.equals("[]=") && nombre(c.resultado)) nombres.add(c.resultado);
        return nombres;
    }

    static Set<String> nombres(List<Cuadrupla> codigo) {
        Set<String> nombres = new HashSet<>();
        for (Cuadrupla c : codigo) {
            nombres.addAll(lecturas(c));
            if (define(c)) nombres.add(c.resultado);
        }
        return nombres;
    }

    static boolean pura(Cuadrupla c) {
        return switch (c.op) {
            case "=", "+", "-", "*", "/", "%", "**", "==", "!=", "<", "<=", ">", ">=", "!" -> true;
            default -> false;
        };
    }

    static ProgramaIR limpiar(ProgramaIR programa) {
        Set<String> presentes = nombres(programa.principal());
        List<FuncionIR> funciones = new ArrayList<>();
        for (FuncionIR f : programa.funciones()) {
            Set<String> usados = nombres(f.cuerpo());
            presentes.addAll(usados);
            funciones.add(new FuncionIR(f.nombre(), f.parametros(),
                    f.locales().stream().filter(usados::contains).toList(), f.cuerpo()));
        }
        var tipos = new HashMap<String, Type>(programa.tipos());
        tipos.keySet().retainAll(presentes);
        return new ProgramaIR(funciones, programa.principal(), tipos);
    }
}

package com.compiler.ir;

import java.util.List;
import java.util.Map;

import com.compiler.semantic.Type;

/** Funciones en orden de declaracion y codigo principal, sin delimitadores de funcion. */
public record ProgramaIR(List<FuncionIR> funciones, List<Cuadrupla> principal, Map<String, Type> tipos) {
    public ProgramaIR {
        funciones = List.copyOf(funciones);
        principal = List.copyOf(principal);
        tipos = Map.copyOf(tipos);
    }

    /** Tipo de un nombre IR o literal; null si el operando no es conocido. */
    public Type tipo(String operando) {
        if (operando == null) return null;
        Type tipo = tipos.get(operando);
        if (tipo != null) return tipo;
        if (operando.equals("true") || operando.equals("false")) return Type.BOOL;
        if (operando.equals("null")) return Type.NULL;
        if (operando.startsWith("\"") && operando.endsWith("\"") && operando.length() >= 2) return Type.STRING;
        if (operando.startsWith("'") && operando.endsWith("'") && operando.length() >= 3) return Type.CHAR;
        if (operando.matches("-?[0-9]+(?:\\.[0-9]+)?")) return operando.contains(".") ? Type.FLOAT : Type.INT;
        return null;
    }
}

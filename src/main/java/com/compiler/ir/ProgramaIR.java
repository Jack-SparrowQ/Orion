package com.compiler.ir;

import java.util.List;

/** Funciones en orden de declaracion y codigo principal, sin delimitadores de funcion. */
public record ProgramaIR(List<FuncionIR> funciones, List<Cuadrupla> principal) {
    public ProgramaIR {
        funciones = List.copyOf(funciones);
        principal = List.copyOf(principal);
    }
}

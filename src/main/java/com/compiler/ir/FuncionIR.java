package com.compiler.ir;

import java.util.List;

/** Definicion de una funcion con parametros IR ordenados y cuerpo independiente. */
public record FuncionIR(String nombre, List<String> parametros, List<String> locales, List<Cuadrupla> cuerpo) {
    public FuncionIR {
        parametros = List.copyOf(parametros);
        locales = List.copyOf(locales);
        cuerpo = List.copyOf(cuerpo);
    }
}

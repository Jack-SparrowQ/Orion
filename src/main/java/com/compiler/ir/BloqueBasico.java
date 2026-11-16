package com.compiler.ir;

import java.util.List;

/** Secuencia lineal de instrucciones y sus conexiones por id de bloque. */
public record BloqueBasico(int id, List<Cuadrupla> instrucciones,
        List<Integer> sucesores, List<Integer> predecesores) {
    public BloqueBasico {
        instrucciones = List.copyOf(instrucciones);
        sucesores = List.copyOf(sucesores);
        predecesores = List.copyOf(predecesores);
    }
}

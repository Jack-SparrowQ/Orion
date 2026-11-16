package com.compiler.ir.opt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import com.compiler.ir.BloqueBasico;
import com.compiler.ir.Cuadrupla;
import com.compiler.ir.FuncionIR;
import com.compiler.ir.GrafoFlujo;
import com.compiler.ir.ProgramaIR;

/** Elimina caminos inalcanzables y saltos que no cambian el flujo. */
public final class SimplificacionSaltos implements UnaryOperator<ProgramaIR> {
    @Override
    public ProgramaIR apply(ProgramaIR programa) {
        List<FuncionIR> funciones = programa.funciones().stream().map(f -> new FuncionIR(f.nombre(),
                f.parametros(), f.locales(), simplificar(f.cuerpo()))).toList();
        return OperacionesIR.limpiar(new ProgramaIR(funciones, simplificar(programa.principal()), programa.tipos()));
    }

    private List<Cuadrupla> simplificar(List<Cuadrupla> codigo) {
        List<Cuadrupla> alcanzables = alcanzables(codigo);
        Map<String, String> cadenas = new HashMap<>();
        for (BloqueBasico bloque : GrafoFlujo.construir(alcanzables).bloques()) {
            List<Cuadrupla> instrucciones = bloque.instrucciones();
            if (instrucciones.size() == 2 && instrucciones.getFirst().op.equals("label")
                    && instrucciones.getLast().op.equals("goto")) {
                cadenas.put(instrucciones.getFirst().resultado, instrucciones.getLast().resultado);
            }
        }
        List<Cuadrupla> redirigidas = new ArrayList<>();
        for (Cuadrupla c : alcanzables) {
            if (salto(c)) {
                String destino = c.resultado;
                Set<String> visitadas = new HashSet<>();
                while (cadenas.containsKey(destino) && visitadas.add(destino)) destino = cadenas.get(destino);
                c = new Cuadrupla(c.op, c.arg1, c.arg2, destino);
            }
            redirigidas.add(c);
        }
        // Redirigir una cadena puede dejar sus bloques intermedios sin entrada.
        alcanzables = alcanzables(redirigidas);
        List<Cuadrupla> salida = new ArrayList<>();
        Set<String> referenciadas = new HashSet<>();
        for (int i = 0; i < alcanzables.size(); i++) {
            Cuadrupla c = alcanzables.get(i);
            if (salto(c) && i + 1 < alcanzables.size()) {
                Cuadrupla siguiente = alcanzables.get(i + 1);
                if (siguiente.op.equals("label") && c.resultado.equals(siguiente.resultado)) continue;
            }
            salida.add(c);
            if (salto(c)) referenciadas.add(c.resultado);
        }
        return salida.stream().filter(c -> !c.op.equals("label") || referenciadas.contains(c.resultado)).toList();
    }

    private List<Cuadrupla> alcanzables(List<Cuadrupla> codigo) {
        GrafoFlujo grafo = GrafoFlujo.construir(codigo);
        if (grafo.bloques().isEmpty()) return List.of();
        Set<Integer> visitados = new HashSet<>();
        ArrayDeque<Integer> pendientes = new ArrayDeque<>();
        pendientes.add(0);
        while (!pendientes.isEmpty()) {
            int id = pendientes.removeFirst();
            if (visitados.add(id)) pendientes.addAll(grafo.bloques().get(id).sucesores());
        }
        return grafo.bloques().stream().filter(b -> visitados.contains(b.id()))
                .flatMap(b -> b.instrucciones().stream()).toList();
    }

    private boolean salto(Cuadrupla c) {
        return c.op.equals("goto") || c.op.equals("ifFalse");
    }
}

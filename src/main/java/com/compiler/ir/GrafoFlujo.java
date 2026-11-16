package com.compiler.ir;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Grafo de un cuerpo IR; conserva incluso las instrucciones inalcanzables. */
public record GrafoFlujo(List<BloqueBasico> bloques) {
    public GrafoFlujo {
        bloques = List.copyOf(bloques);
    }

    public static GrafoFlujo construir(List<Cuadrupla> codigo) {
        List<Integer> lideres = new ArrayList<>();
        for (int i = 0; i < codigo.size(); i++) {
            if (i == 0 || codigo.get(i).op.equals("label") || terminaBloque(codigo.get(i - 1))) {
                lideres.add(i);
            }
        }
        List<List<Cuadrupla>> instrucciones = new ArrayList<>();
        List<List<Integer>> sucesores = new ArrayList<>();
        List<List<Integer>> predecesores = new ArrayList<>();
        Map<String, Integer> etiquetas = new HashMap<>();
        for (int id = 0; id < lideres.size(); id++) {
            int fin = id + 1 < lideres.size() ? lideres.get(id + 1) : codigo.size();
            List<Cuadrupla> cuerpo = List.copyOf(codigo.subList(lideres.get(id), fin));
            instrucciones.add(cuerpo);
            sucesores.add(new ArrayList<>());
            predecesores.add(new ArrayList<>());
            if (cuerpo.getFirst().op.equals("label")) etiquetas.put(cuerpo.getFirst().resultado, id);
        }
        for (int id = 0; id < instrucciones.size(); id++) {
            Cuadrupla ultima = instrucciones.get(id).getLast();
            List<Integer> destinos = sucesores.get(id);
            if (ultima.op.equals("goto") || ultima.op.equals("ifFalse")) {
                Integer destino = etiquetas.get(ultima.resultado);
                if (destino == null) {
                    throw new IllegalArgumentException("Etiqueta inexistente: " + ultima.resultado);
                }
                destinos.add(destino);
            }
            if (!ultima.op.equals("goto") && !ultima.op.equals("return") && id + 1 < instrucciones.size()
                    && !destinos.contains(id + 1)) {
                destinos.add(id + 1);
            }
            for (int destino : destinos) predecesores.get(destino).add(id);
        }
        List<BloqueBasico> bloques = new ArrayList<>();
        for (int id = 0; id < instrucciones.size(); id++) {
            bloques.add(new BloqueBasico(id, instrucciones.get(id), sucesores.get(id), predecesores.get(id)));
        }
        return new GrafoFlujo(bloques);
    }

    private static boolean terminaBloque(Cuadrupla instruccion) {
        return switch (instruccion.op) {
            case "goto", "ifFalse", "return" -> true;
            default -> false;
        };
    }

    public List<Cuadrupla> aplanar() {
        return bloques.stream().sorted(Comparator.comparingInt(BloqueBasico::id))
                .flatMap(b -> b.instrucciones().stream()).toList();
    }

    public String aTexto() {
        StringBuilder texto = new StringBuilder();
        for (BloqueBasico bloque : bloques) {
            texto.append("B").append(bloque.id()).append(" ->")
                    .append(bloque.sucesores().isEmpty() ? "" : " " + bloque.sucesores().stream()
                            .map(id -> "B" + id).collect(Collectors.joining(", ")))
                    .append('\n');
            for (Cuadrupla instruccion : bloque.instrucciones()) {
                if (!instruccion.op.equals("label")) texto.append("    ");
                texto.append(instruccion).append('\n');
            }
        }
        return texto.toString();
    }
}

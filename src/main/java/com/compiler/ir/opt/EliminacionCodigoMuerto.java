package com.compiler.ir.opt;

import static com.compiler.ir.opt.OperacionesIR.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import com.compiler.ir.BloqueBasico;
import com.compiler.ir.Cuadrupla;
import com.compiler.ir.FuncionIR;
import com.compiler.ir.GrafoFlujo;
import com.compiler.ir.ProgramaIR;

/** Elimina solo definiciones puras cuyos resultados no estan vivos. */
public final class EliminacionCodigoMuerto implements UnaryOperator<ProgramaIR> {
    @Override
    public ProgramaIR apply(ProgramaIR programa) {
        Set<String> globales = new HashSet<>(programa.tipos().keySet());
        for (FuncionIR f : programa.funciones()) {
            globales.removeAll(f.locales());
            globales.removeAll(f.parametros());
        }
        List<FuncionIR> funciones = programa.funciones().stream().map(f -> new FuncionIR(f.nombre(),
                f.parametros(), f.locales(), eliminar(f.cuerpo(), globales, true))).toList();
        return limpiar(new ProgramaIR(funciones, eliminar(programa.principal(), globales, false), programa.tipos()));
    }

    private List<Cuadrupla> eliminar(List<Cuadrupla> codigo, Set<String> globales, boolean funcion) {
        GrafoFlujo grafo = GrafoFlujo.construir(codigo);
        List<Set<String>> entradas = new ArrayList<>();
        List<Set<String>> salidas = new ArrayList<>();
        for (BloqueBasico bloque : grafo.bloques()) {
            entradas.add(Set.of());
            salidas.add(Set.of());
        }
        boolean cambio;
        do {
            cambio = false;
            for (int id = grafo.bloques().size() - 1; id >= 0; id--) {
                BloqueBasico bloque = grafo.bloques().get(id);
                Set<String> salida = new HashSet<>();
                if (funcion && bloque.sucesores().isEmpty()) salida.addAll(globales);
                for (int sucesor : bloque.sucesores()) salida.addAll(entradas.get(sucesor));
                Set<String> entrada = new HashSet<>(salida);
                for (int i = bloque.instrucciones().size() - 1; i >= 0; i--) {
                    transferir(bloque.instrucciones().get(i), entrada, globales);
                }
                if (!entrada.equals(entradas.get(id)) || !salida.equals(salidas.get(id))) cambio = true;
                entradas.set(id, entrada);
                salidas.set(id, salida);
            }
        } while (cambio);

        List<Cuadrupla> resultado = new ArrayList<>();
        for (BloqueBasico bloque : grafo.bloques()) {
            Set<String> vivas = new HashSet<>(salidas.get(bloque.id()));
            List<Cuadrupla> conservadas = new ArrayList<>();
            for (int i = bloque.instrucciones().size() - 1; i >= 0; i--) {
                Cuadrupla c = bloque.instrucciones().get(i);
                if (pura(c) && define(c) && !vivas.contains(c.resultado)) continue;
                conservadas.add(c);
                transferir(c, vivas, globales);
            }
            Collections.reverse(conservadas);
            resultado.addAll(conservadas);
        }
        return List.copyOf(resultado);
    }

    private void transferir(Cuadrupla c, Set<String> vivas, Set<String> globales) {
        if (define(c)) vivas.remove(c.resultado);
        vivas.addAll(lecturas(c));
        // El llamado puede leer cualquier global, incluso si su resultado no se usa.
        if (c.op.equals("call")) vivas.addAll(globales);
    }
}

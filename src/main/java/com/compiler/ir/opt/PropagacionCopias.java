package com.compiler.ir.opt;

import static com.compiler.ir.opt.OperacionesIR.*;

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
import com.compiler.semantic.Type;

/** Fusion de temporales adyacentes y propagacion de copias dentro de cada bloque. */
public final class PropagacionCopias implements UnaryOperator<ProgramaIR> {
    @Override
    public ProgramaIR apply(ProgramaIR programa) {
        List<FuncionIR> funciones = new ArrayList<>();
        Set<String> originales = nombres(programa.principal());
        Set<String> conservados = new HashSet<>();
        for (FuncionIR funcion : programa.funciones()) {
            Set<String> locales = new HashSet<>(funcion.locales());
            locales.addAll(funcion.parametros());
            List<Cuadrupla> cuerpo = optimizar(funcion.cuerpo(), locales);
            Set<String> presentes = nombres(cuerpo);
            Set<String> anteriores = nombres(funcion.cuerpo());
            originales.addAll(anteriores);
            conservados.addAll(presentes);
            funciones.add(new FuncionIR(funcion.nombre(), funcion.parametros(), funcion.locales().stream()
                    .filter(n -> !temporal(n) || !anteriores.contains(n) || presentes.contains(n)).toList(), cuerpo));
        }
        List<Cuadrupla> principal = optimizar(programa.principal(), Set.of());
        conservados.addAll(nombres(principal));
        Map<String, Type> tipos = new HashMap<>(programa.tipos());
        tipos.keySet().removeIf(n -> temporal(n) && originales.contains(n) && !conservados.contains(n));
        return new ProgramaIR(funciones, principal, tipos);
    }

    private List<Cuadrupla> optimizar(List<Cuadrupla> codigo, Set<String> locales) {
        Map<String, Integer> definiciones = new HashMap<>();
        Map<String, Integer> usos = new HashMap<>();
        for (Cuadrupla c : codigo) {
            if (define(c)) definiciones.merge(c.resultado, 1, Integer::sum);
            for (String nombre : lecturas(c)) usos.merge(nombre, 1, Integer::sum);
        }
        List<BloqueBasico> bloques = new ArrayList<>();
        for (BloqueBasico bloque : GrafoFlujo.construir(codigo).bloques()) {
            List<Cuadrupla> fusionadas = new ArrayList<>();
            List<Cuadrupla> instrucciones = bloque.instrucciones();
            for (int i = 0; i < instrucciones.size(); i++) {
                Cuadrupla c = instrucciones.get(i);
                while (define(c) && temporal(c.resultado) && definiciones.getOrDefault(c.resultado, 0) == 1
                        && usos.getOrDefault(c.resultado, 0) == 1 && i + 1 < instrucciones.size()) {
                    Cuadrupla copia = instrucciones.get(i + 1);
                    if (!copia.op.equals("=") || !c.resultado.equals(copia.arg1)) break;
                    c = new Cuadrupla(c.op, c.arg1, c.arg2, copia.resultado);
                    i++;
                }
                fusionadas.add(c);
            }
            Map<String, String> copias = new HashMap<>();
            List<Cuadrupla> salida = new ArrayList<>();
            for (Cuadrupla c : fusionadas) {
                String a = leeArg1(c) ? copias.getOrDefault(c.arg1, c.arg1) : c.arg1;
                String b = c.op.equals("call") ? c.arg2 : copias.getOrDefault(c.arg2, c.arg2);
                String r = c.op.equals("[]=") ? copias.getOrDefault(c.resultado, c.resultado) : c.resultado;
                Cuadrupla nueva = new Cuadrupla(c.op, a, b, r);
                if (nueva.op.equals("=") && r.equals(a)) continue;
                if (c.op.equals("call")) {
                    copias.entrySet().removeIf(e -> !protegido(e.getKey(), locales)
                            || !protegido(e.getValue(), locales));
                }
                if (define(c)) {
                    copias.entrySet().removeIf(e -> e.getKey().equals(c.resultado) || e.getValue().equals(c.resultado));
                }
                if (nueva.op.equals("=") && nombre(a)) copias.put(r, a);
                salida.add(nueva);
            }
            bloques.add(new BloqueBasico(bloque.id(), salida, bloque.sucesores(), bloque.predecesores()));
        }
        return new GrafoFlujo(bloques).aplanar();
    }

    private boolean protegido(String nombre, Set<String> locales) {
        return temporal(nombre) || locales.contains(nombre);
    }

    private static boolean temporal(String nombre) {
        return nombre != null && nombre.startsWith("$t");
    }

}

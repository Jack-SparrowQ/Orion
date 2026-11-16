package com.compiler.ir.opt;

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

/** Propagacion hacia delante, independiente en cada bloque basico. */
public final class PlegadoConstantes implements UnaryOperator<ProgramaIR> {
    @Override
    public ProgramaIR apply(ProgramaIR programa) {
        List<FuncionIR> funciones = programa.funciones().stream().map(f -> new FuncionIR(
                f.nombre(), f.parametros(), f.locales(), optimizar(f.cuerpo(),
                        new HashSet<>(java.util.stream.Stream.concat(f.locales().stream(),
                                f.parametros().stream()).toList())))).toList();
        return new ProgramaIR(funciones, optimizar(programa.principal(), Set.of()), programa.tipos());
    }

    private List<Cuadrupla> optimizar(List<Cuadrupla> codigo, Set<String> locales) {
        List<BloqueBasico> bloques = new ArrayList<>();
        for (BloqueBasico bloque : GrafoFlujo.construir(codigo).bloques()) {
            Map<String, String> constantes = new HashMap<>();
            List<Cuadrupla> instrucciones = new ArrayList<>();
            for (Cuadrupla original : bloque.instrucciones()) {
                // call.arg1 es un nombre de funcion; label/goto.resultado son etiquetas.
                String a = switch (original.op) {
                    case "call", "label", "goto" -> original.arg1;
                    default -> constantes.getOrDefault(original.arg1, original.arg1);
                };
                String b = constantes.getOrDefault(original.arg2, original.arg2);
                // En []=, resultado es un valor leido, no una definicion.
                String r = original.op.equals("[]=")
                        ? constantes.getOrDefault(original.resultado, original.resultado) : original.resultado;
                Cuadrupla c = new Cuadrupla(original.op, a, b, r);
                if (c.op.equals("call")) {
                    constantes.keySet().removeIf(n -> !n.startsWith("$t") && !locales.contains(n));
                }
                if (define(c)) constantes.remove(c.resultado);
                String plegada = plegar(c);
                if (plegada != null) c = new Cuadrupla("=", plegada, null, c.resultado);
                if (c.op.equals("=") && literal(c.arg1)) constantes.put(c.resultado, c.arg1);
                if (c.op.equals("ifFalse")) {
                    if ("true".equals(c.arg1)) continue;
                    if ("false".equals(c.arg1)) c = new Cuadrupla("goto", null, null, c.resultado);
                }
                instrucciones.add(c);
            }
            bloques.add(new BloqueBasico(bloque.id(), instrucciones, bloque.sucesores(), bloque.predecesores()));
        }
        return new GrafoFlujo(bloques).aplanar();
    }

    private boolean define(Cuadrupla c) {
        return c.resultado != null && !Set.of("label", "goto", "ifFalse", "[]=").contains(c.op);
    }

    private static boolean numero(String s) {
        return s != null && s.matches("-?[0-9]+(?:\\.[0-9]+)?");
    }

    private static boolean booleano(String s) {
        return "true".equals(s) || "false".equals(s);
    }

    private static boolean cadena(String s) {
        return s != null && s.startsWith("\"") && s.endsWith("\"");
    }

    private static boolean literal(String s) {
        return numero(s) || booleano(s) || cadena(s) || "null".equals(s)
                || (s != null && s.startsWith("'") && s.endsWith("'"));
    }

    private String plegar(Cuadrupla c) {
        if (c.op.equals("concat") && cadena(c.arg1) && cadena(c.arg2)) {
            // Normalizar los escapes evita crear un escape octal al unir "\0" y "1".
            return unirCadenas(c.arg1, c.arg2);
        }
        if (booleano(c.arg1)) {
            if (c.op.equals("!")) return Boolean.toString(!Boolean.parseBoolean(c.arg1));
            if (booleano(c.arg2)) {
                if (c.op.equals("==")) return Boolean.toString(c.arg1.equals(c.arg2));
                if (c.op.equals("!=")) return Boolean.toString(!c.arg1.equals(c.arg2));
            }
        }
        if (!numero(c.arg1) || (c.arg2 != null && !numero(c.arg2))) return null;
        double a = Double.parseDouble(c.arg1);
        double b = c.arg2 == null ? 0 : Double.parseDouble(c.arg2);
        return switch (c.op) {
            case "+" -> formato(a + b);
            case "-" -> formato(c.arg2 == null ? -a : a - b);
            case "*" -> formato(a * b);
            case "/" -> formato(a / b);
            case "%" -> formato(a % b);
            // Las potencias generales pueden diferir en el ultimo bit entre motores.
            case "**" -> potencia(a, b);
            case "==" -> Boolean.toString(a == b);
            case "!=" -> Boolean.toString(a != b);
            case "<" -> Boolean.toString(a < b);
            case "<=" -> Boolean.toString(a <= b);
            case ">" -> Boolean.toString(a > b);
            case ">=" -> Boolean.toString(a >= b);
            default -> null;
        };
    }

    private String potencia(double base, double exponente) {
        if (exponente != Math.rint(exponente) || Math.abs(exponente) > 1024) return null;
        // Enteros exactos o potencias de dos: evitamos el redondeo de libm con bases decimales.
        boolean potenciaDeDos = base > 0 && Double.isFinite(base)
                && Math.scalb(1.0, Math.getExponent(base)) == base;
        if ((base == Math.rint(base) && exponente >= 0) || potenciaDeDos) {
            return formato(Math.pow(base, exponente));
        }
        return null;
    }

    private String formato(double valor) {
        if (!Double.isFinite(valor) || Double.doubleToRawLongBits(valor) == Long.MIN_VALUE
                || Math.abs(valor) > 9007199254740992d) return null;
        if (valor == Math.rint(valor)) return Long.toString((long) valor);
        String texto = Double.toString(valor);
        return texto.contains("E") || texto.contains("e") ? null : texto;
    }

    private String unirCadenas(String a, String b) {
        StringBuilder resultado = new StringBuilder("\"");
        for (String lexema : List.of(a, b)) {
            for (int i = 1; i < lexema.length() - 1; i++) {
                char c = lexema.charAt(i);
                if (c == '\\') {
                    char escape = lexema.charAt(++i);
                    resultado.append(escape == '0' ? "\\u0000" : "\\" + escape);
                } else resultado.append(c);
            }
        }
        return resultado.append('"').toString();
    }
}

package com.compiler.codegen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.compiler.ir.Cuadrupla;
import com.compiler.ir.FuncionIR;
import com.compiler.ir.ProgramaIR;
import com.compiler.semantic.Type;
import com.compiler.util.JSGenerator;

/** Traduce el IR a JS lineal o a un despachador cuando hay etiquetas. */
public class GeneradorJS {
    public String generar(ProgramaIR programa) {
        StringBuilder js = new StringBuilder();
        boolean usaAyudantes = Stream.concat(programa.principal().stream(),
                programa.funciones().stream().flatMap(f -> f.cuerpo().stream()))
                .anyMatch(c -> Set.of("concat", "[]", "[]=", "len").contains(c.op));
        if (usaAyudantes) js.append(JSGenerator.AYUDANTES);

        Set<String> nombresLocales = new HashSet<>();
        for (FuncionIR funcion : programa.funciones()) {
            nombresLocales.addAll(funcion.parametros());
            nombresLocales.addAll(funcion.locales());
        }
        declarar(js, programa.tipos().keySet().stream().filter(n -> !nombresLocales.contains(n)).sorted().toList(), "");
        for (FuncionIR funcion : programa.funciones()) {
            js.append("function ").append(funcion.nombre()).append("(")
                    .append(String.join(", ", funcion.parametros())).append(") {\n");
            declarar(js, funcion.locales(), "    ");
            generarCuerpo(js, programa, funcion.cuerpo(), "    ", true);
            js.append("}\n");
        }
        generarCuerpo(js, programa, programa.principal(), "", false);
        return js.toString();
    }

    private void declarar(StringBuilder js, List<String> nombres, String sangria) {
        if (!nombres.isEmpty()) js.append(sangria).append("let ").append(String.join(", ", nombres)).append(";\n");
    }

    private void generarCuerpo(StringBuilder js, ProgramaIR programa, List<Cuadrupla> cuerpo,
            String sangria, boolean funcion) {
        Map<String, Integer> etiquetas = new HashMap<>();
        for (Cuadrupla c : cuerpo) {
            if (c.op.equals("label")) {
                if (etiquetas.putIfAbsent(c.resultado, etiquetas.size() + 1) != null) {
                    throw new IllegalArgumentException("Etiqueta duplicada: " + c.resultado);
                }
            }
        }
        boolean despachador = !etiquetas.isEmpty();
        if (despachador) {
            js.append(sangria).append("let $pc = 0;\n")
                    .append(sangria).append("$bucle: for (;;) switch ($pc) {\n")
                    .append(sangria).append("    case 0:\n");
        }
        String instruccion = despachador ? sangria + "        " : sangria;
        List<String> parametros = new ArrayList<>();
        for (Cuadrupla c : cuerpo) {
            String a = operando(c.arg1);
            // JS exige parentesis para una base negativa literal en una potencia.
            if (c.op.equals("**") && c.arg1.startsWith("-")) a = "(" + a + ")";
            String b = operando(c.arg2);
            String r = c.resultado;
            String sentencia = switch (c.op) {
                case "label" -> {
                    js.append(sangria).append("    case ").append(etiquetas.get(r)).append(":\n");
                    yield null;
                }
                case "goto" -> salto(etiquetas, r);
                case "ifFalse" -> "if (!(" + a + ")) { " + salto(etiquetas, r) + " }";
                case "=" -> r + " = " + a + ";";
                case "concat" -> r + " = $concatenar(" + a + ", " + b + ");";
                // Un literal con huecos tiene la longitud solicitada sin usar el nombre global Array.
                case "array" -> r + " = [" + ",".repeat(Integer.parseInt(c.arg1)) + "];";
                case "[]" -> r + " = $en(" + a + ", " + b + ");";
                case "[]=" -> "$asignar(" + a + ", " + b + ", () => " + operando(r) + ");";
                case "len" -> r + " = $longitud(" + a + ");";
                case "param" -> {
                    parametros.add(c.arg1);
                    yield null;
                }
                case "call" -> {
                    List<String> argumentos = consumir(parametros, Integer.parseInt(c.arg2));
                    yield (r == null ? "" : r + " = ") + c.arg1 + "(" + lista(argumentos) + ");";
                }
                case "print" -> imprimir(programa, List.of(c.arg1));
                case "printn" -> imprimir(programa, consumir(parametros, Integer.parseInt(c.arg1)));
                case "return" -> {
                    if (!funcion) throw new IllegalArgumentException("return fuera de funcion");
                    yield "return" + (c.arg1 == null ? "" : " " + a) + ";";
                }
                case "+", "-", "*", "/", "%", "**", "<", "<=", ">", ">=", "==", "!=", "!" ->
                    r + " = " + (c.arg2 == null ? c.op + "(" + a + ")" : a + " " + c.op + " " + b) + ";";
                default -> throw new IllegalArgumentException("Operacion IR desconocida: " + c.op);
            };
            if (sentencia != null) js.append(instruccion).append(sentencia).append("\n");
        }
        if (!parametros.isEmpty()) throw new IllegalArgumentException("param sin call/printn");
        if (despachador) {
            js.append(instruccion).append("break $bucle;\n").append(sangria).append("}\n");
        }
    }

    private String salto(Map<String, Integer> etiquetas, String destino) {
        Integer caso = etiquetas.get(destino);
        if (caso == null) throw new IllegalArgumentException("Etiqueta no definida: " + destino);
        return "$pc = " + caso + "; continue $bucle;";
    }

    private List<String> consumir(List<String> parametros, int cantidad) {
        if (cantidad != parametros.size()) throw new IllegalArgumentException("Cantidad de param inconsistente");
        List<String> argumentos = List.copyOf(parametros);
        parametros.clear();
        return argumentos;
    }

    private String imprimir(ProgramaIR programa, List<String> argumentos) {
        String prefijo = "";
        if (argumentos.size() > 1) {
            Type tipo = programa.tipo(argumentos.get(0));
            if (tipo == Type.STRING || tipo == Type.CHAR) prefijo = "\"%s\", ";
        }
        return "console.log(" + prefijo + lista(argumentos) + ");";
    }

    private String lista(List<String> argumentos) {
        return String.join(", ", argumentos.stream().map(this::operando).toList());
    }

    private String operando(String operando) {
        // Reutiliza el tratamiento de NUL seguido de digitos y CR crudo del oraculo AST.
        return operando == null ? null : JSGenerator.literalJS(operando);
    }
}

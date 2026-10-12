package com.compiler.codegen;

// Una instrucción de código intermedio: (op, arg1, arg2, resultado)
//
//   (=,       10, _,  a )   ->  a = 10
//   (+,       a,  t1, t2)   ->  t2 = a + t1
//   (!,       b,  _,  t3)   ->  t3 = !b
//   (print,   a,  _,  _ )   ->  print a
//   (ifFalse, t2, _,  L1)   ->  ifFalse t2 goto L1
//   (label,   _,  _,  L1)   ->  L1:
public class Cuadrupla {
    public final String op;
    public final String arg1;
    public final String arg2;
    public final String resultado;

    public Cuadrupla(String op, String arg1, String arg2, String resultado) {
        this.op = op;
        this.arg1 = arg1;
        this.arg2 = arg2;
        this.resultado = resultado;
    }

    // Formato de tres direcciones: t1 = a + b
    @Override
    public String toString() {
        switch (op) {
            case "label":
                return resultado + ":";
            case "print":
                return "print " + arg1;
            case "ifFalse":
                return "ifFalse " + arg1 + " goto " + resultado;
            case "=":
                return resultado + " = " + arg1;
            default:
                if (arg2 == null) { // operador unario
                    return resultado + " = " + op + arg1;
                }
                return resultado + " = " + arg1 + " " + op + " " + arg2;
        }
    }

    // Formato de cuádrupla: (op, arg1, arg2, resultado)
    public String comoTupla() {
        return "(" + op + ", " + vacio(arg1) + ", " + vacio(arg2) + ", " + vacio(resultado) + ")";
    }

    private static String vacio(String s) {
        return s == null ? "_" : s;
    }
}
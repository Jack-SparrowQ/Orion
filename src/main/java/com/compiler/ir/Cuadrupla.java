package com.compiler.ir;

// Una instrucción de código intermedio: (op, arg1, arg2, resultado)
//
//   (=,       10, _,  a )   ->  a = 10
//   (+,       a,  t1, t2)   ->  t2 = a + t1
//   (!,       b,  _,  t3)   ->  t3 = !b
//   (print,   a,  _,  _ )   ->  print a
//   (ifFalse, t2, _,  L1)   ->  ifFalse t2 goto L1
//   (goto,    _,  _,  L2)   ->  goto L2
//   (label,   _,  _,  L1)   ->  L1:
//   (func,    f,  2,  _ )   ->  func f, 2      (inicio de la función f con 2 parámetros)
//   (endfunc, _,  _,  _ )   ->  endfunc
//   (param,   a,  _,  _ )   ->  param a        (argumento de la próxima llamada)
//   (call,    f,  2,  t4)   ->  t4 = call f, 2 (sin resultado: call f, 2)
//   (return,  t4, _,  _ )   ->  return t4      (sin valor: return)
//   (len,     t1, _,  t2)   ->  t2 = len t1    (elementos de un arreglo o caracteres Unicode de una cadena)
//   ([],      t1, t2, x )   ->  x = t1[t2]     (elemento en esa posición, contando desde 0)
//   ([]=,     t1, t2, x )   ->  t1[t2] = x
//   (array,   3,  _,  t1)   ->  t1 = array 3   (arreglo nuevo de 3 elementos)
// Un índice fuera de rango en [] o []= detiene el programa.
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
            case "goto":
                return "goto " + resultado;
            case "=":
                return resultado + " = " + arg1;
            case "func":
                return "func " + arg1 + ", " + arg2;
            case "endfunc":
                return "endfunc";
            case "param":
                return "param " + arg1;
            case "call":
                return (resultado == null ? "" : resultado + " = ") + "call " + arg1 + ", " + arg2;
            case "return":
                return arg1 == null ? "return" : "return " + arg1;
            case "len":
                return resultado + " = len " + arg1;
            case "[]":
                return resultado + " = " + arg1 + "[" + arg2 + "]";
            case "[]=":
                return arg1 + "[" + arg2 + "] = " + resultado;
            case "array":
                return resultado + " = array " + arg1;
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
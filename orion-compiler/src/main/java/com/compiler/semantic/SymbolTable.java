package com.compiler.semantic;

import java.util.Map;
import java.util.LinkedHashMap;

public class SymbolTable {

    private final LinkedHashMap<String, Symbol> symbols;
    private final SymbolTable father;

    public SymbolTable() {
        this(null);
    }

    public SymbolTable(SymbolTable father) {
        this.father = father;
        this.symbols = new LinkedHashMap<>();
    }

    //Funcion que comprueba si existe un simbolo en la tabla de simbolos.
    public boolean existe(String nombre) {
        return symbols.containsKey(nombre);
    }

    //Funcion para insertar un simbolo en la tabla de simbolos.
    public boolean insertar(Symbol symbol) {

        if (existe(symbol.getNombre())) {
            return false;
        }

        symbols.put(symbol.getNombre(), symbol);
        return true;
    }

    //Funcion para buscar un simbolo en la tabla de simbolos.
    public Symbol buscar(String nombre) {
        if(symbols.containsKey(nombre)) {
            return symbols.get(nombre);
        }

        if(father != null) {
            return father.buscar(nombre);
        }
        return  null;
    }

    //Funcion para eleiminar un simbolo en la tabla de simbolos
    public void eliminar(String nombre) {
        symbols.remove(nombre);
    }

// Muestra la tabla en formato tabular, en orden de declaración
public void mostrar() {
    if (symbols.isEmpty()) {
        System.out.println("(La tabla de símbolos está vacía)");
        return;
    }

    // El ancho de la columna Nombre se ajusta al identificador más largo
    int anchoNombre = "Nombre".length();
    for (Symbol s : symbols.values()) {
        anchoNombre = Math.max(anchoNombre, s.getNombre().length());
    }

    String formato = "| %-" + anchoNombre + "s | %-6s | %-9s | %5s | %7s |%n";
    String separador = "+" + "-".repeat(anchoNombre + 2)
            + "+" + "-".repeat(8)
            + "+" + "-".repeat(11)
            + "+" + "-".repeat(7)
            + "+" + "-".repeat(9) + "+";

    System.out.println(separador);
    System.out.printf(formato, "Nombre", "Tipo", "Categoría", "Línea", "Columna");
    System.out.println(separador);

    for (Symbol s : symbols.values()) {
        System.out.printf(formato,
                s.getNombre(),
                String.valueOf(s.getTipo()),
                String.valueOf(s.getCategoria()),
                String.valueOf(s.getLinea()),
                String.valueOf(s.getColumna()));
    }

    System.out.println(separador);
}
}
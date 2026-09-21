package main.java.com.compiler.semantic;

import java.util.HashMap;
import java.util.Map;

public class SymbolTable {

    private final Map<String, Symbol> symbols;
    private final SymbolTable father;

    public SymbolTable() {
        this(null);
    }

    public SymbolTable(SymbolTable father) {
        this.father = father;
        this.symbols = new HashMap<>();
    }

    //Funcion que comprueba si existe un simbolo en la tabla de simbolos.
    public boolean existe(String nombre) {
        return symbols.containsKey(nombre);
    }

    //Funcion para insertar un simbolo en la tabla de simbolos.
    public void insertar(Symbol symbol) {

        if (existe(symbol.getNombre())) {
            throw new RuntimeException(
                "El símbolo '" + symbol.getNombre()
                + "' ya fue declarado en este ambito."
            );
        }

        symbols.put(symbol.getNombre(), symbol);
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
}
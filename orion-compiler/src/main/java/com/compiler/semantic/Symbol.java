package main.java.com.compiler.semantic;

public class Symbol {

    private String nombre;
    private Type tipo;
    private SymbolCategory categoria;

    private int linea;
    private int columna;

    public Symbol(
            String nombre,
            Type tipo,
            SymbolCategory categoria,
            int linea,
            int columna) {

        this.nombre = nombre;
        this.tipo = tipo;
        this.categoria = categoria;
        this.linea = linea;
        this.columna = columna;
    }

    public String getNombre() {
        return nombre;
    }

    public Type getTipo() {
        return tipo;
    }

    public SymbolCategory getCategoria() {
        return categoria;
    }

    public int getLinea() {
        return linea;
    }

    public int getColumna() {
        return columna;
    }
}
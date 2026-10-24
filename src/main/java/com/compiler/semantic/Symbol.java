package com.compiler.semantic;

import java.util.List;

/**
 * Entrada de la tabla de símbolos: una variable, constante, parámetro o función.
 * En una función, {@link #getTipo()} es el tipo de retorno (VOID si no devuelve nada).
 */
public class Symbol {

    private String nombre;
    private Type tipo;
    private SymbolCategory categoria;

    private int linea;
    private int columna;

    /** Tipos de los parámetros, en orden; vacío si no es una función. */
    private final List<Type> parametros;

    public Symbol(
            String nombre,
            Type tipo,
            SymbolCategory categoria,
            int linea,
            int columna) {

        this(nombre, tipo, categoria, linea, columna, List.of());
    }

    /**
     * Crea el símbolo de una función.
     *
     * @param nombre     nombre de la función
     * @param retorno    tipo de retorno (VOID si no devuelve nada)
     * @param categoria  normalmente {@link SymbolCategory#FUNCION}
     * @param linea      línea de la declaración
     * @param columna    columna de la declaración
     * @param parametros tipos de los parámetros, en orden
     */
    public Symbol(
            String nombre,
            Type retorno,
            SymbolCategory categoria,
            int linea,
            int columna,
            List<Type> parametros) {

        this.nombre = nombre;
        this.tipo = retorno;
        this.categoria = categoria;
        this.linea = linea;
        this.columna = columna;
        this.parametros = parametros;
    }

    /**
     * @return tipos de los parámetros de la función, en orden (vacío si no es una función)
     */
    public List<Type> getParametros() {
        return parametros;
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
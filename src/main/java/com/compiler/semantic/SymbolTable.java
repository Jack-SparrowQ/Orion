package com.compiler.semantic;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Tabla de símbolos de un ámbito. Las tablas forman un árbol: la raíz es el
 * ámbito global y cada bloque abre una tabla hija con {@link #abrirAmbito()}.
 */
public class SymbolTable {

    private final LinkedHashMap<String, Symbol> symbols;
    private final SymbolTable father;

    /** Ámbitos hijos, en el orden en que se abrieron. */
    private final List<SymbolTable> hijos = new ArrayList<>();

    /** Crea la tabla del ámbito global. */
    public SymbolTable() {
        this(null);
    }

    /**
     * Crea la tabla de un ámbito anidado.
     *
     * @param father ámbito que contiene a este, o null si es el global
     */
    public SymbolTable(SymbolTable father) {
        this.father = father;
        this.symbols = new LinkedHashMap<>();
    }

    /**
     * Abre un ámbito hijo de este (el de un bloque) y lo registra.
     *
     * @return la tabla del nuevo ámbito
     */
    public SymbolTable abrirAmbito() {
        SymbolTable hijo = new SymbolTable(this);
        hijos.add(hijo);
        return hijo;
    }

    /**
     * Ámbitos hijos en el orden en que se abrieron, que es el mismo orden en
     * que se recorren los bloques del AST.
     *
     * @return los ámbitos hijos (lista viva, no copiarla para modificar)
     */
    public List<SymbolTable> getHijos() {
        return hijos;
    }

    /**
     * Comprueba si el símbolo está declarado en este ámbito (sin mirar los que lo contienen).
     *
     * @param nombre identificador a buscar
     * @return true si está en este ámbito
     */
    public boolean existe(String nombre) {
        return symbols.containsKey(nombre);
    }

    /**
     * Inserta un símbolo en este ámbito. Falla si el nombre ya es visible aquí
     * o en un ámbito que lo contiene: Orion no permite ocultar nombres
     * (grammar.md, 10.2 regla 4). Dos bloques hermanos sí pueden repetirlo.
     *
     * @param symbol símbolo a insertar
     * @return false si el nombre ya era visible (E302)
     */
    public boolean insertar(Symbol symbol) {

        if (buscar(symbol.getNombre()) != null) {
            return false;
        }

        symbols.put(symbol.getNombre(), symbol);
        return true;
    }

    /**
     * Busca un símbolo en este ámbito y, si no está, en los que lo contienen.
     *
     * @param nombre identificador a buscar
     * @return el símbolo visible desde este ámbito, o null
     */
    public Symbol buscar(String nombre) {
        if(symbols.containsKey(nombre)) {
            return symbols.get(nombre);
        }

        if(father != null) {
            return father.buscar(nombre);
        }
        return  null;
    }

    /**
     * Busca un símbolo en este ámbito y en todos sus descendientes. Sirve para
     * distinguir E303 (existe en otro ámbito) de E301 (no existe en ninguno).
     *
     * @param nombre identificador a buscar
     * @return el primer símbolo con ese nombre en el subárbol, o null
     */
    public Symbol buscarEnSubambitos(String nombre) {
        if (symbols.containsKey(nombre)) {
            return symbols.get(nombre);
        }

        for (SymbolTable hijo : hijos) {
            Symbol symbol = hijo.buscarEnSubambitos(nombre);
            if (symbol != null) {
                return symbol;
            }
        }
        return null;
    }

    /**
     * Elimina un símbolo de este ámbito.
     *
     * @param nombre identificador a eliminar
     */
    public void eliminar(String nombre) {
        symbols.remove(nombre);
    }

    /** Fila de {@link #mostrar}: un símbolo y la profundidad de su ámbito. */
    private record Fila(int nivel, Symbol symbol) {}

    /**
     * Muestra en formato tabular los símbolos de este ámbito y de todos sus
     * descendientes, en orden de declaración. La columna Nivel es la
     * profundidad del ámbito (0 = este ámbito).
     *
     * @param out flujo de salida
     */
    public void mostrar(PrintStream out) {
        List<Fila> filas = new ArrayList<>();
        recolectar(filas, 0);

        if (filas.isEmpty()) {
            out.println("(La tabla de símbolos está vacía)");
            return;
        }

        filas.sort(Comparator.comparingInt((Fila f) -> f.symbol().getLinea())
                .thenComparingInt(f -> f.symbol().getColumna()));

        // El ancho de la columna Nombre se ajusta al identificador más largo
        int anchoNombre = "Nombre".length();
        for (Fila f : filas) {
            anchoNombre = Math.max(anchoNombre, f.symbol().getNombre().length());
        }

        String formato = "| %-" + anchoNombre + "s | %-6s | %-9s | %5s | %5s | %7s |%n";
        String separador = "+" + "-".repeat(anchoNombre + 2)
                + "+" + "-".repeat(8)
                + "+" + "-".repeat(11)
                + "+" + "-".repeat(7)
                + "+" + "-".repeat(7)
                + "+" + "-".repeat(9) + "+";

        out.println(separador);
        out.printf(formato, "Nombre", "Tipo", "Categoría", "Nivel", "Línea", "Columna");
        out.println(separador);

        for (Fila f : filas) {
            Symbol s = f.symbol();
            out.printf(formato,
                    s.getNombre(),
                    String.valueOf(s.getTipo()),
                    String.valueOf(s.getCategoria()),
                    String.valueOf(f.nivel()),
                    String.valueOf(s.getLinea()),
                    String.valueOf(s.getColumna()));
        }

        out.println(separador);
    }

    /**
     * Agrega a la lista los símbolos de este ámbito y de sus descendientes.
     *
     * @param filas lista destino
     * @param nivel profundidad de este ámbito
     */
    private void recolectar(List<Fila> filas, int nivel) {
        for (Symbol s : symbols.values()) {
            filas.add(new Fila(nivel, s));
        }
        for (SymbolTable hijo : hijos) {
            hijo.recolectar(filas, nivel + 1);
        }
    }
}

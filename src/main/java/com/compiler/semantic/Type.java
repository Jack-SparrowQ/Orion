package com.compiler.semantic;

/**
 * Tipo de Orion. Hay una sola instancia por tipo, incluidos los arreglos
 * (ver {@link #arreglo()}), así que los tipos se comparan con ==.
 */
public final class Type {

    public static final Type INT = new Type("INT", null);
    public static final Type FLOAT = new Type("FLOAT", null);
    public static final Type CHAR = new Type("CHAR", null);
    public static final Type STRING = new Type("STRING", null);
    public static final Type BOOL = new Type("BOOL", null);
    public static final Type NULL = new Type("NULL", null);
    public static final Type VOID = new Type("VOID", null);

    /** Tipo interno de '[]': aún no se conoce el de sus elementos, lo aporta el contexto. */
    public static final Type VACIO = new Type("[]", null);

    private final String nombre;

    /** Tipo de los elementos, o null si no es un arreglo. */
    private final Type elemento;

    /** El tipo "arreglo de este", creado la primera vez que se pide. */
    private Type arreglo;

    private Type(String nombre, Type elemento) {
        this.nombre = nombre;
        this.elemento = elemento;
    }

    /**
     * @return el tipo T[], siendo T este tipo; siempre la misma instancia
     */
    public Type arreglo() {
        if (arreglo == null) {
            arreglo = new Type(nombre + "[]", this);
        }
        return arreglo;
    }

    public boolean esArreglo() {
        return elemento != null;
    }

    /**
     * @return el tipo de los elementos, o null si no es un arreglo
     */
    public Type elemento() {
        return elemento;
    }

    /**
     * @return true si es '[]' o un arreglo que lo contiene, como el de [[]]
     */
    public boolean contieneVacio() {
        return this == VACIO || (esArreglo() && elemento.contieneVacio());
    }

    public String name() {
        return nombre;
    }

    @Override
    public String toString() {
        return nombre;
    }
}

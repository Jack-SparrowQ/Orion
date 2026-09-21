package main.java.com.compiler.error;

public class CompilationError {
    
    private final String code;
    private final ErrorType type;
    private final String mensaje;
    private final int line;
    private  final int columna;

    public CompilationError(
            String code,
            ErrorType tipo,
            String mensaje,
            int line,
            int columna) {

        this.code = code;
        this.type = tipo;
        this.mensaje = mensaje;
        this.line = line;
        this.columna = columna;
    }

        public ErrorType getTipo() {
        return type;
    }

    public String getMensaje() {
        return mensaje;
    }

    public String getCode() {
        return code;
    }

    public int getline() {
        return line;
    }

    public int getColumna() {
        return columna;
    }

    @Override
    public String toString() {

        return code + "[" + type + "] "
                + "Línea " + line
                + ", columna " + columna
                + ": " + mensaje;
    }

}

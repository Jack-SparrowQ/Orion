package main.java.com.compiler.error;

import java.util.ArrayList;
import java.util.List;

//Esta clase permite que el compilador encuentre
//varios errores en una sola ejecucion.
public class ErrorHandler {

    private final List<CompilationError> errores;

    public ErrorHandler() {
        errores = new ArrayList<>();
    }

    public void agregar(
            String code,
            ErrorType tipo,
            String mensaje,
            int linea,
            int columna) {

        errores.add(
            new CompilationError(
                code,
                tipo,
                mensaje,
                linea,
                columna
            )
        );
    }

    public boolean hayErrores() {
        return !errores.isEmpty();
    }

    public List<CompilationError> obtenerErrores() {
        return errores;
    }

    public void mostrarErrores() {

        for (CompilationError error : errores) {
            System.out.println(error);
        }
    }
}
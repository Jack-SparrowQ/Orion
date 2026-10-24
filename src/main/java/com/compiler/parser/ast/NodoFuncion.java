package com.compiler.parser.ast;

import java.util.List;

import com.compiler.lexer.Token;

/**
 * "fn" nombre "(" [ parametros ] ")" [ ":" tipoRetorno ] cuerpo
 * Solo puede aparecer en el nivel superior del programa (lo garantiza el parser).
 *
 * @param nombre      nombre de la funcion
 * @param parametros  parametros en orden de declaracion
 * @param tipoRetorno tipo anotado (puede ser 'void'), o null si se omitir (void)
 * @param cuerpo      bloque de la funcion; comparte ambito con los parametros
 */
public record NodoFuncion(Token nombre, List<NodoParametro> parametros, Token tipoRetorno, NodoBloque cuerpo)
        implements Sentencia {
}

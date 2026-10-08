package com.compiler.lexer;

public enum TokenType {
    
    //Control y flujo
    IF, ELSE, WHILE, FOR, IN, BREAK, CONTINUE, RETURN,

    //Declaraciones
    LET, CONST, FN,

    //Valores logicos y especiales
    TRUE, FALSE, NULL, VOID,

    //Modulos
    IMPORT, FROM, EXPORT,

    //Otros
    PRINT,

    //Literales e identificadores
    IDENTIFICADOR,
    NUMERO,

    //Operadores y delimitadores
    IGUAL, SUMA, RESTA,MULTI, DIVISION,
    MODULO, POTENCIA,
    PARENTESIS_IZQ,   // (
    PARENTESIS_DER,   // )
    LLAVE_IZQ,        // {
    LLAVE_DER,        // }
    CORCHETE_DER,     // [
    CORCHETE_IZQ,     // ]
    PUNTO_Y_COMA,     // ;
    PUNTO,             // .

    //Operadores de comparacion
    COMPARACION_IGUAL,// ==
    DIFERENTE,        // !=
    MENOR,            // <
    MENOR_IGUAL,      // <=
    MAYOR,            // >
    MAYOR_IGUAL,      // >=

    //Operadores Logicos
    AND_LOGICO, //&&
    OR_LOGICO, // `
    NEGACION_LOGICA, //!

    //Operadores de asignacion
    SUMA_Y_ASIGNACION, //+=
    RESTA_Y_ASIGNACION, //-=
    MULTI_Y_ASIGNACION, // *=
    DIVISION_Y_ASIGNACION, // /=
    MODULO_Y_ASIGNACION, //%=


    EOF // Fin de archivo
}
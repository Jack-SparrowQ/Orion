package com.compiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalculatorTest {

    @Test
    void debeSumarDosNumeros() {
        Calculator calculator = new Calculator();

        int resultado = calculator.sumar(2, 3);

        assertEquals(5, resultado);
    }
}

package com.javafarmer.runtime;

/** Java concepts detected in a player's program (used by missions and daily challenges). */
public enum ProgramFeature {
    VARIABLE("variável"),
    FOR("laço for"),
    WHILE("laço while"),
    NESTED_LOOP("laços aninhados"),
    IF("if/else"),
    LOGICAL("operadores && ou ||"),
    MODULO("operador %"),
    SENSE("sensores do robô"),
    LOG("FarmerBot.log"),
    METHOD("método próprio"),
    RETURN_VALUE("método com retorno"),
    ARRAY("array"),
    TERNARY("operador ternário");

    public final String label;

    ProgramFeature(String label) {
        this.label = label;
    }
}

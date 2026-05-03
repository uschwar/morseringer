package com.uschwar.morseringer.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MorseEncoderTest {

    @Test
    fun `encode converts all supported characters correctly`() {
        // Letters
        assertEquals(".-", MorseEncoder.encode("A"))
        assertEquals("-...", MorseEncoder.encode("B"))
        assertEquals("-.-.", MorseEncoder.encode("C"))
        assertEquals("-..", MorseEncoder.encode("D"))
        assertEquals(".", MorseEncoder.encode("E"))
        assertEquals("..-.", MorseEncoder.encode("F"))
        assertEquals("--.", MorseEncoder.encode("G"))
        assertEquals("....", MorseEncoder.encode("H"))
        assertEquals("..", MorseEncoder.encode("I"))
        assertEquals(".---", MorseEncoder.encode("J"))
        assertEquals("-.-", MorseEncoder.encode("K"))
        assertEquals(".-..", MorseEncoder.encode("L"))
        assertEquals("--", MorseEncoder.encode("M"))
        assertEquals("-.", MorseEncoder.encode("N"))
        assertEquals("---", MorseEncoder.encode("O"))
        assertEquals(".--.", MorseEncoder.encode("P"))
        assertEquals("--.-", MorseEncoder.encode("Q"))
        assertEquals(".-.", MorseEncoder.encode("R"))
        assertEquals("...", MorseEncoder.encode("S"))
        assertEquals("-", MorseEncoder.encode("T"))
        assertEquals("..-", MorseEncoder.encode("U"))
        assertEquals("...-", MorseEncoder.encode("V"))
        assertEquals(".--", MorseEncoder.encode("W"))
        assertEquals("-..-", MorseEncoder.encode("X"))
        assertEquals("-.--", MorseEncoder.encode("Y"))
        assertEquals("--..", MorseEncoder.encode("Z"))

        // Numbers
        assertEquals("-----", MorseEncoder.encode("0"))
        assertEquals(".----", MorseEncoder.encode("1"))
        assertEquals("..---", MorseEncoder.encode("2"))
        assertEquals("...--", MorseEncoder.encode("3"))
        assertEquals("....-", MorseEncoder.encode("4"))
        assertEquals(".....", MorseEncoder.encode("5"))
        assertEquals("-....", MorseEncoder.encode("6"))
        assertEquals("--...", MorseEncoder.encode("7"))
        assertEquals("---..", MorseEncoder.encode("8"))
        assertEquals("----.", MorseEncoder.encode("9"))
    }

    @Test
    fun `encode converts supported punctuation`() {
        assertEquals(".-.-.-", MorseEncoder.encode("."))
        assertEquals("--..--", MorseEncoder.encode(","))
        assertEquals("..--..", MorseEncoder.encode("?"))
        assertEquals(".----.", MorseEncoder.encode("'"))
        assertEquals("-.-.--", MorseEncoder.encode("!"))
        assertEquals("-..-.", MorseEncoder.encode("/"))
        assertEquals("-.--.", MorseEncoder.encode("("))
        assertEquals("-.--.-", MorseEncoder.encode(")"))
        assertEquals(".-...", MorseEncoder.encode("&"))
        assertEquals("---...", MorseEncoder.encode(":"))
        assertEquals("-.-.-.", MorseEncoder.encode(";"))
        assertEquals("-...-", MorseEncoder.encode("="))
        assertEquals(".-.-.", MorseEncoder.encode("+"))
        assertEquals("-....-", MorseEncoder.encode("-"))
        assertEquals("..--.-", MorseEncoder.encode("_"))
        assertEquals(".-..-.", MorseEncoder.encode("\""))
        assertEquals("...-..-", MorseEncoder.encode("$"))
        assertEquals(".--.-.", MorseEncoder.encode("@"))
    }

    @Test
    fun `encode handles unmapped characters as question mark`() {
        val input = "H#ELLO"
        // H = ....
        // # = ..--.. (mapped to ?)
        // E = .
        // L = .-..
        // L = .-..
        // O = ---
        val expected = ".... ..--.. . .-.. .-.. ---"
        assertEquals(expected, MorseEncoder.encode(input))
    }

    @Test
    fun `encode handles spaces as word separators`() {
        val input = "A B"
        val expected = ".- / -..."
        assertEquals(expected, MorseEncoder.encode(input))
    }
}
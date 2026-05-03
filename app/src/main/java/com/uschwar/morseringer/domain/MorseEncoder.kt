package com.uschwar.morseringer.domain

/**
 * Pure domain logic for translating alphanumeric text into International Morse Code.
 * 
 * This object handles the character-to-symbol mapping and provides a consistent 
 * string representation using dots, dashes, and separators.
 */
object MorseEncoder {

    private const val UNKNOWN_CHAR_MORSE = "..--.."
    private const val LETTER_SEPARATOR = " "
    private const val WORD_SEPARATOR = " / "

    private val charToMorse = mapOf(
        'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".",
        'F' to "..-.", 'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---",
        'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---",
        'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-",
        'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--",
        'Z' to "--..",
        '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-",
        '5' to ".....", '6' to "-....", '7' to "--...", '8' to "---..", '9' to "----.",
        '.' to ".-.-.-", ',' to "--..--", '?' to "..--..", '\'' to ".----.", '!' to "-.-.--",
        '/' to "-..-.", '(' to "-.--.", ')' to "-.--.-", '&' to ".-...", ':' to "---...",
        ';' to "-.-.-.", '=' to "-...-", '+' to ".-.-.", '-' to "-....-", '_' to "..--.-",
        '"' to ".-..-.", '$' to "...-..-", '@' to ".--.-.",
    )

    fun encode(text: String): String =
        text.trim().uppercase().split(Regex("\\s+")).joinToString(WORD_SEPARATOR) { word ->
            word.map { charToMorse.getOrDefault(it, UNKNOWN_CHAR_MORSE) }
                .joinToString(LETTER_SEPARATOR)
        }
}

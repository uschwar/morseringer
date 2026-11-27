package com.uschwar.morseringer.domain

import java.text.Normalizer

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
        // Non-English letters with their own Morse code.
        'Ä' to ".-.-", 'Ö' to "---.", 'Ü' to "..--", 'É' to "..-..",
        'À' to ".--.-", 'Å' to ".--.-", 'Ñ' to "--.--",
    )

    private val combiningMarks = Regex("\\p{Mn}+")

    /**
     * Looks up [char]; accented letters without a code of their own
     * (e.g. Ç, Ô, È) fall back to their unaccented base letter.
     */
    private fun encodeChar(char: Char): String =
        charToMorse[char]
            ?: Normalizer.normalize(char.toString(), Normalizer.Form.NFD)
                .replace(combiningMarks, "")
                .singleOrNull()
                ?.let { charToMorse[it] }
            ?: UNKNOWN_CHAR_MORSE

    fun encode(text: String): String =
        text.trim().uppercase().split(Regex("\\s+")).joinToString(WORD_SEPARATOR) { word ->
            word.map(::encodeChar)
                .joinToString(LETTER_SEPARATOR)
        }
}

package net.akehurst.kotlin.gradle.plugin.exportPublic

//TODO: hopefully this will be in the kotlin-stdlib at some point
/*
 * Modified from code given in [https://stackoverflow.com/questions/1247772/is-there-an-equivalent-of-java-util-regex-for-glob-type-patterns]
 * ("I hereby grant that the code in this answer is in the public domain. – Neil Traft Apr 17 at 18:49")
 *
 * Converts a standard POSIX Shell globbing pattern into a regular expression
 * pattern. The result can be used with the standard {@link java.util.regex} API to
 * recognize strings which match the glob pattern.
 * <p/>
 * See also, the POSIX Shell language:
 * http://pubs.opengroup.org/onlinepubs/009695399/utilities/xcu_chap02.html#tag_02_13_01
 *
 * original author Neil Traft
 *
 */
/**
 * Converts the glob string into a regular expression [Regex] with the specified set of [options].
 *
 * @param separator the character that separates the glob parts,
 * i.e. '.' (for kotlin qualified names) or '/' (for file paths)
 *
 */
internal fun String.toRegexFromGlob(separator: Char, options: Set<RegexOption> = emptySet()): Regex {
    val globStr = this
    var regex = ""
    var inGroup = 0
    var inCharacterClass = 0
    var firstIndexInClass = -1

    var i = 0
    while (i < globStr.length) {
        val ch = globStr[i]
        when (ch) {
            '\\' -> {
                if (++i >= globStr.length) {
                    regex += '\\'
                } else {
                    val next = globStr[i]
                    when (next) {
                        ',' -> Unit /* escape not needed */
                        'Q', 'E' ->  regex += "\\\\" /* extra escape needed */
                        else -> regex += '\\'
                    }
                    regex += next
                }
            }
            '*' -> {
                if (inCharacterClass == 0) {
                    if (i + 1 >= globStr.length) {
                        regex += "[^$separator]*"
                    } else {
                        val next = globStr[i + 1]
                        when (next) {
                            '*' -> {
                                regex += ".*"
                                i++
                            }
                            else -> regex += "[^$separator]*"
                        }
                    }
                } else {
                    regex += '*'
                }
            }
            '?' -> regex += if (inCharacterClass == 0) "([^$separator])" else '?'
            '[' -> {
                inCharacterClass++
                firstIndexInClass = i + 1
                regex += '['
            }
            ']' -> {
                inCharacterClass--
                regex += "&&[^$separator]]"
            }
            '{' -> {
                inGroup++
                regex += '('
            }
            '}' -> {
                inGroup--
                regex += ')'
            }
            '!' -> regex += if (firstIndexInClass == i) '^' else '!'
            ',' -> regex += if (inGroup > 0) '|' else ','
            '.', '(', ')', '+', '|', '^', '$', '@', '%' -> {
                if (inCharacterClass == 0 || (firstIndexInClass == i && ch == '^')) regex += '\\'
                regex += ch
            }
            else -> regex += ch
        }
        i++
    }

    return Regex(regex, options)
}
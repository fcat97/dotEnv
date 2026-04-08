package io.github.fcat97.internal

class EnvParser {

    static List<String> readLines(File envFile) {
        envFile.readLines().findAll { it && !it.startsWith('#') && it.contains('=') }
    }

    static String sanitizeKey(String raw) {
        raw.trim().toUpperCase().replaceAll(/[^A-Za-z0-9_]/, '_')
    }

    static boolean isList(String value) {
        (value.startsWith('[') && value.endsWith(']')) || value.contains(',')
    }

    static List<String> parseListItems(String value) {
        if (value.startsWith('[') && value.endsWith(']')) {
            def inner = value[1..-2].trim()
            return inner.split(/,(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)/).collect { it.trim().replaceAll(/^"|"$/, '') }
        }
        return value.split(',').collect { it.trim() }
    }

    static boolean isBoolean(String value) {
        value.equalsIgnoreCase('true') || value.equalsIgnoreCase('false')
    }

    static boolean isLong(String value) {
        value ==~ /^-?\d+[lL]?$/
    }

    static boolean isDouble(String value) {
        value ==~ /^-?\d*\.\d+([eE][+-]?\d+)?$/
    }

    static boolean isString(String value) {
        !isList(value) && !isBoolean(value) && !isLong(value) && !isDouble(value)
    }

    /** Strip surrounding double-quotes from a value if present. */
    static String stripQuotes(String value) {
        if (value.length() >= 2) {
            if ((value.startsWith('"') && value.endsWith('"')) ||
                (value.startsWith("'") && value.endsWith("'"))) {
                return value.substring(1, value.length() - 1)
            }
        }
        return value
    }
}

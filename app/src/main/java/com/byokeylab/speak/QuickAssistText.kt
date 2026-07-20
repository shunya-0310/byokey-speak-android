package com.byokeylab.speak

internal fun appendAssistText(current: String, addition: String): String {
    val cleanAddition = addition.trim()
    if (cleanAddition.isBlank()) return current
    if (current.isBlank()) return cleanAddition
    val separator = if (current.last().isWhitespace()) "" else " "
    return current + separator + cleanAddition
}

package com.byokeylab.speak

/**
 * コーチ返信末尾の "Vocab: expression | 意味" 行を抽出し、
 * 表示用テキストからは取り除く。純粋関数(テスト対象)。
 */
object VocabExtractor {
    data class Draft(val expression: String, val meaning: String)
    data class Result(val cleanText: String, val vocab: List<Draft>)

    fun extract(text: String): Result {
        val vocab = mutableListOf<Draft>()
        val keptLines = text.lines().filter { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("Vocab:", ignoreCase = true)) {
                val body = trimmed.substring("Vocab:".length).trim()
                val parts = body.split("|", "｜", limit = 2)
                val expression = parts.getOrNull(0)?.trim()?.trim('"')?.trim().orEmpty()
                val meaning = parts.getOrNull(1)?.trim().orEmpty()
                if (expression.isNotBlank()) {
                    vocab.add(Draft(expression = expression, meaning = meaning))
                }
                false
            } else {
                true
            }
        }
        return Result(
            cleanText = keptLines.joinToString("\n").trim(),
            vocab = vocab.take(3)
        )
    }
}

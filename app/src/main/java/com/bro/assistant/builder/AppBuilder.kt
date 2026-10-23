package com.bro.assistant.builder

object AppBuilder {
    data class RepairResult(val success: Boolean, val content: String, val message: String)

    fun lintAndRepair(fileName: String, originalContent: String): RepairResult {
        var content = originalContent.trim()
        if (content.isEmpty()) return RepairResult(false, originalContent, "Empty")
        if (fileName.endsWith(".kt") && !content.contains("package ")) {
            content = "package com.bro.assistant\n\n$content"
        }
        val open = content.count { it == '{' }
        val close = content.count { it == '}' }
        if (open != close) return RepairResult(false, content, "Unbalanced braces")
        return RepairResult(true, content, "OK")
    }
}

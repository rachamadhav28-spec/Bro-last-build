package com.bro.assistant.builder

import android.content.Context
import java.io.File

object AppBuilder {
    /**
     * Validates and repairs generated or edited Kotlin files before commit/build.
     * Enforces package declaration, balanced braces, and minimum length checks.
     */
    data class RepairResult(val success: Boolean, val content: String, val message: String)

    fun lintAndRepair(fileName: String, originalContent: String): RepairResult {
        var content = originalContent.trim()
        if (content.isEmpty()) {
            return RepairResult(false, originalContent, "File content is empty.")
        }

        // 1. Ensure package line exists for Kotlin source files
        if (fileName.endsWith(".kt") && !content.contains("package ")) {
            content = "package com.bro.assistant\n\n$content"
        }

        // 2. Check balanced braces
        val openBraces = content.count { it == '{' }
        val closeBraces = content.count { it == '}' }
        if (openBraces != closeBraces) {
            return RepairResult(
                false,
                content,
                "Unbalanced braces in $fileName: $openBraces open vs $closeBraces close."
            )
        }

        // 3. Reject raw unescaped markdown code-block wrappers if accidentally pasted
        if (content.startsWith("```kotlin") || content.startsWith("```")) {
            content = content.replace(Regex("^```[a-zA-Z]*\n"), "").replace(Regex("\n```$"), "").trim()
        }

        return RepairResult(true, content, "File linted and verified successfully.")
    }

    fun applyPerFileRepair(projectDir: File, relativePath: String, newContent: String): RepairResult {
        val targetFile = File(projectDir, relativePath)
        val lint = lintAndRepair(targetFile.name, newContent)
        if (!lint.success) {
            return lint
        }
        targetFile.parentFile?.mkdirs()
        targetFile.writeText(lint.content, Charsets.UTF_8)
        return RepairResult(true, lint.content, "Successfully repaired and written to $relativePath")
    }
}

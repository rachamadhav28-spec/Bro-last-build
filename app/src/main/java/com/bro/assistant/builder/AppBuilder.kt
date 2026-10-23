package com.bro.assistant.builder

import java.io.File

object CodeLint {
    private val toolCallArtifacts = listOf(
        "<tool_call>", "</tool_call>", "<function_calls>", "<invoke",
        "I will now update", "Here is the updated file"
    )

    /**
     * Strips markdown fences, deduplicates repeated import lines, and cleans trailing whitespace.
     */
    fun dedupeAndFix(raw: String): String {
        var cleaned = raw.trim()
        if (cleaned.startsWith("```")) {
            cleaned = cleaned
                .replace(Regex("^```[a-zA-Z0-9_-]*\\s*\\n"), "")
                .replace(Regex("\\n```\\s*$"), "")
                .trim()
        }

        val lines = cleaned.lines()
        val seenImports = mutableSetOf<String>()
        val seenPackages = mutableSetOf<String>()
        val out = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("package ") -> {
                    if (seenPackages.add(trimmed)) out.add(trimmed)
                }
                trimmed.startsWith("import ") -> {
                    if (seenImports.add(trimmed)) out.add(trimmed)
                }
                else -> out.add(line.trimEnd())
            }
        }
        return out.joinToString("\n").trim()
    }

    fun containsToolCallJunk(text: String): Boolean {
        return toolCallArtifacts.any { text.contains(it, ignoreCase = true) }
    }
}

object AppBuilder {

    data class ValidationResult(
        val isValid: Boolean,
        val cleanedCode: String,
        val reason: String,
        val rebuildHint: String? = null
    )

    /**
     * Validates a generated Kotlin file against all BRO Master Prompt rules:
     * 1. Rejects tool-call text
     * 2. First line must be the package line
     * 3. Balanced braces { }
     * 4. At least 60% of original length (if original existed)
     */
    fun validateAndLint(
        filePath: String,
        candidateCode: String,
        originalCode: String = ""
    ): ValidationResult {
        val linted = CodeLint.dedupeAndFix(candidateCode)

        if (linted.isBlank()) {
            return ValidationResult(
                isValid = false,
                cleanedCode = "",
                reason = "Candidate file is empty.",
                rebuildHint = buildRebuildHint(filePath, originalCode, "Empty output returned.")
            )
        }

        if (CodeLint.containsToolCallJunk(linted)) {
            return ValidationResult(
                isValid = false,
                cleanedCode = linted,
                reason = "Rejected: Contains raw tool-call or conversational wrapper text.",
                rebuildHint = buildRebuildHint(filePath, originalCode, "Strip all tool-call tags and prose.")
            )
        }

        val firstNonBlank = linted.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: ""
        if (filePath.endsWith(".kt") && !firstNonBlank.startsWith("package ")) {
            return ValidationResult(
                isValid = false,
                cleanedCode = linted,
                reason = "Rejected: First line must be the package declaration (found: '$firstNonBlank').",
                rebuildHint = buildRebuildHint(filePath, originalCode, "Start line 1 with 'package com.bro.assistant...'")
            )
        }

        val openBraces = linted.count { it == '{' }
        val closeBraces = linted.count { it == '}' }
        if (openBraces != closeBraces) {
            return ValidationResult(
                isValid = false,
                cleanedCode = linted,
                reason = "Rejected: Unbalanced braces ($openBraces '{' vs $closeBraces '}').",
                rebuildHint = buildRebuildHint(filePath, originalCode, "Balance all opening and closing braces.")
            )
        }

        if (originalCode.isNotBlank()) {
            val minAllowedLength = (originalCode.length * 0.60).toInt()
            if (linted.length < minAllowedLength) {
                return ValidationResult(
                    isValid = false,
                    cleanedCode = linted,
                    reason = "Rejected: Gutted file detected (${linted.length} chars < 60% of original ${originalCode.length} chars).",
                    rebuildHint = buildRebuildHint(filePath, originalCode, "Do not truncate or omit existing functions.")
                )
            }
        }

        return ValidationResult(
            isValid = true,
            cleanedCode = linted,
            reason = "CodeLint & structural validation passed."
        )
    }

    /**
     * Generates a per-file repair prompt when a build or validation fails.
     */
    fun buildPerFileRepairPrompt(
        filePath: String,
        brokenCode: String,
        compilerError: String
    ): String {
        return """
            REPAIR FILE: $filePath
            COMPILER / LINT ERROR:
            $compilerError

            RULES:
            1. Return ONLY valid Kotlin code for $filePath.
            2. Line 1 MUST be the package declaration.
            3. Keep all braces balanced and preserve at least 60% of the original file length.
            4. Do NOT include markdown prose or tool-call tags.

            CURRENT FILE CONTENT:
            $brokenCode
        """.trimIndent()
    }

    fun buildRebuildHint(filePath: String, originalCode: String, failureReason: String): String {
        return "REBUILD HINT for $filePath [$failureReason]: Restore full implementation (${originalCode.length} chars baseline) with package header on line 1."
    }

    /**
     * Writes validated code or cleans up damaged/gutted files by restoring backup.
     */
    fun safeWriteOrRestore(targetFile: File, candidateCode: String): ValidationResult {
        val original = if (targetFile.exists()) targetFile.readText(Charsets.UTF_8) else ""
        val check = validateAndLint(targetFile.name, candidateCode, original)

        if (check.isValid) {
            targetFile.parentFile?.mkdirs()
            targetFile.writeText(check.cleanedCode, Charsets.UTF_8)
        } else if (original.isNotBlank()) {
            // Cleanup damaged file by preserving intact original
            targetFile.writeText(original, Charsets.UTF_8)
        }
        return check
    }
}
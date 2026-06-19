package cli.shady.commands

class AliasNameDeriver(
    private val reservedNames: Set<String> = DEFAULT_RESERVED_NAMES,
) {
    /**
     * Derive a suggested alias name from a command string.
     * Returns empty string if all 99 suffix attempts are exhausted.
     */
    fun derive(command: String, existingNames: Set<String>): String {
        val baseName = extractBaseName(command)
        return resolveConflicts(baseName, existingNames)
    }

    private fun extractBaseName(command: String): String {
        val tokens = command.trim().split("\\s+".toRegex())
        if (tokens.isEmpty()) return FALLBACK_NAME

        // Split at operators — process the first meaningful segment
        val segment = takeFirstSegment(tokens)
        if (segment.isEmpty()) return FALLBACK_NAME

        // Skip leading tool names
        val afterToolNames = skipLeadingToolNames(segment)
        if (afterToolNames.isEmpty()) return FALLBACK_NAME

        // Skip leading tool verbs
        val afterToolVerbs = skipLeadingToolVerbs(afterToolNames)
        if (afterToolVerbs.isEmpty()) return FALLBACK_NAME

        // Take up to 3 remaining tokens, join with `-`
        val meaningful = afterToolVerbs.take(3)
        val joined = meaningful.joinToString("-")

        // Sanitize: keep only valid characters, lowercase
        val sanitized = sanitize(joined)
        if (sanitized.isEmpty()) return FALLBACK_NAME

        // Truncate at last complete token boundary if >30 chars
        return truncateAtTokenBoundary(sanitized)
    }

    private fun takeFirstSegment(tokens: List<String>): List<String> {
        val segment = mutableListOf<String>()
        for (token in tokens) {
            if (token in OPERATORS) break
            segment.add(token)
        }
        return segment
    }

    private fun skipLeadingToolNames(tokens: List<String>): List<String> {
        val startIndex = tokens.indexOfFirst { it.lowercase() !in TOOL_NAMES }
        return if (startIndex < 0) emptyList() else tokens.subList(startIndex, tokens.size)
    }

    private fun skipLeadingToolVerbs(tokens: List<String>): List<String> {
        val startIndex = tokens.indexOfFirst { it.lowercase() !in TOOL_VERBS }
        return if (startIndex < 0) emptyList() else tokens.subList(startIndex, tokens.size)
    }

    private fun sanitize(input: String): String {
        return input.lowercase()
            .map { ch -> if (ch.toString().matches(CHAR_PATTERN)) ch else "" }
            .joinToString("")
    }

    private fun truncateAtTokenBoundary(name: String): String {
        if (name.length <= MAX_NAME_LENGTH) return name

        // Find the last `-` before or at the max length position
        val truncated = name.substring(0, MAX_NAME_LENGTH)
        val lastSeparator = truncated.lastIndexOf('-')
        return if (lastSeparator > 0) {
            truncated.substring(0, lastSeparator)
        } else {
            truncated
        }
    }

    private fun resolveConflicts(baseName: String, existingNames: Set<String>): String {
        val conflictSet = existingNames + reservedNames

        if (baseName !in conflictSet) return baseName

        for (suffix in 2..MAX_SUFFIX_ATTEMPTS + 1) {
            val candidate = "$baseName-$suffix"
            if (candidate !in conflictSet && candidate.length <= MAX_NAME_LENGTH) {
                return candidate
            }
        }
        return ""
    }

    companion object {
        val DEFAULT_RESERVED_NAMES: Set<String> = setOf(
            "alias", "help", "prehook", "suggest", "accept",
        )

        /** Known tool verbs to skip when extracting tokens. */
        val TOOL_VERBS: Set<String> = setOf(
            "run", "exec", "test", "build", "start", "install",
        )

        /** Known tool names to discard. */
        val TOOL_NAMES: Set<String> = setOf(
            "npm", "npx", "git", "yarn", "pnpm", "docker", "kubectl", "gradle", "gradlew", "./gradlew",
        )

        /** Operators that act as token boundaries. */
        val OPERATORS: Set<String> = setOf("&&", "||", "|", ";")

        /** Valid alias name pattern. */
        val NAME_PATTERN: Regex = "[a-zA-Z0-9_.\\-]+".toRegex()

        /** Valid single character pattern for sanitization. */
        private val CHAR_PATTERN: Regex = "[a-zA-Z0-9_.\\-]".toRegex()

        /** Fallback name when no meaningful tokens are extracted. */
        private const val FALLBACK_NAME: String = "cmd"

        /** Maximum alias name length. */
        const val MAX_NAME_LENGTH: Int = 30

        /** Maximum numeric suffix attempts. */
        const val MAX_SUFFIX_ATTEMPTS: Int = 99
    }
}

package cli.shady.cli

import cli.shady.commands.CommandHelp

object CommandLine {
    const val INVALID_ARGUMENT_EXIT_CODE = 2
    const val USAGE = CommandHelp.USAGE

    fun parse(args: Array<String>): CommandLineRequest =
        when {
            args.isEmpty() -> CommandLineRequest.Invalid(
                "Missing command.\n\n$USAGE",
            )
            args.size == 1 && args.single() == START_COMMAND -> CommandLineRequest.Start
            else -> CommandLineRequest.Execute(CliInput.from(args))
        }

    private const val START_COMMAND = "start"
}

data class CliInput(
    val rawInput: String,
    val tokens: List<String>,
) {
    fun textAfterDoubleDash(): String? {
        val markerIndex = tokens.indexOf(DOUBLE_DASH)
        if (markerIndex >= 0 && markerIndex < tokens.lastIndex) {
            return tokens.drop(markerIndex + 1).joinToString(" ").trim().takeIf(String::isNotEmpty)
        }

        val rawMarker = DOUBLE_DASH_REGEX.find(rawInput)
        return rawMarker
            ?.let { rawInput.substring(it.range.last + 1).trim() }
            ?.takeIf(String::isNotEmpty)
    }

    companion object {
        private const val DOUBLE_DASH = "--"
        private val DOUBLE_DASH_REGEX = Regex("""(?:^|\s)--(?:\s+|$)""")

        fun from(args: Array<String>): CliInput {
            val rawInput = if (args.size == 1) args.single() else args.joinToString(" ")
            val tokens = if (args.size == 1) splitSingleArgument(args.single()) else args.toList()
            return CliInput(rawInput = rawInput, tokens = tokens)
        }

        private fun splitSingleArgument(input: String): List<String> {
            val tokens = mutableListOf<String>()
            val current = StringBuilder()
            var quote: Char? = null
            var escaping = false

            input.forEach { char ->
                when {
                    escaping -> {
                        current.append(char)
                        escaping = false
                    }
                    char == '\\' -> escaping = true
                    quote != null && char == quote -> quote = null
                    quote == null && (char == '\'' || char == '"') -> quote = char
                    quote == null && char.isWhitespace() -> {
                        if (current.isNotEmpty()) {
                            tokens += current.toString()
                            current.clear()
                        }
                    }
                    else -> current.append(char)
                }
            }

            if (escaping) {
                current.append('\\')
            }
            if (current.isNotEmpty()) {
                tokens += current.toString()
            }
            return tokens
        }
    }
}

sealed interface CommandLineRequest {
    data object Start : CommandLineRequest

    data class Execute(val input: CliInput) : CommandLineRequest

    data class Invalid(val message: String) : CommandLineRequest
}

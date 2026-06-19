package cli.shady.terminal

import cli.shady.commands.config.ColorRules

class AnsiOutputColorizer(
    private val rulesProvider: () -> ColorRules,
    private val enabled: Boolean,
) {
    private var rules: ColorRules = runCatching(rulesProvider).getOrDefault(ColorRules())
    private var activeCommand: String? = null
    private var nativeSgrActive = false
    private var alternateScreen = false
    private var pendingEscape = ""

    @Synchronized
    fun setActiveCommand(command: String?) {
        rules = runCatching(rulesProvider).getOrDefault(rules)
        activeCommand = command
        nativeSgrActive = false
    }

    @Synchronized
    fun transform(chunk: String): String {
        if (!enabled || chunk.isEmpty()) return chunk
        val input = pendingEscape + chunk
        pendingEscape = ""
        val commandColor = activeCommand?.let(::commandName)?.let(rules.commands::get)
        if (commandColor == null && rules.fileTypes.isEmpty()) return input

        val output = StringBuilder(input.length + 32)
        var index = 0
        while (index < input.length) {
            if (input[index] == ESCAPE) {
                val sequenceEnd = escapeSequenceEnd(input, index)
                if (sequenceEnd < 0) {
                    pendingEscape = input.substring(index)
                    break
                }
                val sequence = input.substring(index, sequenceEnd + 1)
                updateTerminalMode(sequence)
                output.append(sequence)
                index = sequenceEnd + 1
                continue
            }

            val nextEscape = input.indexOf(ESCAPE, index).let { if (it < 0) input.length else it }
            val plain = input.substring(index, nextEscape)
            if (activeCommand != null && !nativeSgrActive && !alternateScreen) {
                output.append(colorPlainText(plain, commandColor))
            } else {
                output.append(plain)
            }
            index = nextEscape
        }
        return output.toString()
    }

    private fun colorPlainText(text: String, commandColor: String?): String {
        if (text.isEmpty()) return text
        val matches = FILE_TOKEN.findAll(text).toList()
        if (matches.isEmpty()) return commandColor?.let { wrap(text, it) } ?: text

        val output = StringBuilder(text.length + 32)
        var cursor = 0
        matches.forEach { match ->
            val prefix = text.substring(cursor, match.range.first)
            output.append(commandColor?.let { wrap(prefix, it) } ?: prefix)
            val extension = match.groupValues[1].lowercase()
            val fileColor = rules.fileTypes[extension]
            output.append(
                when {
                    fileColor != null -> wrap(match.value, fileColor)
                    commandColor != null -> wrap(match.value, commandColor)
                    else -> match.value
                },
            )
            cursor = match.range.last + 1
        }
        val suffix = text.substring(cursor)
        output.append(commandColor?.let { wrap(suffix, it) } ?: suffix)
        return output.toString()
    }

    private fun updateTerminalMode(sequence: String) {
        when {
            sequence == "\u001B[?1049h" || sequence == "\u001B[?47h" -> alternateScreen = true
            sequence == "\u001B[?1049l" || sequence == "\u001B[?47l" -> alternateScreen = false
            sequence.endsWith('m') -> {
                val parameters = sequence.removePrefix("\u001B[").dropLast(1)
                nativeSgrActive = when {
                    parameters.isBlank() -> false
                    parameters.split(';').any { it == "0" || it == "39" } -> false
                    else -> true
                }
            }
        }
    }

    private fun escapeSequenceEnd(text: String, start: Int): Int {
        if (start + 1 >= text.length) return -1
        if (text[start + 1] != '[' && text[start + 1] != ']') return start + 1
        for (index in start + 2 until text.length) {
            val char = text[index]
            if (text[start + 1] == ']' && char == '\u0007') return index
            if (text[start + 1] == '[' && char in '@'..'~') return index
        }
        return -1
    }

    private fun commandName(command: String): String? {
        val tokens = command.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        if (tokens.isEmpty()) return null
        if (tokens.first() != "sudo") return tokens.first().substringAfterLast('/')
        return tokens.drop(1)
            .dropWhile { it.startsWith('-') }
            .firstOrNull()
            ?.substringAfterLast('/')
    }

    private fun wrap(text: String, color: String): String {
        if (text.isEmpty()) return text
        return "${ansi(color)}$text$RESET"
    }

    private fun ansi(color: String): String {
        val normalized = color.lowercase()
        NAMED_CODES[normalized]?.let { return "\u001B[${it}m" }
        val hex = color.removePrefix("#")
        val red = hex.substring(0, 2).toInt(16)
        val green = hex.substring(2, 4).toInt(16)
        val blue = hex.substring(4, 6).toInt(16)
        return "\u001B[38;2;$red;$green;${blue}m"
    }

    private companion object {
        const val ESCAPE = '\u001B'
        const val RESET = "\u001B[0m"
        val FILE_TOKEN = Regex("""(?:^|(?<=\s))[^\s]+\.([A-Za-z0-9]{1,12})(?=$|\s)""")
        val NAMED_CODES = mapOf(
            "black" to 30,
            "red" to 31,
            "green" to 32,
            "yellow" to 33,
            "blue" to 34,
            "magenta" to 35,
            "cyan" to 36,
            "white" to 37,
            "bright-black" to 90,
            "bright-red" to 91,
            "bright-green" to 92,
            "bright-yellow" to 93,
            "bright-blue" to 94,
            "bright-magenta" to 95,
            "bright-cyan" to 96,
            "bright-white" to 97,
        )
    }
}

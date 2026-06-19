package cli.shady.commands

import cli.shady.commands.builtins.AliasCommand
import cli.shady.commands.builtins.GitPrehookScripts
import cli.shady.commands.builtins.ColorCommandsCommand

object CommandHelp {
    const val START_COMMAND_USAGE = "Emulator:\n" +
        "  shady start\n" +
        "      Open the shady terminal emulator."

    const val EMULATOR_COMMANDS_USAGE = "Emulator-only commands:\n" +
        "  shady sys\n" +
        "      Open the live macOS resource dashboard in its own window.\n" +
        "  shady windows\n" +
        "      Open a clickable list of visible macOS windows in its own window.\n" +
        "  shady update\n" +
        "      Install the latest configured Shady release."

    const val RAW_COMMANDS_USAGE = "Raw CLI commands:\n" +
        "  shady <command> [args...]\n" +
        "      Run any CLI tool in the current terminal.\n" +
        "      Example: shady git status\n" +
        "      Example: shady python3 python.py\n" +
        "      Example: shady \"npm run lint && npm run prettier-write\""

    const val HELP_COMMAND_USAGE = "Help commands:\n" +
        "  shady help\n" +
            "      Show this global command overview."

    const val CONFIGURATION_USAGE = "Configuration and styles:\n" +
        "  shady config show\n" +
        "  shady config set <key> <value>\n" +
        "  shady styles\n" +
        "      Open the style directory when the feature is enabled."

    const val USAGE = START_COMMAND_USAGE + "\n\n" +
        EMULATOR_COMMANDS_USAGE + "\n\n" +
        RAW_COMMANDS_USAGE + "\n\n" +
        HELP_COMMAND_USAGE + "\n\n" +
        CONFIGURATION_USAGE + "\n\n" +
        ColorCommandsCommand.USAGE + "\n\n" +
        AliasCommand.USAGE + "\n\n" +
        GitPrehookScripts.USAGE
}

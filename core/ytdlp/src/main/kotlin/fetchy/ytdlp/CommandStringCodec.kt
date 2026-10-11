package fetchy.ytdlp

enum class ShellFamily {
    BASH,
    CMD,
    POWERSHELL,
}

object CommandStringCodec {

    const val MASK: String = "***"

    private val SAFE_ARGUMENT: Regex = Regex("""^[A-Za-z0-9_@%+=:,./-]+$""")
    private val USER_INFO: Regex = Regex("""(?<=://)[^/@\s]+(?=@)""")

    private val SECRET_VALUE_OPTIONS: Set<String> =
        setOf(
            "--password",
            "-p",
            "--video-password",
            "--twofactor",
            "--ap-username",
            "--ap-password",
            "--cookies",
        )

    private val SECRET_HEADER_NAMES: Set<String> =
        setOf(
            "authorization",
            "proxy-authorization",
            "cookie",
            "set-cookie",
            "x-api-key",
            "x-auth-token",
        )

    fun format(argv: List<String>, shell: ShellFamily): String =
        argv.joinToString(" ") { argument ->
            when (shell) {
                ShellFamily.BASH -> quotePosix(argument)
                ShellFamily.CMD -> quoteCmd(argument)
                ShellFamily.POWERSHELL -> quotePowerShell(argument)
            }
        }

    fun parse(command: String, shell: ShellFamily): List<String> =
        when (shell) {
            ShellFamily.BASH -> parsePosix(command)
            ShellFamily.CMD -> parseCmd(command)
            ShellFamily.POWERSHELL -> parsePowerShell(command)
        }

    fun importArgs(command: String, shell: ShellFamily): List<String> {
        val parsed: List<String> = parse(command, shell)
        val first: String = parsed.firstOrNull() ?: return parsed
        val base: String = first.substringAfterLast('/').substringAfterLast('\\')
        val isExecutable: Boolean = base.equals("yt-dlp", true) || base.equals("yt-dlp.exe", true)
        return if (isExecutable) parsed.drop(1) else parsed
    }

    fun mask(argv: List<String>): List<String> {
        val result = ArrayList<String>(argv.size)
        var index: Int = 0
        while (index < argv.size) {
            val argument: String = argv[index]
            val inlineName: String? = inlineOptionName(argument)
            when {
                inlineName in SECRET_VALUE_OPTIONS -> result += "$inlineName=$MASK"

                inlineName == "--proxy" -> result += "--proxy=${maskUserInfo(inlineValue(argument))}"

                inlineName == "--add-headers" -> result += "--add-headers=${maskHeader(inlineValue(argument))}"

                argument in SECRET_VALUE_OPTIONS -> {
                    result += argument
                    if (index + 1 < argv.size) {
                        result += MASK
                        index++
                    }
                }

                argument == "--proxy" -> {
                    result += argument
                    if (index + 1 < argv.size) {
                        result += maskUserInfo(argv[index + 1])
                        index++
                    }
                }

                argument == "--add-headers" -> {
                    result += argument
                    if (index + 1 < argv.size) {
                        result += maskHeader(argv[index + 1])
                        index++
                    }
                }

                else -> result += maskUserInfo(argument)
            }
            index++
        }
        return result
    }

    private fun inlineOptionName(argument: String): String? {
        if (!argument.startsWith("--")) return null
        val separator: Int = argument.indexOf('=')
        return if (separator > 0) argument.substring(0, separator) else null
    }

    private fun inlineValue(argument: String): String = argument.substringAfter('=', "")

    private fun maskUserInfo(value: String): String = USER_INFO.replace(value, MASK)

    private fun maskHeader(value: String): String {
        val separator: Int = value.indexOf(':')
        if (separator <= 0) return value
        val name: String = value.substring(0, separator).trim().lowercase()
        return if (name in SECRET_HEADER_NAMES) "$name: $MASK" else value
    }

    private fun isSafe(argument: String): Boolean = argument.isNotEmpty() && SAFE_ARGUMENT.matches(argument)

    private fun quotePosix(argument: String): String {
        if (isSafe(argument)) return argument
        return "'" + argument.replace("'", "'\\''") + "'"
    }

    private fun quotePowerShell(argument: String): String {
        if (isSafe(argument)) return argument
        return "'" + argument.replace("'", "''") + "'"
    }

    private fun quoteCmd(argument: String): String {
        val needsQuotes: Boolean = argument.isEmpty() || argument.any { it == ' ' || it == '\t' || it == '"' }
        if (!needsQuotes) return argument
        val builder = StringBuilder()
        builder.append('"')
        var backslashes: Int = 0
        for (character in argument) {
            when (character) {
                '\\' -> backslashes++
                '"' -> {
                    repeat(backslashes * 2 + 1) { builder.append('\\') }
                    backslashes = 0
                    builder.append('"')
                }

                else -> {
                    repeat(backslashes) { builder.append('\\') }
                    backslashes = 0
                    builder.append(character)
                }
            }
        }
        repeat(backslashes * 2) { builder.append('\\') }
        builder.append('"')
        return builder.toString()
    }

    private fun parsePosix(command: String): List<String> {
        val result = ArrayList<String>()
        val token = StringBuilder()
        var hasToken: Boolean = false
        var index: Int = 0
        while (index < command.length) {
            val character: Char = command[index]
            when {
                character == ' ' || character == '\t' -> {
                    if (hasToken) {
                        result += token.toString()
                        token.setLength(0)
                        hasToken = false
                    }
                    index++
                }

                character == '\'' -> {
                    hasToken = true
                    index++
                    while (index < command.length && command[index] != '\'') {
                        token.append(command[index])
                        index++
                    }
                    index++
                }

                character == '"' -> {
                    hasToken = true
                    index++
                    while (index < command.length && command[index] != '"') {
                        if (command[index] == '\\' && index + 1 < command.length && command[index + 1] in "\"\\$`") {
                            token.append(command[index + 1])
                            index += 2
                        } else {
                            token.append(command[index])
                            index++
                        }
                    }
                    index++
                }

                character == '\\' && index + 1 < command.length -> {
                    hasToken = true
                    token.append(command[index + 1])
                    index += 2
                }

                else -> {
                    hasToken = true
                    token.append(character)
                    index++
                }
            }
        }
        if (hasToken) result += token.toString()
        return result
    }

    private fun parseCmd(command: String): List<String> {
        val result = ArrayList<String>()
        val token = StringBuilder()
        var hasToken: Boolean = false
        var inQuotes: Boolean = false
        var index: Int = 0
        while (index < command.length) {
            val character: Char = command[index]
            when {
                !inQuotes && (character == ' ' || character == '\t') -> {
                    if (hasToken) {
                        result += token.toString()
                        token.setLength(0)
                        hasToken = false
                    }
                    index++
                }

                character == '\\' -> {
                    var count: Int = 0
                    while (index < command.length && command[index] == '\\') {
                        count++
                        index++
                    }
                    if (index < command.length && command[index] == '"') {
                        repeat(count / 2) { token.append('\\') }
                        if (count % 2 == 1) {
                            token.append('"')
                            index++
                        } else {
                            inQuotes = !inQuotes
                            index++
                        }
                    } else {
                        repeat(count) { token.append('\\') }
                    }
                    hasToken = true
                }

                character == '"' -> {
                    inQuotes = !inQuotes
                    hasToken = true
                    index++
                }

                else -> {
                    token.append(character)
                    hasToken = true
                    index++
                }
            }
        }
        if (hasToken) result += token.toString()
        return result
    }

    private fun parsePowerShell(command: String): List<String> {
        val result = ArrayList<String>()
        val token = StringBuilder()
        var hasToken: Boolean = false
        var index: Int = 0
        while (index < command.length) {
            val character: Char = command[index]
            when {
                character == ' ' || character == '\t' -> {
                    if (hasToken) {
                        result += token.toString()
                        token.setLength(0)
                        hasToken = false
                    }
                    index++
                }

                character == '\'' -> {
                    hasToken = true
                    index++
                    while (index < command.length) {
                        if (command[index] == '\'') {
                            if (index + 1 < command.length && command[index + 1] == '\'') {
                                token.append('\'')
                                index += 2
                            } else {
                                index++
                                break
                            }
                        } else {
                            token.append(command[index])
                            index++
                        }
                    }
                }

                character == '"' -> {
                    hasToken = true
                    index++
                    while (index < command.length && command[index] != '"') {
                        if (command[index] == '`' && index + 1 < command.length) {
                            token.append(command[index + 1])
                            index += 2
                        } else {
                            token.append(command[index])
                            index++
                        }
                    }
                    index++
                }

                character == '`' && index + 1 < command.length -> {
                    hasToken = true
                    token.append(command[index + 1])
                    index += 2
                }

                else -> {
                    hasToken = true
                    token.append(character)
                    index++
                }
            }
        }
        if (hasToken) result += token.toString()
        return result
    }
}

package fetchy.ytdlp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path

data class HelpOption(
    val name: String,
    val short: String?,
    val long: String?,
    val aliases: List<String>,
    val metavar: String?,
    val description: String,
    val section: String,
    val negatable: Boolean,
) {
    val takesValue: Boolean
        get() = metavar != null

    fun matches(flag: String): Boolean =
        flag == name || flag == short || flag == long || aliases.contains(flag)
}

data class HelpRegistry(
    val version: String,
    val options: List<HelpOption>,
) {
    val size: Int
        get() = options.size

    fun find(flag: String): HelpOption? = options.firstOrNull { it.matches(flag) }

    fun inSection(section: String): List<HelpOption> = options.filter { it.section == section }

    fun sections(): List<String> = options.map { it.section }.distinct()
}

object HelpParser {

    private val SECTION: Regex = Regex("""^\s{0,2}([A-Za-z][A-Za-z0-9 /&'\-.()]*):\s*$""")
    private val DOUBLE_SPACE: Regex = Regex(""" {2,}""")
    private val TOKEN_SPLIT: Regex = Regex("""[\s,]+""")
    private const val NO_PREFIX: String = "[no-]"

    fun parse(helpText: String, version: String): HelpRegistry {
        val options = ArrayList<HelpOption>()
        var section: String = "General"
        var pending: Pending? = null
        for (rawLine in helpText.split('\n')) {
            val line: String = rawLine.trimEnd()
            if (line.isBlank()) continue
            val header: MatchResult? = SECTION.matchEntire(line)
            if (header != null) {
                flush(pending, options)
                pending = null
                section = header.groupValues[1].trim()
                continue
            }
            val leading: Int = line.length - line.trimStart().length
            val trimmed: String = line.trimStart()
            if (leading <= 4 && trimmed.startsWith("-")) {
                flush(pending, options)
                pending = parseOptionLine(trimmed, section)
            } else if (pending != null) {
                pending.appendDescription(trimmed)
            }
        }
        flush(pending, options)
        return HelpRegistry(version, options)
    }

    fun toJson(registry: HelpRegistry): String =
        buildJsonObject {
            put("version", JsonPrimitive(registry.version))
            put(
                "options",
                buildJsonArray {
                    registry.options.forEach { option ->
                        add(
                            buildJsonObject {
                                put("name", JsonPrimitive(option.name))
                                put("short", nullable(option.short))
                                put("long", nullable(option.long))
                                put(
                                    "aliases",
                                    buildJsonArray { option.aliases.forEach { add(JsonPrimitive(it)) } },
                                )
                                put("metavar", nullable(option.metavar))
                                put("description", JsonPrimitive(option.description))
                                put("section", JsonPrimitive(option.section))
                                put("negatable", JsonPrimitive(option.negatable))
                            },
                        )
                    }
                },
            )
        }.toString()

    fun fromJson(json: String): HelpRegistry {
        val root: JsonObject = Json.parseToJsonElement(json).jsonObject
        val version: String = root["version"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val options: List<HelpOption> =
            root["options"]?.jsonArray.orEmpty().mapNotNull { element ->
                val obj: JsonObject = element.jsonObject
                val name: String? = obj["name"]?.jsonPrimitive?.contentOrNull
                if (name == null) {
                    null
                } else {
                    HelpOption(
                        name = name,
                        short = obj["short"]?.jsonPrimitive?.contentOrNull,
                        long = obj["long"]?.jsonPrimitive?.contentOrNull,
                        aliases = obj["aliases"]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull },
                        metavar = obj["metavar"]?.jsonPrimitive?.contentOrNull,
                        description = obj["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        section = obj["section"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        negatable = obj["negatable"]?.jsonPrimitive?.contentOrNull.toBoolean(),
                    )
                }
            }
        return HelpRegistry(version, options)
    }

    private fun nullable(value: String?): JsonElement = if (value == null) JsonNull else JsonPrimitive(value)

    private fun flush(pending: Pending?, out: MutableList<HelpOption>) {
        pending?.toOption()?.let { out += it }
    }

    private fun parseOptionLine(trimmed: String, section: String): Pending {
        val split: MatchResult? = DOUBLE_SPACE.find(trimmed)
        val spec: String = if (split != null) trimmed.substring(0, split.range.first) else trimmed
        val description: String = if (split != null) trimmed.substring(split.range.last + 1) else ""
        val pending = Pending(section)
        pending.appendDescription(description)
        for (token in spec.split(TOKEN_SPLIT)) {
            when {
                token.isEmpty() -> Unit
                token.startsWith("--") -> pending.addLong(token)
                token.startsWith("-") && token.length > 1 -> pending.short = pending.short ?: token
                else -> pending.metavar = pending.metavar ?: token
            }
        }
        return pending
    }

    private class Pending(private val section: String) {
        var short: String? = null
        var metavar: String? = null
        private val longNames = ArrayList<String>()
        private val description = StringBuilder()
        private var negatable: Boolean = false

        fun addLong(token: String) {
            val bare: String = token.removePrefix("--")
            if (bare.startsWith(NO_PREFIX) && bare.length > NO_PREFIX.length) {
                val base: String = bare.substring(NO_PREFIX.length)
                longNames += "--$base"
                longNames += "--no-$base"
                negatable = true
            } else {
                longNames += token
            }
        }

        fun appendDescription(text: String) {
            val clean: String = text.trim()
            if (clean.isEmpty()) return
            if (description.isNotEmpty()) description.append(' ')
            description.append(clean)
        }

        fun toOption(): HelpOption? {
            val long: String? = longNames.firstOrNull()
            val name: String = long ?: short ?: return null
            return HelpOption(
                name = name,
                short = short,
                long = long,
                aliases = if (long != null) longNames.drop(1) else emptyList(),
                metavar = metavar,
                description = description.toString(),
                section = section,
                negatable = negatable,
            )
        }
    }
}

class HelpRegistryCache(private val directory: Path) {

    fun load(version: String): HelpRegistry? {
        val file: Path = fileFor(version)
        if (!Files.exists(file)) return null
        return runCatching { HelpParser.fromJson(Files.readString(file)) }.getOrNull()
    }

    fun save(registry: HelpRegistry) {
        Files.createDirectories(directory)
        Files.writeString(fileFor(registry.version), HelpParser.toJson(registry))
    }

    private fun fileFor(version: String): Path = directory.resolve("ytdlp-help-$version.json")
}

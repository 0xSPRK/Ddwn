package fetchy.ytdlp.integrity

object SumsParser {

    fun parse(text: String): Map<String, String> {
        val result: MutableMap<String, String> = LinkedHashMap()
        for (line in text.lineSequence()) {
            val parsed: Pair<String, String>? = parseLine(line)
            if (parsed != null) {
                result[parsed.second] = parsed.first
            }
        }
        return result
    }

    fun find(text: String, assetName: String): String? {
        val direct: String? = parse(text)[assetName]
        if (direct != null) return normalizeHash(direct)
        val normalizedTarget: String = assetName.substringAfterLast('/')
        val suffixMatch: Map.Entry<String, String>? =
            parse(text).entries.firstOrNull { (name, _) ->
                name.substringAfterLast('/') == normalizedTarget
            }
        return suffixMatch?.value?.let { normalizeHash(it) }
    }

    private fun parseLine(line: String): Pair<String, String>? {
        val trimmed: String = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null
        val hash: String = trimmed.substringBefore(' ').trim()
        if (hash.length != 64 || !hash.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return null
        val rest: String = trimmed.substring(hash.length).trim()
        if (rest.isEmpty()) return null
        val name: String = rest.removePrefix("*").trim()
        if (name.isEmpty()) return null
        return normalizeHash(hash) to name
    }

    private fun normalizeHash(hash: String): String = hash.lowercase()
}

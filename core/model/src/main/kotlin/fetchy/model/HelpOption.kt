package fetchy.model

data class HelpOption(
    val section: String,
    val name: String,
    val shortFlag: String? = null,
    val value: String? = null,
    val description: String,
)

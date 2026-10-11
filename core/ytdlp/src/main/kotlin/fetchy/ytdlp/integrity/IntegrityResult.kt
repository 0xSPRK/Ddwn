package fetchy.ytdlp.integrity

sealed interface IntegrityResult {

    data class Verified(val sha256: String) : IntegrityResult

    data class NotVerified(val reason: String) : IntegrityResult

    data class Mismatch(val expected: String, val actual: String) : IntegrityResult

    val verified: Boolean
        get() = this is Verified
}

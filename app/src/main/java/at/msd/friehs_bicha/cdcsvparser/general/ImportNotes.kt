package at.msd.friehs_bicha.cdcsvparser.general

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray

/**
 * Persistence kernel for the "partially imported" history marker.
 *
 * A file whose parse was cut by the hard parse budget is only PARTIALLY in
 * the model, but the cut state itself is per-parse (thread_local in the
 * core, read once on the cpp-core thread). This marker keeps that data
 * integrity signal visible after leaving the parse screen: the history
 * list shows a chip on every file that was cut at import time.
 *
 * The stored value is a plain JSON array of file names (the history
 * entries' stable names), held as ONE DataStore string. Every function is
 * a pure string→string (or string→set) kernel with no context, clock or
 * IO, so the round trip is covered by the JVM tests; the persistence and
 * the UI read the same kernel on the same value.
 */
object ImportNotes {

    /** Tolerant by design: a hand-edited or half-written store value
     *  degrades to "no markers", never to a crash in the history list. */
    private val json = Json { ignoreUnknownKeys = true }

    /** The cut file names encoded in [raw]; empty on null/garbage. */
    fun loadPartialImported(raw: String?): Set<String> = when {
        raw.isNullOrBlank() -> emptySet()
        else -> runCatching { json.parseToJsonElement(raw) }
            .getOrNull()
            ?.jsonArrayOrNull()
            ?.mapNotNull { it.primitiveOrNull() }
            ?.toSet()
            ?: emptySet()
    }

    /** Encodes [names] as the store value (sorted: idempotent for the same set).
     *  [JsonElement.toString] is the exact JSON text of the element. */
    fun encodePartialImported(names: Set<String>): String =
        JsonArray(names.sorted().map { JsonPrimitive(it) }).toString()

    /** The store value with [added] merged in; the original string when
     *  nothing new arrives (no pointless re-write). */
    fun mergePartialImported(raw: String?, added: Collection<String>): String {
        val current = loadPartialImported(raw)
        if (added.all { it in current }) return raw.orEmpty()
        return encodePartialImported(current + added)
    }

    /** The store value with [names] REMOVED; the original string when
     *  none of them is marked (no pointless re-write). A same-name
     *  re-import that completes without a cut makes the upload whole,
     *  so its earlier marker must go with the re-import. */
    fun removePartialImported(raw: String?, names: Collection<String>): String {
        val current = loadPartialImported(raw)
        val gone = current.intersect(names)
        if (gone.isEmpty()) return raw.orEmpty()
        return encodePartialImported(current - gone)
    }

    private fun JsonElement.jsonArrayOrNull(): List<JsonElement>? =
        (this as? JsonArray)?.jsonArray

    private fun JsonElement.primitiveOrNull(): String? =
        (this as? JsonPrimitive)?.contentOrNull
}

package io.github.fredleonam.graphscope.model

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * A durable identity for a [GraphScopeGraph].
 *
 * [formatVersion] identifies the canonical byte format that was hashed. [sha256Hex] is always a
 * lower-case, 64-character SHA-256 digest of those bytes.
 */
data class GraphFingerprint(
    val formatVersion: Int,
    val sha256Hex: String,
) {
    init {
        require(formatVersion > 0) { "Fingerprint format version must be positive." }
        require(SHA256_HEX.matches(sha256Hex)) {
            "SHA-256 fingerprint must contain exactly 64 lower-case hexadecimal characters."
        }
    }

    override fun toString(): String = "graphscope-v$formatVersion-sha256:$sha256Hex"

    private companion object {
        val SHA256_HEX = Regex("[0-9a-f]{64}")
    }
}

/**
 * The versioned canonical binary representation of a [GraphScopeGraph].
 *
 * Version 1 starts with the ASCII bytes `GraphScope`, followed by a big-endian 32-bit version and
 * the graph's canonically ordered component, binding, and edge collections. Collections are
 * prefixed by a big-endian 32-bit count. Strings are UTF-8 prefixed by their byte length, and
 * nullable strings have a one-byte presence marker before the length and value.
 *
 * The representation is independent of JVM object hashing, default character sets, and input
 * discovery order. Future incompatible changes must increment [VERSION]. The returned byte array
 * is a new snapshot and is suitable for persistence.
 */
object GraphScopeCanonicalFormat {
    const val VERSION: Int = 1

    private val MAGIC = "GraphScope".toByteArray(StandardCharsets.US_ASCII)

    @JvmStatic
    fun encode(graph: GraphScopeGraph): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.write(MAGIC)
            output.writeInt(VERSION)

            output.writeInt(graph.components.size)
            graph.components.forEach { component ->
                output.writeUtf8(component.id.value)
                output.writeUtf8(component.name)
                output.writeNullableUtf8(component.parentId?.value)
            }

            output.writeInt(graph.bindings.size)
            graph.bindings.forEach { binding ->
                output.writeUtf8(binding.id.value)
                output.writeUtf8(binding.key.typeName)
                output.writeNullableUtf8(binding.key.qualifier?.canonicalName)
                output.writeUtf8(binding.componentId.value)
                output.writeNullableUtf8(binding.scope?.name)
            }

            output.writeInt(graph.edges.size)
            graph.edges.forEach { edge ->
                output.writeUtf8(edge.dependentBindingId.value)
                output.writeUtf8(edge.dependencyBindingId.value)
            }
        }
        return bytes.toByteArray()
    }

    @JvmStatic
    fun fingerprint(graph: GraphScopeGraph): GraphFingerprint {
        val digest = MessageDigest.getInstance("SHA-256").digest(encode(graph))
        return GraphFingerprint(VERSION, digest.toHexString())
    }

    private fun DataOutputStream.writeUtf8(value: String) {
        val encoded = value.toByteArray(StandardCharsets.UTF_8)
        writeInt(encoded.size)
        write(encoded)
    }

    private fun DataOutputStream.writeNullableUtf8(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeUtf8(value)
    }

    private fun ByteArray.toHexString(): String {
        val digits = "0123456789abcdef"
        val result = CharArray(size * 2)
        forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xff
            result[index * 2] = digits[value ushr 4]
            result[index * 2 + 1] = digits[value and 0x0f]
        }
        return result.concatToString()
    }
}

package io.github.fredleonam.graphscope.persistence.internal

import io.github.fredleonam.graphscope.persistence.GraphScopeMigrationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MigrationRegistryTest {
    @Test
    fun `applies a deterministic synthetic migration chain`() {
        val applied = mutableListOf<String>()
        val registry = MigrationRegistry(
            listOf(
                migration(1, 2, applied),
                migration(2, 3, applied),
            ),
        )

        val migrated = registry.migrate(document(1), targetVersion = 3)

        assertEquals(listOf("1->2", "2->3"), applied)
        assertEquals(JsonPrimitive(3), migrated["schemaVersion"])
    }

    @Test
    fun `wraps migration failures in the persistence error boundary`() {
        val registry = MigrationRegistry(
            listOf(
                object : GraphMigration {
                    override val fromVersion = 1
                    override val toVersion = 2

                    override fun migrate(document: JsonObject): JsonObject = error("synthetic failure")
                },
            ),
        )

        val error = assertFailsWith<GraphScopeMigrationException> {
            registry.migrate(document(1), targetVersion = 2)
        }

        assertEquals("synthetic failure", error.cause?.message)
    }

    @Test
    fun `rejects ambiguous migration starting points`() {
        assertFailsWith<IllegalArgumentException> {
            MigrationRegistry(
                listOf(
                    migration(1, 2, mutableListOf()),
                    migration(1, 3, mutableListOf()),
                ),
            )
        }
    }

    private fun migration(
        from: Int,
        to: Int,
        applied: MutableList<String>,
    ): GraphMigration = object : GraphMigration {
        override val fromVersion = from
        override val toVersion = to

        override fun migrate(document: JsonObject): JsonObject {
            applied += "$from->$to"
            return JsonObject(document + ("schemaVersion" to JsonPrimitive(to)))
        }
    }

    private fun document(version: Int): JsonObject = buildJsonObject {
        put("schemaVersion", JsonPrimitive(version))
    }
}

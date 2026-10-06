package io.github.fredleonam.graphscope.persistence.internal

import io.github.fredleonam.graphscope.persistence.GraphScopeMalformedDocumentException
import io.github.fredleonam.graphscope.persistence.GraphScopeMigrationException
import io.github.fredleonam.graphscope.persistence.GraphScopeUnsupportedSchemaVersionException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** One deterministic transformation between two persisted document schema versions. */
internal interface GraphMigration {
    val fromVersion: Int
    val toVersion: Int

    fun migrate(document: JsonObject): JsonObject
}

/** Selects and applies a unique forward migration chain before current-schema decoding. */
internal class MigrationRegistry(migrations: List<GraphMigration>) {
    private val migrationByVersion: Map<Int, GraphMigration>

    init {
        require(migrations.all { it.fromVersion > 0 && it.toVersion > it.fromVersion }) {
            "Graph persistence migrations must move between positive, increasing versions."
        }
        require(migrations.map(GraphMigration::fromVersion).distinct().size == migrations.size) {
            "Only one graph persistence migration may start at each schema version."
        }
        migrationByVersion = migrations.associateBy(GraphMigration::fromVersion)
    }

    fun migrate(document: JsonObject, targetVersion: Int): JsonObject {
        var currentDocument = document
        var currentVersion = schemaVersion(currentDocument)

        if (currentVersion > targetVersion) {
            throw GraphScopeUnsupportedSchemaVersionException(currentVersion, targetVersion)
        }

        while (currentVersion < targetVersion) {
            val migration = migrationByVersion[currentVersion]
                ?: throw GraphScopeUnsupportedSchemaVersionException(currentVersion, targetVersion)
            if (migration.toVersion > targetVersion) {
                throw GraphScopeUnsupportedSchemaVersionException(currentVersion, targetVersion)
            }
            currentDocument = try {
                migration.migrate(currentDocument)
            } catch (error: Exception) {
                throw GraphScopeMigrationException(
                    "Failed to migrate GraphScope persistence schema version " +
                        "${migration.fromVersion} to ${migration.toVersion}: ${error.message}",
                    error,
                )
            }

            val migratedVersion = try {
                schemaVersion(currentDocument)
            } catch (error: GraphScopeMalformedDocumentException) {
                throw GraphScopeMigrationException(
                    "Migration from GraphScope persistence schema version " +
                        "${migration.fromVersion} to ${migration.toVersion} produced an invalid document.",
                    error,
                )
            }
            if (migratedVersion != migration.toVersion) {
                throw GraphScopeMigrationException(
                    "Migration from GraphScope persistence schema version ${migration.fromVersion} " +
                        "declared target ${migration.toVersion} but produced version $migratedVersion.",
                )
            }
            currentVersion = migratedVersion
        }

        return currentDocument
    }

    private fun schemaVersion(document: JsonObject): Int {
        val versionElement = document["schemaVersion"]
            ?: throw GraphScopeMalformedDocumentException(
                "Malformed GraphScope persistence document: missing required field 'schemaVersion'.",
            )
        val primitive = try {
            versionElement.jsonPrimitive
        } catch (error: IllegalArgumentException) {
            throw GraphScopeMalformedDocumentException(
                "Malformed GraphScope persistence document: 'schemaVersion' must be an integer.",
                error,
            )
        }
        val version = if (!primitive.isString) primitive.intOrNull else null
        return version?.takeIf { it > 0 }
            ?: throw GraphScopeMalformedDocumentException(
                "Malformed GraphScope persistence document: 'schemaVersion' must be a positive integer.",
            )
    }
}

package io.github.fredleonam.graphscope.persistence

import io.github.fredleonam.graphscope.model.Binding
import io.github.fredleonam.graphscope.model.BindingId
import io.github.fredleonam.graphscope.model.BindingKey
import io.github.fredleonam.graphscope.model.Component
import io.github.fredleonam.graphscope.model.ComponentId
import io.github.fredleonam.graphscope.model.DependencyEdge
import io.github.fredleonam.graphscope.model.GraphScopeGraph
import io.github.fredleonam.graphscope.model.Qualifier
import io.github.fredleonam.graphscope.model.Scope
import io.github.fredleonam.graphscope.persistence.internal.MigrationRegistry
import io.github.fredleonam.graphscope.persistence.internal.PersistedBindingKeyV1
import io.github.fredleonam.graphscope.persistence.internal.PersistedBindingV1
import io.github.fredleonam.graphscope.persistence.internal.PersistedComponentV1
import io.github.fredleonam.graphscope.persistence.internal.PersistedEdgeV1
import io.github.fredleonam.graphscope.persistence.internal.PersistedGraphV1
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

/** Encodes and decodes the durable, framework-independent GraphScope JSON format. */
@OptIn(ExperimentalSerializationApi::class)
object GraphScopePersistence {
    const val FORMAT: String = "graphscope"
    const val CURRENT_SCHEMA_VERSION: Int = 1

    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    // Schema v1 is the first persisted format, so there are no production migrations yet.
    private val migrations = MigrationRegistry(emptyList())

    /** Returns deterministic, UTF-8-compatible JSON for [graph]. */
    @JvmStatic
    fun encode(graph: GraphScopeGraph): String =
        json.encodeToString(PersistedGraphV1.serializer(), graph.toPersistedGraph()) + "\n"

    /**
     * Decodes [document], migrates it to the current schema, and validates it through the model.
     *
     * Parsing/version failures and model validation failures are exposed through distinct
     * [GraphScopePersistenceException] subtypes rather than serialization-library exceptions.
     */
    @JvmStatic
    fun decode(document: String): GraphScopeGraph {
        val rawDocument = parseObject(document)
        requireFormat(rawDocument)
        val currentDocument = migrations.migrate(rawDocument, CURRENT_SCHEMA_VERSION)
        val persisted = try {
            json.decodeFromJsonElement<PersistedGraphV1>(currentDocument)
        } catch (error: SerializationException) {
            throw GraphScopeMalformedDocumentException(
                "Malformed GraphScope persistence document: ${error.message}",
                error,
            )
        }

        if (persisted.format != FORMAT) {
            throw GraphScopeMalformedDocumentException(
                "Unsupported GraphScope persistence format '${persisted.format}'; expected '$FORMAT'.",
            )
        }
        if (persisted.schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw GraphScopeUnsupportedSchemaVersionException(
                persisted.schemaVersion,
                CURRENT_SCHEMA_VERSION,
            )
        }

        return try {
            persisted.toGraph()
        } catch (error: IllegalArgumentException) {
            throw GraphScopeInvalidGraphException(
                "Persisted GraphScope graph is structurally invalid: ${error.message}",
                error,
            )
        }
    }

    private fun parseObject(document: String): JsonObject {
        val element = try {
            json.parseToJsonElement(document)
        } catch (error: SerializationException) {
            throw GraphScopeMalformedDocumentException(
                "Malformed GraphScope persistence document: ${error.message}",
                error,
            )
        }
        return element as? JsonObject
            ?: throw GraphScopeMalformedDocumentException(
                "Malformed GraphScope persistence document: expected a JSON object at the root.",
            )
    }

    private fun requireFormat(document: JsonObject) {
        val formatElement = document["format"]
            ?: throw GraphScopeMalformedDocumentException(
                "Malformed GraphScope persistence document: missing required field 'format'.",
            )
        val format = try {
            formatElement.jsonPrimitive.takeIf { it.isString }?.content
        } catch (_: IllegalArgumentException) {
            null
        }
        if (format != FORMAT) {
            throw GraphScopeMalformedDocumentException(
                "Unsupported GraphScope persistence format $formatElement; expected '$FORMAT'.",
            )
        }
    }

    private fun GraphScopeGraph.toPersistedGraph(): PersistedGraphV1 = PersistedGraphV1(
        format = FORMAT,
        schemaVersion = CURRENT_SCHEMA_VERSION,
        components = components.map { component ->
            PersistedComponentV1(
                id = component.id.value,
                name = component.name,
                parentId = component.parentId?.value,
            )
        },
        bindings = bindings.map { binding ->
            PersistedBindingV1(
                id = binding.id.value,
                key = PersistedBindingKeyV1(
                    typeName = binding.key.typeName,
                    qualifier = binding.key.qualifier?.canonicalName,
                ),
                componentId = binding.componentId.value,
                scope = binding.scope?.name,
            )
        },
        edges = edges.map { edge ->
            PersistedEdgeV1(
                dependentBindingId = edge.dependentBindingId.value,
                dependencyBindingId = edge.dependencyBindingId.value,
            )
        },
    )

    private fun PersistedGraphV1.toGraph(): GraphScopeGraph = GraphScopeGraph.create(
        components = components.map { component ->
            Component(
                id = ComponentId(component.id),
                name = component.name,
                parentId = component.parentId?.let(::ComponentId),
            )
        },
        bindings = bindings.map { binding ->
            Binding(
                id = BindingId(binding.id),
                key = BindingKey(
                    typeName = binding.key.typeName,
                    qualifier = binding.key.qualifier?.let(::Qualifier),
                ),
                componentId = ComponentId(binding.componentId),
                scope = binding.scope?.let(::Scope),
            )
        },
        edges = edges.map { edge ->
            DependencyEdge(
                dependentBindingId = BindingId(edge.dependentBindingId),
                dependencyBindingId = BindingId(edge.dependencyBindingId),
            )
        },
    )
}

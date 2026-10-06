package io.github.fredleonam.graphscope.persistence

import io.github.fredleonam.graphscope.model.Binding
import io.github.fredleonam.graphscope.model.BindingId
import io.github.fredleonam.graphscope.model.BindingKey
import io.github.fredleonam.graphscope.model.Component
import io.github.fredleonam.graphscope.model.ComponentId
import io.github.fredleonam.graphscope.model.DependencyEdge
import io.github.fredleonam.graphscope.model.GraphScopeCanonicalFormat
import io.github.fredleonam.graphscope.model.GraphScopeGraph
import io.github.fredleonam.graphscope.model.Qualifier
import io.github.fredleonam.graphscope.model.Scope
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GraphScopePersistenceTest {
    @Test
    fun `round trip preserves the complete graph and canonical fingerprint`() {
        val original = representativeGraph()

        val restored = GraphScopePersistence.decode(GraphScopePersistence.encode(original))

        assertEquals(original, restored)
        assertEquals(original.fingerprint(), restored.fingerprint())

        val child = restored.components.single { it.id == ComponentId("child") }
        assertEquals(ComponentId("root"), child.parentId)

        val repository = restored.binding(BindingId("repository"))!!
        assertEquals(Qualifier("example.Production"), repository.key.qualifier)
        assertEquals(Scope("javax.inject.Singleton"), repository.scope)

        val controller = restored.binding(BindingId("controller"))!!
        assertNull(controller.scope)
        assertEquals(
            DependencyEdge(controller.id, repository.id),
            restored.edges.single(),
            "Dependency direction must remain dependent -> dependency",
        )
    }

    @Test
    fun `equal graphs created in different orders have identical JSON`() {
        val first = representativeGraph()
        val second = GraphScopeGraph.create(
            components = first.components.reversed(),
            bindings = first.bindings.reversed(),
            edges = first.edges.reversed(),
        )

        assertEquals(GraphScopePersistence.encode(first), GraphScopePersistence.encode(second))
    }

    @Test
    fun `document carries its own schema contract independently of canonical identity`() {
        val graph = representativeGraph()
        val document = GraphScopePersistence.encode(graph)

        assertTrue(document.contains("\"schemaVersion\": ${GraphScopePersistence.CURRENT_SCHEMA_VERSION}"))
        assertEquals(GraphScopeCanonicalFormat.VERSION, graph.fingerprint().formatVersion)
    }

    @Test
    fun `save and load use UTF-8 persistence JSON`(@TempDir directory: Path) {
        val graph = representativeGraph()
        val path = directory.resolve("graph.json")

        GraphScopeGraphFiles.save(graph, path)

        assertEquals(GraphScopePersistence.encode(graph), path.readText())
        assertEquals(graph, GraphScopeGraphFiles.load(path))
    }

    @Test
    fun `golden version 1 document remains exact`() {
        val expected = requireNotNull(javaClass.getResource("/graph-v1.json")).readText()

        assertEquals(expected, GraphScopePersistence.encode(goldenGraph()))
        assertEquals(goldenGraph(), GraphScopePersistence.decode(expected))
    }

    @Test
    fun `rejects a binding owned by a missing component`() {
        val document = validMinimalDocument().replace(
            "\"componentId\": \"root\"",
            "\"componentId\": \"missing\"",
        )

        val error = assertFailsWith<GraphScopeInvalidGraphException> {
            GraphScopePersistence.decode(document)
        }

        assertTrue(error.message.orEmpty().contains("unknown component 'missing'"))
    }

    @Test
    fun `rejects an edge to a missing binding`() {
        val document = validMinimalDocument().replace(
            "\"edges\": []",
            """
            "edges": [
              {
                "dependentBindingId": "client",
                "dependencyBindingId": "missing"
              }
            ]
            """.trimIndent(),
        )

        val error = assertFailsWith<GraphScopeInvalidGraphException> {
            GraphScopePersistence.decode(document)
        }

        assertTrue(error.message.orEmpty().contains("unknown dependency binding 'missing'"))
    }

    @Test
    fun `rejects malformed JSON through the persistence error boundary`() {
        val error = assertFailsWith<GraphScopeMalformedDocumentException> {
            GraphScopePersistence.decode("{not-json")
        }

        assertTrue(error.message.orEmpty().startsWith("Malformed GraphScope persistence document"))
    }

    @Test
    fun `rejects missing required fields explicitly`() {
        val document = validMinimalDocument().replace("      \"componentId\": \"root\",\n", "")

        val error = assertFailsWith<GraphScopeMalformedDocumentException> {
            GraphScopePersistence.decode(document)
        }

        assertTrue(error.message.orEmpty().contains("componentId"))
    }

    @Test
    fun `rejects unsupported future schema versions`() {
        val futureVersion = GraphScopePersistence.CURRENT_SCHEMA_VERSION + 1
        val document = validMinimalDocument().replace("\"schemaVersion\": 1", "\"schemaVersion\": $futureVersion")

        val error = assertFailsWith<GraphScopeUnsupportedSchemaVersionException> {
            GraphScopePersistence.decode(document)
        }

        assertEquals(futureVersion, error.schemaVersion)
        assertEquals(GraphScopePersistence.CURRENT_SCHEMA_VERSION, error.currentSchemaVersion)
    }

    @Test
    fun `rejects a missing schema version`() {
        val document = validMinimalDocument().replace("  \"schemaVersion\": 1,\n", "")

        val error = assertFailsWith<GraphScopeMalformedDocumentException> {
            GraphScopePersistence.decode(document)
        }

        assertTrue(error.message.orEmpty().contains("missing required field 'schemaVersion'"))
    }

    @Test
    fun `rejects schema version with an invalid JSON type`() {
        val document = validMinimalDocument().replace("\"schemaVersion\": 1", "\"schemaVersion\": \"1\"")

        assertFailsWith<GraphScopeMalformedDocumentException> {
            GraphScopePersistence.decode(document)
        }
    }

    private fun representativeGraph(): GraphScopeGraph {
        val root = Component(ComponentId("root"), "example.RootComponent")
        val child = Component(ComponentId("child"), "example.ChildComponent", root.id)
        val repository = Binding(
            id = BindingId("repository"),
            key = BindingKey("example.Repository", Qualifier("example.Production")),
            componentId = root.id,
            scope = Scope("javax.inject.Singleton"),
        )
        val controller = Binding(
            id = BindingId("controller"),
            key = BindingKey("example.Controller"),
            componentId = child.id,
        )
        return GraphScopeGraph.create(
            components = listOf(root, child),
            bindings = listOf(repository, controller),
            edges = listOf(DependencyEdge(controller.id, repository.id)),
        )
    }

    private fun goldenGraph(): GraphScopeGraph {
        val root = Component(ComponentId("root"), "example.Root")
        val client = Binding(BindingId("client"), BindingKey("example.Client"), root.id)
        return GraphScopeGraph.create(listOf(root), listOf(client), emptyList())
    }

    private fun validMinimalDocument(): String = GraphScopePersistence.encode(goldenGraph())
}

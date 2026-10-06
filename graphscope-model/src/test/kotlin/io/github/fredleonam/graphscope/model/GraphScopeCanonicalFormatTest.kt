package io.github.fredleonam.graphscope.model

import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class GraphScopeCanonicalFormatTest {
    @Test
    fun `canonical representation has an explicit versioned header`() {
        val encoded = GraphScopeCanonicalFormat.encode(baselineGraph())
        val magic = "GraphScope".toByteArray(Charsets.US_ASCII)

        assertContentEquals(magic, encoded.copyOfRange(0, magic.size))
        assertEquals(
            GraphScopeCanonicalFormat.VERSION,
            ByteBuffer.wrap(encoded, magic.size, Int.SIZE_BYTES).int,
        )
    }

    @Test
    fun `equal graphs have the same bytes and fingerprint regardless of discovery order`() {
        val first = baselineGraph()
        val second = GraphScopeGraph.create(
            components = first.components.reversed(),
            bindings = first.bindings.reversed(),
            edges = first.edges.reversed(),
        )

        assertContentEquals(
            GraphScopeCanonicalFormat.encode(first),
            GraphScopeCanonicalFormat.encode(second),
        )
        assertEquals(first.fingerprint(), second.fingerprint())
    }

    @Test
    fun `fingerprint matches the version 1 golden value`() {
        assertEquals(
            GraphFingerprint(
                formatVersion = 1,
                sha256Hex = "6212762a2eadf7f1d2b16967941c992b87fa00bb211095e09beca0cbe4cda100",
            ),
            baselineGraph().fingerprint(),
        )
    }

    @Test
    fun `fingerprint changes for every meaningful part of the graph`() {
        val baseline = baselineGraph()
        val root = baseline.components.single { it.id == ComponentId("root") }
        val child = baseline.components.single { it.id == ComponentId("child") }
        val repository = baseline.bindings.single { it.id == BindingId("repository") }
        val controller = baseline.bindings.single { it.id == BindingId("controller") }

        val variants = listOf(
            GraphScopeGraph.create(
                listOf(root.copy(name = "RenamedRoot"), child),
                baseline.bindings,
                baseline.edges,
            ),
            GraphScopeGraph.create(
                baseline.components,
                listOf(repository.copy(key = BindingKey("example.OtherRepository")), controller),
                baseline.edges,
            ),
            GraphScopeGraph.create(
                baseline.components,
                listOf(
                    repository.copy(key = repository.key.copy(qualifier = Qualifier("example.Primary"))),
                    controller,
                ),
                baseline.edges,
            ),
            GraphScopeGraph.create(
                baseline.components,
                listOf(repository.copy(scope = Scope("Session")), controller),
                baseline.edges,
            ),
            GraphScopeGraph.create(
                baseline.components,
                listOf(repository.copy(componentId = child.id), controller),
                baseline.edges,
            ),
            GraphScopeGraph.create(
                baseline.components,
                baseline.bindings,
                listOf(DependencyEdge(repository.id, controller.id)),
            ),
        )

        variants.forEach { variant ->
            assertNotEquals(baseline.fingerprint(), variant.fingerprint())
        }
    }

    @Test
    fun `canonical encoding returns an independent snapshot`() {
        val graph = baselineGraph()
        val first = GraphScopeCanonicalFormat.encode(graph)
        val expected = first.copyOf()

        first.fill(0)

        assertContentEquals(expected, GraphScopeCanonicalFormat.encode(graph))
    }

    private fun baselineGraph(): GraphScopeGraph {
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
}

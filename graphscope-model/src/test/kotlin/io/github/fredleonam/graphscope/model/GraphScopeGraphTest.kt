package io.github.fredleonam.graphscope.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GraphScopeGraphTest {
    @Test
    fun `represents a component hierarchy bindings scopes and dependency edges`() {
        val application = Component(ComponentId("application"), "ApplicationComponent")
        val activity = Component(ComponentId("activity"), "ActivityComponent", application.id)
        val viewModel = Binding(BindingId("view-model"), BindingKey("example.CheckoutViewModel"), activity.id)
        val repository = Binding(
            BindingId("repository"),
            BindingKey("example.CheckoutRepository"),
            application.id,
            Scope("Singleton"),
        )
        val paymentApi = Binding(BindingId("payment-api"), BindingKey("example.PaymentApi"), application.id)

        val graph = GraphScopeGraph.create(
            components = listOf(activity, application),
            bindings = listOf(paymentApi, viewModel, repository),
            edges = listOf(
                DependencyEdge(viewModel.id, repository.id),
                DependencyEdge(repository.id, paymentApi.id),
            ),
        )

        assertEquals(listOf(activity, application), graph.components)
        assertEquals(listOf(paymentApi, repository, viewModel), graph.bindings)
        assertEquals(Scope("Singleton"), graph.binding(repository.id)?.scope)
        assertEquals(activity.id, graph.binding(viewModel.id)?.componentId)
        assertEquals(listOf(activity), graph.childrenOf(application.id))
        assertEquals(
            listOf(
                DependencyEdge(repository.id, paymentApi.id),
                DependencyEdge(viewModel.id, repository.id),
            ),
            graph.edges,
        )
    }

    @Test
    fun `represents an unscoped binding with null rather than a sentinel`() {
        val component = Component(ComponentId("root"), "Root")
        val binding = Binding(BindingId("client"), BindingKey("example.Client"), component.id)

        val graph = GraphScopeGraph.create(listOf(component), listOf(binding), emptyList())

        assertNull(graph.binding(binding.id)?.scope)
    }

    @Test
    fun `represents arbitrary framework neutral scopes and qualified keys`() {
        val component = Component(ComponentId("root"), "Root")
        val binding = Binding(
            id = BindingId("session-client"),
            key = BindingKey("example.Client", Qualifier("example.Primary")),
            componentId = component.id,
            scope = Scope("SessionScope"),
        )

        val graph = GraphScopeGraph.create(listOf(component), listOf(binding), emptyList())

        assertEquals(Scope("SessionScope"), graph.bindings.single().scope)
        assertEquals(Qualifier("example.Primary"), graph.bindings.single().key.qualifier)
    }

    @Test
    fun `canonicalizes every collection regardless of discovery order`() {
        val root = Component(ComponentId("root"), "Root")
        val child = Component(ComponentId("child"), "Child", root.id)
        val alpha = Binding(BindingId("alpha"), BindingKey("example.Alpha"), root.id)
        val beta = Binding(BindingId("beta"), BindingKey("example.Beta"), child.id)
        val gamma = Binding(BindingId("gamma"), BindingKey("example.Gamma"), child.id)
        val alphaToBeta = DependencyEdge(alpha.id, beta.id)
        val betaToGamma = DependencyEdge(beta.id, gamma.id)

        val first = GraphScopeGraph.create(
            listOf(root, child),
            listOf(alpha, beta, gamma),
            listOf(alphaToBeta, betaToGamma),
        )
        val second = GraphScopeGraph.create(
            listOf(child, root),
            listOf(gamma, alpha, beta),
            listOf(betaToGamma, alphaToBeta),
        )

        assertEquals(first, second)
        assertEquals(listOf(child, root), second.components)
        assertEquals(listOf(alpha, beta, gamma), second.bindings)
        assertEquals(listOf(alphaToBeta, betaToGamma), second.edges)
    }

    @Test
    fun `rejects duplicate component and binding identifiers`() {
        val component = Component(ComponentId("root"), "Root")

        assertThrows<IllegalArgumentException> {
            GraphScopeGraph.create(listOf(component, component), emptyList(), emptyList())
        }
        assertThrows<IllegalArgumentException> {
            GraphScopeGraph.create(
                listOf(component),
                listOf(
                    Binding(BindingId("binding"), BindingKey("example.One"), component.id),
                    Binding(BindingId("binding"), BindingKey("example.Two"), component.id),
                ),
                emptyList(),
            )
        }
    }

    @Test
    fun `rejects bindings owned by an unknown component`() {
        assertThrows<IllegalArgumentException> {
            GraphScopeGraph.create(
                emptyList(),
                listOf(Binding(BindingId("binding"), BindingKey("example.Type"), ComponentId("missing"))),
                emptyList(),
            )
        }
    }

    @Test
    fun `rejects edges with unknown bindings and duplicate edges`() {
        val component = Component(ComponentId("root"), "Root")
        val binding = Binding(BindingId("binding"), BindingKey("example.Type"), component.id)
        val missingEdge = DependencyEdge(binding.id, BindingId("missing"))
        val duplicateEdge = DependencyEdge(binding.id, binding.id)

        assertThrows<IllegalArgumentException> {
            GraphScopeGraph.create(listOf(component), listOf(binding), listOf(missingEdge))
        }
        assertThrows<IllegalArgumentException> {
            GraphScopeGraph.create(listOf(component), listOf(binding), listOf(duplicateEdge, duplicateEdge))
        }
    }

    @Test
    fun `rejects missing and self component parents`() {
        assertThrows<IllegalArgumentException> {
            GraphScopeGraph.create(
                listOf(Component(ComponentId("child"), "Child", ComponentId("missing"))),
                emptyList(),
                emptyList(),
            )
        }
        assertThrows<IllegalArgumentException> {
            val id = ComponentId("self")
            GraphScopeGraph.create(listOf(Component(id, "Self", id)), emptyList(), emptyList())
        }
    }

    @Test
    fun `rejects component hierarchy cycles`() {
        val first = ComponentId("first")
        val second = ComponentId("second")

        assertThrows<IllegalArgumentException> {
            GraphScopeGraph.create(
                listOf(Component(first, "First", second), Component(second, "Second", first)),
                emptyList(),
                emptyList(),
            )
        }
    }

    @Test
    fun `permits cyclic dependency topology`() {
        val component = Component(ComponentId("root"), "Root")
        val first = Binding(BindingId("first"), BindingKey("example.First"), component.id)
        val second = Binding(BindingId("second"), BindingKey("example.Second"), component.id)

        val graph = GraphScopeGraph.create(
            listOf(component),
            listOf(first, second),
            listOf(DependencyEdge(first.id, second.id), DependencyEdge(second.id, first.id)),
        )

        assertEquals(2, graph.edges.size)
    }

    @Test
    fun `public graph collections are immutable snapshots`() {
        val component = Component(ComponentId("root"), "Root")
        val binding = Binding(BindingId("binding"), BindingKey("example.Type"), component.id)
        val edge = DependencyEdge(binding.id, binding.id)
        val graph = GraphScopeGraph.create(listOf(component), listOf(binding), listOf(edge))

        assertThrows<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (graph.components as MutableList<Component>).add(Component(ComponentId("other"), "Other"))
        }
        assertThrows<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (graph.bindings as MutableList<Binding>).clear()
        }
        assertThrows<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (graph.edges as MutableList<DependencyEdge>).clear()
        }
        assertTrue(graph.components.contains(component))
    }
}

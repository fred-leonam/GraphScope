package io.github.fredleonam.graphscope.model

import java.util.Collections

/** An identifier that is unique within one [GraphScopeGraph]. */
@JvmInline
value class ComponentId(val value: String) : Comparable<ComponentId> {
    init {
        require(value.isNotBlank()) { "Component ID must not be blank." }
    }

    override fun compareTo(other: ComponentId): Int = value.compareTo(other.value)
}

/** An identifier that is unique within one [GraphScopeGraph]. */
@JvmInline
value class BindingId(val value: String) : Comparable<BindingId> {
    init {
        require(value.isNotBlank()) { "Binding ID must not be blank." }
    }

    override fun compareTo(other: BindingId): Int = value.compareTo(other.value)
}

/**
 * A framework-neutral qualifier supplied by an adapter.
 *
 * [canonicalName] is an adapter-defined, deterministic description. It deliberately has no
 * annotation type because GraphScope's model does not depend on an injection annotation API.
 */
@JvmInline
value class Qualifier(val canonicalName: String) : Comparable<Qualifier> {
    init {
        require(canonicalName.isNotBlank()) { "Qualifier name must not be blank." }
    }

    override fun compareTo(other: Qualifier): Int = canonicalName.compareTo(other.canonicalName)
}

/** A framework-neutral dependency key: a represented type and, optionally, a qualifier. */
data class BindingKey(
    val typeName: String,
    val qualifier: Qualifier? = null,
) : Comparable<BindingKey> {
    init {
        require(typeName.isNotBlank()) { "Binding key type name must not be blank." }
    }

    override fun compareTo(other: BindingKey): Int =
        compareValuesBy(this, other, BindingKey::typeName, { it.qualifier?.canonicalName })
}

/** A framework-neutral scope name supplied by an adapter or another graph producer. */
@JvmInline
value class Scope(val name: String) : Comparable<Scope> {
    init {
        require(name.isNotBlank()) { "Scope name must not be blank." }
    }

    override fun compareTo(other: Scope): Int = name.compareTo(other.name)
}

/** A dependency-injection container. [parentId] describes its optional containing component. */
data class Component(
    val id: ComponentId,
    val name: String,
    val parentId: ComponentId? = null,
) {
    init {
        require(name.isNotBlank()) { "Component name must not be blank." }
    }
}

/** A binding owned by [componentId]. A null [scope] explicitly represents an unscoped binding. */
data class Binding(
    val id: BindingId,
    val key: BindingKey,
    val componentId: ComponentId,
    val scope: Scope? = null,
)

/**
 * A directed dependency relationship between bindings.
 *
 * `dependentBindingId -> dependencyBindingId` means the dependent binding requires the dependency
 * binding. Dependency cycles are representable because topology is analysis data, not a model
 * construction error.
 */
data class DependencyEdge(
    val dependentBindingId: BindingId,
    val dependencyBindingId: BindingId,
)

/**
 * A validated, canonically ordered dependency-injection graph.
 *
 * Use [create] to construct a graph. It snapshots inputs, validates structural references, and
 * sorts components and bindings by ID and edges by dependent then dependency ID. The exposed lists
 * are Java unmodifiable snapshots, so callers cannot mutate a graph after construction.
 */
class GraphScopeGraph private constructor(
    components: List<Component>,
    bindings: List<Binding>,
    edges: List<DependencyEdge>,
) {
    val components: List<Component> = immutableList(components)
    val bindings: List<Binding> = immutableList(bindings)
    val edges: List<DependencyEdge> = immutableList(edges)

    private val componentById: Map<ComponentId, Component> = components.associateBy(Component::id)
    private val bindingById: Map<BindingId, Binding> = bindings.associateBy(Binding::id)

    fun component(id: ComponentId): Component? = componentById[id]

    fun binding(id: BindingId): Binding? = bindingById[id]

    fun childrenOf(componentId: ComponentId): List<Component> =
        immutableList(components.filter { it.parentId == componentId })

    override fun equals(other: Any?): Boolean =
        other is GraphScopeGraph &&
            components == other.components &&
            bindings == other.bindings &&
            edges == other.edges

    override fun hashCode(): Int = 31 * (31 * components.hashCode() + bindings.hashCode()) + edges.hashCode()

    override fun toString(): String =
        "GraphScopeGraph(components=$components, bindings=$bindings, edges=$edges)"

    companion object {
        fun create(
            components: Iterable<Component>,
            bindings: Iterable<Binding>,
            edges: Iterable<DependencyEdge>,
        ): GraphScopeGraph {
            val componentSnapshot = components.toList()
            val bindingSnapshot = bindings.toList()
            val edgeSnapshot = edges.toList()

            requireNoDuplicate(componentSnapshot, Component::id, "component IDs")
            requireNoDuplicate(bindingSnapshot, Binding::id, "binding IDs")
            requireNoDuplicate(edgeSnapshot, { it }, "dependency edges")

            val componentIds = componentSnapshot.mapTo(mutableSetOf(), Component::id)
            componentSnapshot.forEach { component ->
                require(component.parentId == null || component.parentId in componentIds) {
                    "Component '${component.id.value}' references unknown parent '${component.parentId?.value}'."
                }
                require(component.parentId != component.id) {
                    "Component '${component.id.value}' cannot be its own parent."
                }
            }
            requireAcyclicComponentHierarchy(componentSnapshot.associateBy(Component::id))

            bindingSnapshot.forEach { binding ->
                require(binding.componentId in componentIds) {
                    "Binding '${binding.id.value}' references unknown component '${binding.componentId.value}'."
                }
            }

            val bindingIds = bindingSnapshot.mapTo(mutableSetOf(), Binding::id)
            edgeSnapshot.forEach { edge ->
                require(edge.dependentBindingId in bindingIds) {
                    "Dependency edge references unknown dependent binding '${edge.dependentBindingId.value}'."
                }
                require(edge.dependencyBindingId in bindingIds) {
                    "Dependency edge references unknown dependency binding '${edge.dependencyBindingId.value}'."
                }
            }

            return GraphScopeGraph(
                components = componentSnapshot.sortedBy { it.id },
                bindings = bindingSnapshot.sortedBy { it.id },
                edges = edgeSnapshot.sortedWith(
                    compareBy(DependencyEdge::dependentBindingId, DependencyEdge::dependencyBindingId),
                ),
            )
        }

        private fun requireAcyclicComponentHierarchy(components: Map<ComponentId, Component>) {
            components.keys.forEach { start ->
                val ancestors = mutableSetOf<ComponentId>()
                var current: ComponentId? = start
                while (current != null) {
                    val visiting = current
                    require(ancestors.add(visiting)) {
                        "Component hierarchy contains a cycle at '${visiting.value}'."
                    }
                    current = components.getValue(visiting).parentId
                }
            }
        }

        private fun <T, K> requireNoDuplicate(values: List<T>, key: (T) -> K, description: String) {
            require(values.map(key).toSet().size == values.size) { "Duplicate $description are not allowed." }
        }

        private fun <T> immutableList(values: List<T>): List<T> =
            Collections.unmodifiableList(ArrayList(values))
    }
}

package io.github.fredleonam.graphscope.dagger

import dagger.spi.model.Binding
import dagger.spi.model.BindingGraph
import dagger.spi.model.ComponentPath
import dagger.spi.model.DaggerAnnotation
import dagger.spi.model.DaggerProcessingEnv
import dagger.spi.model.DaggerType
import dagger.spi.model.DaggerTypeElement
import io.github.fredleonam.graphscope.model.Binding as GraphScopeBinding
import io.github.fredleonam.graphscope.model.BindingId
import io.github.fredleonam.graphscope.model.BindingKey
import io.github.fredleonam.graphscope.model.Component
import io.github.fredleonam.graphscope.model.ComponentId
import io.github.fredleonam.graphscope.model.DependencyEdge
import io.github.fredleonam.graphscope.model.GraphScopeGraph
import io.github.fredleonam.graphscope.model.Qualifier
import io.github.fredleonam.graphscope.model.Scope

/** Converts Dagger's compile-time binding graph into GraphScope's framework-neutral model. */
object DaggerGraphExtractor {
    /**
     * Extracts components, owned bindings, scopes, qualifiers, and binding-to-binding dependencies.
     *
     * Component entry-point edges are omitted because [GraphScopeGraph] represents dependencies
     * between bindings. Parallel Dagger dependency requests between the same bindings collapse to
     * one structural edge.
     */
    @JvmStatic
    fun extract(graph: BindingGraph): GraphScopeGraph {
        val componentIds = graph.componentNodes().associate { node ->
            node.componentPath() to componentId(node.componentPath())
        }
        val bindingIds = graph.bindings().associateWith(::bindingId)

        val components = graph.componentNodes().map { node ->
            val path = node.componentPath()
            Component(
                id = componentIds.getValue(path),
                name = path.currentComponent().canonicalName(),
                parentId = if (path.atRoot()) null else componentIds.getValue(path.parent()),
            )
        }
        val bindings = graph.bindings().map { binding ->
            GraphScopeBinding(
                id = bindingIds.getValue(binding),
                key = BindingKey(
                    typeName = binding.key().type().canonicalName(),
                    qualifier = binding.key().qualifier().map { Qualifier(it.canonicalName()) }.orElse(null),
                ),
                componentId = componentIds.getValue(binding.componentPath()),
                scope = binding.scope().map { Scope(it.scopeAnnotation().canonicalName()) }.orElse(null),
            )
        }
        val edges = graph.dependencyEdges().mapNotNull { edge ->
            val endpoints = graph.network().incidentNodes(edge)
            val dependent = endpoints.source() as? Binding ?: return@mapNotNull null
            val dependency = endpoints.target() as? Binding ?: return@mapNotNull null
            DependencyEdge(
                dependentBindingId = bindingIds.getValue(dependent),
                dependencyBindingId = bindingIds.getValue(dependency),
            )
        }.distinct()

        return GraphScopeGraph.create(components, bindings, edges)
    }

    private fun componentId(path: ComponentPath): ComponentId =
        ComponentId(path.components().joinToString(" -> ") { it.canonicalName() })

    private fun bindingId(binding: Binding): BindingId =
        BindingId("${componentId(binding.componentPath()).value} | ${binding.key()}")

    private fun DaggerTypeElement.canonicalName(): String =
        when (backend()) {
            DaggerProcessingEnv.Backend.JAVAC -> javac().qualifiedName.toString()
            DaggerProcessingEnv.Backend.KSP ->
                requireNotNull(ksp().qualifiedName?.asString()) { "Dagger type element has no qualified name: ${ksp()}" }
        }

    private fun DaggerType.canonicalName(): String =
        when (backend()) {
            DaggerProcessingEnv.Backend.JAVAC -> javac().toString()
            DaggerProcessingEnv.Backend.KSP -> ksp().toString()
        }

    private fun DaggerAnnotation.canonicalName(): String =
        when (backend()) {
            DaggerProcessingEnv.Backend.JAVAC -> javac().toString()
            DaggerProcessingEnv.Backend.KSP -> ksp().toString()
        }
}

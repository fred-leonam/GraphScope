package io.github.fredleonam.graphscope.dagger

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import dagger.internal.codegen.ComponentProcessor
import io.github.fredleonam.graphscope.model.Binding
import io.github.fredleonam.graphscope.model.GraphScopeGraph
import javax.tools.JavaFileObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DaggerGraphExtractorIntegrationTest {
    @Test
    fun `extracts a real Dagger component hierarchy and dependency topology`() {
        val source = JavaFileObjects.forSourceString(
            "example.TestGraph",
            """
            package example;

            import dagger.Component;
            import dagger.Module;
            import dagger.Provides;
            import dagger.Subcomponent;
            import java.lang.annotation.Retention;
            import javax.inject.Inject;
            import javax.inject.Qualifier;
            import javax.inject.Singleton;

            import static java.lang.annotation.RetentionPolicy.RUNTIME;

            @Qualifier
            @Retention(RUNTIME)
            @interface Primary {}

            final class Api {}

            @Singleton
            final class Repository {
              @Inject Repository(@Primary Api api) {}
            }

            final class Controller {
              @Inject Controller(Repository repository) {}
            }

            @Module(subcomponents = ChildComponent.class)
            final class RootModule {
              @Provides @Primary static Api api() { return new Api(); }
            }

            @Subcomponent
            interface ChildComponent {
              Controller controller();

              @Subcomponent.Factory
              interface Factory {
                ChildComponent create();
              }
            }

            @Singleton
            @Component(modules = RootModule.class)
            interface RootComponent {
              ChildComponent.Factory childFactory();
            }
            """.trimIndent(),
        )

        val graph = compileAndExtract(source)

        val root = graph.components.single { it.name == "example.RootComponent" }
        val child = graph.components.single { it.name == "example.ChildComponent" }
        assertEquals(root.id, child.parentId)

        val api = graph.bindingWithType("example.Api")
        val repository = graph.bindingWithType("example.Repository")
        val controller = graph.bindingWithType("example.Controller")

        assertTrue(api.key.qualifier?.canonicalName.orEmpty().contains("example.Primary"))
        assertTrue(repository.scope?.name.orEmpty().contains("javax.inject.Singleton"))
        assertEquals(root.id, repository.componentId)
        assertEquals(child.id, controller.componentId)
        assertTrue(graph.edges.any { it.dependentBindingId == repository.id && it.dependencyBindingId == api.id })
        assertTrue(graph.edges.any { it.dependentBindingId == controller.id && it.dependencyBindingId == repository.id })

        assertEquals(graph, compileAndExtract(source), "Extraction must be stable across compilations")
    }

    private fun compileAndExtract(source: JavaFileObject): GraphScopeGraph {
        RecordingBindingGraphPlugin.extractedGraph = null
        val compilation = Compiler.javac()
            .withProcessors(ComponentProcessor())
            .compile(source)

        assertEquals(Compilation.Status.SUCCESS, compilation.status(), compilation.errors().joinToString("\n"))
        return assertNotNull(RecordingBindingGraphPlugin.extractedGraph)
    }

    private fun GraphScopeGraph.bindingWithType(typeName: String): Binding =
        bindings.single { it.key.typeName == typeName }
}

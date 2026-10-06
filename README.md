# GraphScope

GraphScope is building a framework-independent view of dependency-injection topology.

## Current status

The `graphscope-model` module implements the canonical DI graph model. It represents:

- components and their parent/child hierarchy;
- bindings, their owning components, and dependency keys;
- scoped bindings with arbitrary framework-neutral scope names, plus explicitly unscoped bindings;
- directed dependency edges, where `dependent -> dependency` means the dependent binding requires the dependency;
- deterministic canonical ordering and structural validation of graph references.

The model has no dependency on Dagger, Hilt, injection annotation APIs, Android, or compiler APIs.

The `graphscope-dagger` module translates Dagger's compile-time SPI graph into that model. It extracts component hierarchy, binding ownership, keys, qualifiers, scopes, and binding dependency edges. Component and binding identifiers are deterministic across compilations of the same graph. Since Hilt builds its components with Dagger, the same SPI boundary is suitable for Hilt graphs.

```
Dagger/Hilt
    ↓ graphscope-dagger
Canonical GraphScope model
```

Use the extractor inside a Dagger `BindingGraphPlugin`:

```kotlin
override fun visitGraph(
    bindingGraph: dagger.spi.model.BindingGraph,
    diagnosticReporter: dagger.spi.model.DiagnosticReporter,
) {
    val graph = DaggerGraphExtractor.extract(bindingGraph)
    // Analyze or hand the canonical graph to another GraphScope module.
}
```

Register that plugin on the annotation processor path as described by the Dagger SPI. The adapter uses Dagger 2.60.1's current SPI, which Dagger marks experimental.

Stable identity hashing, persistence, analysis, diffing, telemetry, and visualization remain future milestones.

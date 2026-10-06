# GraphScope

GraphScope is building a framework-independent view of dependency-injection topology.

## Current status

GraphScope currently implements four foundations:

1. **Canonical DI model.** The `graphscope-model` module represents:

- components and their parent/child hierarchy;
- bindings, their owning components, and dependency keys;
- scoped bindings with arbitrary framework-neutral scope names, plus explicitly unscoped bindings;
- directed dependency edges, where `dependent -> dependency` means the dependent binding requires the dependency;
- deterministic canonical ordering and structural validation of graph references.

2. **Stable canonical fingerprint.** `GraphScopeCanonicalFormat` defines a versioned deterministic
binary representation used by `GraphFingerprint`. Equal graphs have the same SHA-256 identity across
processes and machines. This identity format is deliberately separate from persisted JSON.

3. **Dagger BindingGraph extraction.** The `graphscope-dagger` module translates Dagger's
compile-time SPI graph into the canonical model. It extracts component hierarchy, binding ownership,
keys, qualifiers, scopes, and dependency edges. Component and binding identifiers are deterministic
across compilations of the same graph. Since Hilt builds its components with Dagger, the same SPI
boundary is suitable for Hilt graphs.

4. **Graph persistence.** The `graphscope-persistence` module maps `GraphScopeGraph` to and from a
deterministic, human-inspectable JSON document. Persistence schema version `1` contains an explicit
`format: "graphscope"` marker, `schemaVersion`, components, bindings, and directed edges. Its DTOs and
JSON dependency remain outside `graphscope-model`.

The model has no dependency on Dagger, Hilt, injection annotation APIs, Android, compiler APIs, or a
serialization library. The persistence module likewise has no Dagger or Hilt dependency.

```
Dagger/Hilt
    ↓ graphscope-dagger
GraphScopeGraph
    ↓ graphscope-persistence
Versioned JSON artifact
```

Use the extractor inside a Dagger `BindingGraphPlugin`:

```kotlin
override fun visitGraph(
    bindingGraph: dagger.spi.model.BindingGraph,
    diagnosticReporter: dagger.spi.model.DiagnosticReporter,
) {
    val graph = DaggerGraphExtractor.extract(bindingGraph)
    val document = GraphScopePersistence.encode(graph)
}
```

Register that plugin on the annotation processor path as described by the Dagger SPI. The adapter uses Dagger 2.60.1's current SPI, which Dagger marks experimental.

A persisted graph can be restored without Dagger or Hilt on the classpath:

```kotlin
val restored = GraphScopePersistence.decode(document)

GraphScopeGraphFiles.save(restored, path)
val loaded = GraphScopeGraphFiles.load(path)
```

Persistence schema versions are explicit and independent of `GraphScopeCanonicalFormat.VERSION`.
Unknown future versions and documents without a version are rejected. Before current-schema DTO
decoding and canonical model validation, documents pass through an explicit ordered migration
boundary; schema v1 is the first real schema, so no historical production migration is registered.

The next milestone is the Graph Analysis Engine. CLI tooling, graph diffing, telemetry, and
visualization remain future work.

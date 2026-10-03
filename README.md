# GraphScope

GraphScope is building a framework-independent view of dependency-injection topology.

## Current status

The `graphscope-model` module implements the canonical DI graph model. It represents:

- components and their parent/child hierarchy;
- bindings, their owning components, and dependency keys;
- scoped bindings with arbitrary framework-neutral scope names, plus explicitly unscoped bindings;
- directed dependency edges, where `dependent -> dependency` means the dependent binding requires the dependency;
- deterministic canonical ordering and structural validation of graph references.

The model has no dependency on Dagger, Hilt, injection annotation APIs, Android, or compiler APIs. A future adapter will translate a source framework into this boundary:

```
Dagger/Hilt
    ↓ future adapter
Canonical GraphScope model
```

Dagger/Hilt extraction is not implemented yet. Stable graph or binding identity hashing, persistence, analysis, diffing, telemetry, and visualization are also future milestones.

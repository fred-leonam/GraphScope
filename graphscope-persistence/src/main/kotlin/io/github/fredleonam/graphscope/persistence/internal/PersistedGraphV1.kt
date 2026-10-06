package io.github.fredleonam.graphscope.persistence.internal

import kotlinx.serialization.Serializable

@Serializable
internal data class PersistedGraphV1(
    val format: String,
    val schemaVersion: Int,
    val components: List<PersistedComponentV1>,
    val bindings: List<PersistedBindingV1>,
    val edges: List<PersistedEdgeV1>,
)

@Serializable
internal data class PersistedComponentV1(
    val id: String,
    val name: String,
    val parentId: String?,
)

@Serializable
internal data class PersistedBindingV1(
    val id: String,
    val key: PersistedBindingKeyV1,
    val componentId: String,
    val scope: String?,
)

@Serializable
internal data class PersistedBindingKeyV1(
    val typeName: String,
    val qualifier: String?,
)

@Serializable
internal data class PersistedEdgeV1(
    val dependentBindingId: String,
    val dependencyBindingId: String,
)

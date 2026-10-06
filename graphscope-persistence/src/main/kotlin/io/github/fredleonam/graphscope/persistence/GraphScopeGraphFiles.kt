package io.github.fredleonam.graphscope.persistence

import io.github.fredleonam.graphscope.model.GraphScopeGraph
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** Narrow filesystem helpers kept separate from JSON encoding and decoding policy. */
object GraphScopeGraphFiles {
    @JvmStatic
    fun save(graph: GraphScopeGraph, path: Path) {
        Files.writeString(path, GraphScopePersistence.encode(graph), StandardCharsets.UTF_8)
    }

    @JvmStatic
    fun load(path: Path): GraphScopeGraph =
        GraphScopePersistence.decode(Files.readString(path, StandardCharsets.UTF_8))
}

package io.github.fredleonam.graphscope.persistence

import io.github.fredleonam.graphscope.model.GraphScopeGraph

/** Base error reported while interpreting a GraphScope persistence document. */
sealed class GraphScopePersistenceException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

/** The input is not a syntactically valid GraphScope persistence document. */
class GraphScopeMalformedDocumentException(
    message: String,
    cause: Throwable? = null,
) : GraphScopePersistenceException(message, cause)

/** The document uses a persistence schema version this build cannot read. */
class GraphScopeUnsupportedSchemaVersionException(
    val schemaVersion: Int,
    val currentSchemaVersion: Int,
) : GraphScopePersistenceException(
    "Unsupported GraphScope persistence schema version $schemaVersion; " +
        "current version is $currentSchemaVersion.",
)

/** A registered persistence schema migration could not transform its input. */
class GraphScopeMigrationException(
    message: String,
    cause: Throwable? = null,
) : GraphScopePersistenceException(message, cause)

/** The document parsed successfully but does not describe a valid [GraphScopeGraph]. */
class GraphScopeInvalidGraphException(
    message: String,
    cause: Throwable? = null,
) : GraphScopePersistenceException(message, cause)

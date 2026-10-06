package io.github.fredleonam.graphscope.dagger

import dagger.spi.model.BindingGraph
import dagger.spi.model.BindingGraphPlugin
import dagger.spi.model.DiagnosticReporter
import io.github.fredleonam.graphscope.model.GraphScopeGraph

class RecordingBindingGraphPlugin : BindingGraphPlugin {
    override fun visitGraph(bindingGraph: BindingGraph, diagnosticReporter: DiagnosticReporter) {
        extractedGraph = DaggerGraphExtractor.extract(bindingGraph)
    }

    companion object {
        @Volatile
        var extractedGraph: GraphScopeGraph? = null
    }
}

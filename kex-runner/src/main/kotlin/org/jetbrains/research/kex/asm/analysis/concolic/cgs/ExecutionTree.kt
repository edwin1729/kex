package org.jetbrains.research.kex.asm.analysis.concolic.cgs

import org.jetbrains.research.kex.state.BasicState
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.trace.symbolic.Clause
import org.jetbrains.research.kex.trace.symbolic.PathCondition
import org.jetbrains.research.kex.trace.symbolic.SymbolicState
import org.jetbrains.research.kthelper.collection.queueOf
import org.jetbrains.research.kthelper.graph.*


sealed class Vertex : PredecessorGraph.PredecessorVertex<Vertex> {
    val upEdges = mutableSetOf<Vertex>()
    val downEdges = mutableSetOf<Vertex>()
    val states = mutableMapOf<PathCondition, SymbolicState>()

    override val predecessors: Set<Vertex>
        get() = upEdges

    override val successors: Set<Vertex>
        get() = downEdges

    operator fun set(path: PathCondition, state: SymbolicState) {
        states[path] = state
    }

    fun addUpEdge(vertex: Vertex) {
        upEdges += vertex
    }

    fun addDownEdge(vertex: Vertex) {
        downEdges += vertex
    }
}

class ClauseVertex(val clause: Clause) : Vertex() {
    override fun toString() = "${clause.predicate}"
}

data class PathVertex(val clause: Clause) : Vertex() {
    override fun toString() = "${clause.predicate}"
}

//sealed class Edge {
//    private val vertices = mutableListOf<Vertex?>(null, null)
//
//    var entry: Vertex
//        get() = vertices.first()!!
//        set(value) {
//            vertices[0] = value
//        }
//    var exit
//        get() = vertices.last()!!
//        set(value) {
//            vertices[1] = value
//        }
//}
//
//class StraightEdge : Edge() {
//    override fun toString() = ""
//
//    override fun hashCode(): Int {
//        return javaClass.hashCode()
//    }
//
//    override fun equals(other: Any?): Boolean {
//        if (this === other) return true
//        if (other !is StraightEdge) return false
//        return true
//    }
//}

//data class PathEdge(val clause: Clause) : Edge() {
//    val traces = mutableMapOf<PathCondition, SymbolicState>()
//
//    operator fun set(path: PathCondition, state: SymbolicState) {
//        traces[path] = state
//    }
//
//    override fun toString() = "${clause.predicate}"
//}

data class Context(
    val context: List<PathVertex>,
    val fullPath: PathCondition,
    val symbolicState: SymbolicState
) {
    val condition get() = context.last()
    val size get() = context.size
}

class ExecutionTree : PredecessorGraph<Vertex>, Viewable {
    private val _nodes = mutableMapOf<Clause, Vertex>()
    private var _root: Vertex? = null
    private var dominators: DominatorTree<Vertex>? = null
    private val edges = mutableMapOf<Clause, PathVertex>()

    val hasEntry get() = _root != null

    override val entry get() = _root!!
    override val nodes: Set<Vertex>
        get() = _nodes.values.toSet()
    var depth: Int = 0
        private set

    fun getBranches(depth: Int): Set<PathVertex> = getBranchDepths().filter { it.value == depth }.keys

    fun addTrace(symbolicState: SymbolicState) {
        var prevVertex: Vertex? = null

        var currentDepth = 0
        for (predicate in (symbolicState.state as BasicState)) {
            val current = Clause(symbolicState[predicate], predicate)
            val currentVertex = _nodes.getOrPut(current) {
                when (predicate.type) {
                    is PredicateType.Path -> PathVertex(current).also {
                        ++currentDepth
                        it[symbolicState.path.subPath(current)] = symbolicState
                        edges[current] = it
                    }
                    else -> ClauseVertex(current).also {
                        if (_root == null) {
                            _root = it
                        }
                    }
                }
            }

            prevVertex?.let { prev ->
                prev.addDownEdge(currentVertex)
                currentVertex.addUpEdge(prev)
            }

            prevVertex = currentVertex
        }

        if (currentDepth > depth) depth = currentDepth
        dominators = DominatorTreeBuilder(this).build()
    }

    fun Vertex.dominates(other: Vertex) = dominators?.let { tree ->
        tree[this]?.dominates(other)
    } ?: false

    fun contexts(pathVertex: PathVertex, k: Int): List<Context> = pathVertex.states.map { (path, state) ->
        Context(path.reversed().map { edges[it]!! }.filter { !it.dominates(pathVertex) }.take(k), path, state)
    }

    private fun getBranchDepths(): Map<PathVertex, Int> {
        if (!hasEntry) return emptyMap()

        val search = mutableMapOf<PathVertex, Int>()
        val visited = mutableSetOf<Vertex>()
        val queue = queueOf<Pair<Vertex, Int>>()
        queue.add(entry to 1)
        while (queue.isNotEmpty()) {
            val (top, depth) = queue.poll()
            if (top !in visited) {
                visited += top
                for (vertex in top.downEdges.filterIsInstance<PathVertex>()) {
                    search.merge(vertex, depth, ::minOf)
                }
                top.downEdges.filter { it !in visited }.forEach {
                    val newDepth = if (it is PathVertex) depth + 1 else depth
                    queue.add(it to newDepth)
                }
            }
        }
        return search
    }

    override val graphView: List<GraphView>
        get() {
            val graphNodes = mutableMapOf<Vertex, GraphView>()
            val depths = getBranchDepths()

            for (vertex in nodes) {
                graphNodes[vertex] = GraphView("$vertex", "$vertex")
            }

            for (vertex in nodes) {
                val current = graphNodes.getValue(vertex)
                for (child in vertex.downEdges) {
                    val suffix = depths[child]?.let { " - $it" } ?: ""
                    current.addSuccessor(graphNodes.getValue(child), "$child$suffix")
                }
            }

            return graphNodes.values.toList()
        }
}
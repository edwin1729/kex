package org.jetbrains.research.kex.state.predicate

import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kfg.ir.Location

class ThrowPredicate(throwable: Term, type: PredicateType = PredicateType.State(), location: Location = Location()) :
        Predicate(type, location, listOf(throwable)) {

    val throwable: Term
        get() = operands[0]

    override fun print() = "throw $throwable"

    override fun <T: Transformer<T>> accept(t: Transformer<T>): Predicate {
        val tthrowable = t.transform(throwable)
        return when {
            tthrowable == throwable -> this
            else -> t.pf.getThrow(throwable, type)
        }
    }
}
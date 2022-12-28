package org.vorpal.research.kex.symbolic

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.junit.Test

@ExperimentalSerializationApi
@InternalSerializationApi
@DelicateCoroutinesApi
class PrimitiveSymbolicLongTest : SymbolicTest() {
    @Test
    fun primitiveConcolicTest() {
        assertCoverage(cm["org/vorpal/research/kex/test/concolic/PrimitiveConcolicTests"], 1.0)
    }
}

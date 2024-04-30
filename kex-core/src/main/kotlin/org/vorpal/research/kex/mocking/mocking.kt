package org.vorpal.research.kex.mocking

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.DescriptorContext
import org.vorpal.research.kex.descriptor.MockDescriptor
import org.vorpal.research.kex.descriptor.any
import org.vorpal.research.kex.descriptor.transform
import org.vorpal.research.kex.parameters.map
import org.vorpal.research.kex.state.predicate.CallPredicate
import org.vorpal.research.kex.state.term.CallTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.trace.symbolic.SymbolicState
import org.vorpal.research.kex.util.isMockingEnabled
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.type.ClassType
import org.vorpal.research.kfg.type.TypeFactory
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.logging.warn

fun Descriptor.isRequireMocks(
    mockMaker: MockMaker,
    expectedClass: Map<Descriptor, Class>,
    visited: MutableSet<Descriptor> = mutableSetOf()
): Boolean =
    any(visited) { descriptor -> mockMaker.canMock(descriptor, expectedClass[descriptor]) }

fun createDescriptorToMock(
    allDescriptors: Iterable<Descriptor>,
    mockMaker: MockMaker,
    expectedClasses: Map<Descriptor, Class>
): Map<Descriptor, MockDescriptor> {
    val descriptorToMock = mutableMapOf<Descriptor, MockDescriptor?>()
    allDescriptors.forEach {
        it.transform(descriptorToMock) { descriptor ->
            mockMaker.mockOrNull(descriptor, expectedClasses[descriptor])
        }
    }

    return descriptorToMock.filterValues { value -> value != null }.mapValues { (_, v) -> v!! }
}

fun setupMocks(
    types: TypeFactory,
    methodCalls: List<CallPredicate>,
    termToDescriptor: Map<Term, Descriptor>,
    descriptorToMock: Map<Descriptor, MockDescriptor>,
) {
    for (callPredicate in methodCalls) {
        if (!callPredicate.hasLhv) continue
        val call = callPredicate.call as CallTerm
        if (call.method.canMock(types).not()) continue

        val mock =
            termToDescriptor[call.owner]?.let { descriptorToMock[it] ?: it } as? MockDescriptor
        val value = termToDescriptor[callPredicate.lhvUnsafe]?.let { descriptorToMock[it] ?: it }
        mock ?: log.warn { "No mock for $call" }

        if (mock is MockDescriptor && value != null) {
            mock.addReturnValue(call.method, value)
        }
    }
}

fun DescriptorContext.performMocking(
    ctx: ExecutionContext,
    state: SymbolicState,
    method: Method
): DescriptorContext {
    if (!kexConfig.isMockingEnabled) {
        return this
    }

    val expectedClasses = method.argTypes
        .map { (it as? ClassType)?.klass }
        .zip(parameters.arguments)
        .filter { (klass, _) -> klass != null }
        .associate { (klass, descriptor) -> descriptor to klass!! }

    val mockMaker = kexConfig.getMockMaker(ctx).filterNot { desc -> desc == parameters.instance }
    if (!kexConfig.isExpectMocks) {
        val visited = mutableSetOf<Descriptor>()
        if (parameters.asList.none { it.isRequireMocks(mockMaker, expectedClasses, visited) }) {
            return this
        }
    }
    generateAll()
    val visited = mutableSetOf<Descriptor>()
    if (allDescriptors.none { it.isRequireMocks(mockMaker, expectedClasses, visited) }) {
        return this
    }

    return transform {
        val descriptorToMock = createDescriptorToMock(allDescriptors, mockMaker, expectedClasses)
        val withMocks = parameters.map { descriptor -> descriptorToMock[descriptor] ?: descriptor }
        val methodCalls = state.methodCalls()
        setupMocks(ctx.types, methodCalls, termToDescriptor, descriptorToMock)
        withMocks
    }
}

fun DescriptorContext.withoutMocksOrNull(
    ctx: ExecutionContext,
    method: Method
): DescriptorContext? {
    if (!kexConfig.isMockingEnabled) {
        return this
    }

    val expectedClasses = method.argTypes
        .map { (it as? ClassType)?.klass }
        .zip(parameters.arguments)
        .filter { (klass, _) -> klass != null }
        .associate { (klass, descriptor) -> descriptor to klass!! }

    val mockMaker = kexConfig.getMockMaker(ctx).filterNot { desc -> desc == parameters.instance }
    if (!kexConfig.isExpectMocks) {
        val visited = mutableSetOf<Descriptor>()
        if (parameters.asList.none { it.isRequireMocks(mockMaker, expectedClasses, visited) }) {
            return this
        }
    }
    generateAll()
    val visited = mutableSetOf<Descriptor>()
    return when {
        allDescriptors.none { it.isRequireMocks(mockMaker, expectedClasses, visited) } -> this
        else -> null
    }
}

private fun SymbolicState.methodCalls(): List<CallPredicate> {
    return clauses.map { clause -> clause.predicate }.filterIsInstance<CallPredicate>()
}

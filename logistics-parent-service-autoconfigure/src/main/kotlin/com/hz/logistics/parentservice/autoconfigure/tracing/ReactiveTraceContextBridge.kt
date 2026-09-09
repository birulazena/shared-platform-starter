package com.hz.logistics.parentservice.autoconfigure.tracing

import reactor.core.publisher.Hooks

/**
 * Enables Reactor's supported automatic context propagation. Reactor captures
 * and restores registered context accessors around scheduler boundaries, rather
 * than copying a thread-local trace into an unrelated execution.
 */
class ReactiveTraceContextBridge {

    init {
        Hooks.enableAutomaticContextPropagation()
    }
}

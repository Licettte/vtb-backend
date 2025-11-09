package org.elly.app.web

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.security.core.context.ReactiveSecurityContextHolder

suspend fun currentUserId(): Long =
    ReactiveSecurityContextHolder.getContext().awaitSingle().authentication.principal as Long


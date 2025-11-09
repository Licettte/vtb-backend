package org.elly.app.web

import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@RestController
@RequestMapping("/api/onboarding")
class OnboardingSseController {

    /** На jobId держим мультикаст-стрим, чтобы несколько вкладок/клиентов могли подписаться */
    private val sinks = ConcurrentHashMap<String, Sinks.Many<ServerSentEvent<String>>>()

    /** Подписка клиента на события конкретного jobId */
    @GetMapping("/{jobId}/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun events(@PathVariable jobId: String): Flux<ServerSentEvent<String>> {
        val sink = sinks.computeIfAbsent(jobId) { Sinks.many().multicast().onBackpressureBuffer() }

        // heartbeat раз в 15 сек — держит соединение живым за прокси/CDN
        val heartbeats = Flux.interval(Duration.ofSeconds(15))
            .map {
                ServerSentEvent.builder<String>()
                    .event("heartbeat")
                    .data("💓")
                    .build()
            }

        return sink.asFlux().mergeWith(heartbeats)
    }

    /** Публикация события в поток конкретного job */
    fun emit(jobId: String, event: String, jsonPayload: String) {
        sinks[jobId]?.tryEmitNext(
            ServerSentEvent.builder<String>()
                .event(event)
                .data(jsonPayload)
                .build()
        )
    }

    /** Закрыть поток (когда job завершён/упал) */
    fun complete(jobId: String) {
        sinks.remove(jobId)?.tryEmitComplete()
    }
}

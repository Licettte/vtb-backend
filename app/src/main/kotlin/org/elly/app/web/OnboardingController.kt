package org.elly.app.web

import org.elly.app.services.OnboardingService
import org.elly.app.web.currentUserId
import org.elly.core.model.BankCode
import org.elly.core.model.UserId
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/onboarding")
class OnboardingController(
    private val svc: OnboardingService
) {
    data class StartReq(val banks: List<String>? = null)
    data class StartResp(val jobId: String, val status: String)

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    suspend fun start(@RequestBody req: StartReq): StartResp {
        val uid = UserId(currentUserId())

        val banks = (req.banks?.takeIf { it.isNotEmpty() } ?: listOf("vbank","abank"))
            .map { BankCode(it) }

        val job = svc.startAsync(uid, banks) // ← без clientId
        return StartResp(jobId = job.jobId, status = job.phase.name)
    }

    @GetMapping("/{jobId}")
    suspend fun status(@PathVariable jobId: String) =
        svc.status(jobId) ?: throw NoSuchElementException("job $jobId not found")
}


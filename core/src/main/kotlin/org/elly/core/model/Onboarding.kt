package org.elly.core.model

enum class OnbPhase { CONSENTS_IN_PROGRESS, TRANSACTIONS_COLLECTING, ANALYSIS, DONE, FAILED }

data class OnboardingJob(
    val jobId: String,
    val userId: UserId,
    val phase: OnbPhase,
    val progress: Int = 0, // 0..100
    val perBankConsent: Map<BankCode, String> = emptyMap(), // e.g. "approved"/"failed"
    val obligationsDetected: Int? = null,
    val error: String? = null
)

package org.elly.core.repo

import org.elly.core.model.OnboardingJob

interface OnboardingRepo {
    suspend fun create(job: OnboardingJob): OnboardingJob
    suspend fun update(job: OnboardingJob): OnboardingJob
    suspend fun get(jobId: String): OnboardingJob?
}
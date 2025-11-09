package org.elly.storage.repos

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.elly.core.model.*
import org.elly.core.repo.OnboardingRepo
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.Parameter
import org.springframework.stereotype.Repository

@Repository
class OnboardingRepoR2dbc(private val db: DatabaseClient) : OnboardingRepo {

    override suspend fun create(job: OnboardingJob): OnboardingJob {
        val sql = """
      INSERT INTO onboarding_jobs(job_id,user_id,phase,progress,per_bank,obligations_detected,error)
      VALUES (:id,:uid,:phase,:progress,CAST(:perbank AS jsonb),:det,:err)
    """.trimIndent()
        db.sql(sql)
            .bind("id", job.jobId)
            .bind("uid", job.userId.value)
            .bind("phase", job.phase.name)
            .bind("progress", job.progress)
            .bind("perbank", toJson(job.perBankConsent))
            .bind("det", Parameter.fromOrEmpty(job.obligationsDetected, Integer::class.java))
            .bind("err", Parameter.fromOrEmpty(job.error, String::class.java))
            .fetch().rowsUpdated().awaitSingle()
        return job
    }

    override suspend fun update(job: OnboardingJob): OnboardingJob {
        val sql = """
      UPDATE onboarding_jobs
      SET phase=:phase, progress=:progress, per_bank=CAST(:perbank AS jsonb),
          obligations_detected=:det, error=:err
      WHERE job_id=:id
    """.trimIndent()
        db.sql(sql)
            .bind("phase", job.phase.name)
            .bind("progress", job.progress)
            .bind("perbank", toJson(job.perBankConsent))
            .bind("det", Parameter.fromOrEmpty(job.obligationsDetected, Integer::class.java))
            .bind("err", Parameter.fromOrEmpty(job.error, String::class.java))
            .bind("id", job.jobId)
            .fetch().rowsUpdated().awaitSingle()
        return job
    }

    override suspend fun get(jobId: String): OnboardingJob? {
        val sql = """
      SELECT job_id,user_id,phase,progress,per_bank,obligations_detected,error
      FROM onboarding_jobs WHERE job_id=:id
    """.trimIndent()

        return db.sql(sql).bind("id", jobId).map { row, _ ->
            OnboardingJob(
                jobId = row.get("job_id", String::class.java)!!,
                userId = UserId((row.get("user_id", java.lang.Long::class.java)!!).toLong()),
                phase = OnbPhase.valueOf(row.get("phase", String::class.java)!!),
                progress = row.get("progress", java.lang.Integer::class.java)!!.toInt(),
                perBankConsent = fromJson(row.get("per_bank", String::class.java)!!),
                obligationsDetected = row.get("obligations_detected", java.lang.Integer::class.java)?.toInt(),
                error = row.get("error", String::class.java)
            )
        }.one().awaitSingleOrNull()
    }

    /* --- примитивная (но рабочая) упаковка JSON Map<BankCode,String> --- */

    private fun toJson(map: Map<BankCode, String>) =
        map.entries.joinToString(prefix = "{", postfix = "}") {
            "\"${it.key.value}\":\"${it.value}\""
        }

    private fun fromJson(json: String): Map<BankCode, String> =
        Regex("\"(.*?)\":\"(.*?)\"").findAll(json).associate { BankCode(it.groupValues[1]) to it.groupValues[2] }
}

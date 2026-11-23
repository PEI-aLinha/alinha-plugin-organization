package pt.alinha.organization.internal

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import pt.alinha.core.audit.AuditTrail
import pt.alinha.core.compliance.ApplicabilityRules
import pt.alinha.core.compliance.Nis2EntityType
import pt.alinha.organization.internal.domain.AnswersValidation
import pt.alinha.organization.internal.domain.ApplicabilityAnswers
import pt.alinha.organization.internal.domain.ApplicabilityAnswersInput
import pt.alinha.organization.internal.domain.ApplicabilityAnswersValidator
import pt.alinha.organization.internal.domain.CncsQualificationInput
import pt.alinha.organization.internal.domain.CncsQualificationValidator
import pt.alinha.organization.internal.domain.QualificationValidation
import pt.alinha.organization.internal.domain.suggestedEntityTypes
import pt.alinha.organization.internal.persistence.ApplicabilityAnswersEntity
import pt.alinha.organization.internal.persistence.ApplicabilityAnswersRepository
import pt.alinha.organization.internal.persistence.CncsQualificationEntity
import pt.alinha.organization.internal.persistence.CncsQualificationRepository
import pt.alinha.organization.internal.persistence.OrganizationProfileEntity
import pt.alinha.organization.internal.persistence.OrganizationProfileRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

internal const val ANSWERS_ENTITY_TYPE = "applicability"
internal const val ANSWERS_TENANT_CONSTRAINT = "applicability_answers_tenant_uq"
internal const val QUALIFICATION_ENTITY_TYPE = "cncs-qualification"
internal const val QUALIFICATION_TENANT_CONSTRAINT = "cncs_qualification_tenant_uq"

/** The Act's date is checked against the date in Portugal, not the server's. */
private val LISBON: ZoneId = ZoneId.of("Europe/Lisbon")

/**
 * Regulatory scope of the current tenant (FR02, FR03, US11, US34, organization#4): the applicability answers, the
 * result of the core applicability rules and the official CNCS qualification. The result is computed on every read
 * from the answers and the current profile, so a later change to the size shows at once; every save records the
 * rules version and the classification of that moment in the audit trail (NFR05).
 */
@Service
class ScopeService(
    private val profiles: OrganizationProfileRepository,
    private val answers: ApplicabilityAnswersRepository,
    private val qualifications: CncsQualificationRepository,
    private val rules: ApplicabilityRules,
    private val audit: AuditTrail,
) {

    @Transactional(readOnly = true)
    fun scope(): ScopeView {
        val profile = profiles.findFirstBy() ?: throw OrganizationNotRegisteredException()
        return view(profile, answers.findFirstBy(), qualifications.findFirstBy())
    }

    /**
     * Saves the answers. The first save has no [version]; later ones must send the version that was read. No
     * [version] once answers exist is a stale form (opened before someone else's first save), so a 409 like any
     * other old version. Saving the same answers changes and records nothing.
     */
    @Transactional
    fun saveAnswers(input: ApplicabilityAnswersInput, version: Long?): ScopeView {
        // Looked up first: with no organization there is nothing to answer for, whatever the data.
        val profile = profiles.findFirstBy() ?: throw OrganizationNotRegisteredException()
        val entity = answers.findFirstBy()
        val validated = when (val result = ApplicabilityAnswersValidator.validate(input)) {
            is AnswersValidation.Valid -> result.answers
            is AnswersValidation.Invalid -> throw InvalidOrganizationException(result.errors)
        }
        val now = Instant.now()
        val saved = when {
            entity == null -> {
                if (version != null) throw StaleVersionException()
                save(ApplicabilityAnswersEntity(UUID.randomUUID(), now, validated))
            }
            version != entity.version -> throw StaleVersionException()
            !entity.update(validated, now) -> return view(profile, entity, qualifications.findFirstBy())
            else -> save(entity)
        }
        val result = rules.assess(validated.toInput(profile.employees, profile.turnover))
        audit.record(
            "organization.scope-answered", ANSWERS_ENTITY_TYPE, saved.id.toString(),
            mapOf(
                "rulesVersion" to result.rulesVersion,
                "nis2Classification" to result.nis2Classification.apiValue(),
                "entityTypes" to validated.nis2.entityTypes.map(Nis2EntityType::name),
            ),
        )
        return view(profile, saved, qualifications.findFirstBy())
    }

    /**
     * Registers or corrects the official CNCS qualification, which from then on overrides the indicative one. The
     * first registration has no [version]; a correction must send the version that was read. No [version] once a
     * qualification exists is a stale form, so a 409. The same Act again changes and records nothing.
     */
    @Transactional
    fun registerQualification(input: CncsQualificationInput, version: Long?): ScopeView {
        val profile = profiles.findFirstBy() ?: throw OrganizationNotRegisteredException()
        val entity = qualifications.findFirstBy()
        val today = LocalDate.now(LISBON)
        val validated = when (val result = CncsQualificationValidator.validate(input, today)) {
            is QualificationValidation.Valid -> result.qualification
            is QualificationValidation.Invalid -> throw InvalidOrganizationException(result.errors)
        }
        val answersEntity = answers.findFirstBy()
        val now = Instant.now()
        val saved = when {
            entity == null -> {
                if (version != null) throw StaleVersionException()
                val created = saveQualification(CncsQualificationEntity(UUID.randomUUID(), now, validated))
                val suggested = answersEntity?.toAnswers()?.let { assess(it, profile) }?.nis2Classification
                audit.record(
                    "organization.qualification-registered", QUALIFICATION_ENTITY_TYPE, created.id.toString(),
                    mapOf(
                        "classification" to validated.classification.apiValue(),
                        "actDate" to validated.actDate.toString(),
                        "suggested" to suggested?.apiValue(),
                    ),
                )
                created
            }
            version != entity.version -> throw StaleVersionException()
            else -> {
                val changed = entity.update(validated, now)
                if (changed.isEmpty()) return view(profile, answersEntity, entity)
                saveQualification(entity).also {
                    // Field names only, as for the profile: the values are in the qualification itself.
                    audit.record(
                        "organization.qualification-corrected", QUALIFICATION_ENTITY_TYPE, it.id.toString(),
                        mapOf("changed" to changed),
                    )
                }
            }
        }
        return view(profile, answersEntity, saved)
    }

    private fun save(entity: ApplicabilityAnswersEntity): ApplicabilityAnswersEntity =
        try {
            answers.saveAndFlush(entity)
        } catch (e: DataIntegrityViolationException) {
            // Another first save of this tenant committed in between: as if this form were stale.
            if (violatedConstraint(e) == ANSWERS_TENANT_CONSTRAINT) throw StaleVersionException()
            throw e
        }

    private fun saveQualification(entity: CncsQualificationEntity): CncsQualificationEntity =
        try {
            qualifications.saveAndFlush(entity)
        } catch (e: DataIntegrityViolationException) {
            if (violatedConstraint(e) == QUALIFICATION_TENANT_CONSTRAINT) throw StaleVersionException()
            throw e
        }

    private fun view(
        profile: OrganizationProfileEntity,
        answersEntity: ApplicabilityAnswersEntity?,
        qualification: CncsQualificationEntity?,
    ): ScopeView {
        val result = answersEntity?.toAnswers()?.let { assess(it, profile) }
        val suggested = suggestedEntityTypes(profile.sector, profile.subsector, profile.cae)
        return ScopeView(
            answers = answersEntity?.toView(),
            result = result?.toView(),
            qualification = qualification?.toView(),
            nis2 = nis2Status(qualification?.classification, result?.nis2Classification),
            entityTypes = Nis2EntityType.entries.map {
                EntityTypeOption(it, it.annex.name, it.reference, it.label, it in suggested)
            },
        )
    }

    private fun assess(answers: ApplicabilityAnswers, profile: OrganizationProfileEntity) =
        rules.assess(answers.toInput(profile.employees, profile.turnover))
}

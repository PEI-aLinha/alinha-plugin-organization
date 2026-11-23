-- Applicability answers (FR02, FR03, organization#4) and the official CNCS qualification (US11). One row per tenant
-- each. The applicability result is not stored: it is computed on every read from these answers and the organization
-- profile; each save records the rules version and the classification in the audit trail (NFR05).
create table organization.applicability_answers (
    id                             uuid primary key,
    tenant_id                      uuid        not null,
    version                        bigint      not null,
    public_body                    boolean     not null,
    annual_balance_sheet           bigint      check (annual_balance_sheet >= 0),
    -- Nis2EntityType names; validated by the application, as the list belongs to the core.
    nis2_entity_types              text[]      not null check (cardinality(nis2_entity_types) <= 100),
    critical_entity                boolean     not null,
    significant_impact             boolean     not null,
    higher_education               boolean     not null,
    processes_personal_data        boolean     not null,
    regular_or_risky_processing    boolean     not null,
    special_or_criminal_data       boolean     not null,
    large_scale_monitoring         boolean     not null,
    large_scale_special_data       boolean     not null,
    uses_ai_systems                boolean     not null,
    provides_ai_systems            boolean     not null,
    high_risk_area                 boolean     not null,
    interacts_or_generates_content boolean     not null,
    answered_at                    timestamptz not null,
    updated_at                     timestamptz not null,
    constraint applicability_answers_tenant_uq unique (tenant_id),
    -- The same contradictions the core rules refuse (docs/applicability.md, "Input validation").
    constraint applicability_answers_gdpr_ck check (processes_personal_data or not (regular_or_risky_processing
        or special_or_criminal_data or large_scale_monitoring or large_scale_special_data)),
    constraint applicability_answers_ai_ck check (uses_ai_systems or provides_ai_systems
        or not (high_risk_area or interacts_or_generates_content))
);

create table organization.cncs_qualification (
    id             uuid primary key,
    tenant_id      uuid        not null,
    version        bigint      not null,
    -- The CNCS only qualifies covered entities.
    classification text        not null check (classification in ('ESSENTIAL', 'IMPORTANT')),
    -- Decree-Law 125/2025 was published on 2025-12-04: no Qualification Act is older.
    act_date       date        not null check (act_date >= date '2025-12-04'),
    act_reference  text        check (char_length(act_reference) <= 100),
    registered_at  timestamptz not null,
    updated_at     timestamptz not null,
    constraint cncs_qualification_tenant_uq unique (tenant_id)
);

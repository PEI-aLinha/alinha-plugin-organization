-- Organization data for the NIS2 classification (FR01, organization#2). One profile per tenant.
create table organization.organization_profile (
    id                      uuid primary key,
    tenant_id               uuid        not null,
    version                 bigint      not null,
    name                    text        not null check (char_length(name) <= 200),
    nif                     text        not null check (nif ~ '^[0-9]{9}$'),
    cae                     text        not null check (cae ~ '^[0-9]{5}$'),
    sector                  text        not null,
    subsector               text,
    services                text        not null check (char_length(services) <= 2000),
    employees               integer     not null check (employees >= 1),
    turnover                bigint      not null check (turnover >= 0),
    street                  text        not null check (char_length(street) <= 300),
    postal_code             text        not null,
    city                    text        not null check (char_length(city) <= 120),
    phone                   text        not null,
    email                   text        not null check (char_length(email) <= 254),
    permanent_contact_name  text        not null check (char_length(permanent_contact_name) <= 120),
    permanent_contact_phone text        not null,
    registered_at           timestamptz not null,
    updated_at              timestamptz not null,
    constraint organization_profile_tenant_uq unique (tenant_id),
    -- A company is registered once on the platform, whoever registers it.
    constraint organization_profile_nif_uq unique (nif)
);

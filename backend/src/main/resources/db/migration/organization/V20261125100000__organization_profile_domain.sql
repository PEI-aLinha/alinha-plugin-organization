-- The organization's own internet domain (website and email), checked from the internet by the external-surface
-- provider (PEI-aLinha/alinha-provider-external-surface#1). Optional. Lower case ASCII (internationalized names in
-- their xn-- form), at least two labels and a real top-level domain, so never an IP address.
alter table organization.organization_profile
    add column domain text
        constraint organization_profile_domain_ck check (
            domain is null or (
                char_length(domain) <= 253
                and domain ~ '^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)+([a-z]{2,63}|xn--[a-z0-9-]{1,59})$'
            )
        );

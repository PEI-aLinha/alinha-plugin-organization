import { useState, type FormEvent } from 'react'
import type { components } from '@alinha/api-client'
import { ApiError, errorMessage, fieldErrors, useApiMutation, useHost } from '@alinha/plugin-sdk'
import { Button, Field, TextInput } from '@alinha/ui'
import { normalizeDomain } from '../registration/domain'
import { DOMAIN_INVALID } from '../registration/organizationForm'

type OrganizationProfile = components['schemas']['OrganizationProfile']
type OrganizationProfileUpdate = components['schemas']['OrganizationProfileUpdate']

/** The profile as an update body with another domain: the PUT replaces the whole profile. */
export function withDomain(profile: OrganizationProfile, domain: string | null): OrganizationProfileUpdate {
  return {
    version: profile.version,
    name: profile.name,
    nif: profile.nif,
    cae: profile.cae,
    sector: profile.sector,
    subsector: profile.subsector,
    services: profile.services,
    employees: profile.employees,
    turnover: profile.turnover,
    address: profile.address,
    contacts: profile.contacts,
    permanentContact: profile.permanentContact,
    domain,
  }
}

/** Shows the organization's domain and, with `organization:edit`, lets it be changed in place. */
export function DomainEditor({ profile }: { profile: OrganizationProfile }) {
  const { can } = useHost()
  const [editing, setEditing] = useState(false)
  const [typed, setTyped] = useState(profile.domain ?? '')
  const [error, setError] = useState<string>()
  const save = useApiMutation<OrganizationProfileUpdate, OrganizationProfile>({
    method: 'PUT',
    path: '/organization/profile',
    invalidates: [['organization', 'profile']],
    onSuccess: () => setEditing(false),
    onError: (e) => setError(fieldErrors(e).domain ?? errorMessage(e)),
  })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const domain = typed.trim() ? normalizeDomain(typed) : null
    if (typed.trim() && !domain) {
      setError(DOMAIN_INVALID)
      return
    }
    setError(undefined)
    save.mutate(withDomain(profile, domain))
  }

  if (!editing) {
    return (
      <div className="flex flex-wrap items-baseline gap-x-3">
        <span>{profile.domain ?? <span className="text-muted">Não indicado</span>}</span>
        {can('organization', 'edit') && (
          <Button variant="link" size="sm" aria-label={profile.domain ? 'Alterar o domínio' : 'Indicar o domínio'}
            onClick={() => { setTyped(profile.domain ?? ''); setError(undefined); setEditing(true) }}>
            {profile.domain ? 'Alterar' : 'Indicar'}
          </Button>
        )}
      </div>
    )
  }

  const conflict = save.error instanceof ApiError && save.error.status === 409
  return (
    <form onSubmit={submit} className="flex flex-col gap-3" noValidate>
      <Field label="Domínio da organização" hint="Exemplo: empresa.pt. Deixe vazio para retirar." error={error} optional>
        {(p) => (
          <TextInput id={p.id} aria-describedby={p.describedBy} invalid={p.invalid} name="domain" inputMode="url"
            value={typed} onChange={(e) => { setTyped(e.target.value); setError(undefined) }} placeholder="empresa.pt" />
        )}
      </Field>
      {conflict && <p role="alert" className="text-[0.8125rem] text-danger">Os dados mudaram entretanto. Recarregue a página e tente de novo.</p>}
      <div className="flex gap-2">
        <Button type="submit" size="sm" disabled={save.isPending}>{save.isPending ? 'A guardar…' : 'Guardar'}</Button>
        <Button type="button" size="sm" variant="secondary" onClick={() => setEditing(false)}>Cancelar</Button>
      </div>
    </form>
  )
}

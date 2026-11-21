import type { ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Building2 } from 'lucide-react'
import type { ApiResponse } from '@alinha/api-client'
import { ApiError, useHost } from '@alinha/plugin-sdk'
import { Card, EmptyState, ErrorState, PageHeader, SkeletonRows } from '@alinha/ui'

type OrganizationProfile = NonNullable<ApiResponse<'/api/organization/profile'>>

const TITLE = 'Organização'

export function OrganizationPage() {
  const { api } = useHost()
  const profile = useQuery({
    queryKey: ['organization', 'profile'],
    queryFn: ({ signal }) => api<OrganizationProfile>('/organization/profile', { signal }),
  })

  if (profile.isPending) {
    return (
      <>
        <PageHeader title={TITLE} />
        <SkeletonRows rows={6} label="A carregar os dados da organização…" />
      </>
    )
  }

  if (profile.isError) {
    const notFound = profile.error instanceof ApiError && profile.error.status === 404
    return (
      <>
        <PageHeader title={TITLE} />
        {notFound ? (
          <EmptyState icon={<Building2 className="size-5" aria-hidden />} title="Ainda sem dados da organização">
            Os dados da sua organização ainda não foram registados. Assim que estiverem preenchidos, aparecem aqui.
          </EmptyState>
        ) : (
          <ErrorState error={profile.error} onRetry={() => void profile.refetch()} />
        )}
      </>
    )
  }

  return (
    <>
      <PageHeader title={TITLE} subtitle={profile.data.name} />
      <ProfileCard profile={profile.data} />
    </>
  )
}

function ProfileCard({ profile }: { profile: OrganizationProfile }) {
  const { address, permanentContact } = profile
  return (
    <Card>
      <dl className="grid gap-x-8 gap-y-4 sm:grid-cols-2">
        <Field label="NIF">{profile.nif}</Field>
        <Field label="CAE">{profile.cae}</Field>
        <Field label="Setor">{profile.subsector ? `${profile.sector} — ${profile.subsector}` : profile.sector}</Field>
        <Field label="Nº de trabalhadores">{profile.employees.toLocaleString('pt-PT')}</Field>
        <Field label="Morada">
          {address.street}
          <br />
          {address.postalCode} {address.city}
        </Field>
        <Field label="Contacto permanente">
          {permanentContact.name}
          <br />
          {permanentContact.phone}
        </Field>
      </dl>
    </Card>
  )
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-1">
      <dt className="text-[0.8125rem] text-muted">{label}</dt>
      <dd className="text-[0.9375rem]">{children}</dd>
    </div>
  )
}

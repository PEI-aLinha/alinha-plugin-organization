import type { ReactNode } from 'react'
import { Building2 } from 'lucide-react'
import type { ApiResponse } from '@alinha/api-client'
import { ApiError, formatNumber, useApiQuery } from '@alinha/plugin-sdk'
import { Card, EmptyState, ErrorState, PageHeader, SkeletonRows } from '@alinha/ui'
import { sectorLabel } from '../registration/sectors'

type OrganizationProfile = NonNullable<ApiResponse<'/api/organization/profile'>>

const TITLE = 'Organização'

/** The backend's 404 for an organization that has not registered its profile yet; any other 404 is a fault. */
const isNotRegistered = (error: unknown) =>
  error instanceof ApiError && error.status === 404 && error.problem?.code === 'ORGANIZATION_NOT_REGISTERED'

export function OrganizationPage() {
  const profile = useApiQuery<OrganizationProfile>(['organization', 'profile'], '/organization/profile')

  if (profile.isPending) {
    return (
      <>
        <PageHeader title={TITLE} />
        <SkeletonRows rows={6} label="A carregar os dados da organização…" />
      </>
    )
  }

  if (profile.isError) {
    return (
      <>
        <PageHeader title={TITLE} />
        {isNotRegistered(profile.error) ? (
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
        <Field label="Setor">{sectorLabel(profile.sector, profile.subsector)}</Field>
        <Field label="Nº de trabalhadores">{formatNumber(profile.employees)}</Field>
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

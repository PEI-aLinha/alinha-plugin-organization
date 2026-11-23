import { useState } from 'react'
import { Link } from 'react-router'
import { ArrowLeft, Scale } from 'lucide-react'
import { ApiError, useApiQuery, useHost } from '@alinha/plugin-sdk'
import { EmptyState, ErrorState, PageHeader, SkeletonRows, buttonClass } from '@alinha/ui'
import { QualificationModal } from './QualificationModal'
import { ScopeQuestions } from './ScopeQuestions'
import { ScopeResult } from './ScopeResult'
import type { Scope } from './scopeForm'

const TITLE = 'Âmbito regulatório'
const SUBTITLE = 'Que leis de cibersegurança, dados pessoais e inteligência artificial se aplicam à empresa.'

const isNotRegistered = (error: unknown) =>
  error instanceof ApiError && error.status === 404 && error.problem?.code === 'ORGANIZATION_NOT_REGISTERED'

/**
 * Regulatory scope of the organization (organization#4): the applicability questions, the indicative NIS2
 * classification and the laws that apply, and the official CNCS qualification, which overrides the suggestion.
 */
export function ScopePage() {
  const { can } = useHost()
  const canEdit = can('organization', 'edit')
  const scope = useApiQuery<Scope>(['organization', 'scope'], '/organization/scope')
  const [editing, setEditing] = useState(false)
  const [qualifying, setQualifying] = useState(false)

  // Relative to the URL, not the route tree: "..", by route, would leave the organization area.
  const back = (
    <Link to=".." relative="path" className={buttonClass({ variant: 'secondary' })}>
      <ArrowLeft className="size-4" aria-hidden />Voltar à organização
    </Link>
  )
  const header = <PageHeader title={TITLE} subtitle={SUBTITLE} actions={back} />

  if (scope.isPending) {
    return <>{header}<SkeletonRows rows={6} label="A carregar o âmbito regulatório…" /></>
  }
  if (scope.isError) {
    return (
      <>
        {header}
        {isNotRegistered(scope.error) ? (
          <EmptyState title="Ainda sem dados da organização">
            Os dados da sua organização ainda não foram registados. Sem eles não é possível ver o âmbito regulatório.
          </EmptyState>
        ) : (
          <ErrorState error={scope.error} onRetry={() => void scope.refetch()} />
        )}
      </>
    )
  }

  const data = scope.data
  const nothingYet = !data.answers && !data.qualification

  if (editing || (nothingYet && canEdit)) {
    return (
      <>
        {header}
        <ScopeQuestions key={data.answers?.version ?? 'new'} scope={data} onSaved={() => setEditing(false)}
          onCancel={nothingYet ? undefined : () => setEditing(false)} />
      </>
    )
  }

  if (nothingYet) {
    return (
      <>
        {header}
        <EmptyState icon={<Scale className="size-5" aria-hidden />} title="Ainda sem respostas">
          O Responsável designado ainda não respondeu às perguntas sobre a empresa.
        </EmptyState>
      </>
    )
  }

  return (
    <>
      {header}
      <ScopeResult scope={data} canEdit={canEdit} onEditAnswers={() => setEditing(true)}
        onRegisterQualification={() => setQualifying(true)} />
      {qualifying && (
        <QualificationModal current={data.qualification} suggested={data.result?.nis2Classification ?? null}
          onClose={() => setQualifying(false)} />
      )}
    </>
  )
}

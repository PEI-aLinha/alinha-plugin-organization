import { useId, type ReactNode } from 'react'
import { BadgeCheck, Info } from 'lucide-react'
import type { components } from '@alinha/api-client'
import { formatDate } from '@alinha/plugin-sdk'
import { Badge, Button, Card, Eyebrow, Note } from '@alinha/ui'
import { APPLIES_LABEL, FRAMEWORKS, MYCIBER_REMINDER, NIS2_LABEL, NIS2_MEANING } from './labels'
import type { Scope } from './scopeForm'

type Finding = components['schemas']['ApplicabilityFinding']
type Framework = components['schemas']['FrameworkApplicability']

const NIS2 = 'nis2-qnrcs'

function Findings({ findings }: { findings: Finding[] }) {
  return (
    <ul className="flex flex-col gap-2.5">
      {findings.map((f) => (
        <li key={f.id} className="flex flex-col gap-1">
          <span className="flex flex-wrap items-start gap-2 text-[0.875rem] text-ink">
            <span>{f.explanation}</span>
            {f.review && <Badge tone="outline">O consultor confirma</Badge>}
          </span>
          <span className="text-[0.75rem] text-muted">{f.legalBasis}</span>
        </li>
      ))}
    </ul>
  )
}

function Section({ title, children, action }: { title: string; children: ReactNode; action?: ReactNode }) {
  const titleId = useId()
  return (
    <Card aria-labelledby={titleId} className="flex flex-col gap-4 px-6 py-6">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <h2 id={titleId} className="section-title">{title}</h2>
        {action}
      </header>
      {children}
    </Card>
  )
}

function FrameworkCard({ framework }: { framework: Framework }) {
  const info = FRAMEWORKS[framework.framework] ?? { name: framework.framework, fullName: '', summary: '' }
  const titleId = useId()
  return (
    <Card aria-labelledby={titleId} soft={framework.applies === 'no'} className="flex h-full flex-col gap-3 px-6 py-6">
      <header className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 id={titleId} className="text-[1.25rem] font-extrabold title-tight">{info.name}</h3>
          {info.fullName && <p className="mt-1 text-[0.8125rem] text-ink-soft">{info.fullName}</p>}
        </div>
        <Badge tone={framework.applies === 'yes' ? 'dark' : framework.applies === 'review' ? 'outline' : 'neutral'}>
          {APPLIES_LABEL[framework.applies]}
        </Badge>
      </header>
      {info.summary && <p className="text-[0.875rem] text-ink-soft">{info.summary}</p>}
      <Findings findings={framework.findings} />
    </Card>
  )
}

/**
 * Result screen (FR02, FR03, US11, US34): the NIS2 classification that counts (the CNCS's once registered,
 * otherwise the indicative one) and what it implies, the laws that apply with plain explanations, and the official
 * CNCS qualification.
 */
export function ScopeResult({ scope, canEdit, onEditAnswers, onRegisterQualification }: {
  scope: Scope
  canEdit: boolean
  onEditAnswers: () => void
  onRegisterQualification: () => void
}) {
  const nis2TitleId = useId()
  const { nis2, result, qualification } = scope
  if (!nis2) return null
  const official = nis2.official && qualification
  const nis2Findings = result?.frameworks.find((f) => f.framework === NIS2)?.findings ?? []

  return (
    <div className="flex flex-col gap-6">
      <section aria-labelledby={nis2TitleId} className="flex flex-col gap-4 rounded-[20px] bg-brand-deep px-8 py-[30px] text-white max-md:px-6">
        <div className="flex flex-wrap items-center gap-3">
          <Eyebrow className="text-signal-soft">{official ? 'Qualificação oficial' : 'Enquadramento sugerido'}</Eyebrow>
          {official
            ? <Badge tone="signal" icon={<BadgeCheck className="size-3.5" aria-hidden />}>Oficial · CNCS</Badge>
            : <Badge tone="signal" icon={<Info className="size-3.5" aria-hidden />}>Indicativo</Badge>}
        </div>
        <h2 id={nis2TitleId} className="text-[2.125rem] font-extrabold title-tight max-md:text-[1.75rem]">{NIS2_LABEL[nis2.classification]}</h2>
        {official ? (
          <p className="text-[0.875rem] font-semibold text-brand-soft">
            Atribuída pelo CNCS no Ato de Qualificação de {formatDate(official.actDate)}.
            {official.actReference && ` Referência: ${official.actReference}.`}
          </p>
        ) : (
          <ul className="flex flex-col gap-1.5 text-[0.875rem] font-semibold text-brand-soft">
            {nis2Findings.map((f) => (
              <li key={f.id}>
                {f.explanation}
                {f.review && <> <Badge tone="signal">O consultor confirma</Badge></>}
              </li>
            ))}
          </ul>
        )}
      </section>

      <Note>
        {!official && MYCIBER_REMINDER}
        {official && nis2.suggested && nis2.suggested !== nis2.classification
          && `A sugestão do aLinha era ${NIS2_LABEL[nis2.suggested]}. A partir de agora conta a qualificação do CNCS.`}
        {official && nis2.suggested === nis2.classification && 'A qualificação do CNCS confirma a sugestão do aLinha.'}
        {official && !nis2.suggested && 'Esta é a classificação atribuída pelo CNCS.'}
      </Note>

      <Section title="O que isto significa">
        <ul className="flex flex-col gap-2">
          {NIS2_MEANING[nis2.classification].map((t) => (
            <li key={t} className="flex items-start gap-2.5 text-[0.875rem] text-ink">
              <span aria-hidden className="mt-[7px] size-1.5 shrink-0 rounded-full bg-signal" />
              <span>{t}</span>
            </li>
          ))}
        </ul>
      </Section>

      <Section title="Que leis se aplicam à empresa"
        action={canEdit && result && <Button variant="secondary" onClick={onEditAnswers}>Mudar respostas</Button>}>
        {result ? (
          <ul aria-label="Leis avaliadas" className="grid gap-4 lg:grid-cols-3">
            {result.frameworks.map((f) => <li key={f.framework}><FrameworkCard framework={f} /></li>)}
          </ul>
        ) : (
          <div className="flex flex-col items-start gap-3">
            <p className="text-[0.875rem] text-ink-soft">Responda às perguntas sobre a empresa para ver que leis se aplicam.</p>
            {canEdit && <Button onClick={onEditAnswers}>Responder às perguntas</Button>}
          </div>
        )}
      </Section>

      <Section title="Qualificação oficial do CNCS"
        action={canEdit && (
          <Button variant={qualification ? 'secondary' : 'primary'} onClick={onRegisterQualification}>
            {qualification ? 'Corrigir' : 'Registar qualificação oficial'}
          </Button>
        )}>
        {qualification ? (
          <dl className="grid gap-x-8 gap-y-3 sm:grid-cols-3">
            <div><dt className="text-[0.8125rem] text-muted">Classificação</dt><dd className="font-bold">{NIS2_LABEL[qualification.classification]}</dd></div>
            <div><dt className="text-[0.8125rem] text-muted">Data do Ato</dt><dd>{formatDate(qualification.actDate)}</dd></div>
            <div><dt className="text-[0.8125rem] text-muted">Referência</dt><dd>{qualification.actReference ?? '—'}</dd></div>
          </dl>
        ) : (
          <p className="text-[0.875rem] text-ink-soft">
            Ainda não registou a qualificação oficial. Quando receber o Ato de Qualificação do CNCS, registe-o aqui: a
            classificação oficial passa a contar em vez da sugestão.
          </p>
        )}
      </Section>
    </div>
  )
}

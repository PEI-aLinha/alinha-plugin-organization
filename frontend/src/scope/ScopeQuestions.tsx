import { useId, useState, type FormEvent, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { ApiError, errorMessage, fieldErrors, useApiMutation } from '@alinha/plugin-sdk'
import { Button, Card, Checkbox, Field, Note, RadioGroup, TextInput, type RadioOption } from '@alinha/ui'
import {
  fromAnswers, groupEntityTypes, toAnswersBody, validateScope,
  type AnswersBody, type EntityTypeOption, type Nis2EntityType, type Scope, type ScopeErrors, type ScopeForm, type YesNo,
} from './scopeForm'

const YES_NO: RadioOption<YesNo>[] = [
  { value: 'yes', label: 'Sim' },
  { value: 'no', label: 'Não' },
]

/** Server field (JSON path) → the question that shows its message. */
const SERVER_FIELDS: Record<string, keyof ScopeForm> = {
  'nis2.entityTypes': 'entityTypes',
  annualBalanceSheetEur: 'balanceSheet',
  gdpr: 'processesPersonalData',
  'gdpr.processesPersonalData': 'processesPersonalData',
  aiAct: 'usesAiSystems',
}

const PERSONAL_DATA_DETAILS = [
  ['regularOrRiskyProcessing', 'Trata estes dados com regularidade, não só de vez em quando'],
  ['specialOrCriminalData', 'Trata dados de saúde, biométricos ou de registo criminal'],
  ['largeScaleMonitoring', 'A atividade principal é acompanhar pessoas em grande escala (por exemplo, videovigilância ou seguimento online)'],
  ['largeScaleSpecialData', 'A atividade principal é tratar dados sensíveis em grande escala'],
] as const

const AI_DETAILS = [
  ['highRiskArea', 'Usa IA em recrutamento, crédito, seguros, educação ou identificação de pessoas'],
  ['interactsOrGeneratesContent', 'A IA fala diretamente com pessoas ou cria imagens, vídeos ou textos que são publicados'],
] as const

function Question({ n, title, children }: { n: number; title: string; children: ReactNode }) {
  const titleId = useId()
  return (
    <section aria-labelledby={titleId} className="flex flex-col gap-4 border-t border-line-soft pt-6 first:border-t-0 first:pt-0">
      <h2 id={titleId} className="text-[1rem] font-bold">{n}. {title}</h2>
      {children}
    </section>
  )
}

function ErrorText({ children }: { children?: string }) {
  if (!children) return null
  return <p role="alert" className="text-[0.8125rem] font-semibold text-danger">{children}</p>
}

/**
 * The applicability questions (FR02, FR03, US34, organization#4): what the company does (NIS2 entity types), a few
 * facts the sector alone does not decide, personal data and AI. Plain European Portuguese; the follow-up questions
 * only show once their question is answered "yes".
 */
export function ScopeQuestions({ scope, onSaved, onCancel }: { scope: Scope; onSaved: () => void; onCancel?: () => void }) {
  const queryClient = useQueryClient()
  const [form, setForm] = useState<ScopeForm>(() => fromAnswers(scope.answers))
  const [submitted, setSubmitted] = useState(false)
  const [serverErrors, setServerErrors] = useState<ScopeErrors>({})
  const save = useApiMutation<AnswersBody, Scope>({
    method: 'PUT',
    path: '/organization/scope/answers',
    invalidates: [['organization', 'scope']],
    onSuccess: () => onSaved(),
    onError: (error) => {
      const byField: ScopeErrors = {}
      for (const [field, message] of Object.entries(fieldErrors(error))) {
        const key = SERVER_FIELDS[field]
        if (key) byField[key] = message
      }
      setServerErrors(byField)
    },
  })

  const errors: ScopeErrors = { ...(submitted ? validateScope(form) : {}), ...serverErrors }
  const set = <K extends keyof ScopeForm>(key: K, value: ScopeForm[K]) => setForm((f) => ({ ...f, [key]: value }))
  const conflict = save.error instanceof ApiError && save.error.status === 409
  const unmapped = save.isError && !conflict && Object.keys(serverErrors).length === 0

  const toggleType = (id: Nis2EntityType, on: boolean) => setForm((f) => ({
    ...f,
    noEntityType: on ? false : f.noEntityType,
    entityTypes: on ? [...f.entityTypes, id] : f.entityTypes.filter((t) => t !== id),
  }))

  const submit = (e: FormEvent) => {
    e.preventDefault()
    setSubmitted(true)
    setServerErrors({})
    if (Object.keys(validateScope(form)).length > 0) return
    save.mutate(toAnswersBody(form, scope.answers?.version ?? null))
  }

  const { suggested, sections } = groupEntityTypes(scope.entityTypes)
  const typeBox = (o: EntityTypeOption) => (
    <Checkbox key={o.id} label={o.label} hint={o.reference} checked={form.entityTypes.includes(o.id)}
      onChange={(e) => toggleType(o.id, e.target.checked)} />
  )
  const yesNo = (key: 'publicBody' | 'higherEducation' | 'criticalEntity' | 'significantImpact' | 'processesPersonalData' | 'usesAiSystems' | 'providesAiSystems', legend: string) => (
    <RadioGroup name={key} legend={legend} columns={2} options={YES_NO} value={form[key]} onChange={(v) => set(key, v)} error={errors[key]} />
  )
  const usesOrProvidesAi = form.usesAiSystems === 'yes' || form.providesAiSystems === 'yes'

  return (
    <Card className="flex flex-col gap-6 px-6 py-6">
      <form noValidate onSubmit={submit} className="flex flex-col gap-6">
        <Question n={1} title="O que a empresa faz">
          <p className="text-[0.875rem] text-ink-soft">
            Marque todas as atividades que a empresa faz. É isto, e não só o setor, que decide se a lei de cibersegurança se aplica.
          </p>
          {suggested.length > 0 && (
            <fieldset className="flex min-w-0 flex-col gap-3">
              <legend className="mb-1.5 text-[0.8125rem] font-semibold text-ink-soft">Sugeridos pelo seu setor</legend>
              {suggested.map(typeBox)}
            </fieldset>
          )}
          <div className="flex flex-col gap-2">
            {sections.map((s) => (
              <details key={s.key} className="rounded-[12px] border border-line-soft px-4 py-3">
                <summary className="cursor-pointer text-[0.9375rem] font-semibold">{s.title}</summary>
                <div className="mt-3 flex flex-col gap-3">{s.options.map(typeBox)}</div>
              </details>
            ))}
          </div>
          <Checkbox label="Nenhum destes descreve a empresa" checked={form.noEntityType}
            onChange={(e) => setForm((f) => ({ ...f, noEntityType: e.target.checked, entityTypes: e.target.checked ? [] : f.entityTypes }))} />
          <ErrorText>{errors.entityTypes}</ErrorText>
        </Question>

        <Question n={2} title="Mais sobre a empresa">
          {yesNo('publicBody', 'A empresa é uma entidade pública?')}
          {yesNo('higherEducation', 'É uma instituição de ensino superior?')}
          {yesNo('criticalEntity', 'Foi identificada pelo Estado como entidade crítica?')}
          <div className="flex flex-col gap-1.5">
            {yesNo('significantImpact', 'Se o vosso serviço parasse, isso afetaria seriamente a segurança, a saúde ou muitas outras empresas?')}
            <p className="text-[0.8125rem] text-ink-soft">Por exemplo, ser o único fornecedor de um serviço essencial na região.</p>
          </div>
          <Field label="Balanço total do último ano (euros)" optional hint="Pode baixar a dimensão da empresa." error={errors.balanceSheet} className="max-w-[320px]">
            {(p) => (
              <TextInput id={p.id} aria-describedby={p.describedBy} invalid={p.invalid} inputMode="numeric"
                value={form.balanceSheet} onChange={(e) => set('balanceSheet', e.target.value)} />
            )}
          </Field>
        </Question>

        <Question n={3} title="Dados de pessoas">
          {yesNo('processesPersonalData', 'A empresa guarda ou usa dados de pessoas (clientes, trabalhadores, contactos)?')}
          {form.processesPersonalData === 'yes' && (
            <div className="flex flex-col gap-3">
              {PERSONAL_DATA_DETAILS.map(([key, label]) => (
                <Checkbox key={key} label={label} checked={form[key]} onChange={(e) => set(key, e.target.checked)} />
              ))}
            </div>
          )}
        </Question>

        <Question n={4} title="Inteligência artificial">
          {yesNo('usesAiSystems', 'A empresa usa ferramentas de IA no trabalho (por exemplo, ChatGPT ou Copilot)?')}
          {/* AI Act art. 3.º, 3): a provider offers the system under its own name "whether for payment or free of charge". */}
          {yesNo('providesAiSystems', 'Desenvolve sistemas de IA e disponibiliza-os com o seu nome, a pagar ou de graça?')}
          {usesOrProvidesAi && (
            <div className="flex flex-col gap-3">
              {AI_DETAILS.map(([key, label]) => (
                <Checkbox key={key} label={label} checked={form[key]} onChange={(e) => set(key, e.target.checked)} />
              ))}
            </div>
          )}
        </Question>

        {conflict && (
          <Note>
            <span className="flex flex-col items-start gap-2">
              <span>{errorMessage(save.error)}</span>
              <Button variant="secondary" size="sm"
                onClick={() => void queryClient.invalidateQueries({ queryKey: ['organization', 'scope'] })}>
                Carregar as respostas atuais
              </Button>
            </span>
          </Note>
        )}
        {unmapped && <ErrorText>{errorMessage(save.error)}</ErrorText>}

        <div className="flex flex-wrap items-center gap-3 max-sm:flex-col max-sm:items-stretch">
          <Button type="submit" loading={save.isPending}>Ver o resultado</Button>
          {onCancel && <Button variant="secondary" onClick={onCancel}>Cancelar</Button>}
        </div>
      </form>
    </Card>
  )
}

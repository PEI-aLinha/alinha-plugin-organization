import { useId, useState, type FormEvent } from 'react'
import type { components } from '@alinha/api-client'
import { errorMessage, fieldErrors, useApiMutation } from '@alinha/plugin-sdk'
import { Button, Field, Modal, RadioGroup, TextInput, type RadioOption } from '@alinha/ui'
import { NIS2_LABEL } from './labels'
import type { Nis2Classification, Scope } from './scopeForm'

type Qualification = components['schemas']['CncsQualification']
type QualificationBody = components['schemas']['CncsQualificationUpdate']
type Official = QualificationBody['classification']
type Errors = Partial<Record<'classification' | 'actDate' | 'actReference', string>>

const MAX_REFERENCE = 100
const OPTIONS: RadioOption<Official>[] = [
  { value: 'essential', label: NIS2_LABEL.essential },
  { value: 'important', label: NIS2_LABEL.important },
]

/** Today's date in Portugal, as `yyyy-mm-dd` (the backend checks the Act's date against the same day). */
export function todayInLisbon(now: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Lisbon' }).format(now)
}

/** Same rules and messages as the backend, except the 2025-12-04 floor, which only the backend checks. */
function validate(classification: Official | null, actDate: string, reference: string, today: string): Errors {
  const errors: Errors = {}
  if (!classification) errors.classification = 'Escolha a classificação que está no Ato de Qualificação.'
  if (!actDate) errors.actDate = 'Indique a data do Ato de Qualificação.'
  else if (actDate > today) errors.actDate = 'A data não pode ser no futuro.'
  if (reference.trim().length > MAX_REFERENCE) errors.actReference = `Use no máximo ${MAX_REFERENCE} caracteres.`
  return errors
}

/**
 * Register, or correct, the official qualification from the CNCS Qualification Act (US11). From then on it
 * overrides the indicative classification. The Act's PDF is not attached yet (no file storage in the core).
 */
export function QualificationModal({ current, suggested, onClose }: {
  current: Qualification | null
  suggested: Nis2Classification | null
  onClose: () => void
}) {
  const formId = useId()
  const today = todayInLisbon()
  const [classification, setClassification] = useState<Official | null>(current?.classification ?? null)
  const [actDate, setActDate] = useState(current?.actDate ?? '')
  const [reference, setReference] = useState(current?.actReference ?? '')
  const [submitted, setSubmitted] = useState(false)
  const save = useApiMutation<QualificationBody, Scope>({
    method: 'PUT',
    path: '/organization/scope/qualification',
    invalidates: [['organization', 'scope']],
    onSuccess: () => onClose(),
  })

  const server = save.isError ? fieldErrors(save.error) : {}
  const errors: Errors = {
    ...(submitted ? validate(classification, actDate, reference, today) : {}),
    ...(server.classification && { classification: server.classification }),
    ...(server.actDate && { actDate: server.actDate }),
    ...(server.actReference && { actReference: server.actReference }),
  }
  const generalError = save.isError && !server.classification && !server.actDate && !server.actReference

  const submit = (e: FormEvent) => {
    e.preventDefault()
    setSubmitted(true)
    if (Object.keys(validate(classification, actDate, reference, today)).length > 0 || !classification) return
    save.mutate({
      version: current?.version ?? null,
      classification,
      actDate,
      actReference: reference.trim() || null,
    })
  }

  const title = current ? 'Corrigir qualificação oficial' : 'Registar qualificação oficial'
  const legend = suggested
    ? `Classificação atribuída pelo CNCS (a sugestão do aLinha era: ${NIS2_LABEL[suggested]})`
    : 'Classificação atribuída pelo CNCS'

  return (
    <Modal open onOpenChange={(open) => !open && onClose()} width={600} title={title}
      description="Use os dados do Ato de Qualificação que recebeu do CNCS depois do registo na plataforma MyCiber."
      footer={(
        <>
          <Button variant="secondary" onClick={onClose}>Cancelar</Button>
          <Button type="submit" form={formId} loading={save.isPending}>{current ? 'Guardar correção' : 'Registar qualificação'}</Button>
        </>
      )}>
      <form id={formId} noValidate onSubmit={submit} className="flex flex-col gap-[18px]">
        <RadioGroup name="classification" legend={legend} columns={2} options={OPTIONS} value={classification}
          onChange={setClassification} error={errors.classification} />
        <Field label="Data do Ato de Qualificação" error={errors.actDate} className="max-w-[260px]">
          {(p) => (
            <TextInput id={p.id} type="date" aria-describedby={p.describedBy} invalid={p.invalid} max={today}
              value={actDate} onChange={(e) => setActDate(e.target.value)} />
          )}
        </Field>
        <Field label="Referência do Ato" optional hint="Como aparece no documento." error={errors.actReference}>
          {(p) => (
            <TextInput id={p.id} aria-describedby={p.describedBy} invalid={p.invalid}
              value={reference} onChange={(e) => setReference(e.target.value)} />
          )}
        </Field>
        {generalError && <p role="alert" className="text-[0.8125rem] font-semibold text-danger">{errorMessage(save.error)}</p>}
      </form>
    </Modal>
  )
}

import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import type { components } from '@alinha/api-client'
import { ApiError, errorMessage, fieldErrors, useApiMutation } from '@alinha/plugin-sdk'
import { Button, Field, Modal, Note, RadioGroup, TextInput, type RadioOption } from '@alinha/ui'
import { NIS2_LABEL } from './labels'
import type { Nis2Classification, Scope } from './scopeForm'
import { focusFirstInvalid } from './focus'

type Qualification = components['schemas']['CncsQualification']
type QualificationBody = components['schemas']['CncsQualificationUpdate']
type Official = QualificationBody['classification']
type ErrorField = 'classification' | 'actDate' | 'actReference'
type Errors = Partial<Record<ErrorField, string>>

const MAX_REFERENCE = 100
/** Decree-Law 125/2025 was published on this day: no Qualification Act is older (the backend checks it too). */
export const RJC_PUBLISHED = '2025-12-04'
const OPTIONS: RadioOption<Official>[] = [
  { value: 'essential', label: NIS2_LABEL.essential },
  { value: 'important', label: NIS2_LABEL.important },
]

/** Today's date in Portugal, as `yyyy-mm-dd` (the backend checks the Act's date against the same day). */
export function todayInLisbon(now: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Lisbon' }).format(now)
}

/** Same rules and messages as the backend. Dates are `yyyy-mm-dd`, so they compare as strings. */
function validate(classification: Official | null, actDate: string, reference: string, today: string): Errors {
  const errors: Errors = {}
  if (!classification) errors.classification = 'Escolha a classificação que está no Ato de Qualificação.'
  if (!actDate) errors.actDate = 'Indique a data do Ato de Qualificação.'
  else if (actDate > today) errors.actDate = 'A data não pode ser no futuro.'
  else if (actDate < RJC_PUBLISHED) {
    errors.actDate = 'A data não pode ser anterior a 4 de dezembro de 2025, quando a lei foi publicada.'
  }
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
  const formRef = useRef<HTMLFormElement>(null)
  const queryClient = useQueryClient()
  const today = todayInLisbon()
  const [classification, setClassification] = useState<Official | null>(current?.classification ?? null)
  const [actDate, setActDate] = useState(current?.actDate ?? '')
  const [reference, setReference] = useState(current?.actReference ?? '')
  const [submitted, setSubmitted] = useState(false)
  const [server, setServer] = useState<Errors>({})
  // Bumped after a failed submit (here or on the server): the effect then focuses the first invalid field.
  const [failedSubmits, setFailedSubmits] = useState(0)
  useEffect(() => {
    if (failedSubmits > 0) focusFirstInvalid(formRef.current)
  }, [failedSubmits])
  const save = useApiMutation<QualificationBody, Scope>({
    method: 'PUT',
    path: '/organization/scope/qualification',
    invalidates: [['organization', 'scope']],
    onSuccess: () => onClose(),
    onError: (error) => {
      const all = fieldErrors(error)
      const byField: Errors = {
        ...(all.classification && { classification: all.classification }),
        ...(all.actDate && { actDate: all.actDate }),
        ...(all.actReference && { actReference: all.actReference }),
      }
      setServer(byField)
      if (Object.keys(byField).length > 0) setFailedSubmits((n) => n + 1)
    },
  })

  /** A server message is about the value as it was sent: once that value changes, it no longer applies. */
  const changed = (field: ErrorField) =>
    setServer((current) => (field in current ? { ...current, [field]: undefined } : current))
  const errors: Errors = {
    ...(submitted ? validate(classification, actDate, reference, today) : {}),
    ...(server.classification && { classification: server.classification }),
    ...(server.actDate && { actDate: server.actDate }),
    ...(server.actReference && { actReference: server.actReference }),
  }
  // Someone else registered or corrected the Act since this page loaded: reloading the scope gives the modal the
  // current qualification and version (ScopePage keys it by that version).
  const conflict = save.error instanceof ApiError && save.error.status === 409
  const generalError = save.isError && !conflict && Object.keys(fieldErrors(save.error)).length === 0

  const submit = (e: FormEvent) => {
    e.preventDefault()
    setSubmitted(true)
    setServer({})
    if (Object.keys(validate(classification, actDate, reference, today)).length > 0 || !classification) {
      setFailedSubmits((n) => n + 1)
      return
    }
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
      <form ref={formRef} id={formId} noValidate onSubmit={submit} className="flex flex-col gap-[18px]">
        <RadioGroup name="classification" legend={legend} columns={2} options={OPTIONS} value={classification}
          onChange={(v) => { setClassification(v); changed('classification') }} error={errors.classification} />
        <Field label="Data do Ato de Qualificação" error={errors.actDate} className="max-w-[260px]">
          {(p) => (
            <TextInput id={p.id} type="date" aria-describedby={p.describedBy} invalid={p.invalid}
              min={RJC_PUBLISHED} max={today}
              value={actDate} onChange={(e) => { setActDate(e.target.value); changed('actDate') }} />
          )}
        </Field>
        <Field label="Referência do Ato" optional hint="Como aparece no documento." error={errors.actReference}>
          {(p) => (
            <TextInput id={p.id} aria-describedby={p.describedBy} invalid={p.invalid}
              value={reference} onChange={(e) => { setReference(e.target.value); changed('actReference') }} />
          )}
        </Field>
        {conflict && (
          <Note>
            <span className="flex flex-col items-start gap-2">
              <span>{errorMessage(save.error)}</span>
              <span>Ao carregar, os dados que escreveu aqui são substituídos pelos que estão guardados.</span>
              <Button variant="secondary" size="sm"
                onClick={() => void queryClient.invalidateQueries({ queryKey: ['organization', 'scope'] })}>
                Carregar a qualificação atual
              </Button>
            </span>
          </Note>
        )}
        {generalError && <p role="alert" className="text-[0.8125rem] font-semibold text-danger">{errorMessage(save.error)}</p>}
      </form>
    </Modal>
  )
}

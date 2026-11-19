import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router'
import { useMutation } from '@tanstack/react-query'
import { ApiError, useHost } from '@alinha/plugin-sdk'
import { clearDraft, loadDraft, markRegistered, registration, saveDraft } from './draft'
import { buttonClass } from './fields'
import {
  EMPTY_ORGANIZATION, STEPS, firstStepWithErrors, fromServerErrors, isStepId, stepIndex, toOrganizationInput,
  validateOrganization, validateStep, type OrganizationErrors, type OrganizationForm, type OrganizationProfile, type StepId,
} from './organizationForm'
import { RegistrationDone } from './RegistrationDone'
import { STEP_FIELDS } from './steps'
import { Stepper } from './Stepper'
import { Summary } from './Summary'

const AUTOSAVE_DELAY_MS = 400
const GENERIC_ERROR = 'Não foi possível concluir o registo. Verifique a ligação e tente novamente.'

/** What to do with a failed registration: errors on fields (and the step to show), or a message for the page. */
type Failure = { fields: OrganizationErrors; step: StepId } | { message: string } | { reauthenticate: true }

function describeFailure(error: unknown): Failure {
  if (!(error instanceof ApiError)) return { message: GENERIC_ERROR }
  const { code, detail, errors } = error.problem ?? {}
  if (code === 'ORGANIZATION_ALREADY_EXISTS') return { reauthenticate: true }
  if (code === 'NIF_ALREADY_REGISTERED') return { fields: { nif: detail ?? 'Já existe uma organização registada com este NIF.' }, step: 'company' }
  if (code === 'VALIDATION_FAILED') {
    const fields = fromServerErrors(errors)
    const step = firstStepWithErrors(fields)
    if (step) return { fields, step }
  }
  return { message: detail ?? GENERIC_ERROR }
}

/**
 * Registration wizard (FR01, US10), step 3 of the self-registration after the Keycloak account and MFA (ADR 0004):
 * company → activity → size → contacts → summary, then one POST /organization/registration.
 * The step is in the URL (`?step=`); the half-filled form is kept in this browser to resume later.
 */
export function RegistrationWizard() {
  const { user } = useHost()
  // The draft is kept per account: without an id there is no key that is safe to read or write.
  if (!user?.id) return null
  // `key`: another account gets a fresh wizard, with its own draft, not the state of the previous one.
  return <AccountRegistrationWizard key={user.id} userId={user.id} userName={user.name} />
}

function AccountRegistrationWizard({ userId, userName }: { userId: string; userName: string }) {
  const { api, reauthenticate } = useHost()
  const [restored] = useState(() => loadDraft(userId))
  const [registeredBefore] = useState(() => registration(userId))
  const [form, setForm] = useState<OrganizationForm>(restored ?? EMPTY_ORGANIZATION)
  // Without `?step=`, start where the restored draft stopped (fixed once, so typing never moves the step).
  const [startStep] = useState<StepId>(() => (restored ? firstStepWithErrors(validateOrganization(restored)) ?? 'summary' : 'company'))
  const [attempted, setAttempted] = useState<ReadonlySet<StepId>>(() => new Set())
  const [serverErrors, setServerErrors] = useState<OrganizationErrors>({})
  const [notice, setNotice] = useState<string | null>(null)
  const [registered, setRegistered] = useState<{ profile: OrganizationProfile; form: OrganizationForm } | null>(null)
  const [params, setParams] = useSearchParams()
  const formRef = useRef<HTMLFormElement>(null)
  const headingRef = useRef<HTMLHeadingElement>(null)
  const focusFirstError = useRef(false)
  // Set once the server says the organization exists: a pending autosave must not bring the cleared draft back.
  const finished = useRef(false)

  // The step cannot be ahead of the first incomplete one (no skipping through the URL).
  const firstIncomplete = firstStepWithErrors(validateOrganization(form)) ?? 'summary'
  const requested = params.get('step')
  const wanted: StepId = isStepId(requested) ? requested : startStep
  const step = stepIndex(wanted) > stepIndex(firstIncomplete) ? firstIncomplete : wanted
  const errors: OrganizationErrors = { ...(attempted.has(step) ? validateStep(step, form) : {}), ...serverErrors }

  const register = useMutation({
    mutationFn: (body: ReturnType<typeof toOrganizationInput>) =>
      api<OrganizationProfile>('/organization/registration', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
      }),
  })

  // Saves the draft shortly after each change, until the organization is registered.
  useEffect(() => {
    if (registered) return undefined
    const timer = window.setTimeout(() => {
      if (!finished.current) saveDraft(userId, form)
    }, AUTOSAVE_DELAY_MS)
    return () => window.clearTimeout(timer)
  }, [form, userId, registered])

  // A new step: move the focus to its title, so keyboard and screen reader users start there.
  const shownStep = useRef(step)
  useEffect(() => {
    // A pending field error wins: the effect below focuses that field instead.
    if (shownStep.current !== step && !focusFirstError.current) headingRef.current?.focus()
    shownStep.current = step
  }, [step])

  // After a failed "Continuar" or a server refusal, focus the first field with an error.
  useEffect(() => {
    if (!focusFirstError.current) return
    // The field may only appear once the step has changed (server errors on another step).
    const field = formRef.current?.querySelector<HTMLElement>('[aria-invalid="true"]')
    if (!field) return
    focusFirstError.current = false
    field.focus()
  })

  const goTo = (target: StepId) => setParams({ step: target })

  const change = (changes: Partial<OrganizationForm>) => {
    setForm((f) => ({ ...f, ...changes }))
    setServerErrors((e) => Object.fromEntries(Object.entries(e).filter(([k]) => !(k in changes))))
    setNotice(null)
    register.reset()
  }

  const submit = () => {
    const body = toOrganizationInput(form)
    register.mutate(body, {
      onSuccess: (profile) => {
        finished.current = true
        clearDraft(userId)
        markRegistered(userId, profile.name)
        setRegistered({ profile, form })
      },
      onError: (error) => {
        const failure = describeFailure(error)
        if ('reauthenticate' in failure) {
          // The 409 carries no name. The mark still turns a return here, before the new token, into "already registered".
          finished.current = true
          clearDraft(userId)
          markRegistered(userId)
          reauthenticate('/')
        } else if ('fields' in failure) {
          setServerErrors(failure.fields)
          focusFirstError.current = true
          goTo(failure.step)
        }
      },
    })
  }

  const next = (e: FormEvent) => {
    e.preventDefault()
    if (step === 'summary') return submit()
    setAttempted((a) => new Set(a).add(step))
    const stepErrors = { ...validateStep(step, form), ...serverErrors }
    if (STEPS[stepIndex(step)].fields.some((f) => stepErrors[f])) {
      focusFirstError.current = true
      return undefined
    }
    goTo(STEPS[stepIndex(step) + 1].id)
    return undefined
  }

  const saveForLater = () => {
    setNotice(saveDraft(userId, form)
      ? 'Guardado neste navegador. Pode fechar a página e continuar mais tarde a partir daqui.'
      : 'Não foi possível guardar neste navegador. Termine o registo antes de fechar a página.')
  }

  if (registered) return <RegistrationDone name={registered.profile.name} form={registered.form} />
  // Registered earlier but never signed in again: the token is stale, so offer the sign-in, not an empty form.
  if (registeredBefore) return <RegistrationDone name={registeredBefore.name} />

  const index = stepIndex(step)
  const failure = register.isError ? describeFailure(register.error) : null
  const StepFields = step === 'summary' ? null : STEP_FIELDS[step]

  return (
    <div className="flex flex-col gap-8">
      <header className="flex flex-col gap-2">
        <h1 className="text-3xl font-extrabold tracking-tight text-gray-900">Registe a sua organização</h1>
        <p className="text-base text-gray-700">Com estes dados indicamos que leis se aplicam à empresa. Encontra a maioria na certidão permanente.</p>
      </header>

      <Stepper steps={STEPS} current={step} />

      {restored && !notice && (
        <p className="rounded-md bg-gray-100 px-4 py-3 text-sm text-gray-800">Retomámos o registo que deixou a meio. Confirme os dados e continue quando quiser.</p>
      )}

      <form ref={formRef} noValidate onSubmit={next} aria-labelledby="passo-titulo" className="flex flex-col gap-6">
        <h2 id="passo-titulo" ref={headingRef} tabIndex={-1} className="text-xl font-bold text-gray-900 outline-none">
          <span className="block text-sm font-semibold text-gray-600">Passo {index + 1} de {STEPS.length}</span>
          {step === 'summary' ? 'Confirme os dados' : STEPS[index].label}
        </h2>

        {StepFields
          ? <StepFields form={form} errors={errors} onChange={change} userName={userName} />
          : <Summary form={form} onEdit={goTo} />}

        {failure && 'message' in failure && (
          <p role="alert" className="rounded-md bg-red-50 px-4 py-3 text-sm font-semibold text-red-800">{failure.message}</p>
        )}
        <p role="status" className="text-sm text-gray-700 empty:hidden">{notice}</p>

        <div className="flex flex-wrap items-center gap-3 max-sm:flex-col-reverse max-sm:items-stretch">
          {index > 0 && (
            <button type="button" className={buttonClass.secondary} onClick={() => goTo(STEPS[index - 1].id)} disabled={register.isPending}>
              Voltar
            </button>
          )}
          <button type="submit" className={buttonClass.primary} disabled={register.isPending}>
            {step === 'summary' ? (register.isPending ? 'A registar…' : 'Registar organização') : 'Continuar'}
          </button>
          <button type="button" className={`${buttonClass.link} sm:ml-auto`} onClick={saveForLater} disabled={register.isPending}>
            Guardar e continuar mais tarde
          </button>
        </div>
      </form>
    </div>
  )
}

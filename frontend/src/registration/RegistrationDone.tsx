import { useHost } from '@alinha/plugin-sdk'
import { buttonClass } from './fields'
import type { OrganizationForm } from './organizationForm'
import { Summary } from './Summary'

/**
 * End of the wizard: the organization is registered and the account is now its designated responsible person.
 * Without `form`, the user registered on an earlier visit and only needs to sign in again; `name` is null when
 * that visit ended with a 409 (the account already had an organization), which does not say which one.
 */
export function RegistrationDone({ name, form, onRegisterAgain }: {
  name: string | null
  form?: OrganizationForm
  /** On an earlier visit's mark: lets the person get back to the form if signing in again did not help. */
  onRegisterAgain?: () => void
}) {
  const { reauthenticate } = useHost()
  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-2">
        <p className="text-sm font-semibold uppercase tracking-wide text-gray-600">Registo concluído</p>
        <h1 className="text-3xl font-extrabold tracking-tight text-gray-900">{name ? `A ${name} está registada` : 'A sua organização já está registada'}</h1>
        <p className="text-base text-gray-700">
          {form
            ? 'A organização ficou associada à sua conta. É o Responsável designado: vê toda a informação da empresa e pode convidar a sua equipa.'
            : 'O registo já foi feito. Entre de novo para abrir a área da sua organização.'}
        </p>
      </header>
      {form && <Summary form={form} />}
      <div className="flex flex-wrap items-center gap-3 max-sm:flex-col max-sm:items-stretch">
        {/* A new sign-in brings the token with the new organization (ADR 0004). */}
        <button type="button" className={`${buttonClass.primary} max-sm:w-full`} onClick={() => reauthenticate('/')}>
          Entrar na organização
        </button>
        {onRegisterAgain && (
          <button type="button" className={buttonClass.link} onClick={onRegisterAgain}>
            Já entrei de novo e continuo sem organização
          </button>
        )}
      </div>
    </div>
  )
}

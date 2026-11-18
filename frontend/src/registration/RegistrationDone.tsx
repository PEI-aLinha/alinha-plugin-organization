import { useHost } from '@alinha/plugin-sdk'
import { buttonClass } from './fields'
import type { OrganizationForm } from './organizationForm'
import { Summary } from './Summary'

/** End of the wizard: the organization is registered and the account is now its designated responsible person. */
export function RegistrationDone({ name, form }: { name: string; form: OrganizationForm }) {
  const { reauthenticate } = useHost()
  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-2">
        <p className="text-sm font-semibold uppercase tracking-wide text-gray-600">Registo concluído</p>
        <h1 className="text-3xl font-extrabold tracking-tight text-gray-900">A {name} está registada</h1>
        <p className="text-base text-gray-700">
          A organização ficou associada à sua conta. É o Responsável designado: vê toda a informação da empresa e pode convidar a sua equipa.
        </p>
      </header>
      <Summary form={form} />
      <div>
        {/* A new sign-in brings the token with the new organization (ADR 0004). */}
        <button type="button" className={`${buttonClass.primary} max-sm:w-full`} onClick={() => reauthenticate('/')}>
          Entrar na organização
        </button>
      </div>
    </div>
  )
}

import { useHost } from '@alinha/plugin-sdk'
import { Button, PageHeader } from '@alinha/ui'
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
      <PageHeader
        title={name ? `A ${name} está registada` : 'A sua organização já está registada'}
        subtitle={form
          ? 'A organização ficou associada à sua conta. É o Responsável designado: vê toda a informação da empresa e pode convidar a sua equipa.'
          : 'O registo já foi feito. Entre de novo para abrir a área da sua organização.'}
      />
      {form && <Summary form={form} />}
      <div className="flex flex-wrap items-center gap-3 max-sm:flex-col max-sm:items-stretch">
        {/* A new sign-in brings the token with the new organization (ADR 0004). */}
        <Button className="max-sm:w-full" onClick={() => reauthenticate('/')}>
          Entrar na organização
        </Button>
        {onRegisterAgain && (
          <Button variant="link" onClick={onRegisterAgain}>
            Já entrei de novo e continuo sem organização
          </Button>
        )}
      </div>
    </div>
  )
}

import { useState } from 'react'
import { useLocation } from 'react-router'
import { MailOpen } from 'lucide-react'
import { ApiError, errorMessage, useApiMutation, useHost } from '@alinha/plugin-sdk'
import { Button, Card, EmptyState, Logo } from '@alinha/ui'

/**
 * `/onboarding/invitation#<token>`: a new account joins the organization that invited it (organization#6, ADR 0007).
 * The person already created their account (Keycloak registration, verified email and MFA); the shell kept this
 * address across the login. After joining, `reauthenticate()` gets the new session and the shell leaves the
 * onboarding for the organization.
 */
export function AcceptInvitationPage() {
  const { hash } = useLocation()
  const { reauthenticate } = useHost()
  const token = safeDecode(hash.replace(/^#/, '')).trim()
  const [refreshError, setRefreshError] = useState<unknown>(null)
  const accept = useApiMutation<{ token: string }, void>({
    path: '/organization/invitations/accept',
    onSuccess: async () => {
      try {
        await reauthenticate()
      } catch (e) {
        setRefreshError(e)
      }
    },
  })

  let content
  if (!token) {
    content = (
      <EmptyState icon={<MailOpen className="size-5" aria-hidden />} title="Este link de convite não está completo" headingLevel={1}>
        Abra outra vez o link que recebeu, sem o alterar. Se não funcionar, peça um convite novo a quem o enviou.
      </EmptyState>
    )
  } else {
    const error = accept.isError ? acceptError(accept.error) : refreshError ? errorMessage(refreshError) : null
    content = (
      <Card className="flex flex-col gap-5 px-7 py-7 max-md:px-5">
        <header className="flex flex-col gap-2">
          <h1 className="text-[1.875rem] font-extrabold title-tight">Juntar-se à equipa</h1>
          <p className="text-[1rem] text-ink-soft">
            Foi convidado para trabalhar como analista numa organização no aLinha. Como analista, vê só as tarefas
            que lhe forem atribuídas.
          </p>
        </header>
        {error && <p role="alert" className="rounded-[10px] bg-risk-high px-3.5 py-2.5 text-[0.8125rem] font-semibold text-risk-high-ink">{error}</p>}
        <div>
          <Button loading={accept.isPending} disabled={accept.isSuccess && !refreshError}
            onClick={() => (refreshError ? void retryRefresh() : accept.mutate({ token }))}>
            {refreshError ? 'Tentar de novo' : 'Aceitar convite'}
          </Button>
        </div>
      </Card>
    )
  }

  async function retryRefresh() {
    setRefreshError(null)
    try {
      await reauthenticate()
    } catch (e) {
      setRefreshError(e)
    }
  }

  return (
    <div className="min-h-dvh bg-paper-soft px-5 pb-28 pt-10 max-md:pt-6">
      <div className="mx-auto flex max-w-[640px] flex-col gap-7">
        <header><Logo /></header>
        <main id="conteudo" className="flex flex-col gap-7">{content}</main>
      </div>
    </div>
  )
}

/**
 * A 403 without a `code` means the account cannot accept at all: usually its email is not verified yet (the contract
 * lists the cases). Say what to do instead of the generic "no permission".
 */
export const CANNOT_ACCEPT =
  'Esta conta ainda não pode aceitar convites. Confirme o seu email, entre de novo e volte a abrir este link.'

function acceptError(error: unknown): string {
  if (error instanceof ApiError && error.status === 403 && !error.problem?.code) return CANNOT_ACCEPT
  return errorMessage(error)
}

function safeDecode(value: string): string {
  try {
    return decodeURIComponent(value)
  } catch {
    return ''
  }
}

import { useId, useState, type FormEvent } from 'react'
import { errorMessage, fieldErrors, formatDate, useApiMutation } from '@alinha/plugin-sdk'
import { Button, Field, Modal, Note, TextInput } from '@alinha/ui'
import { invitationLink, TEAM_KEY, type IssuedInvitation } from './types'

/**
 * Invites an analyst, or sends an invitation again ([initialEmail]). Until the invitation email exists
 * (notifications#3) the link is shown here to copy and send.
 */
export function InviteModal({ initialEmail = '', onClose }: { initialEmail?: string; onClose: () => void }) {
  const formId = useId()
  const [email, setEmail] = useState(initialEmail)
  const [missing, setMissing] = useState(false)
  const [copied, setCopied] = useState(false)
  const invite = useApiMutation<{ email: string }, IssuedInvitation>({
    path: '/organization/invitations',
    invalidates: [[...TEAM_KEY]],
  })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (!email.trim()) {
      setMissing(true)
      return
    }
    invite.mutate({ email: email.trim() })
  }

  const issued = invite.data
  if (issued) {
    const link = invitationLink(issued.token)
    const copy = async () => {
      try {
        await navigator.clipboard.writeText(link)
        setCopied(true)
      } catch {
        setCopied(false)
      }
    }
    return (
      <Modal open onOpenChange={(open) => !open && onClose()} title="Convite criado"
        description={`Envie este link a ${issued.email}. Só funciona uma vez, até ${formatDate(issued.expiresAt)}.`}
        footer={<Button onClick={onClose}>Concluir</Button>}>
        <div className="flex flex-col gap-3">
          <Field label="Link do convite">
            {(p) => <TextInput id={p.id} value={link} readOnly onFocus={(e) => e.currentTarget.select()} />}
          </Field>
          <div className="flex items-center gap-3">
            <Button variant="secondary" size="sm" onClick={() => void copy()}>Copiar link</Button>
            {copied && <span role="status" className="text-[0.8125rem] text-ink-soft">Link copiado.</span>}
          </div>
          <Note>
            A pessoa cria a sua conta no aLinha com este email e depois aceita o convite. Como analista, vê só as
            tarefas que lhe atribuir.
          </Note>
        </div>
      </Modal>
    )
  }

  const errors = fieldErrors(invite.error)
  const emailError = missing && !email.trim() ? 'Indique o email do analista.' : errors.email
  const otherError = invite.isError && !errors.email ? errorMessage(invite.error) : null
  return (
    <Modal open onOpenChange={(open) => !open && onClose()} title={initialEmail ? 'Reenviar convite' : 'Convidar analista'}
      description="O analista só vê as tarefas que lhe atribuir. Pode ser um técnico externo."
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancelar</Button>
          <Button type="submit" form={formId} loading={invite.isPending}>Criar convite</Button>
        </>
      }>
      <form id={formId} noValidate onSubmit={submit} className="flex flex-col gap-4">
        <Field label="Email" error={emailError}>
          {(p) => (
            <TextInput id={p.id} type="email" autoComplete="off" invalid={p.invalid} aria-describedby={p.describedBy}
              value={email} onChange={(e) => setEmail(e.target.value)} placeholder="nome@empresa.pt" autoFocus />
          )}
        </Field>
        {initialEmail && <p className="text-[0.8125rem] text-ink-soft">O convite anterior deixa de funcionar.</p>}
        {otherError && <p role="alert" className="text-[0.8125rem] font-semibold text-danger">{otherError}</p>}
      </form>
    </Modal>
  )
}

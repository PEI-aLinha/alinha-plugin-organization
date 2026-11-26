import { useState } from 'react'
import { UserPlus, Users } from 'lucide-react'
import { errorMessage, formatDate, useApiMutation, useApiQuery, useHost } from '@alinha/plugin-sdk'
import { Badge, Button, Card, EmptyState, ErrorState, Modal, PageHeader, SkeletonRows } from '@alinha/ui'
import { InviteModal } from './InviteModal'
import { ROLE_LABEL, TEAM_KEY, type Invitation, type Member } from './types'

type Confirm = { kind: 'remove'; member: Member } | { kind: 'cancel'; invitation: Invitation }

/** The Designated Officer's team page (organization#6, FR05, US12): members and pending invitations. */
export function TeamPage() {
  const { can } = useHost()
  const manage = can('member', 'manage')
  const members = useApiQuery<Member[]>([...TEAM_KEY, 'members'], '/organization/members')
  const invitations = useApiQuery<Invitation[]>([...TEAM_KEY, 'invitations'], '/organization/invitations')
  const [inviting, setInviting] = useState<{ email?: string } | null>(null)
  const [confirm, setConfirm] = useState<Confirm | null>(null)

  const header = (
    <PageHeader title="Equipa" subtitle="Quem trabalha consigo no aLinha."
      actions={manage && (
        <Button icon={<UserPlus className="size-4" aria-hidden />} onClick={() => setInviting({})}>Convidar analista</Button>
      )} />
  )

  if (members.isPending || invitations.isPending) {
    return <>{header}<SkeletonRows rows={4} label="A carregar a equipa…" /></>
  }
  if (members.isError || invitations.isError) {
    const failed = members.isError ? members : invitations
    return <>{header}<ErrorState error={failed.error} onRetry={() => void failed.refetch()} /></>
  }

  const analysts = members.data.filter((m) => m.role === 'analyst')
  return (
    <>
      {header}
      <section aria-labelledby="team-members" className="flex flex-col gap-3">
        <h2 id="team-members" className="section-title">Membros</h2>
        <Card>
          <ul role="list" aria-label="Membros da organização" className="flex flex-col divide-y divide-line-soft">
            {members.data.map((m) => (
              <li key={m.id} className="flex flex-wrap items-center justify-between gap-3 py-3 first:pt-0 last:pb-0">
                <div className="flex min-w-0 flex-col">
                  <span className="font-semibold text-ink">{m.name ?? m.email ?? 'Sem nome'}</span>
                  {m.name && m.email && <span className="text-[0.8125rem] text-ink-soft">{m.email}</span>}
                </div>
                <div className="flex items-center gap-3">
                  <Badge tone={m.role === 'responsible' ? 'dark' : 'neutral'}>{ROLE_LABEL[m.role]}</Badge>
                  {manage && m.role === 'analyst' && (
                    <Button variant="ghost" size="sm" onClick={() => setConfirm({ kind: 'remove', member: m })}
                      aria-label={`Remover ${m.name ?? m.email}`}>Remover</Button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        </Card>
        {analysts.length === 0 && invitations.data.length === 0 && (
          <EmptyState icon={<Users className="size-5" aria-hidden />} title="Ainda sem analistas" headingLevel={3}>
            Convide quem o ajuda com a cibersegurança, da sua equipa ou de fora. Cada analista vê só as tarefas que
            lhe atribuir.
          </EmptyState>
        )}
      </section>

      {invitations.data.length > 0 && (
        <section aria-labelledby="team-invitations" className="mt-8 flex flex-col gap-3">
          <h2 id="team-invitations" className="section-title">Convites pendentes</h2>
          <Card>
            <ul role="list" aria-label="Convites pendentes" className="flex flex-col divide-y divide-line-soft">
              {invitations.data.map((inv) => (
                <li key={inv.id} className="flex flex-wrap items-center justify-between gap-3 py-3 first:pt-0 last:pb-0">
                  <div className="flex min-w-0 flex-col">
                    <span className="font-semibold text-ink">{inv.email}</span>
                    <span className="text-[0.8125rem] text-ink-soft">
                      {inv.expired ? 'Expirou' : `Válido até ${formatDate(inv.expiresAt)}`}
                    </span>
                  </div>
                  {manage && (
                    <div className="flex items-center gap-2">
                      <Button variant="secondary" size="sm" onClick={() => setInviting({ email: inv.email })}
                        aria-label={`Reenviar o convite a ${inv.email}`}>Reenviar</Button>
                      <Button variant="ghost" size="sm" onClick={() => setConfirm({ kind: 'cancel', invitation: inv })}
                        aria-label={`Cancelar o convite a ${inv.email}`}>Cancelar convite</Button>
                    </div>
                  )}
                </li>
              ))}
            </ul>
          </Card>
        </section>
      )}

      {inviting && <InviteModal initialEmail={inviting.email} onClose={() => setInviting(null)} />}
      {confirm && <ConfirmModal confirm={confirm} onClose={() => setConfirm(null)} />}
    </>
  )
}

function ConfirmModal({ confirm, onClose }: { confirm: Confirm; onClose: () => void }) {
  const action = useApiMutation<void, unknown>({
    method: 'DELETE',
    path: confirm.kind === 'remove'
      ? `/organization/members/${encodeURIComponent(confirm.member.id)}`
      : `/organization/invitations/${encodeURIComponent(confirm.invitation.id)}`,
    invalidates: [[...TEAM_KEY]],
    onSuccess: onClose,
  })
  const who = confirm.kind === 'remove' ? (confirm.member.name ?? confirm.member.email ?? 'esta pessoa') : confirm.invitation.email
  return (
    <Modal open onOpenChange={(open) => !open && onClose()}
      title={confirm.kind === 'remove' ? `Remover ${who}?` : 'Cancelar o convite?'}
      description={confirm.kind === 'remove'
        ? `${who} deixa de ter acesso à organização. O que já registou fica guardado.`
        : `O link enviado a ${who} deixa de funcionar.`}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Voltar</Button>
          <Button variant="danger" loading={action.isPending} onClick={() => action.mutate(undefined)}>
            {confirm.kind === 'remove' ? 'Remover da equipa' : 'Cancelar convite'}
          </Button>
        </>
      }>
      {action.isError && <p role="alert" className="text-[0.8125rem] font-semibold text-danger">{errorMessage(action.error)}</p>}
    </Modal>
  )
}

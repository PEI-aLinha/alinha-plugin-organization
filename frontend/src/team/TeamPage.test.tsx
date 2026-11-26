import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ApiError, type PluginHost } from '@alinha/plugin-sdk'
import { renderWithHost } from '../test/renderWithHost'
import { TeamPage } from './TeamPage'
import { invitationLink, type Invitation, type Member } from './types'

const OFFICER: Member = { id: 'u-rui', name: 'Rui Santos', email: 'rui@compagueda.pt', role: 'responsible' }
const ANALYST: Member = { id: 'u-pedro', name: 'Pedro Almeida', email: 'pedro@compagueda.pt', role: 'analyst' }
const PENDING: Invitation = {
  id: 'inv-1', email: 'ana@compagueda.pt', createdAt: '2026-11-20T10:00:00Z', expiresAt: '2026-11-27T10:00:00Z', expired: false,
}

type Call = { path: string; init?: RequestInit }

function fakeApi(members: Member[], invitations: Invitation[], onWrite?: (call: Call) => unknown) {
  const calls: Call[] = []
  const api = vi.fn(async (path: string, init?: RequestInit) => {
    calls.push({ path, init })
    if (!init?.method) return path.endsWith('/members') ? members : invitations
    return onWrite?.({ path, init })
  }) as unknown as PluginHost['api']
  return { api, calls }
}

const renderPage = (api: PluginHost['api'], can: PluginHost['can'] = () => true) =>
  renderWithHost(<TeamPage />, { host: { api, can } })

describe('TeamPage', () => {
  it('lists the members with their role and the pending invitations', async () => {
    renderPage(fakeApi([OFFICER, ANALYST], [PENDING]).api)

    const members = await screen.findByRole('list', { name: 'Membros da organização' })
    expect(within(members).getByText('Rui Santos')).toBeInTheDocument()
    expect(within(members).getByText('Responsável')).toBeInTheDocument()
    expect(within(members).getByText('Analista')).toBeInTheDocument()
    const pending = screen.getByRole('list', { name: 'Convites pendentes' })
    expect(within(pending).getByText('ana@compagueda.pt')).toBeInTheDocument()
    expect(within(pending).getByText(/Válido até/)).toBeInTheDocument()
  })

  it('never offers to remove the Designated Officer', async () => {
    renderPage(fakeApi([OFFICER, ANALYST], []).api)

    expect(await screen.findByRole('button', { name: 'Remover Pedro Almeida' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Remover Rui Santos' })).not.toBeInTheDocument()
  })

  it('shows an empty state when there are no analysts yet', async () => {
    renderPage(fakeApi([OFFICER], []).api)

    expect(await screen.findByText('Ainda sem analistas')).toBeInTheDocument()
  })

  it('invites an analyst and shows the link to send, with the secret in the fragment', async () => {
    const { api, calls } = fakeApi([OFFICER], [], () => ({
      id: 'inv-2', email: 'ana@compagueda.pt', createdAt: '2026-11-26T10:00:00Z', expiresAt: '2026-12-03T10:00:00Z', token: 'segredo-123',
    }))
    renderPage(api)

    fireEvent.click(await screen.findByRole('button', { name: 'Convidar analista' }))
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'ana@compagueda.pt' } })
    fireEvent.click(screen.getByRole('button', { name: 'Criar convite' }))

    expect(await screen.findByText('Convite criado')).toBeInTheDocument()
    expect(screen.getByLabelText('Link do convite')).toHaveValue(invitationLink('segredo-123'))
    expect(invitationLink('segredo-123', 'https://alinha.pt')).toBe('https://alinha.pt/onboarding/invitation#segredo-123')
    const post = calls.find((c) => c.init?.method === 'POST')!
    expect(post.path).toBe('/organization/invitations')
    expect(JSON.parse(post.init!.body as string)).toEqual({ email: 'ana@compagueda.pt' })
  })

  it('asks for the email before sending anything', async () => {
    const { api, calls } = fakeApi([OFFICER], [])
    renderPage(api)

    fireEvent.click(await screen.findByRole('button', { name: 'Convidar analista' }))
    fireEvent.click(screen.getByRole('button', { name: 'Criar convite' }))

    expect(await screen.findByText('Indique o email do analista.')).toBeInTheDocument()
    expect(calls.some((c) => c.init?.method === 'POST')).toBe(false)
  })

  it('shows the server field error next to the email', async () => {
    const { api } = fakeApi([OFFICER], [], () => {
      throw new ApiError(422, '/organization/invitations', undefined, {
        code: 'INVALID_EMAIL', detail: 'Há dados por corrigir.',
        errors: [{ field: 'email', message: 'Este email não parece válido. Exemplo: nome@empresa.pt' }],
      })
    })
    renderPage(api)

    fireEvent.click(await screen.findByRole('button', { name: 'Convidar analista' }))
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'ana' } })
    fireEvent.click(screen.getByRole('button', { name: 'Criar convite' }))

    expect(await screen.findByText('Este email não parece válido. Exemplo: nome@empresa.pt')).toBeInTheDocument()
  })

  it('removes an analyst after confirming', async () => {
    const { api, calls } = fakeApi([OFFICER, ANALYST], [], () => undefined)
    renderPage(api)

    fireEvent.click(await screen.findByRole('button', { name: 'Remover Pedro Almeida' }))
    fireEvent.click(screen.getByRole('button', { name: 'Remover da equipa' }))

    await waitFor(() => expect(calls.some((c) => c.init?.method === 'DELETE')).toBe(true))
    expect(calls.find((c) => c.init?.method === 'DELETE')!.path).toBe('/organization/members/u-pedro')
  })

  it('cancels a pending invitation after confirming', async () => {
    const { api, calls } = fakeApi([OFFICER], [PENDING], () => undefined)
    renderPage(api)

    fireEvent.click(await screen.findByRole('button', { name: 'Cancelar o convite a ana@compagueda.pt' }))
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar convite' }))

    await waitFor(() => expect(calls.find((c) => c.init?.method === 'DELETE')?.path).toBe('/organization/invitations/inv-1'))
  })

  it('offers no changes without member:manage', async () => {
    renderPage(fakeApi([OFFICER, ANALYST], [PENDING]).api, (resource, action) => !(resource === 'member' && action === 'manage'))

    await screen.findByRole('list', { name: 'Membros da organização' })
    expect(screen.queryByRole('button', { name: 'Convidar analista' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Remover/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Cancelar o convite/ })).not.toBeInTheDocument()
  })
})

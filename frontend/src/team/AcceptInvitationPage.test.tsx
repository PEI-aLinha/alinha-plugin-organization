import { fireEvent, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ApiError, type PluginHost } from '@alinha/plugin-sdk'
import { renderWithHost } from '../test/renderWithHost'
import { AcceptInvitationPage } from './AcceptInvitationPage'

describe('AcceptInvitationPage', () => {
  it('accepts with the token from the fragment, then refreshes the session', async () => {
    const api = vi.fn(async () => undefined) as unknown as PluginHost['api']
    const reauthenticate = vi.fn(async () => undefined)
    renderWithHost(<AcceptInvitationPage />, { url: '/onboarding/invitation#segredo-123', host: { api, reauthenticate } })

    fireEvent.click(screen.getByRole('button', { name: 'Aceitar convite' }))

    await waitFor(() => expect(reauthenticate).toHaveBeenCalledTimes(1))
    expect(api).toHaveBeenCalledWith('/organization/invitations/accept', expect.objectContaining({
      method: 'POST', body: JSON.stringify({ token: 'segredo-123' }),
    }))
  })

  it('explains a link without its secret and sends nothing', () => {
    const api = vi.fn() as unknown as PluginHost['api']
    renderWithHost(<AcceptInvitationPage />, { url: '/onboarding/invitation', host: { api } })

    expect(screen.getByText('Este link de convite não está completo')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Aceitar convite' })).not.toBeInTheDocument()
    expect(api).not.toHaveBeenCalled()
  })

  it('shows the server message when the invitation is for another email', async () => {
    const detail = 'Este convite foi enviado para outro email. Entre com a conta desse email para o aceitar.'
    const api = vi.fn(async () => {
      throw new ApiError(403, '/organization/invitations/accept', undefined, { code: 'INVITATION_FOR_ANOTHER_EMAIL', detail })
    }) as unknown as PluginHost['api']
    const reauthenticate = vi.fn()
    renderWithHost(<AcceptInvitationPage />, { url: '/onboarding/invitation#segredo-123', host: { api, reauthenticate } })

    fireEvent.click(screen.getByRole('button', { name: 'Aceitar convite' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(detail)
    expect(reauthenticate).not.toHaveBeenCalled()
  })

  it('lets the person retry the session refresh without accepting twice', async () => {
    const api = vi.fn(async () => undefined) as unknown as PluginHost['api']
    const reauthenticate = vi.fn().mockRejectedValueOnce(new ApiError(503, '/auth/refresh')).mockResolvedValueOnce(undefined)
    renderWithHost(<AcceptInvitationPage />, { url: '/onboarding/invitation#segredo-123', host: { api, reauthenticate } })

    fireEvent.click(screen.getByRole('button', { name: 'Aceitar convite' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Tentar de novo' }))

    await waitFor(() => expect(reauthenticate).toHaveBeenCalledTimes(2))
    expect(api).toHaveBeenCalledTimes(1)
  })
})

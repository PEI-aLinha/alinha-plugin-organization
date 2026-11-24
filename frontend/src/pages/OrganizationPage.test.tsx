import { fireEvent, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { ApiResponse } from '@alinha/api-client'
import { ApiError, type PluginHost } from '@alinha/plugin-sdk'
import { renderWithHost } from '../test/renderWithHost'
import { OrganizationPage } from './OrganizationPage'

type OrganizationProfile = NonNullable<ApiResponse<'/api/organization/profile'>>

const PROFILE: OrganizationProfile = {
  id: '6f1c2a54-3b0e-4d7a-9c1f-2e8b5d4a7c90',
  version: 1,
  name: 'Componentes Águeda, Lda.',
  nif: '509180027',
  cae: '29320',
  sector: 'industria',
  subsector: 'automoveis',
  sectorAnnex: 'II',
  services: 'Fabrico de componentes para a indústria automóvel.',
  employees: 12000,
  turnover: 18500000,
  address: { street: 'Rua da Zona Industrial do Casarão, 45', postalCode: '3750-750', city: 'Águeda' },
  contacts: { phone: '234600120', email: 'geral@compagueda.pt' },
  permanentContact: { name: 'Rui Santos', phone: '912345678' },
  domain: null,
  registeredAt: '2026-11-01T10:00:00Z',
  updatedAt: '2026-11-01T10:00:00Z',
}

const renderPage = (api: PluginHost['api']) => renderWithHost(<OrganizationPage />, { host: { api } })

describe('OrganizationPage', () => {
  it('shows a loading state while the profile is on its way', () => {
    renderPage(vi.fn(() => new Promise<never>(() => {})) as PluginHost['api'])

    expect(screen.getByRole('status', { name: 'A carregar os dados da organização…' })).toBeInTheDocument()
  })

  it('shows an empty state when the organization is not registered yet', async () => {
    const api = vi.fn(async () => {
      throw new ApiError(404, '/organization/profile', undefined, { code: 'ORGANIZATION_NOT_REGISTERED' })
    }) as PluginHost['api']
    renderPage(api)

    expect(await screen.findByText('Ainda sem dados da organização')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Tentar de novo' })).not.toBeInTheDocument()
  })

  it('shows an error with a retry for a 404 that is not the not-registered one', async () => {
    const api = vi.fn(async () => {
      throw new ApiError(404, '/organization/profile')
    }) as PluginHost['api']
    renderPage(api)

    expect(await screen.findByRole('button', { name: 'Tentar de novo' })).toBeInTheDocument()
    expect(screen.queryByText('Ainda sem dados da organização')).not.toBeInTheDocument()
  })

  it('shows an error with a retry that loads the profile again', async () => {
    const api = vi.fn()
      .mockRejectedValueOnce(new ApiError(500, '/organization/profile'))
      .mockResolvedValueOnce(PROFILE) as unknown as PluginHost['api']
    renderPage(api)

    fireEvent.click(await screen.findByRole('button', { name: 'Tentar de novo' }))

    expect(await screen.findByText('509180027')).toBeInTheDocument()
    expect(api).toHaveBeenCalledTimes(2)
  })

  it('shows the profile with the sector label instead of its id', async () => {
    const api = vi.fn(async () => PROFILE) as unknown as PluginHost['api']
    renderPage(api)

    expect(await screen.findByText('Indústria transformadora · Fabrico de veículos automóveis, reboques e componentes')).toBeInTheDocument()
    expect(screen.queryByText(/industria|automoveis/)).not.toBeInTheDocument()
    expect(screen.getByText('Componentes Águeda, Lda.')).toBeInTheDocument()
    expect(screen.getByText('12 000')).toBeInTheDocument()
    expect(api).toHaveBeenCalledWith('/organization/profile', expect.objectContaining({ signal: expect.any(AbortSignal) }))
  })

  it('links to the regulatory scope', async () => {
    renderPage(vi.fn(async () => PROFILE) as unknown as PluginHost['api'])

    const link = await screen.findByRole('link', { name: 'Ver o âmbito regulatório' })
    expect(link.getAttribute('href')).toMatch(/\/scope$/)
  })

  it('lets an editor give the domain, sending the whole profile with it', async () => {
    const api = vi.fn(async (_path: string, init?: RequestInit) =>
      init?.method === 'PUT' ? { ...PROFILE, version: 2, domain: 'compagueda.pt' } : PROFILE,
    ) as unknown as PluginHost['api']
    renderPage(api)

    fireEvent.click(await screen.findByRole('button', { name: 'Indicar o domínio' }))
    fireEvent.change(screen.getByLabelText(/Domínio da organização/), { target: { value: 'https://www.CompAgueda.pt/' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }))

    await vi.waitFor(() => expect(api).toHaveBeenCalledWith('/organization/profile', expect.objectContaining({ method: 'PUT' })))
    const put = (api as unknown as ReturnType<typeof vi.fn>).mock.calls.find(([, init]) => init?.method === 'PUT')
    expect(JSON.parse(put?.[1]?.body as string)).toMatchObject({ version: 1, nif: '509180027', domain: 'compagueda.pt' })
  })

  it('refuses something that is not a domain without calling the server', async () => {
    const api = vi.fn(async () => PROFILE) as unknown as PluginHost['api']
    renderPage(api)

    fireEvent.click(await screen.findByRole('button', { name: 'Indicar o domínio' }))
    fireEvent.change(screen.getByLabelText(/Domínio da organização/), { target: { value: '10.0.0.1' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }))

    expect(await screen.findByText('Indique só o domínio, por exemplo empresa.pt.')).toBeInTheDocument()
    expect(api).toHaveBeenCalledTimes(1)
  })

  it('shows the server message for a shared domain', async () => {
    const api = vi.fn(async (_path: string, init?: RequestInit) => {
      if (init?.method !== 'PUT') return PROFILE
      throw new ApiError(422, '/organization/profile', undefined, {
        errors: [{ field: 'domain', message: 'Este domínio é de um serviço partilhado.' }],
      })
    }) as unknown as PluginHost['api']
    renderPage(api)

    fireEvent.click(await screen.findByRole('button', { name: 'Indicar o domínio' }))
    fireEvent.change(screen.getByLabelText(/Domínio da organização/), { target: { value: 'gmail.com' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }))

    expect(await screen.findByText('Este domínio é de um serviço partilhado.')).toBeInTheDocument()
  })

  it('shows the domain without a change link to someone who cannot edit', async () => {
    const api = vi.fn(async () => ({ ...PROFILE, domain: 'compagueda.pt' })) as unknown as PluginHost['api']
    renderWithHost(<OrganizationPage />, { host: { api, can: () => false } })

    expect(await screen.findByText('compagueda.pt')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /domínio/ })).not.toBeInTheDocument()
  })
})

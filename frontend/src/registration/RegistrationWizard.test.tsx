import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { ApiError, type PluginHost } from '@alinha/plugin-sdk'
import { loadDraft, markRegistered, registeredName, saveDraft } from './draft'
import { EMPTY_ORGANIZATION } from './organizationForm'
import { RegistrationWizard } from './RegistrationWizard'
import { VALID } from '../test/fixtures'
import { renderWithHost } from '../test/renderWithHost'

const type = (label: string | RegExp, value: string) =>
  fireEvent.change(screen.getByLabelText(label), { target: { value } })
const choose = (label: string, value: string) => fireEvent.change(screen.getByLabelText(label), { target: { value } })
const click = (name: string | RegExp) => fireEvent.click(screen.getByRole('button', { name }))
const stepTitle = () => screen.getByRole('heading', { level: 2 })

const PROFILE = { id: 'org-1', version: 0, name: 'Componentes Águeda, Lda.' }
const problem = (status: number, body: object) => new ApiError(status, '/organization/registration', undefined, body)

beforeEach(() => localStorage.clear())
afterEach(() => vi.restoreAllMocks())

function fillCompany() {
  type('Denominação', VALID.name)
  type('NIF', VALID.nif)
  type('Código CAE principal', VALID.cae)
}

describe('RegistrationWizard', () => {
  it('starts on the company step and blocks "Continuar" with inline errors', async () => {
    renderWithHost(<RegistrationWizard />)
    expect(stepTitle()).toHaveTextContent('Passo 1 de 5Empresa')
    click('Continuar')
    expect(await screen.findByText('Indique o nome da empresa, como está na certidão permanente.')).toBeInTheDocument()
    expect(screen.getByLabelText('NIF')).toHaveAttribute('aria-invalid', 'true')
    await waitFor(() => expect(screen.getByLabelText('Denominação')).toHaveFocus())
    expect(stepTitle()).toHaveTextContent('Empresa')
  })

  it('goes through every step to the summary, with the step in the URL', async () => {
    const { router } = renderWithHost(<RegistrationWizard />)
    fillCompany()
    click('Continuar')
    await waitFor(() => expect(stepTitle()).toHaveTextContent('Atividade'))
    expect(router.state.location.search).toBe('?step=activity')
    choose('Setor', 'industria')
    choose('Subsetor', 'automoveis')
    type('Serviços prestados', VALID.services)
    click('Continuar')
    await waitFor(() => expect(stepTitle()).toHaveTextContent('Dimensão'))
    type('Número de trabalhadores', VALID.employees)
    type('Volume de negócios anual (€)', VALID.turnover)
    click('Continuar')
    await waitFor(() => expect(stepTitle()).toHaveTextContent('Contactos'))
    type('Morada', VALID.address)
    type('Código postal', VALID.postalCode)
    type('Localidade', VALID.city)
    type('Telefone', VALID.phone)
    type('Email geral', VALID.email)
    click('Sou eu (Rui Santos)')
    expect(screen.getByLabelText('Nome')).toHaveValue('Rui Santos')
    type('Telefone disponível a qualquer hora', VALID.contactPhone)
    click('Continuar')
    await waitFor(() => expect(stepTitle()).toHaveTextContent('Confirme os dados'))
    expect(screen.getByText('Indústria transformadora · Fabrico de veículos automóveis, reboques e componentes')).toBeInTheDocument()
    expect(stepTitle()).toHaveFocus()
  })

  it('goes back without losing what was typed', async () => {
    renderWithHost(<RegistrationWizard />)
    fillCompany()
    click('Continuar')
    await waitFor(() => expect(stepTitle()).toHaveTextContent('Atividade'))
    click('Voltar')
    await waitFor(() => expect(stepTitle()).toHaveTextContent('Empresa'))
    expect(screen.getByLabelText('Denominação')).toHaveValue(VALID.name)
  })

  it('does not let the URL skip an incomplete step', () => {
    renderWithHost(<RegistrationWizard />, { url: '/?step=summary' })
    expect(stepTitle()).toHaveTextContent('Empresa')
  })

  it('resumes a saved draft where it stopped', () => {
    saveDraft('u1', { ...VALID, employees: '', turnover: '' })
    renderWithHost(<RegistrationWizard />)
    expect(screen.getByText(/Retomámos o registo/)).toBeInTheDocument()
    expect(stepTitle()).toHaveTextContent('Dimensão')
  })

  it('saves the draft on request and while typing', async () => {
    renderWithHost(<RegistrationWizard />)
    type('Denominação', 'Acme')
    click('Guardar e continuar mais tarde')
    expect(screen.getByRole('status')).toHaveTextContent('Guardado neste navegador')
    expect(loadDraft('u1')).toEqual({ ...EMPTY_ORGANIZATION, name: 'Acme' })
    type('NIF', '5')
    await waitFor(() => expect(loadDraft('u1')?.nif).toBe('5'))
  })

  it('registers the organization, clears the draft and offers to sign in again', async () => {
    saveDraft('u1', VALID)
    const api = vi.fn(async () => PROFILE) as unknown as PluginHost['api']
    const { host } = renderWithHost(<RegistrationWizard />, { url: '/?step=summary', host: { api } })
    click('Registar organização')
    expect(await screen.findByRole('heading', { name: 'A Componentes Águeda, Lda. está registada' })).toBeInTheDocument()
    const [path, init] = vi.mocked(api).mock.calls[0] as [string, RequestInit]
    expect(path).toBe('/organization/registration')
    expect(init.method).toBe('POST')
    expect(JSON.parse(String(init.body))).toMatchObject({ nif: '509180027', employees: 120, contacts: { email: 'geral@compagueda.pt' } })
    expect(loadDraft('u1')).toBeNull()
    expect(registeredName('u1')).toBe('Componentes Águeda, Lda.')
    click('Entrar na organização')
    expect(host.reauthenticate).toHaveBeenCalledWith('/')
  })

  it('offers to sign in again, not an empty form, when the account registered on an earlier visit', () => {
    markRegistered('u1', 'Componentes Águeda, Lda.')
    const { host } = renderWithHost(<RegistrationWizard />)
    expect(screen.getByRole('heading', { name: 'A Componentes Águeda, Lda. está registada' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Denominação')).not.toBeInTheDocument()
    click('Entrar na organização')
    expect(host.reauthenticate).toHaveBeenCalledWith('/')
  })

  it('tells screen readers which steps are done', () => {
    saveDraft('u1', VALID)
    renderWithHost(<RegistrationWizard />, { url: '/?step=size' })
    const steps = screen.getByRole('list', { name: 'Passos do registo' })
    expect(steps.querySelectorAll('li')[0]).toHaveTextContent('Concluído')
    expect(steps.querySelector('[aria-current="step"]')).toHaveTextContent('Dimensão')
  })

  it('shows the server field errors on their step', async () => {
    saveDraft('u1', VALID)
    const api = vi.fn(async () => {
      throw problem(422, { code: 'VALIDATION_FAILED', detail: 'Há dados por corrigir.', errors: [{ field: 'address.postalCode', message: 'Código postal recusado.' }] })
    }) as unknown as PluginHost['api']
    renderWithHost(<RegistrationWizard />, { url: '/?step=summary', host: { api } })
    click('Registar organização')
    expect(await screen.findByText('Código postal recusado.')).toBeInTheDocument()
    expect(stepTitle()).toHaveTextContent('Contactos')
    await waitFor(() => expect(screen.getByLabelText('Código postal')).toHaveFocus())
    type('Código postal', '3750-751')
    expect(screen.queryByText('Código postal recusado.')).not.toBeInTheDocument()
  })

  it('sends a taken NIF back to the company step', async () => {
    saveDraft('u1', VALID)
    const api = vi.fn(async () => {
      throw problem(409, { code: 'NIF_ALREADY_REGISTERED', detail: 'Já existe uma organização registada com este NIF.' })
    }) as unknown as PluginHost['api']
    renderWithHost(<RegistrationWizard />, { url: '/?step=summary', host: { api } })
    click('Registar organização')
    expect(await screen.findByText('Já existe uma organização registada com este NIF.')).toBeInTheDocument()
    expect(stepTitle()).toHaveTextContent('Empresa')
  })

  it('signs in again when the account already has an organization', async () => {
    saveDraft('u1', VALID)
    const api = vi.fn(async () => { throw problem(409, { code: 'ORGANIZATION_ALREADY_EXISTS' }) }) as unknown as PluginHost['api']
    const { host } = renderWithHost(<RegistrationWizard />, { url: '/?step=summary', host: { api } })
    click('Registar organização')
    await waitFor(() => expect(host.reauthenticate).toHaveBeenCalledWith('/'))
    expect(loadDraft('u1')).toBeNull()
  })

  it('shows the server message when registering is not possible right now', async () => {
    saveDraft('u1', VALID)
    const api = vi.fn(async () => {
      throw problem(429, { code: 'TOO_MANY_REGISTRATIONS', detail: 'Foram feitas demasiadas tentativas de registo. Tente novamente mais tarde.' })
    }) as unknown as PluginHost['api']
    renderWithHost(<RegistrationWizard />, { url: '/?step=summary', host: { api } })
    click('Registar organização')
    expect(await screen.findByRole('alert')).toHaveTextContent('Foram feitas demasiadas tentativas de registo.')
  })

  it('shows a plain message on a network failure', async () => {
    saveDraft('u1', VALID)
    const api = vi.fn(async () => { throw new TypeError('Failed to fetch') }) as unknown as PluginHost['api']
    renderWithHost(<RegistrationWizard />, { url: '/?step=summary', host: { api } })
    click('Registar organização')
    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível concluir o registo.')
  })
})

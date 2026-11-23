import { fireEvent, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ApiError, type PluginHost } from '@alinha/plugin-sdk'
import { renderWithHost } from '../test/renderWithHost'
import { SCOPE_ANSWERED, SCOPE_EMPTY, SCOPE_OFFICIAL } from '../test/scopeFixtures'
import { MYCIBER_REMINDER, NIS2_MEANING } from './labels'
import { ScopePage } from './ScopePage'
import type { Scope } from './scopeForm'

const returning = (scope: Scope) => vi.fn(async () => scope) as unknown as PluginHost['api']

const renderPage = (api: PluginHost['api'], canEdit = true) =>
  renderWithHost(<ScopePage />, { host: { api, can: (_resource, action = 'read') => action === 'read' || canEdit } })

describe('ScopePage', () => {
  it('shows a loading state while the scope is on its way', () => {
    renderPage(vi.fn(() => new Promise<never>(() => {})) as PluginHost['api'])

    expect(screen.getByRole('status', { name: 'A carregar o âmbito regulatório…' })).toBeInTheDocument()
  })

  it('shows an empty state when the organization is not registered yet', async () => {
    renderPage(vi.fn(async () => {
      throw new ApiError(404, '/organization/scope', undefined, { code: 'ORGANIZATION_NOT_REGISTERED' })
    }) as PluginHost['api'])

    expect(await screen.findByText('Ainda sem dados da organização')).toBeInTheDocument()
  })

  it('shows an error with a retry for any other failure', async () => {
    renderPage(vi.fn(async () => { throw new ApiError(500, '/organization/scope') }) as PluginHost['api'])

    expect(await screen.findByRole('button', { name: 'Tentar de novo' })).toBeInTheDocument()
  })

  it('shows the questions to an editor before any answer', async () => {
    renderPage(returning(SCOPE_EMPTY))

    expect(await screen.findByRole('heading', { name: '1. O que a empresa faz' })).toBeInTheDocument()
  })

  it('shows an empty state to a reader before any answer', async () => {
    renderPage(returning(SCOPE_EMPTY), false)

    expect(await screen.findByText('Ainda sem respostas')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '1. O que a empresa faz' })).not.toBeInTheDocument()
  })

  it('shows the indicative classification with the MyCiber reminder', async () => {
    renderPage(returning(SCOPE_ANSWERED))

    const nis2 = await screen.findByRole('region', { name: 'Entidade importante' })
    expect(within(nis2).getByText('Enquadramento sugerido')).toBeInTheDocument()
    expect(within(nis2).getByText('Indicativo')).toBeInTheDocument()
    expect(within(nis2).getByText(/é um setor abrangido e a organização é pelo menos média empresa/)).toBeInTheDocument()
    expect(screen.getByText(MYCIBER_REMINDER)).toBeInTheDocument()
  })

  it('shows what the classification implies', async () => {
    renderPage(returning(SCOPE_ANSWERED))

    expect(await screen.findByText(NIS2_MEANING.important[0])).toBeInTheDocument()
  })

  it('lists NIS2, RGPD and the AI Act with their explanations', async () => {
    renderPage(returning(SCOPE_ANSWERED))

    const laws = await screen.findByRole('list', { name: 'Leis avaliadas' })
    expect(within(laws).getByRole('heading', { name: 'NIS2' })).toBeInTheDocument()
    expect(within(laws).getByRole('heading', { name: 'RGPD' })).toBeInTheDocument()
    expect(within(laws).getByRole('heading', { name: 'Regulamento da IA' })).toBeInTheDocument()
    expect(within(laws).getAllByText('Aplica-se')).toHaveLength(3)
    expect(within(laws).getByText('Trata dados de pessoas (clientes, trabalhadores ou contactos), por isso o RGPD aplica-se.')).toBeInTheDocument()
    expect(within(laws).getByText('RGPD, art. 2.º, n.º 1, art. 3.º, n.º 1, e art. 4.º, 1) e 2)')).toBeInTheDocument()
  })

  it('marks findings a consultant must confirm', async () => {
    renderPage(returning(SCOPE_ANSWERED))

    const finding = (await screen.findByText(/Pode ser um sistema de risco elevado/)).closest('li')!
    expect(within(finding).getByText('O consultor confirma')).toBeInTheDocument()
  })

  it('the official qualification replaces the suggestion', async () => {
    renderPage(returning(SCOPE_OFFICIAL))

    const nis2 = await screen.findByRole('region', { name: 'Entidade essencial' })
    expect(within(nis2).getByText('Qualificação oficial')).toBeInTheDocument()
    expect(within(nis2).getByText('Oficial · CNCS')).toBeInTheDocument()
    expect(within(nis2).getByText('Atribuída pelo CNCS no Ato de Qualificação de 15/01/2026. Referência: CNCS/QA/2026/0042.')).toBeInTheDocument()
    expect(screen.getByText('A sugestão do aLinha era Entidade importante. A partir de agora conta a qualificação do CNCS.')).toBeInTheDocument()
    expect(screen.queryByText(MYCIBER_REMINDER)).not.toBeInTheDocument()
    expect(screen.getByText(NIS2_MEANING.essential[0])).toBeInTheDocument()
  })

  it('says so when the CNCS confirms the suggestion', async () => {
    renderPage(returning({ ...SCOPE_OFFICIAL, nis2: { classification: 'important', official: true, suggested: 'important' } }))

    expect(await screen.findByText('A qualificação do CNCS confirma a sugestão do aLinha.')).toBeInTheDocument()
  })

  it('with only the official qualification, asks for the answers to show the laws', async () => {
    renderPage(returning({ ...SCOPE_OFFICIAL, answers: null, result: null, nis2: { classification: 'essential', official: true, suggested: null } }))

    expect(await screen.findByText('Responda às perguntas sobre a empresa para ver que leis se aplicam.')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Responder às perguntas' }))
    expect(screen.getByRole('heading', { name: '1. O que a empresa faz' })).toBeInTheDocument()
  })

  it('"Mudar respostas" opens the questions with the saved answers', async () => {
    renderPage(returning(SCOPE_ANSWERED))

    fireEvent.click(await screen.findByRole('button', { name: 'Mudar respostas' }))

    expect(screen.getByLabelText('Fabrico de veículos automóveis, reboques e semirreboques')).toBeChecked()
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(screen.getByRole('region', { name: 'Entidade importante' })).toBeInTheDocument()
  })

  it('opens the form to register the official qualification', async () => {
    renderPage(returning(SCOPE_ANSWERED))

    fireEvent.click(await screen.findByRole('button', { name: 'Registar qualificação oficial' }))

    expect(screen.getByRole('dialog', { name: 'Registar qualificação oficial' })).toBeInTheDocument()
  })

  it('offers a correction once the qualification is registered', async () => {
    renderPage(returning(SCOPE_OFFICIAL))

    fireEvent.click(await screen.findByRole('button', { name: 'Corrigir' }))

    expect(screen.getByRole('dialog', { name: 'Corrigir qualificação oficial' })).toBeInTheDocument()
  })

  it('hides the qualification and answer buttons from readers', async () => {
    renderPage(returning(SCOPE_ANSWERED), false)

    await screen.findByRole('region', { name: 'Entidade importante' })
    expect(screen.queryByRole('button', { name: 'Mudar respostas' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Registar qualificação oficial' })).not.toBeInTheDocument()
  })
})

import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ApiError, type PluginHost } from '@alinha/plugin-sdk'
import { renderWithHost } from '../test/renderWithHost'
import { SCOPE_ANSWERED, SCOPE_EMPTY } from '../test/scopeFixtures'
import { ScopeQuestions } from './ScopeQuestions'
import type { Scope } from './scopeForm'

const QUESTIONS = {
  publicBody: 'A empresa é uma entidade pública?',
  higherEducation: 'É uma instituição de ensino superior?',
  criticalEntity: 'Foi identificada pelo Estado como entidade crítica?',
  significantImpact: 'Se o vosso serviço parasse, isso afetaria seriamente a segurança, a saúde ou muitas outras empresas?',
  personalData: 'A empresa guarda ou usa dados de pessoas (clientes, trabalhadores, contactos)?',
  usesAi: 'A empresa usa ferramentas de IA no trabalho (por exemplo, ChatGPT ou Copilot)?',
  providesAi: 'Desenvolve sistemas de IA e vende-os com o seu nome?',
} as const

const choose = (question: string, answer: 'Sim' | 'Não') =>
  fireEvent.click(within(screen.getByRole('group', { name: question })).getByLabelText(answer))

const answerEverything = (personalData: 'Sim' | 'Não' = 'Não') => {
  choose(QUESTIONS.publicBody, 'Não')
  choose(QUESTIONS.higherEducation, 'Não')
  choose(QUESTIONS.criticalEntity, 'Não')
  choose(QUESTIONS.significantImpact, 'Não')
  choose(QUESTIONS.personalData, personalData)
  choose(QUESTIONS.usesAi, 'Não')
  choose(QUESTIONS.providesAi, 'Não')
}

const submit = () => fireEvent.click(screen.getByRole('button', { name: 'Ver o resultado' }))

function renderQuestions(scope: Scope = SCOPE_EMPTY, api?: PluginHost['api']) {
  const onSaved = vi.fn()
  const view = renderWithHost(<ScopeQuestions scope={scope} onSaved={onSaved} />, api ? { host: { api } } : {})
  return { ...view, onSaved }
}

describe('ScopeQuestions', () => {
  it('shows the suggested types first, unticked', () => {
    renderQuestions()

    const suggested = screen.getByRole('group', { name: 'Sugeridos pelo seu setor' })
    const car = within(suggested).getByLabelText('Fabrico de veículos automóveis, reboques e semirreboques')
    expect(car).not.toBeChecked()
    expect(screen.getByText('Espaço')).toBeInTheDocument()
    expect(screen.getByText('Energia')).toBeInTheDocument()
  })

  it('ticking "none" clears the ticked types, and ticking a type clears "none"', () => {
    renderQuestions()
    const car = screen.getByLabelText('Fabrico de veículos automóveis, reboques e semirreboques')
    const none = screen.getByLabelText('Nenhum destes descreve a empresa')

    fireEvent.click(car)
    expect(car).toBeChecked()
    fireEvent.click(none)
    expect(none).toBeChecked()
    expect(car).not.toBeChecked()
    fireEvent.click(car)
    expect(none).not.toBeChecked()
  })

  it('shows personal-data details only after answering yes', () => {
    renderQuestions()
    const detail = 'Trata dados de saúde, biométricos ou de registo criminal'

    expect(screen.queryByLabelText(detail)).not.toBeInTheDocument()
    choose(QUESTIONS.personalData, 'Sim')
    expect(screen.getByLabelText(detail)).toBeInTheDocument()
  })

  it('shows AI details only when the company uses or provides AI', () => {
    renderQuestions()
    const detail = 'Usa IA em recrutamento, crédito, seguros, educação ou identificação de pessoas'

    choose(QUESTIONS.usesAi, 'Não')
    choose(QUESTIONS.providesAi, 'Não')
    expect(screen.queryByLabelText(detail)).not.toBeInTheDocument()
    choose(QUESTIONS.providesAi, 'Sim')
    expect(screen.getByLabelText(detail)).toBeInTheDocument()
  })

  it('blocks submit and shows the messages when questions are unanswered', () => {
    const api = vi.fn() as unknown as PluginHost['api']
    renderQuestions(SCOPE_EMPTY, api)

    submit()

    expect(screen.getByText('Escolha pelo menos um tipo, ou indique que nenhum descreve a empresa.')).toBeInTheDocument()
    expect(screen.getAllByText('Escolha uma resposta.')).toHaveLength(7)
    expect(api).not.toHaveBeenCalled()
  })

  it('sends the answers and calls onSaved', async () => {
    const api = vi.fn(async () => SCOPE_ANSWERED) as unknown as PluginHost['api']
    const { onSaved } = renderQuestions(SCOPE_EMPTY, api)

    fireEvent.click(screen.getByLabelText('Fabrico de veículos automóveis, reboques e semirreboques'))
    answerEverything('Sim')
    fireEvent.click(screen.getByLabelText('Trata dados de saúde, biométricos ou de registo criminal'))
    choose(QUESTIONS.personalData, 'Não') // the ticked detail must not be sent
    submit()

    await waitFor(() => expect(onSaved).toHaveBeenCalled())
    expect(api).toHaveBeenCalledWith('/organization/scope/answers', expect.objectContaining({ method: 'PUT' }))
    const body = JSON.parse((api as unknown as ReturnType<typeof vi.fn>).mock.calls[0][1].body as string)
    expect(body.version).toBeNull()
    expect(body.nis2.entityTypes).toEqual(['MOTOR_VEHICLE_MANUFACTURING'])
    expect(body.gdpr).toEqual({
      processesPersonalData: false, regularOrRiskyProcessing: false, specialOrCriminalData: false,
      largeScaleMonitoring: false, largeScaleSpecialData: false,
    })
  })

  it('starts from the saved answers and sends their version', async () => {
    const api = vi.fn(async () => SCOPE_ANSWERED) as unknown as PluginHost['api']
    renderQuestions(SCOPE_ANSWERED, api)

    expect(screen.getByLabelText('Fabrico de veículos automóveis, reboques e semirreboques')).toBeChecked()
    expect(within(screen.getByRole('group', { name: QUESTIONS.personalData })).getByLabelText('Sim')).toBeChecked()
    submit()

    await waitFor(() => expect(api).toHaveBeenCalled())
    const body = JSON.parse((api as unknown as ReturnType<typeof vi.fn>).mock.calls[0][1].body as string)
    expect(body.version).toBe(1)
  })

  it('shows the server field errors on a 422', async () => {
    const api = vi.fn(async () => {
      throw new ApiError(422, '/organization/scope/answers', undefined, {
        code: 'VALIDATION_FAILED',
        detail: 'Há dados por corrigir.',
        errors: [{ field: 'nis2.entityTypes', message: 'Há um tipo de entidade que não existe. Recarregue a página e escolha de novo.' }],
      })
    }) as PluginHost['api']
    renderQuestions(SCOPE_EMPTY, api)

    fireEvent.click(screen.getByLabelText('Nenhum destes descreve a empresa'))
    answerEverything()
    submit()

    expect(await screen.findByText('Há um tipo de entidade que não existe. Recarregue a página e escolha de novo.')).toBeInTheDocument()
  })

  it('shows the conflict message on a 409', async () => {
    const api = vi.fn(async () => {
      throw new ApiError(409, '/organization/scope/answers', undefined, {
        code: 'VERSION_CONFLICT',
        detail: 'Os dados foram alterados entretanto. Recarregue a página e volte a fazer as alterações.',
      })
    }) as PluginHost['api']
    renderQuestions(SCOPE_ANSWERED, api)

    submit()

    expect(await screen.findByText('Os dados foram alterados entretanto. Recarregue a página e volte a fazer as alterações.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Carregar as respostas atuais' })).toBeInTheDocument()
  })
})

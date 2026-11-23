import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ApiError, type PluginHost } from '@alinha/plugin-sdk'
import { renderWithHost } from '../test/renderWithHost'
import { SCOPE_OFFICIAL } from '../test/scopeFixtures'
import { QualificationModal, todayInLisbon } from './QualificationModal'

const renderModal = (api: PluginHost['api'] = vi.fn() as unknown as PluginHost['api'], current = SCOPE_OFFICIAL.qualification) => {
  const onClose = vi.fn()
  const view = renderWithHost(<QualificationModal current={current} suggested="important" onClose={onClose} />, { host: { api } })
  return { ...view, onClose }
}

const save = () => fireEvent.click(screen.getByRole('button', { name: /Registar qualificação|Guardar correção/ }))
const typeDate = (value: string) => fireEvent.change(screen.getByLabelText('Data do Ato de Qualificação'), { target: { value } })
const choose = (label: 'Entidade essencial' | 'Entidade importante') =>
  fireEvent.click(within(screen.getByRole('group', { name: /Classificação atribuída pelo CNCS/ })).getByLabelText(label))

const bodyOf = (api: PluginHost['api']) => JSON.parse((api as unknown as ReturnType<typeof vi.fn>).mock.calls[0][1].body as string)

describe('QualificationModal', () => {
  it('shows the suggestion next to the classification', () => {
    renderModal(undefined, null)

    expect(screen.getByRole('group', { name: 'Classificação atribuída pelo CNCS (a sugestão do aLinha era: Entidade importante)' })).toBeInTheDocument()
  })

  it('requires a classification and a date', () => {
    const api = vi.fn() as unknown as PluginHost['api']
    renderModal(api, null)

    save()

    expect(screen.getByText('Escolha a classificação que está no Ato de Qualificação.')).toBeInTheDocument()
    expect(screen.getByText('Indique a data do Ato de Qualificação.')).toBeInTheDocument()
    expect(api).not.toHaveBeenCalled()
  })

  it('refuses a date in the future', () => {
    renderModal(undefined, null)
    choose('Entidade essencial')

    typeDate('2999-01-01')
    save()

    expect(screen.getByText('A data não pode ser no futuro.')).toBeInTheDocument()
    expect(screen.getByLabelText('Data do Ato de Qualificação')).toHaveAttribute('max', todayInLisbon())
  })

  it('refuses a reference over 100 characters', () => {
    renderModal(undefined, null)

    fireEvent.change(screen.getByLabelText(/Referência do Ato/), { target: { value: 'x'.repeat(101) } })
    save()

    expect(screen.getByText('Use no máximo 100 caracteres.')).toBeInTheDocument()
  })

  it('sends a first qualification without version', async () => {
    const api = vi.fn(async () => SCOPE_OFFICIAL) as unknown as PluginHost['api']
    const { onClose } = renderModal(api, null)

    choose('Entidade essencial')
    typeDate('2026-01-15')
    save()

    await waitFor(() => expect(onClose).toHaveBeenCalled())
    expect(api).toHaveBeenCalledWith('/organization/scope/qualification', expect.objectContaining({ method: 'PUT' }))
    expect(bodyOf(api)).toEqual({ version: null, classification: 'essential', actDate: '2026-01-15', actReference: null })
  })

  it('sends a correction with the version', async () => {
    const api = vi.fn(async () => SCOPE_OFFICIAL) as unknown as PluginHost['api']
    renderModal(api)

    expect(screen.getByLabelText('Data do Ato de Qualificação')).toHaveValue('2026-01-15')
    fireEvent.change(screen.getByLabelText(/Referência do Ato/), { target: { value: '  CNCS/QA/2026/0043 ' } })
    save()

    await waitFor(() => expect(api).toHaveBeenCalled())
    expect(bodyOf(api)).toEqual({ version: 0, classification: 'essential', actDate: '2026-01-15', actReference: 'CNCS/QA/2026/0043' })
  })

  it('shows the server field errors', async () => {
    const api = vi.fn(async () => {
      throw new ApiError(422, '/organization/scope/qualification', undefined, {
        code: 'VALIDATION_FAILED',
        detail: 'Há dados por corrigir.',
        errors: [{ field: 'actDate', message: 'A data não pode ser anterior a 4 de dezembro de 2025, quando a lei foi publicada.' }],
      })
    }) as PluginHost['api']
    renderModal(api, null)

    choose('Entidade importante')
    typeDate('2025-11-30')
    save()

    expect(await screen.findByText('A data não pode ser anterior a 4 de dezembro de 2025, quando a lei foi publicada.')).toBeInTheDocument()
  })
})

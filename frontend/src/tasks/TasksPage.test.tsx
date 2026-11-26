import { screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { ApiResponse } from '@alinha/api-client'
import { ApiError, type PluginHost } from '@alinha/plugin-sdk'
import { renderWithHost } from '../test/renderWithHost'
import { TasksPage, isOverdue } from './TasksPage'

type Task = ApiResponse<'/api/organization/tasks/mine'>[number]

const task = (overrides: Partial<Task>): Task => ({
  id: crypto.randomUUID(),
  type: 'assessment-question',
  typeLabel: 'Pergunta da avaliação',
  reference: 'q1',
  title: 'Responder à pergunta sobre cópias de segurança',
  link: 'assessment/a1/questions/q1',
  assigneeId: 'ana',
  dueDate: '2099-12-05',
  status: 'open',
  assignedAt: '2026-11-20T10:00:00Z',
  completedAt: null,
  ...overrides,
})

const renderPage = (api: PluginHost['api']) =>
  renderWithHost(<TasksPage />, { url: '/org/compagueda/tasks', host: { api } })

describe('TasksPage', () => {
  it('lists the open tasks with their due date and a link to the work, then the done ones', async () => {
    const api = vi.fn(async () => [
      task({ title: 'Responder à pergunta sobre cópias de segurança' }),
      task({ title: 'Registar o servidor de ficheiros', type: 'risk-assets-asset', typeLabel: 'Registo de ativo', dueDate: null, link: null }),
      task({ title: 'Responder à pergunta sobre MFA', status: 'done', completedAt: '2026-11-21T09:00:00Z' }),
    ]) as unknown as PluginHost['api']

    renderPage(api)

    const open = await screen.findByRole('region', { name: 'Por fazer' })
    expect(within(open).getAllByRole('listitem')).toHaveLength(2)
    expect(within(open).getByText('Pergunta da avaliação')).toBeInTheDocument()
    expect(within(open).getByText('Registo de ativo')).toBeInTheDocument()
    expect(within(open).getByText('Prazo: 05/12/2099')).toBeInTheDocument()
    expect(within(open).getByText('Sem prazo')).toBeInTheDocument()
    expect(within(open).getAllByRole('link', { name: 'Abrir' })).toHaveLength(1)
    const done = screen.getByRole('region', { name: 'Concluídas' })
    expect(within(done).getByText('Responder à pergunta sobre MFA')).toBeInTheDocument()
    expect(within(done).queryByRole('link')).toBeNull()
    expect(screen.getByText('2 tarefas por fazer')).toBeInTheDocument()
    expect(api).toHaveBeenCalledWith('/organization/tasks/mine', expect.anything())
  })

  it('marks an open task past its due date as overdue', async () => {
    const api = vi.fn(async () => [task({ dueDate: '2020-01-01' })]) as unknown as PluginHost['api']

    renderPage(api)

    expect(await screen.findByText('Em atraso')).toBeInTheDocument()
    expect(screen.getByText('1 tarefa por fazer')).toBeInTheDocument()
  })

  it('says so when there is nothing to do', async () => {
    const api = vi.fn(async () => []) as unknown as PluginHost['api']

    renderPage(api)

    expect(await screen.findByText('Não tem tarefas por fazer')).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Concluídas' })).toBeNull()
  })

  it('shows the error with a retry', async () => {
    const api = vi.fn(async () => { throw new ApiError(500, '/organization/tasks/mine') }) as unknown as PluginHost['api']

    renderPage(api)

    expect(await screen.findByRole('button', { name: /tentar/i })).toBeInTheDocument()
  })
})

describe('isOverdue', () => {
  it('only counts open tasks with a due date before today', () => {
    expect(isOverdue({ status: 'open', dueDate: '2026-11-24' }, '2026-11-25')).toBe(true)
    expect(isOverdue({ status: 'open', dueDate: '2026-11-25' }, '2026-11-25')).toBe(false)
    expect(isOverdue({ status: 'done', dueDate: '2026-11-24' }, '2026-11-25')).toBe(false)
    expect(isOverdue({ status: 'open', dueDate: null }, '2026-11-25')).toBe(false)
  })
})

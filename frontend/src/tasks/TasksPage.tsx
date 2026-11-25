import { Link } from 'react-router'
import { CheckCircle2, ListTodo } from 'lucide-react'
import type { ApiResponse } from '@alinha/api-client'
import { formatDate, useApiQuery } from '@alinha/plugin-sdk'
import { Badge, Card, EmptyState, ErrorState, PageHeader, SkeletonRows, buttonClass } from '@alinha/ui'

type Task = ApiResponse<'/api/organization/tasks/mine'>[number]

const TITLE = 'As minhas tarefas'

/** What each kind of work is called; the owning plugin names the type (organization#7). */
const TYPE_LABELS: Record<string, string> = {
  'assessment-question': 'Pergunta da avaliação',
  'risk-assets-asset': 'Registo de ativo',
  'evidence-source': 'Ligação de fonte de evidências',
}

export const typeLabel = (type: string) => TYPE_LABELS[type] ?? 'Tarefa'

/** A due date before today in Portugal (the date has no time: compared as YYYY-MM-DD). */
export function isOverdue(task: Pick<Task, 'status' | 'dueDate'>, today = todayInLisbon()) {
  return task.status === 'OPEN' && task.dueDate != null && task.dueDate < today
}

function todayInLisbon() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Lisbon' }).format(new Date())
}

/** The signed-in member's tasks in this organization (organization#7): only their own. */
export function TasksPage() {
  const tasks = useApiQuery<Task[]>(['organization', 'tasks', 'mine'], '/organization/tasks/mine')

  if (tasks.isPending) {
    return (
      <>
        <PageHeader title={TITLE} />
        <SkeletonRows rows={4} label="A carregar as suas tarefas…" />
      </>
    )
  }
  if (tasks.isError) {
    return (
      <>
        <PageHeader title={TITLE} />
        <ErrorState error={tasks.error} onRetry={() => void tasks.refetch()} />
      </>
    )
  }

  const open = tasks.data.filter((t) => t.status === 'OPEN')
  const done = tasks.data.filter((t) => t.status === 'DONE')
  const subtitle = open.length === 1 ? '1 tarefa por fazer' : `${open.length} tarefas por fazer`

  return (
    <>
      <PageHeader title={TITLE} subtitle={subtitle} />
      {open.length === 0 ? (
        <EmptyState icon={<CheckCircle2 className="size-5" aria-hidden />} title="Não tem tarefas por fazer">
          Quando lhe atribuírem trabalho, como responder a perguntas da avaliação, aparece aqui.
        </EmptyState>
      ) : (
        <TaskList label="Por fazer" tasks={open} />
      )}
      {done.length > 0 && <TaskList label="Concluídas" tasks={done} />}
    </>
  )
}

function TaskList({ label, tasks }: { label: string; tasks: Task[] }) {
  return (
    <section aria-label={label} className="mt-6">
      <h2 className="mb-3 text-sm font-semibold">{label}</h2>
      <ul className="space-y-3">
        {tasks.map((task) => <TaskItem key={task.id} task={task} />)}
      </ul>
    </section>
  )
}

function TaskItem({ task }: { task: Task }) {
  const overdue = isOverdue(task)
  return (
    <li>
      <Card>
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="text-xs text-muted">{typeLabel(task.type)}</p>
            <p className="font-medium break-words">{task.title}</p>
            <p className="mt-1 text-sm text-muted">
              {task.status === 'DONE'
                ? `Concluída a ${formatDate(task.completedAt)}`
                : task.dueDate ? `Prazo: ${formatDate(task.dueDate)}` : 'Sem prazo'}
            </p>
          </div>
          <div className="flex items-center gap-2">
            {overdue && <Badge tone="dark">Em atraso</Badge>}
            {task.status === 'OPEN' && task.link && (
              // `..` leaves this page's route: the link is relative to the client area (e.g. assessment/<id>).
              <Link to={`../${task.link}`} className={buttonClass({ variant: 'secondary' })}>
                <ListTodo className="size-4" aria-hidden />Abrir
              </Link>
            )}
          </div>
        </div>
      </Card>
    </li>
  )
}

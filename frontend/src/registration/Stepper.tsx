import type { Step, StepId } from './organizationForm'

interface StepperProps {
  steps: readonly Step[]
  current: StepId
}

/** Numbered steps of the wizard. Done steps show a tick; the current one is `aria-current="step"`. */
export function Stepper({ steps, current }: StepperProps) {
  const currentIndex = steps.findIndex((s) => s.id === current)
  return (
    <ol aria-label="Passos do registo" className="flex items-center gap-2">
      {steps.map((step, i) => {
        const done = i < currentIndex
        const active = i === currentIndex
        return (
          <li key={step.id} aria-current={active ? 'step' : undefined} className="flex min-w-0 flex-1 items-center gap-2">
            <span
              className={`grid size-7 shrink-0 place-items-center rounded-full text-sm font-bold ${
                done ? 'bg-gray-200 text-gray-900' : active ? 'bg-gray-900 text-white' : 'border border-gray-300 text-gray-500'
              }`}
            >
              {done ? <><span aria-hidden>✓</span><span className="sr-only">Concluído</span></> : i + 1}
            </span>
            <span className={`truncate text-sm max-sm:sr-only ${active ? 'font-bold text-gray-900' : 'text-gray-600'}`}>{step.label}</span>
            {i < steps.length - 1 && <span aria-hidden className={`h-px flex-1 ${done ? 'bg-gray-900' : 'bg-gray-200'}`} />}
          </li>
        )
      })}
    </ol>
  )
}

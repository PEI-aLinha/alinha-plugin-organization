import { useId, type ReactNode } from 'react'

/** A titled group of fields (a `fieldset`, so the title names the group). Layout only: the controls come from `@alinha/ui`. */
export function Section({ title, description, children }: { title: string; description?: string; children: ReactNode }) {
  const descriptionId = useId()
  return (
    <fieldset aria-describedby={description ? descriptionId : undefined} className="flex min-w-0 flex-col gap-4">
      <legend className="mb-1 text-base font-bold text-ink">{title}</legend>
      {description && <p id={descriptionId} className="-mt-2 text-sm text-ink-soft">{description}</p>}
      {children}
    </fieldset>
  )
}

export const twoColumns = 'grid items-start gap-4 sm:grid-cols-2'

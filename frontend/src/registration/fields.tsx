import { useId, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react'

/**
 * Small form controls for the wizard. `@alinha/ui` has no form fields yet; move these there when it does.
 */

/** Props a control gets from its [Field]: the id the label points to, and its hint/error for screen readers. */
export interface ControlProps {
  id: string
  'aria-describedby'?: string
  'aria-invalid'?: true
}

interface FieldProps {
  label: ReactNode
  hint?: string
  error?: string
  children: (control: ControlProps) => ReactNode
}

const controlClass = (invalid: boolean) =>
  `w-full rounded-md border bg-white px-3 py-2 text-base outline-none transition-colors focus-visible:ring-2 disabled:bg-gray-100 disabled:text-gray-500 ${
    invalid ? 'border-red-600 focus-visible:ring-red-200' : 'border-gray-300 focus-visible:border-gray-900 focus-visible:ring-gray-200'
  }`

/** Label, control, plain-language hint and inline error, wired together for assistive technology. */
export function Field({ label, hint, error, children }: FieldProps) {
  const id = useId()
  const hintId = hint ? `${id}-hint` : undefined
  const errorId = error ? `${id}-error` : undefined
  const describedBy = [hintId, errorId].filter(Boolean).join(' ') || undefined
  return (
    <div className="flex min-w-0 flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-semibold text-gray-900">{label}</label>
      {children({ id, 'aria-describedby': describedBy, 'aria-invalid': error ? true : undefined })}
      {hint && <p id={hintId} className="text-sm text-gray-600">{hint}</p>}
      {error && <p id={errorId} className="text-sm font-medium text-red-700">{error}</p>}
    </div>
  )
}

export function TextInput(props: InputHTMLAttributes<HTMLInputElement>) {
  return <input type="text" {...props} className={controlClass(!!props['aria-invalid'])} />
}

export function TextArea(props: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea rows={4} {...props} className={controlClass(!!props['aria-invalid'])} />
}

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  placeholder: string
  options: ReadonlyArray<{ value: string; label: string }>
}

export function Select({ placeholder, options, ...props }: SelectProps) {
  return (
    <select {...props} className={controlClass(!!props['aria-invalid'])}>
      <option value="">{placeholder}</option>
      {options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
    </select>
  )
}

/** A titled group of fields (a `fieldset`, so the title names the group). */
export function Section({ title, description, children }: { title: string; description?: string; children: ReactNode }) {
  const descriptionId = useId()
  return (
    <fieldset aria-describedby={description ? descriptionId : undefined} className="flex min-w-0 flex-col gap-4">
      <legend className="mb-1 text-base font-bold text-gray-900">{title}</legend>
      {description && <p id={descriptionId} className="-mt-2 text-sm text-gray-600">{description}</p>}
      {children}
    </fieldset>
  )
}

export const twoColumns = 'grid items-start gap-4 sm:grid-cols-2'

const buttonBase = 'inline-flex items-center justify-center rounded-md px-4 py-2 text-sm font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-60'

export const buttonClass = {
  primary: `${buttonBase} bg-gray-900 text-white hover:bg-gray-700 active:bg-black`,
  secondary: `${buttonBase} border border-gray-300 bg-white text-gray-900 hover:bg-gray-50 active:bg-gray-100`,
  link: 'rounded text-sm font-semibold text-gray-900 underline underline-offset-4 hover:text-gray-600 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400',
} as const

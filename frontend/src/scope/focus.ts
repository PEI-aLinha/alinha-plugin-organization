/**
 * Moves the focus to the first invalid question of [form] after a failed submit, so keyboard and screen-reader
 * users land on what needs fixing. A question marks itself with `aria-invalid` (an input, or a fieldset/group
 * around several); a group gets the focus on its first control.
 */
export function focusFirstInvalid(form: HTMLFormElement | null): void {
  const invalid = form?.querySelector<HTMLElement>('[aria-invalid="true"]')
  if (!invalid) return
  const target = invalid.matches('input, textarea, select')
    ? invalid
    : invalid.querySelector<HTMLElement>('input:not([disabled]), textarea, select')
  target?.focus()
}

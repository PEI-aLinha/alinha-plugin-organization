import { buttonClass } from './fields'
import { digits, type OrganizationForm, type StepId } from './organizationForm'
import { sectorById } from './sectors'

const formatNif = (nif: string) => digits(nif).replace(/^(\d{3})(\d{3})(\d{3})$/, '$1 $2 $3')
const formatNumber = (value: string) => Number(digits(value)).toLocaleString('pt-PT')

export function sectorLabel(sectorId: string, subsectorId: string): string {
  const sector = sectorById(sectorId)
  const subsector = sector?.subsectors.find((s) => s.id === subsectorId)
  if (!sector) return '—'
  return subsector ? `${sector.label} · ${subsector.label}` : sector.label
}

interface SummaryProps {
  form: OrganizationForm
  /** Shown with an "Alterar" link per group; omitted on the done screen. */
  onEdit?: (step: StepId) => void
}

/** Read-only review of what will be (or was) registered. */
export function Summary({ form, onEdit }: SummaryProps) {
  const groups: Array<{ step: StepId; title: string; rows: Array<[string, string]> }> = [
    { step: 'company', title: 'Empresa', rows: [['Denominação', form.name.trim()], ['NIF', formatNif(form.nif)], ['CAE', digits(form.cae)]] },
    { step: 'activity', title: 'Atividade', rows: [['Setor', sectorLabel(form.sector, form.subsector)], ['Serviços', form.services.trim()]] },
    { step: 'size', title: 'Dimensão', rows: [['Trabalhadores', formatNumber(form.employees)], ['Volume de negócios', `${formatNumber(form.turnover)} €`]] },
    {
      step: 'contacts', title: 'Contactos', rows: [
        ['Sede', `${form.address.trim()}, ${form.postalCode.trim()} ${form.city.trim()}`],
        ['Telefone', digits(form.phone)],
        ['Email', form.email.trim().toLowerCase()],
        ['Contacto permanente', `${form.contactName.trim()} · ${digits(form.contactPhone)}`],
      ],
    },
  ]
  return (
    <div className="flex flex-col divide-y rounded-lg border">
      {groups.map((g) => (
        <section key={g.step} aria-labelledby={`resumo-${g.step}`} className="flex flex-col gap-3 p-5">
          <div className="flex items-baseline justify-between gap-4">
            <h3 id={`resumo-${g.step}`} className="text-base font-bold">{g.title}</h3>
            {onEdit && (
              <button type="button" className={buttonClass.link} onClick={() => onEdit(g.step)}>
                Alterar<span className="sr-only"> {g.title.toLowerCase()}</span>
              </button>
            )}
          </div>
          <dl className="grid gap-x-6 gap-y-2 sm:grid-cols-[180px_1fr]">
            {g.rows.map(([label, value]) => (
              <div key={label} className="contents">
                <dt className="text-sm text-gray-600">{label}</dt>
                <dd className="whitespace-pre-line break-words text-sm font-semibold text-gray-900 max-sm:-mt-1">{value}</dd>
              </div>
            ))}
          </dl>
        </section>
      ))}
    </div>
  )
}

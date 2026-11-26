import type { ComponentProps, ReactNode } from 'react'
import { Button, Field, Select, TextArea, TextInput } from '@alinha/ui'
import { Section, twoColumns } from './Section'
import type { FieldKey, OrganizationErrors, OrganizationForm, StepId } from './organizationForm'
import { SECTORS, sectorById } from './sectors'

export interface StepProps {
  form: OrganizationForm
  errors: OrganizationErrors
  onChange: (changes: Partial<OrganizationForm>) => void
  /** Name of the signed-in person, for "Sou eu" on the permanent contact. */
  userName: string
}

type InputExtras = Omit<ComponentProps<typeof TextInput>, 'value' | 'onChange' | 'id' | 'name' | 'step' | 'invalid'>

interface TextFieldProps extends InputExtras {
  step: StepProps
  name: FieldKey
  label: ReactNode
  hint?: string
}

/** A text field bound to one form key. */
function TextField({ step: { form, errors, onChange }, name, label, hint, ...extra }: TextFieldProps) {
  return (
    <Field label={label} hint={hint} error={errors[name]}>
      {(p) => (
        <TextInput id={p.id} aria-describedby={p.describedBy} aria-required={p.required} invalid={p.invalid}
          name={name} value={form[name]} onChange={(e) => onChange({ [name]: e.target.value })} {...extra} />
      )}
    </Field>
  )
}

function CompanyStep(props: StepProps) {
  return (
    <Section title="Identificação da empresa" description="Encontra estes dados na certidão permanente.">
      <TextField step={props} name="name" label="Denominação" hint="O nome oficial da empresa." autoComplete="organization" placeholder="Ex.: Componentes Águeda, Lda." />
      <div className={twoColumns}>
        <TextField step={props} name="nif" label="NIF" hint="9 dígitos." inputMode="numeric" maxLength={20} />
        <TextField step={props} name="cae" label="Código CAE principal" hint="5 dígitos. Ex.: 29320." inputMode="numeric" maxLength={20} />
      </div>
    </Section>
  )
}

function ActivityStep({ form, errors, onChange }: StepProps) {
  const sector = sectorById(form.sector)
  const hasSubsectors = !!sector && sector.subsectors.length > 0
  return (
    <Section title="Atividade" description="O setor ajuda a perceber que regras de cibersegurança (NIS2) se podem aplicar à empresa.">
      <div className={twoColumns}>
        <Field label="Setor" error={errors.sector}>
          {(p) => (
            <Select id={p.id} describedBy={p.describedBy} required={p.required} invalid={p.invalid}
              name="sector" value={form.sector} placeholder="Escolher o setor…"
              onChange={(sectorId) => onChange({ sector: sectorId, subsector: '' })}
              options={SECTORS.map((s) => ({ value: s.id, label: s.label }))} />
          )}
        </Field>
        <Field label="Subsetor" hint={sector && !hasSubsectors ? 'Este setor não tem subsetores.' : undefined} error={errors.subsector}>
          {(p) => (
            <Select id={p.id} describedBy={p.describedBy} required={hasSubsectors} invalid={p.invalid}
              name="subsector" disabled={!hasSubsectors} value={form.subsector}
              placeholder={sector ? (hasSubsectors ? 'Escolher o subsetor…' : 'Não se aplica') : 'Escolha primeiro o setor'}
              onChange={(subsectorId) => onChange({ subsector: subsectorId })}
              options={(sector?.subsectors ?? []).map((s) => ({ value: s.id, label: s.label }))} />
          )}
        </Field>
      </div>
      <Field label="Serviços prestados" hint="O que a empresa faz ou vende, e a quem." error={errors.services}>
        {(p) => (
          <TextArea id={p.id} aria-describedby={p.describedBy} aria-required={p.required} invalid={p.invalid} rows={4}
            name="services" value={form.services} onChange={(e) => onChange({ services: e.target.value })}
            placeholder="Ex.: Fabrico de componentes para a indústria automóvel." />
        )}
      </Field>
    </Section>
  )
}

function SizeStep(props: StepProps) {
  return (
    <Section title="Dimensão" description="Use os valores do último ano fechado. A dimensão conta para saber que obrigações se aplicam.">
      <div className={twoColumns}>
        <TextField step={props} name="employees" label="Número de trabalhadores" inputMode="numeric" placeholder="Ex.: 120" />
        <TextField step={props} name="turnover" label="Volume de negócios anual (€)" hint="Em euros, sem cêntimos." inputMode="numeric" placeholder="Ex.: 18 500 000" />
      </div>
    </Section>
  )
}

function ContactsStep(props: StepProps) {
  const { userName, onChange } = props
  return (
    <div className="flex flex-col gap-8">
      <Section title="Sede">
        <TextField step={props} name="address" label="Morada" autoComplete="street-address" />
        <div className={twoColumns}>
          <TextField step={props} name="postalCode" label="Código postal" autoComplete="postal-code" maxLength={8} placeholder="0000-000" />
          <TextField step={props} name="city" label="Localidade" autoComplete="address-level2" />
        </div>
      </Section>
      <Section title="Contactos da empresa">
        <div className={twoColumns}>
          <TextField step={props} name="phone" label="Telefone" type="tel" autoComplete="tel" />
          <TextField step={props} name="email" label="Email geral" type="email" placeholder="geral@empresa.pt" />
        </div>
        <TextField step={props} name="domain" label="Domínio da organização (site e email)"
          hint="Opcional. Serve para verificarmos de fora a segurança do site e do email. Exemplo: empresa.pt"
          inputMode="url" autoComplete="url" placeholder="empresa.pt" />
      </Section>
      <Section title="Ponto de contacto permanente" description="A pessoa que o CNCS pode contactar a qualquer hora em caso de incidente.">
        <div className={twoColumns}>
          <TextField step={props} name="contactName" label="Nome" />
          <TextField step={props} name="contactPhone" label="Telefone disponível a qualquer hora" type="tel" />
        </div>
        {userName && (
          <Button variant="link" className="w-fit" onClick={() => onChange({ contactName: userName })}>
            Sou eu ({userName})
          </Button>
        )}
      </Section>
    </div>
  )
}

/** The fields of each step (the summary has its own component). */
export const STEP_FIELDS: Record<Exclude<StepId, 'summary'>, (props: StepProps) => ReactNode> = {
  company: CompanyStep,
  activity: ActivityStep,
  size: SizeStep,
  contacts: ContactsStep,
}

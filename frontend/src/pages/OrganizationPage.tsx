import { useQuery } from '@tanstack/react-query'
import { useHost } from '@alinha/plugin-sdk'
import { PageHeader } from '@alinha/ui'

export function OrganizationPage() {
  const { api } = useHost()
  const ping = useQuery({
    queryKey: ['organization', 'ping'],
    queryFn: () => api<{ plugin: string; status: string }>('/organization/ping'),
  })

  return (
    <>
      <PageHeader title="Organização" />
      <p>Backend: {ping.isPending ? 'a verificar…' : ping.isError ? 'indisponível' : ping.data.status}</p>
    </>
  )
}

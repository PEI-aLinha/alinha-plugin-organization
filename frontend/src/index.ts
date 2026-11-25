import { definePlugin } from '@alinha/plugin-sdk'
import { Building2, ListTodo } from 'lucide-react'
import { OrganizationPage } from './pages/OrganizationPage'
import { RegistrationWizard } from './registration/RegistrationWizard'
import { ScopePage } from './scope/ScopePage'
import { TasksPage } from './tasks/TasksPage'

/**
 * The plugin's frontend entry point. The shell reads this object and mounts
 * the routes and the menu — nothing to register by hand in the shell.
 *   area 'client'     → /org/:org/<path>
 *   area 'consultant' → /portfolio/:org/<path>
 *   area 'onboarding' → /onboarding/<path> (a new account registering its organization, ADR 0004)
 * User-visible text (title, label) is in European Portuguese, plain language
 * (UI requirement in the technical report). Paths are in English.
 */
export default definePlugin({
  id: 'organization',
  name: 'Organization',
  routes: {
    onboarding: [
      { path: 'organization', handle: { title: 'Registo da organização' }, Component: RegistrationWizard },
    ],
    client: [
      { path: 'organization', handle: { title: 'Organização' }, Component: OrganizationPage },
      { path: 'organization/scope', handle: { title: 'Âmbito regulatório' }, Component: ScopePage },
      { path: 'tasks', handle: { title: 'As minhas tarefas' }, Component: TasksPage },
    ],
  },
  nav: [
    { label: 'Organização', path: 'organization', area: 'client', icon: Building2, order: 20 },
    { label: 'As minhas tarefas', path: 'tasks', area: 'client', icon: ListTodo, order: 15 },
  ],
})

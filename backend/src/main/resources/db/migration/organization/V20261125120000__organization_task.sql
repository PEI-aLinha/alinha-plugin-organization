-- Tasks given to members of an organization (organization#7, FR06, FR18). Other plugins create them through TaskApi
-- for their own work (an assessment question, an asset to register, an evidence source to connect) and close them
-- when that work is done. One task per piece of work: (type, reference) is unique per tenant.
create table organization.task (
    id           uuid primary key,
    tenant_id    uuid        not null,
    version      bigint      not null,
    -- Kind of work, named by the plugin that owns it (e.g. assessment-question).
    type         text        not null check (type ~ '^[a-z][a-z0-9-]{0,49}$'),
    -- The work's id in the owning plugin; opaque here.
    reference    text        not null check (char_length(reference) between 1 and 200),
    title        text        not null check (char_length(title) between 1 and 200),
    -- Where the work is done, relative to the client area (e.g. assessment/<id>), so the task list can link to it.
    link         text        check (char_length(link) <= 300 and link ~ '^[A-Za-z0-9_-]+(/[A-Za-z0-9_-]+)*$'),
    -- The assignee's token `sub`, like the audit trail's actor: no name or e-mail here (RNF15).
    assignee     text        not null check (char_length(assignee) between 1 and 255),
    due_date     date,
    status       text        not null check (status in ('OPEN', 'DONE')),
    assigned_at  timestamptz not null,
    completed_at timestamptz,
    constraint task_work_uq unique (tenant_id, type, reference),
    constraint task_completed_ck check ((status = 'DONE') = (completed_at is not null))
);

-- "My tasks": the open ones first, by due date.
create index task_assignee_idx on organization.task (tenant_id, assignee, status, due_date);

-- =====================================================================================
-- ChuoPulse: people management for administrators + the AI settings (server side only)
-- Run once in the SQL editor (or `supabase db push`). Safe to run again.
-- =====================================================================================

-- ---------- 1. Deleting someone must not be blocked by "who approved them" -------------
do $$
declare c text;
begin
  select conname into c from pg_constraint
   where conrelid = 'public.profiles'::regclass and contype = 'f'
     and pg_get_constraintdef(oid) ilike '%approved_by%';
  if c is not null then
    execute format('alter table public.profiles drop constraint %I', c);
  end if;
  alter table public.profiles
    add constraint profiles_approved_by_fkey foreign key (approved_by) references public.profiles (id) on delete set null;
exception when others then null;
end $$;

-- ---------- 2. Who may manage whom ------------------------------------------------------
-- Campus administrator: students and lecturers of their own campus (never themselves, never other admins).
-- App manager: the administrators of any campus (never themselves, never another app manager).
create or replace function public.can_manage_user(p_target uuid)
returns boolean language plpgsql stable security definer set search_path = public as $$
declare t public.profiles%rowtype;
begin
  select * into t from public.profiles where id = p_target;
  if not found or p_target = auth.uid() or t.is_platform_admin then return false; end if;
  if public.is_platform_admin() then return t.role = 'admin'; end if;
  if public.is_admin() then
    return t.role in ('student', 'lecturer') and t.university_id = public.my_university();
  end if;
  return false;
end $$;
revoke all on function public.can_manage_user(uuid) from public, anon;
grant execute on function public.can_manage_user(uuid) to authenticated;

-- ---------- 3. The people of my campus (campus administrator) -----------------------------
create or replace function public.admin_list_people()
returns table (
  id uuid, full_name text, email text, role text, status text, is_class_rep boolean,
  student_number text, staff_number text, programme_id uuid, programme_name text,
  department text, cohort text, created_at timestamptz
) language plpgsql stable security definer set search_path = public as $$
begin
  if not public.is_admin() or public.is_platform_admin() then raise exception 'not_allowed' using errcode = '42501'; end if;
  return query
    select p.id, p.full_name, p.email, p.role::text, p.status::text, p.is_class_rep,
           p.student_number, p.staff_number, p.programme_id, g.name,
           p.department, p.cohort, p.created_at
    from public.profiles p
    left join public.programmes g on g.id = p.programme_id
    where p.university_id = public.my_university() and not p.is_platform_admin
    order by p.full_name;
end $$;
revoke all on function public.admin_list_people() from public, anon;
grant execute on function public.admin_list_people() to authenticated;

-- ---------- 4. The administrators of every campus (app manager) ----------------------------
create or replace function public.platform_list_admins()
returns table (
  id uuid, full_name text, email text, status text, staff_number text,
  university_id uuid, university_code text, university_name text, created_at timestamptz
) language plpgsql stable security definer set search_path = public as $$
begin
  if not public.is_platform_admin() then raise exception 'not_allowed' using errcode = '42501'; end if;
  return query
    select p.id, p.full_name, p.email, p.status::text, p.staff_number,
           u.id, u.code, u.name, p.created_at
    from public.profiles p
    join public.universities u on u.id = p.university_id
    where p.role = 'admin' and not p.is_platform_admin and not u.is_platform
    order by u.name, p.full_name;
end $$;
revoke all on function public.platform_list_admins() from public, anon;
grant execute on function public.platform_list_admins() to authenticated;

-- ---------- 5. Edit someone's details -------------------------------------------------------
create or replace function public.admin_update_person(
  p_user uuid, p_full_name text, p_number text, p_programme uuid default null, p_department text default null)
returns void language plpgsql security definer set search_path = public as $$
declare t public.profiles%rowtype; v_old uuid;
begin
  if not public.can_manage_user(p_user) then raise exception 'not_allowed' using errcode = '42501'; end if;
  if length(trim(coalesce(p_full_name, ''))) < 3 then raise exception 'invalid_name' using errcode = 'P0001'; end if;
  if length(trim(coalesce(p_number, ''))) < 2 then raise exception 'invalid_number' using errcode = 'P0001'; end if;
  select * into t from public.profiles where id = p_user for update;
  v_old := t.programme_id;
  if p_programme is not null and not exists (
       select 1 from public.programmes where id = p_programme and university_id = t.university_id) then
    raise exception 'unknown_programme' using errcode = 'P0001';
  end if;

  perform set_config('app.internal', '1', true);
  begin
    update public.profiles set
      full_name = trim(p_full_name),
      student_number = case when role = 'student' then trim(p_number) else student_number end,
      staff_number   = case when role <> 'student' then trim(p_number) else staff_number end,
      programme_id   = case when role = 'student' then coalesce(p_programme, programme_id) else programme_id end,
      department     = case when role <> 'student' then nullif(trim(coalesce(p_department, '')), '') else department end,
      is_class_rep   = case when role = 'student' and p_programme is not null and p_programme is distinct from v_old
                            then false else is_class_rep end
    where id = p_user;
  exception when unique_violation then
    perform set_config('app.internal', '', true);
    raise exception 'number_taken' using errcode = 'P0001';
  end;

  -- A student who moved programme gets the new programme's courses.
  if t.role = 'student' and p_programme is not null and p_programme is distinct from v_old then
    delete from public.enrollments e
      using public.programme_courses pc
      where e.student_id = p_user and pc.programme_id = v_old and pc.course_code = e.course_code;
    insert into public.enrollments (student_id, course_code)
      select p_user, pc.course_code from public.programme_courses pc where pc.programme_id = p_programme
      on conflict do nothing;
  end if;
  perform set_config('app.internal', '', true);
  perform public.log_audit('PERSON_EDITED', trim(p_full_name));
end $$;
revoke all on function public.admin_update_person(uuid, text, text, uuid, text) from public, anon;
grant execute on function public.admin_update_person(uuid, text, text, uuid, text) to authenticated;

-- ---------- 6. Change someone's role -----------------------------------------------------------
-- Campus administrator: student <-> lecturer. App manager: an administrator becomes lecturer or student.
create or replace function public.admin_change_role(
  p_user uuid, p_role text, p_number text default null, p_programme uuid default null)
returns void language plpgsql security definer set search_path = public as $$
declare t public.profiles%rowtype; v_num text;
begin
  if not public.can_manage_user(p_user) then raise exception 'not_allowed' using errcode = '42501'; end if;
  if p_role not in ('student', 'lecturer') then raise exception 'role_not_allowed' using errcode = 'P0001'; end if;
  select * into t from public.profiles where id = p_user for update;
  if t.role::text = p_role then return; end if;
  v_num := nullif(trim(coalesce(p_number, '')), '');

  perform set_config('app.internal', '1', true);
  begin
    if p_role = 'student' then
      if p_programme is null or not exists (select 1 from public.programmes where id = p_programme and university_id = t.university_id) then
        perform set_config('app.internal', '', true);
        raise exception 'programme_required' using errcode = 'P0001';
      end if;
      delete from public.teaching_assignments where lecturer_id = p_user;
      update public.profiles set role = 'student', programme_id = p_programme, is_class_rep = false,
        student_number = coalesce(v_num, student_number, staff_number), staff_number = null, department = null
      where id = p_user;
      insert into public.enrollments (student_id, course_code)
        select p_user, pc.course_code from public.programme_courses pc where pc.programme_id = p_programme
        on conflict do nothing;
    else
      delete from public.enrollments where student_id = p_user;
      update public.profiles set role = 'lecturer', programme_id = null, is_class_rep = false,
        staff_number = coalesce(v_num, staff_number, student_number), student_number = null,
        cohort = coalesce(cohort, 'Lecturer')
      where id = p_user;
    end if;
  exception when unique_violation then
    perform set_config('app.internal', '', true);
    raise exception 'number_taken' using errcode = 'P0001';
  end;
  perform set_config('app.internal', '', true);
  perform public.log_audit('ROLE_CHANGED_BY_ADMIN', format('%s -> %s', t.full_name, p_role));
  perform public.notify_users(array[p_user], 'Your role changed', 'Your role is now ' || p_role || '.', 'approval', false);
end $$;
revoke all on function public.admin_change_role(uuid, text, text, uuid) from public, anon;
grant execute on function public.admin_change_role(uuid, text, text, uuid) to authenticated;

-- ---------- 7. Pause / resume -----------------------------------------------------------------------
create or replace function public.admin_set_status(p_user uuid, p_pause boolean, p_reason text default '')
returns void language plpgsql security definer set search_path = public as $$
begin
  if not public.can_manage_user(p_user) then raise exception 'not_allowed' using errcode = '42501'; end if;
  perform set_config('app.internal', '1', true);
  if p_pause then
    update public.profiles set status = 'suspended' where id = p_user and status in ('approved', 'pending');
    delete from public.devices where user_id = p_user;
  else
    update public.profiles set status = 'approved' where id = p_user and status = 'suspended';
  end if;
  perform set_config('app.internal', '', true);
  perform public.log_audit(case when p_pause then 'ACCOUNT_PAUSED' else 'ACCOUNT_RESUMED' end, coalesce(p_reason, ''));
end $$;
revoke all on function public.admin_set_status(uuid, boolean, text) from public, anon;
grant execute on function public.admin_set_status(uuid, boolean, text) to authenticated;

-- ---------- 8. Class representatives, chosen inside the person's programme -----------------------------
-- (public.set_class_rep already lets an administrator do this; this view lists a programme's students.)
create or replace function public.admin_programme_students(p_programme uuid)
returns table (id uuid, full_name text, student_number text, is_class_rep boolean, status text)
language plpgsql stable security definer set search_path = public as $$
begin
  if not public.is_admin() or public.is_platform_admin() then raise exception 'not_allowed' using errcode = '42501'; end if;
  return query
    select p.id, p.full_name, p.student_number, p.is_class_rep, p.status::text
    from public.profiles p
    where p.programme_id = p_programme and p.role = 'student' and p.university_id = public.my_university()
    order by p.full_name;
end $$;
revoke all on function public.admin_programme_students(uuid) from public, anon;
grant execute on function public.admin_programme_students(uuid) to authenticated;

-- ---------- 9. AI settings: the key lives ONLY here, never in the app ------------------------------------------
create table if not exists public.ai_settings (
  id          int primary key default 1 check (id = 1),
  provider    text not null default 'gemini',
  model       text not null default 'gemini-2.5-flash',
  api_key     text not null default '',
  enabled     boolean not null default true,
  updated_by  uuid references public.profiles (id) on delete set null,
  updated_at  timestamptz not null default now()
);
insert into public.ai_settings (id) values (1) on conflict do nothing;
-- Row-level security ON and NO policy: the app (and every signed-in user) can never read the row.
-- Only the Edge Function (service role) and the SECURITY DEFINER functions below can.
alter table public.ai_settings enable row level security;
revoke all on public.ai_settings from public, anon, authenticated;

create table if not exists public.ai_usage (
  user_id uuid not null references public.profiles (id) on delete cascade,
  day     date not null default current_date,
  used    int  not null default 0,
  primary key (user_id, day)
);
alter table public.ai_usage enable row level security;
revoke all on public.ai_usage from public, anon, authenticated;

-- The app manager saves or replaces the key. Empty key = keep the old one.
create or replace function public.platform_set_ai_settings(
  p_provider text, p_model text, p_api_key text, p_enabled boolean)
returns void language plpgsql security definer set search_path = public as $$
begin
  if not public.is_platform_admin() then raise exception 'not_allowed' using errcode = '42501'; end if;
  update public.ai_settings set
    provider = coalesce(nullif(trim(p_provider), ''), provider),
    model    = coalesce(nullif(trim(p_model), ''), model),
    api_key  = case when nullif(trim(coalesce(p_api_key, '')), '') is null then api_key else trim(p_api_key) end,
    enabled  = coalesce(p_enabled, enabled),
    updated_by = auth.uid(), updated_at = now()
  where id = 1;
  perform public.log_audit('AI_SETTINGS_CHANGED', coalesce(nullif(trim(p_model), ''), 'model unchanged'));
end $$;
revoke all on function public.platform_set_ai_settings(text, text, text, boolean) from public, anon;
grant execute on function public.platform_set_ai_settings(text, text, text, boolean) to authenticated;

create or replace function public.platform_clear_ai_key()
returns void language plpgsql security definer set search_path = public as $$
begin
  if not public.is_platform_admin() then raise exception 'not_allowed' using errcode = '42501'; end if;
  update public.ai_settings set api_key = '', updated_by = auth.uid(), updated_at = now() where id = 1;
  perform public.log_audit('AI_KEY_REMOVED', '');
end $$;
revoke all on function public.platform_clear_ai_key() from public, anon;
grant execute on function public.platform_clear_ai_key() to authenticated;

-- What anyone signed in may know: is AI on, which model. Never the key (only a masked hint for the manager).
create or replace function public.ai_status()
returns table (enabled boolean, provider text, model text, has_key boolean, key_hint text, updated_at timestamptz)
language plpgsql stable security definer set search_path = public as $$
declare s public.ai_settings%rowtype;
begin
  if auth.uid() is null then raise exception 'not_allowed' using errcode = '42501'; end if;
  select * into s from public.ai_settings where id = 1;
  return query select s.enabled, s.provider, s.model, length(s.api_key) > 0,
    case when public.is_platform_admin() and length(s.api_key) > 8
         then left(s.api_key, 4) || '…' || right(s.api_key, 4) else '' end,
    s.updated_at;
end $$;
revoke all on function public.ai_status() from public, anon;
grant execute on function public.ai_status() to authenticated;

-- Used by the Edge Function only (service role): counts a use and refuses past the daily limit.
create or replace function public.ai_take_use(p_user uuid, p_limit int)
returns boolean language plpgsql security definer set search_path = public as $$
declare v int;
begin
  insert into public.ai_usage (user_id, day, used) values (p_user, current_date, 1)
  on conflict (user_id, day) do update set used = public.ai_usage.used + 1
  returning used into v;
  return v <= p_limit;
end $$;
revoke all on function public.ai_take_use(uuid, int) from public, anon, authenticated;
grant execute on function public.ai_take_use(uuid, int) to service_role;

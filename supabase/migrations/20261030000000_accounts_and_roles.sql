-- Accounts and roles: app manager, account fixes, class representatives, adopting accounts, provisioning without e-mail codes
-- Merged, in order, from: 20261030000000_platform_manager.sql, 20261031000000_fix_accounts_and_admins.sql, 20261101000000_class_reps_and_sync.sql, 20261102000000_adopt_existing_accounts.sql, 20261103000000_provision_profile_and_no_email_check.sql

-- ===== 20261030000000_platform_manager =====
-- The app manager (platform administrator).
--
-- The manager's EMAIL is stored here. The PASSWORD is never stored in Git or in the app: it is set when
-- the account is created in Supabase (Authentication -> Users -> Add user, "Auto Confirm User" ticked).
-- A person who signs up through the app can never become the manager: public sign-ups are not
-- confirmed when they are created, and the platform campus cannot be chosen at sign-up.

insert into public.app_settings (key, value)
values ('platform_manager_email', 'abcplan2023@gmail.com')
on conflict (key) do update set value = excluded.value;

create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  meta     jsonb := coalesce(new.raw_user_meta_data, '{}'::jsonb);
  v_staff  boolean := coalesce((meta ->> 'is_staff')::boolean, false);
  v_number text := trim(coalesce(meta ->> 'id_number', ''));
  v_uni    uuid;
  v_prog   uuid;
  v_text   text := nullif(trim(coalesce(meta ->> case when coalesce((meta ->> 'is_staff')::boolean, false)
                                                      then 'department' else 'programme' end, '')), '');
  v_mgr    text := (select value from public.app_settings where key = 'platform_manager_email');
begin
  -- The app manager: only an account created already-confirmed (from the Supabase dashboard).
  if v_mgr is not null and lower(new.email) = lower(v_mgr) and new.email_confirmed_at is not null then
    select id into v_uni from public.universities where is_platform;
    insert into public.profiles
      (id, university_id, full_name, email, email_verified, role, requested_role, status,
       staff_number, department, cohort, is_platform_admin, approved_at)
    values
      (new.id, v_uni, 'ChuoPulse Manager', lower(new.email), true, 'admin', 'student', 'approved',
       'APP-001', 'App management', 'App manager', true, now());
    return new;
  end if;

  select id into v_uni from public.universities
  where code = upper(trim(coalesce(meta ->> 'university', ''))) and not is_platform;
  if v_uni is null then
    raise exception 'university_required' using errcode = 'P0001';
  end if;

  if not v_staff then
    select id into v_prog from public.programmes where university_id = v_uni and name = v_text;
    if v_prog is not null then v_text := null; end if;
  end if;

  insert into public.profiles
    (id, university_id, full_name, email, email_verified, role, requested_role, status,
     student_number, staff_number, programme_id, department)
  values
    (new.id, v_uni, trim(coalesce(meta ->> 'full_name', '')), lower(new.email),
     new.email_confirmed_at is not null, 'student',
     case when v_staff then 'lecturer'::public.app_role else 'student'::public.app_role end,
     'pending',
     case when v_staff then null else v_number end,
     case when v_staff then v_number else null end,
     v_prog, v_text);
  return new;
end $$;

-- If the manager's account already exists (created before this migration), promote it now.
insert into public.profiles
  (id, university_id, full_name, email, email_verified, role, requested_role, status,
   staff_number, department, cohort, is_platform_admin, approved_at)
select u.id, (select id from public.universities where is_platform), 'ChuoPulse Manager', lower(u.email), true,
       'admin', 'student', 'approved', 'APP-001', 'App management', 'App manager', true, now()
from auth.users u
where lower(u.email) = 'abcplan2023@gmail.com' and u.email_confirmed_at is not null
on conflict (id) do update
  set role = 'admin', status = 'approved', is_platform_admin = true,
      university_id = excluded.university_id, email_verified = true;

-- ===== 20261031000000_fix_accounts_and_admins =====
-- =====================================================================================
-- Fix: account creation, campus creation, campus administrators.   Safe to run twice.
--
-- What was wrong
--  1. handle_new_user() raised "university_required" for ANY account created without sign-up
--     data. Supabase's dashboard "Add user" sends no such data, so it always failed with
--     "Database error creating new user" (unless the email was the manager's).
--  2. Nothing in the app could create an account with a password for someone else.
--     Now the Edge Function `admin-create-user` does it, after this file's can_provision() says yes.
--  3. platform_create_university() gave raw database errors; now it gives clear ones.
-- =====================================================================================

-- ---------- 1. Sign-up trigger -------------------------------------------------------
-- Three kinds of new account:
--   a) the app manager (email in app_settings, created confirmed from the dashboard)
--   b) provisioned: made by the Edge Function. Role and campus come from raw_app_meta_data, which
--      only the service role can write, so a person signing up can never grant themselves anything
--   c) public sign-up from the app: always a PENDING student/lecturer-request of the chosen campus
-- Anything else (dashboard "Add user", SQL) is allowed to exist WITHOUT a profile; it can do nothing
-- until you run attach_profile() below.

create or replace function public.make_manager_profile(p_id uuid, p_email text)
returns void language plpgsql security definer set search_path = public as $$
declare v_uni uuid;
begin
  select id into v_uni from public.universities where is_platform;
  if v_uni is null then
    insert into public.universities (code, name, is_platform)
    values ('PLATFORM', 'CampusConnect (app management)', true)
    on conflict (code) do update set is_platform = true
    returning id into v_uni;
  end if;
  insert into public.profiles
    (id, university_id, full_name, email, email_verified, role, requested_role, status,
     staff_number, department, cohort, is_platform_admin, approved_at)
  values
    (p_id, v_uni, 'ChuoPulse Manager', lower(p_email), true, 'admin', 'student', 'approved',
     'APP-001', 'App management', 'App manager', true, now())
  on conflict (id) do update
    set role = 'admin', status = 'approved', is_platform_admin = true,
        university_id = excluded.university_id, email_verified = true;
end $$;

create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  meta     jsonb := coalesce(new.raw_user_meta_data, '{}'::jsonb);
  app      jsonb := coalesce(new.raw_app_meta_data,  '{}'::jsonb);
  v_mgr    text  := (select value from public.app_settings where key = 'platform_manager_email');
  v_prov   boolean := coalesce(app ->> 'provisioned', '') = 'true';
  v_src    jsonb := case when v_prov then app else meta end;
  v_staff  boolean := lower(coalesce(meta ->> 'is_staff', 'false')) in ('true', 't', '1');
  v_role   public.app_role := 'student';
  v_code   text := upper(trim(coalesce(v_src ->> 'university', '')));
  v_name   text := trim(coalesce(case when v_prov then meta ->> 'full_name' else v_src ->> 'full_name' end, ''));
  v_number text := trim(coalesce(v_src ->> 'id_number', ''));
  v_text   text;
  v_uni    uuid;
  v_prog   uuid;
begin
  -- a) the app manager
  if v_mgr is not null and lower(new.email) = lower(v_mgr) then
    if new.email_confirmed_at is not null then
      perform public.make_manager_profile(new.id, new.email);
    end if;
    return new;                       -- unconfirmed: promoted when the email is confirmed
  end if;

  -- created from the dashboard / SQL with no sign-up data: allowed, but profile-less
  if v_code = '' and not v_prov and not (meta ? 'university') then
    return new;
  end if;

  select id into v_uni from public.universities where code = v_code and not is_platform;
  if v_uni is null then raise exception 'university_required' using errcode = 'P0001'; end if;
  if length(v_name)   < 3 then raise exception 'name_required'   using errcode = 'P0001'; end if;
  if length(v_number) < 2 then raise exception 'number_required' using errcode = 'P0001'; end if;

  if v_prov then
    v_role := case lower(coalesce(app ->> 'role', 'student'))
                when 'admin' then 'admin' when 'lecturer' then 'lecturer' else 'student' end;
    v_staff := v_role <> 'student';
  end if;

  v_text := nullif(trim(coalesce(v_src ->> case when v_staff then 'department' else 'programme' end, '')), '');
  if not v_staff then
    select id into v_prog from public.programmes where university_id = v_uni and name = v_text;
    if v_prog is not null then v_text := null; end if;
  end if;

  if exists (select 1 from public.profiles where university_id = v_uni
             and lower(coalesce(case when v_staff then staff_number else student_number end, '')) = lower(v_number)) then
    raise exception 'number_taken' using errcode = 'P0001';
  end if;

  insert into public.profiles
    (id, university_id, full_name, email, email_verified, role, requested_role, status,
     student_number, staff_number, programme_id, department, cohort, approved_at)
  values
    (new.id, v_uni, v_name, lower(new.email), new.email_confirmed_at is not null,
     case when v_prov then v_role else 'student' end,
     case when v_staff and v_role <> 'admin' then 'lecturer'::public.app_role else 'student'::public.app_role end,
     case when v_prov then 'approved'::public.account_status else 'pending'::public.account_status end,
     case when v_staff then null else v_number end,
     case when v_staff then v_number else null end,
     v_prog, v_text,
     case when v_prov then (case v_role when 'admin' then 'Campus administrator' when 'lecturer' then 'Lecturer'
                                        else 'Year 1 - Semester 1' end) else null end,
     case when v_prov then now() else null end);

  if v_prov and v_role = 'student' and v_prog is not null then
    insert into public.enrollments (student_id, course_code)
    select new.id, pc.course_code from public.programme_courses pc where pc.programme_id = v_prog
    on conflict do nothing;
  end if;
  return new;
end $$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users
  for each row execute function public.handle_new_user();

-- Confirming the manager's email (for example after a normal sign-up) makes the profile.
create or replace function public.sync_email_verified()
returns trigger language plpgsql security definer set search_path = public as $$
declare v_mgr text := (select value from public.app_settings where key = 'platform_manager_email');
begin
  if new.email_confirmed_at is not null and old.email_confirmed_at is null then
    update public.profiles set email_verified = true where id = new.id;
    if v_mgr is not null and lower(new.email) = lower(v_mgr)
       and not exists (select 1 from public.profiles where id = new.id) then
      perform public.make_manager_profile(new.id, new.email);
    end if;
  end if;
  return new;
end $$;

-- ---------- 2. Attach a profile to an account created from the dashboard -------------
-- SQL editor only (not callable from the app). Example:
--   select public.attach_profile('someone@gmail.com', 'CUOM', 'Full Name', 'admin', 'STAFF-001');
--   select public.attach_profile('you@gmail.com', 'PLATFORM', 'Your Name', 'admin', 'APP-002');  -- app manager
create or replace function public.attach_profile(
  p_email text, p_university_code text, p_full_name text, p_role text, p_number text)
returns text language plpgsql security definer set search_path = public as $$
declare v_user uuid; v_uni public.universities%rowtype; v_role public.app_role; v_platform boolean;
begin
  select id into v_user from auth.users where lower(email) = lower(trim(p_email));
  if v_user is null then raise exception 'no sign-in account with that email: create it first (Authentication -> Users)' using errcode = 'P0001'; end if;
  select * into v_uni from public.universities where code = upper(trim(p_university_code));
  if v_uni.id is null then raise exception 'unknown university code %', p_university_code using errcode = 'P0001'; end if;
  if lower(p_role) not in ('student', 'lecturer', 'admin') then raise exception 'role must be student, lecturer or admin' using errcode = 'P0001'; end if;
  v_role := lower(p_role)::public.app_role;
  v_platform := v_uni.is_platform;
  if v_platform and v_role <> 'admin' then raise exception 'the PLATFORM campus is for the app manager (role admin)' using errcode = 'P0001'; end if;

  perform set_config('app.internal', '1', true);
  insert into public.profiles
    (id, university_id, full_name, email, email_verified, role, requested_role, status,
     student_number, staff_number, cohort, is_platform_admin, approved_at)
  values
    (v_user, v_uni.id, trim(p_full_name), lower(trim(p_email)), true, v_role,
     case when v_role = 'lecturer' then 'lecturer'::public.app_role else 'student'::public.app_role end,
     'approved',
     case when v_role = 'student' then trim(p_number) else null end,
     case when v_role <> 'student' then trim(p_number) else null end,
     case v_role when 'admin' then 'Campus administrator' when 'lecturer' then 'Lecturer' else 'Year 1 - Semester 1' end,
     v_platform, now())
  on conflict (id) do update set
    university_id = excluded.university_id, full_name = excluded.full_name, role = excluded.role,
    status = 'approved', email_verified = true, student_number = excluded.student_number,
    staff_number = excluded.staff_number, is_platform_admin = excluded.is_platform_admin, approved_at = now();
  perform set_config('app.internal', '', true);
  update auth.users set email_confirmed_at = coalesce(email_confirmed_at, now()) where id = v_user;
  return format('%s is now %s at %s', p_email, v_role, v_uni.code);
end $$;
revoke all on function public.attach_profile(text, text, text, text, text) from public, anon, authenticated;

-- ---------- 3. Who may create accounts for others (asked by the Edge Function, with the caller's JWT) ----
create or replace function public.can_provision(p_university_code text, p_role text)
returns boolean language sql stable security definer set search_path = public as $$
  select case
    when public.is_platform_admin() then lower(p_role) in ('student', 'lecturer', 'admin')
    when public.is_admin() then lower(p_role) in ('student', 'lecturer')
         and upper(trim(p_university_code)) = (select code from public.universities where id = public.my_university())
    else false end
$$;
revoke all on function public.can_provision(text, text) from public, anon;
grant execute on function public.can_provision(text, text) to authenticated;

-- ---------- 4. Campuses: clear errors ---------------------------------------------------
create or replace function public.platform_create_university(p_code text, p_name text)
returns uuid language plpgsql security definer set search_path = public as $$
declare v_id uuid; v_code text := upper(trim(p_code));
begin
  if not public.is_platform_admin() then raise exception 'not_allowed' using errcode = '42501'; end if;
  if v_code !~ '^[A-Z0-9_-]{2,20}$' or v_code = 'PLATFORM' or length(trim(p_name)) < 2 then
    raise exception 'bad_details' using errcode = 'P0001';
  end if;
  begin
    insert into public.universities (code, name) values (v_code, trim(p_name)) returning id into v_id;
  exception when unique_violation then
    raise exception 'campus_exists' using errcode = 'P0001';
  end;
  perform public.log_audit('CAMPUS_CREATED', v_code || ' ' || trim(p_name));
  return v_id;
end $$;

-- Totals per campus (adds programmes, rooms and works so the app can show them).
drop function if exists public.platform_campus_summary();
create or replace function public.platform_campus_summary()
returns table (university_id uuid, code text, name text, people bigint, pending bigint, admins bigint,
               courses bigint, sessions bigint, announcements bigint, materials bigint,
               programmes bigint, rooms bigint, works bigint)
language plpgsql stable security definer set search_path = public as $$
begin
  if not public.is_platform_admin() then raise exception 'not_allowed' using errcode = '42501'; end if;
  return query
  select u.id, u.code, u.name,
    (select count(*) from public.profiles p where p.university_id = u.id),
    (select count(*) from public.profiles p where p.university_id = u.id and p.status = 'pending'),
    (select count(*) from public.profiles p where p.university_id = u.id and p.role = 'admin'),
    (select count(*) from public.courses c where c.university_id = u.id),
    (select count(*) from public.class_sessions s join public.profiles p on p.id = s.lecturer_id where p.university_id = u.id),
    (select count(*) from public.announcements a join public.profiles p on p.id = a.author_id where p.university_id = u.id),
    (select count(*) from public.study_materials m join public.profiles p on p.id = m.uploader_id where p.university_id = u.id),
    (select count(*) from public.programmes g where g.university_id = u.id),
    (select count(*) from public.rooms r where r.university_id = u.id),
    (select count(*) from public.deadlines d join public.profiles p on p.id = d.lecturer_id where p.university_id = u.id)
  from public.universities u where not u.is_platform order by u.code;
end $$;
revoke all on function public.platform_campus_summary() from public, anon;
grant execute on function public.platform_campus_summary() to authenticated;

-- ---------- 5. Repair: manager account that already exists ------------------------------
do $$
declare v_mgr text := (select value from public.app_settings where key = 'platform_manager_email'); v_id uuid;
begin
  if v_mgr is null then return; end if;
  select id into v_id from auth.users where lower(email) = lower(v_mgr) and email_confirmed_at is not null;
  if v_id is not null then perform public.make_manager_profile(v_id, v_mgr); end if;
end $$;

-- ===== 20261101000000_class_reps_and_sync =====
-- =====================================================================================
-- Class representatives + what the app needs to show everything from the cloud.  Safe to run twice.
--
-- A class representative is an APPROVED STUDENT with a mark (profiles.is_class_rep). Their "class" is
-- their programme. A representative may, for courses of their own programme only:
--   * post announcements to the programme or to one course (never campus-wide, never Urgent)
--   * add, change, cancel and delete timetable sessions (with the course's own lecturer)
--   * upload study materials
--   * post assignments (deadlines)
-- A representative may NOT see other students' work, or grade anything.
-- The mark is set by the campus administrator, or by a lecturer who teaches that programme.
-- =====================================================================================

alter table public.profiles add column if not exists is_class_rep boolean not null default false;

create or replace function public.is_class_rep()
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.profiles
                 where id = auth.uid() and status = 'approved' and role = 'student'
                   and is_class_rep and programme_id is not null)
$$;

create or replace function public.rep_of_programme(p_programme uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select p_programme is not null and public.is_class_rep()
     and exists (select 1 from public.profiles where id = auth.uid() and programme_id = p_programme)
$$;

create or replace function public.rep_of_course(p_course text)
returns boolean language sql stable security definer set search_path = public as $$
  select p_course is not null and public.is_class_rep() and exists (
    select 1 from public.profiles me
    join public.programme_courses pc on pc.programme_id = me.programme_id
    where me.id = auth.uid() and pc.course_code = p_course)
$$;

grant execute on function public.is_class_rep(), public.rep_of_programme(uuid), public.rep_of_course(text)
  to anon, authenticated;

-- ---------- Limits: a class representative gets modest posting limits -------------------
create or replace function public.limit_for(p_user uuid, p_kind text)
returns bigint language plpgsql stable security definer set search_path = public as $$
declare l public.role_limits%rowtype; v_rep boolean;
begin
  select rl.* into l
  from public.profiles p
  join public.role_limits rl on rl.role = p.role and rl.status = p.status
  where p.id = p_user;
  if not found then return 0; end if;
  select coalesce(p.is_class_rep and p.role = 'student' and p.status = 'approved', false) into v_rep
  from public.profiles p where p.id = p_user;
  if v_rep then
    case p_kind
      when 'announcements_per_day' then return 10;
      when 'urgent_per_day'        then return 0;
      when 'materials_per_day'     then return 10;
      when 'material_max_bytes'    then return 20971520;
      when 'deadlines_per_day'     then return 5;
      else null;
    end case;
  end if;
  return case p_kind
    when 'announcements_per_day' then l.announcements_per_day
    when 'urgent_per_day'        then l.urgent_per_day
    when 'materials_per_day'     then l.materials_per_day
    when 'material_max_bytes'    then l.material_max_bytes
    when 'deadlines_per_day'     then l.deadlines_per_day
    when 'submissions_per_day'   then l.submissions_per_day
    when 'submission_max_bytes'  then l.submission_max_bytes
    when 'devices_max'           then l.devices_max
    else 0
  end;
end $$;

-- ---------- Policies: let the representative act inside their own programme --------------
drop policy if exists cc_ann_insert on public.announcements;
create policy cc_ann_insert on public.announcements for insert to authenticated
  with check (
    author_id = auth.uid()
    and approval in ('draft', 'approved')
    and (public.admin_of(author_id)
         or (public.is_lecturer() and (
               (audience = 'course'    and public.teaches(course_code))
            or (audience = 'programme' and public.teaches_programme(programme_id))))
         or (public.is_class_rep() and (
               (audience = 'course'    and public.rep_of_course(course_code))
            or (audience = 'programme' and public.rep_of_programme(programme_id)))))
  );

drop policy if exists cc_sessions_insert on public.class_sessions;
create policy cc_sessions_insert on public.class_sessions for insert to authenticated
  with check (
    public.admin_of(lecturer_id)
    or (lecturer_id = auth.uid() and public.teaches(course_code))
    or (public.rep_of_course(course_code) and exists (
          select 1 from public.teaching_assignments t
          where t.lecturer_id = class_sessions.lecturer_id and t.course_code = class_sessions.course_code))
  );
drop policy if exists cc_sessions_update on public.class_sessions;
create policy cc_sessions_update on public.class_sessions for update to authenticated
  using (public.admin_of(lecturer_id) or (lecturer_id = auth.uid() and public.teaches(course_code)) or public.rep_of_course(course_code))
  with check (public.admin_of(lecturer_id) or (lecturer_id = auth.uid() and public.teaches(course_code)) or public.rep_of_course(course_code));
drop policy if exists cc_sessions_delete on public.class_sessions;
create policy cc_sessions_delete on public.class_sessions for delete to authenticated
  using (public.admin_of(lecturer_id) or (lecturer_id = auth.uid() and public.teaches(course_code)) or public.rep_of_course(course_code));

drop policy if exists cc_mat_insert on public.study_materials;
create policy cc_mat_insert on public.study_materials for insert to authenticated
  with check (uploader_id = auth.uid()
              and (public.admin_of(uploader_id)
                   or (public.is_lecturer() and public.teaches(course_code))
                   or public.rep_of_course(course_code)));

drop policy if exists cc_dl_insert on public.deadlines;
create policy cc_dl_insert on public.deadlines for insert to authenticated
  with check (lecturer_id = auth.uid()
              and (public.admin_of(lecturer_id)
                   or (public.is_lecturer() and course_code is not null and public.teaches(course_code))
                   or (course_code is not null and public.rep_of_course(course_code))));

-- Files: materials/<COURSE>/<uid>/<file> and announcements/<uid>/<file>
drop policy if exists cc_mat_obj_insert on storage.objects;
create policy cc_mat_obj_insert on storage.objects for insert to authenticated
  with check (bucket_id = 'materials'
              and (storage.foldername(name))[2] = auth.uid()::text
              and (public.is_admin()
                   or (public.is_lecturer() and public.teaches((storage.foldername(name))[1]))
                   or public.rep_of_course((storage.foldername(name))[1])));
drop policy if exists cc_ann_obj_insert on storage.objects;
create policy cc_ann_obj_insert on storage.objects for insert to authenticated
  with check (bucket_id = 'announcements'
              and (public.is_admin() or public.is_lecturer() or public.is_class_rep())
              and (storage.foldername(name))[1] = auth.uid()::text);

-- ---------- Names people may see ----------------------------------------------------------
-- Staff and class representatives are public inside their campus (students otherwise stay private).
create or replace view public.staff_directory as
  select id, full_name, role, is_class_rep from public.profiles
  where status = 'approved' and (role in ('lecturer', 'admin') or is_class_rep) and public.is_active()
    and university_id = public.my_university();

create or replace view public.class_list as
  select e.course_code, p.id as student_id, p.full_name, p.student_number, p.is_class_rep, p.programme_id
  from public.enrollments e
  join public.profiles p on p.id = e.student_id and p.status = 'approved'
  where public.admin_of(p.id) or public.teaches(e.course_code);
grant select on public.staff_directory, public.class_list to authenticated;

-- ---------- Who may be made a class representative, and by whom ----------------------------
create or replace function public.set_class_rep(p_user uuid, p_on boolean)
returns void language plpgsql security definer set search_path = public as $$
declare t public.profiles%rowtype; v_ok boolean;
begin
  select * into t from public.profiles where id = p_user;
  if not found or not public.same_university(p_user) then raise exception 'not_allowed' using errcode = '42501'; end if;
  if t.role <> 'student' or t.status <> 'approved' then raise exception 'not_an_approved_student' using errcode = 'P0001'; end if;
  if t.programme_id is null then raise exception 'student_has_no_programme' using errcode = 'P0001'; end if;
  v_ok := public.is_admin()
       or (public.is_lecturer() and public.teaches_programme(t.programme_id));
  if not v_ok then raise exception 'not_allowed' using errcode = '42501'; end if;

  perform set_config('app.internal', '1', true);
  update public.profiles set is_class_rep = coalesce(p_on, false) where id = p_user;
  perform set_config('app.internal', '', true);

  perform public.log_audit(case when p_on then 'CLASS_REP_SET' else 'CLASS_REP_REMOVED' end, t.full_name);
  perform public.notify_users(array[p_user],
    case when p_on then 'You are now a class representative' else 'You are no longer a class representative' end,
    case when p_on then 'You can post notices, materials, assignments and timetable changes for your class.'
         else 'Your class representative access was removed.' end,
    'approval', false);
end $$;
revoke all on function public.set_class_rep(uuid, boolean) from public, anon;
grant execute on function public.set_class_rep(uuid, boolean) to authenticated;

-- ---------- Lecturers of a course (names), for scheduling ---------------------------------
create or replace function public.course_lecturer_list(p_course text)
returns table (lecturer_id uuid, full_name text)
language plpgsql stable security definer set search_path = public as $$
declare v_code text;
begin
  select c.code into v_code from public.courses c
   where c.university_id = public.my_university()
     and (c.code = trim(p_course) or lower(c.short_code) = lower(trim(p_course)));
  if v_code is null then raise exception 'course_not_found' using errcode = 'P0001'; end if;
  if not (public.is_admin() or public.teaches(v_code) or public.enrolled_in(v_code) or public.rep_of_course(v_code)) then
    raise exception 'not_allowed' using errcode = '42501';
  end if;
  return query
  select p.id, p.full_name from public.teaching_assignments t
  join public.profiles p on p.id = t.lecturer_id and p.status = 'approved'
  where t.course_code = v_code order by p.full_name;
end $$;
revoke all on function public.course_lecturer_list(text) from public, anon;
grant execute on function public.course_lecturer_list(text) to authenticated;

-- ---------- Lecturers can see which of their students are class reps (class_list above) ----
-- ---------- Approving a lecturer WITH courses: accept short codes (CS210) as well as CUOM:CS210 ----
create or replace function public.approve_account(
  p_user uuid, p_role public.app_role, p_programme uuid default null, p_courses text[] default '{}')
returns void language plpgsql security definer set search_path = public as $$
declare v_user public.profiles%rowtype;
begin
  if not public.is_admin() then raise exception 'not_allowed' using errcode = '42501'; end if;
  if p_role not in ('student', 'lecturer') then raise exception 'cannot_grant_admin_here' using errcode = 'P0001'; end if;

  select * into v_user from public.profiles where id = p_user for update;
  if not found or not public.same_university(p_user) then raise exception 'not_allowed' using errcode = '42501'; end if;
  if v_user.status <> 'pending' then raise exception 'not_pending' using errcode = 'P0001'; end if;
  if not v_user.email_verified then raise exception 'email_not_verified' using errcode = 'P0001'; end if;

  perform set_config('app.internal', '1', true);
  update public.profiles set
    role = p_role, status = 'approved', approved_by = auth.uid(), approved_at = now(),
    programme_id = case when p_role = 'student' then coalesce(p_programme, programme_id) else programme_id end,
    cohort = coalesce(cohort, case when p_role = 'student' then 'Year 1 - Semester 1' else 'Lecturer' end)
  where id = p_user;

  if p_role = 'student' then
    insert into public.enrollments (student_id, course_code)
    select p_user, pc.course_code from public.programme_courses pc
    where pc.programme_id = coalesce(p_programme, v_user.programme_id)
    on conflict do nothing;
  else
    insert into public.teaching_assignments (lecturer_id, course_code)
    select p_user, c.code from public.courses c
    where c.university_id = v_user.university_id
      and (c.code = any (p_courses) or c.short_code = any (p_courses))
    on conflict do nothing;
  end if;
  perform set_config('app.internal', '', true);

  perform public.log_audit('ACCOUNT_APPROVED', format('%s as %s', v_user.full_name, p_role));
  perform public.notify_users(array[p_user],
    'Karibu, ' || split_part(v_user.full_name, ' ', 1) || '. You are in.',
    'The Academic Registry approved your account.', 'approval', false);
end $$;

-- ---------- A course added to a programme enrols the programme's students automatically ---------
create or replace function public.programme_courses_enrol()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into public.enrollments (student_id, course_code)
  select p.id, new.course_code from public.profiles p
  where p.programme_id = new.programme_id and p.role = 'student' and p.status = 'approved'
  on conflict do nothing;
  return new;
end $$;
drop trigger if exists programme_courses_ai on public.programme_courses;
create trigger programme_courses_ai after insert on public.programme_courses
  for each row execute function public.programme_courses_enrol();

-- ---------- OWNER TOOLS (SQL editor only; never callable from the app) ---------------------------
-- You, the owner, can always get in and set anything without email, whatever happens to SMTP.
--   select public.owner_set_password('you@gmail.com', 'A-new-strong-password');
--   select public.attach_profile('someone@gmail.com', 'CUOM', 'Full Name', 'admin', 'STAFF-001');
--   select * from public.owner_people();
create or replace function public.owner_set_password(p_email text, p_password text)
returns text language plpgsql security definer set search_path = public, extensions as $$
begin
  if length(coalesce(p_password, '')) < 8 then raise exception 'password must be at least 8 characters' using errcode = 'P0001'; end if;
  update auth.users
     set encrypted_password = extensions.crypt(p_password, extensions.gen_salt('bf')),
         email_confirmed_at = coalesce(email_confirmed_at, now()), updated_at = now()
   where lower(email) = lower(trim(p_email));
  if not found then raise exception 'no account with that email' using errcode = 'P0001'; end if;
  return 'password set for ' || p_email;
end $$;
revoke all on function public.owner_set_password(text, text) from public, anon, authenticated;

create or replace function public.owner_people()
returns table (email text, full_name text, university text, role text, status text, class_rep boolean, manager boolean)
language sql stable security definer set search_path = public as $$
  select p.email, p.full_name, u.code, p.role::text, p.status::text, p.is_class_rep, p.is_platform_admin
  from public.profiles p join public.universities u on u.id = p.university_id
  order by u.code, p.role, p.full_name
$$;
revoke all on function public.owner_people() from public, anon, authenticated;

-- ===== 20261102000000_adopt_existing_accounts =====
-- Makes setup automatic.
-- 1) If a sign-in account already exists WITHOUT a profile (for example one made by hand in
--    Authentication -> Users), the "add administrator/lecturer/student" step now attaches the
--    profile itself instead of stopping with "email taken". Only the app manager may do this.
-- 2) Gives Supabase's account service access to the sign-up trigger functions again, because
--    the first migration revokes execute from PUBLIC (this was why "Add user" failed).
-- Safe to run more than once.

grant usage on schema public to supabase_auth_admin;
grant execute on function public.handle_new_user()      to supabase_auth_admin;
grant execute on function public.sync_email_verified()  to supabase_auth_admin;
grant execute on function public.make_manager_profile(uuid, text) to supabase_auth_admin;

create or replace function public.adopt_account(
  p_caller uuid, p_email text, p_university_code text, p_full_name text,
  p_role text, p_number text, p_programme text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_user  uuid;
  v_uni   uuid;
  v_role  public.app_role;
  v_staff boolean;
  v_prog  uuid;
  v_text  text;
  v_num   text := trim(coalesce(p_number, ''));
  v_name  text := trim(coalesce(p_full_name, ''));
begin
  if not exists (select 1 from public.profiles where id = p_caller and is_platform_admin) then
    raise exception 'not_allowed' using errcode = '42501';
  end if;

  select id into v_user from auth.users where lower(email) = lower(trim(p_email));
  if v_user is null then return jsonb_build_object('status', 'no_account'); end if;
  if exists (select 1 from public.profiles where id = v_user) then
    return jsonb_build_object('status', 'has_profile');
  end if;

  select id into v_uni from public.universities
   where code = upper(trim(p_university_code)) and not is_platform;
  if v_uni is null then raise exception 'unknown_campus' using errcode = 'P0001'; end if;
  if length(v_name) < 3 then raise exception 'name_required'   using errcode = 'P0001'; end if;
  if length(v_num)  < 2 then raise exception 'number_required' using errcode = 'P0001'; end if;

  v_role  := (case lower(coalesce(p_role, 'student'))
                when 'admin' then 'admin' when 'lecturer' then 'lecturer' else 'student' end)::public.app_role;
  v_staff := v_role <> 'student';

  v_text := nullif(trim(coalesce(p_programme, '')), '');
  if not v_staff then
    select id into v_prog from public.programmes where university_id = v_uni and name = v_text;
    if v_prog is not null then v_text := null; end if;
  end if;

  if exists (select 1 from public.profiles where university_id = v_uni
             and lower(coalesce(case when v_staff then staff_number else student_number end, '')) = lower(v_num)) then
    raise exception 'number_taken' using errcode = 'P0001';
  end if;

  perform set_config('app.internal', '1', true);
  insert into public.profiles
    (id, university_id, full_name, email, email_verified, role, requested_role, status,
     student_number, staff_number, programme_id, department, cohort, approved_at)
  values
    (v_user, v_uni, v_name, lower(trim(p_email)), true, v_role,
     case when v_role = 'lecturer' then 'lecturer'::public.app_role else 'student'::public.app_role end,
     'approved',
     case when v_staff then null else v_num end,
     case when v_staff then v_num else null end,
     v_prog, v_text,
     case v_role when 'admin' then 'Campus administrator' when 'lecturer' then 'Lecturer'
                 else 'Year 1 - Semester 1' end,
     now());
  perform set_config('app.internal', '', true);

  if v_role = 'student' and v_prog is not null then
    insert into public.enrollments (student_id, course_code)
    select v_user, pc.course_code from public.programme_courses pc where pc.programme_id = v_prog
    on conflict do nothing;
  end if;

  update auth.users set email_confirmed_at = coalesce(email_confirmed_at, now()) where id = v_user;
  return jsonb_build_object('status', 'adopted', 'user_id', v_user);
end $$;

revoke all on function public.adopt_account(uuid, text, text, text, text, text, text) from public, anon, authenticated;
grant execute on function public.adopt_account(uuid, text, text, text, text, text, text) to service_role;

-- ===== 20261103000000_provision_profile_and_no_email_check =====
-- Fixes accounts made by an administrator, and drops the email-code requirement.
--
-- 1) provision_profile(): the Edge Function calls it right after it creates (or finds) a sign-in
--    account. It creates the APPROVED profile with the right campus, role, programme and courses.
--    It does not depend on the sign-up trigger, which could not see the data the function sends.
--    Allowed callers (checked here, in the database): the app manager, or an approved administrator
--    of that campus creating a student or lecturer. Only the service role can run it.
-- 2) People who register themselves wait for an administrator to approve them; the email address
--    no longer has to be confirmed with a code first. The administrator's approval is the check.
-- Safe to run more than once.

drop function if exists public.adopt_account(uuid, text, text, text, text, text, text);

create or replace function public.provision_profile(
  p_caller uuid, p_user uuid, p_email text, p_university_code text, p_full_name text,
  p_role text, p_number text, p_programme text, p_overwrite boolean)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  c       public.profiles%rowtype;
  v_user  uuid;
  v_uni   uuid;
  v_role  public.app_role;
  v_staff boolean;
  v_prog  uuid;
  v_text  text;
  v_num   text := trim(coalesce(p_number, ''));
  v_name  text := trim(coalesce(p_full_name, ''));
begin
  select id into v_uni from public.universities
   where code = upper(trim(p_university_code)) and not is_platform;
  if v_uni is null then raise exception 'unknown_campus' using errcode = 'P0001'; end if;

  v_role  := (case lower(coalesce(p_role, 'student'))
                when 'admin' then 'admin' when 'lecturer' then 'lecturer' else 'student' end)::public.app_role;
  v_staff := v_role <> 'student';

  select * into c from public.profiles where id = p_caller and status = 'approved';
  if c.id is null or not (c.is_platform_admin
        or (c.role = 'admin' and c.university_id = v_uni and v_role <> 'admin')) then
    raise exception 'not_allowed' using errcode = '42501';
  end if;

  v_user := coalesce(p_user, (select id from auth.users where lower(email) = lower(trim(p_email))));
  if v_user is null or not exists (select 1 from auth.users where id = v_user) then
    return jsonb_build_object('status', 'no_account');
  end if;

  if exists (select 1 from public.profiles where id = v_user) and not p_overwrite then
    return jsonb_build_object('status', 'has_profile');
  end if;

  if length(v_name) < 3 then raise exception 'name_required'   using errcode = 'P0001'; end if;
  if length(v_num)  < 2 then raise exception 'number_required' using errcode = 'P0001'; end if;

  v_text := nullif(trim(coalesce(p_programme, '')), '');
  if not v_staff then
    select id into v_prog from public.programmes where university_id = v_uni and name = v_text;
    if v_prog is not null then v_text := null; end if;
  end if;

  if exists (select 1 from public.profiles
              where university_id = v_uni and id <> v_user
                and lower(coalesce(case when v_staff then staff_number else student_number end, '')) = lower(v_num)) then
    raise exception 'number_taken' using errcode = 'P0001';
  end if;

  perform set_config('app.internal', '1', true);
  insert into public.profiles
    (id, university_id, full_name, email, email_verified, role, requested_role, status,
     student_number, staff_number, programme_id, department, cohort, is_platform_admin, approved_at)
  values
    (v_user, v_uni, v_name, lower(trim(p_email)), true, v_role,
     case when v_role = 'lecturer' then 'lecturer'::public.app_role else 'student'::public.app_role end,
     'approved',
     case when v_staff then null else v_num end,
     case when v_staff then v_num else null end,
     v_prog, v_text,
     case v_role when 'admin' then 'Campus administrator' when 'lecturer' then 'Lecturer'
                 else 'Year 1 - Semester 1' end,
     false, now())
  on conflict (id) do update set
    university_id = excluded.university_id, full_name = excluded.full_name, email = excluded.email,
    email_verified = true, role = excluded.role, requested_role = excluded.requested_role,
    status = 'approved', student_number = excluded.student_number, staff_number = excluded.staff_number,
    programme_id = excluded.programme_id, department = excluded.department, cohort = excluded.cohort,
    is_platform_admin = false, approved_at = now();
  perform set_config('app.internal', '', true);

  if v_role = 'student' and v_prog is not null then
    insert into public.enrollments (student_id, course_code)
    select v_user, pc.course_code from public.programme_courses pc where pc.programme_id = v_prog
    on conflict do nothing;
  end if;

  update auth.users set email_confirmed_at = coalesce(email_confirmed_at, now()) where id = v_user;
  return jsonb_build_object('status', case when p_overwrite then 'created' else 'adopted' end,
                            'user_id', v_user);
end $$;

revoke all on function public.provision_profile(uuid, uuid, text, text, text, text, text, text, boolean)
  from public, anon, authenticated;
grant execute on function public.provision_profile(uuid, uuid, text, text, text, text, text, text, boolean)
  to service_role;

-- ---------- Self-registered people: no email code, the administrator approves ----------------------
create or replace function public.profiles_trust_admin_approval()
returns trigger language plpgsql set search_path = public as $$
begin
  new.email_verified := true;   -- approval by an administrator is the check, not a code
  return new;
end $$;

drop trigger if exists profiles_email_ok on public.profiles;
create trigger profiles_email_ok before insert on public.profiles
  for each row execute function public.profiles_trust_admin_approval();

select set_config('app.internal', '1', true);
update public.profiles set email_verified = true where not email_verified;
select set_config('app.internal', '', true);

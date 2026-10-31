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

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

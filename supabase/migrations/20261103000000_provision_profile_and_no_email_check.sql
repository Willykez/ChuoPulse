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

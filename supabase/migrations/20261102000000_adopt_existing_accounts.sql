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

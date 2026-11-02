-- =====================================================================================
-- ChuoPulse: messages from the app manager to a campus's administrators, and from a campus
-- administrator to the people of their campus. Safe to run again.
-- =====================================================================================

-- App manager -> the administrators of ONE campus
create or replace function public.platform_notify_campus_admins(
  p_university uuid, p_title text, p_message text, p_urgent boolean default false)
returns integer language plpgsql security definer set search_path = public as $$
declare v_ids uuid[];
begin
  if not public.is_platform_admin() then raise exception 'not_allowed' using errcode = '42501'; end if;
  if length(trim(coalesce(p_title, ''))) < 2 or length(trim(coalesce(p_message, ''))) < 2 then
    raise exception 'invalid_message' using errcode = 'P0001';
  end if;
  select array_agg(id) into v_ids from public.profiles
   where university_id = p_university and role = 'admin' and status = 'approved' and not is_platform_admin;
  if v_ids is null then return 0; end if;
  perform public.notify_users(v_ids, left(trim(p_title), 120), left(trim(p_message), 1000), 'announcement', coalesce(p_urgent, false));
  perform public.log_audit('PLATFORM_MESSAGE', left(trim(p_title), 120));
  return array_length(v_ids, 1);
end $$;
revoke all on function public.platform_notify_campus_admins(uuid, text, text, boolean) from public, anon;
grant execute on function public.platform_notify_campus_admins(uuid, text, text, boolean) to authenticated;

-- Campus administrator -> people of the own campus.
-- p_audience: 'all' | 'students' | 'lecturers' | 'reps' | 'programme' (needs p_programme)
create or replace function public.admin_notify_people(
  p_audience text, p_programme uuid, p_title text, p_message text, p_urgent boolean default false)
returns integer language plpgsql security definer set search_path = public as $$
declare v_ids uuid[]; v_uni uuid := public.my_university();
begin
  if not public.is_admin() or public.is_platform_admin() then raise exception 'not_allowed' using errcode = '42501'; end if;
  if length(trim(coalesce(p_title, ''))) < 2 or length(trim(coalesce(p_message, ''))) < 2 then
    raise exception 'invalid_message' using errcode = 'P0001';
  end if;
  if p_audience = 'programme' and (p_programme is null or not exists (
       select 1 from public.programmes where id = p_programme and university_id = v_uni)) then
    raise exception 'unknown_programme' using errcode = 'P0001';
  end if;
  select array_agg(p.id) into v_ids from public.profiles p
   where p.university_id = v_uni and p.status = 'approved' and not p.is_platform_admin and p.id <> auth.uid()
     and case p_audience
           when 'all'       then true
           when 'students'  then p.role = 'student'
           when 'lecturers' then p.role = 'lecturer'
           when 'reps'      then p.role = 'student' and p.is_class_rep
           when 'programme' then p.role = 'student' and p.programme_id = p_programme
           else false end;
  if v_ids is null then return 0; end if;
  perform public.notify_users(v_ids, left(trim(p_title), 120), left(trim(p_message), 1000), 'announcement', coalesce(p_urgent, false));
  perform public.log_audit('ADMIN_MESSAGE', left(trim(p_title), 120) || ' -> ' || p_audience);
  return array_length(v_ids, 1);
end $$;
revoke all on function public.admin_notify_people(text, uuid, text, text, boolean) from public, anon;
grant execute on function public.admin_notify_people(text, uuid, text, text, boolean) to authenticated;

-- =====================================================================================
-- ChuoPulse: put courses into a programme by hand (after courses were imported with no programme).
-- Makes the given courses EXACTLY the courses of the programme. Students of the programme are
-- enrolled in the added courses (existing trigger) and removed from the dropped ones.
-- Safe to run again.
-- =====================================================================================
create or replace function public.set_programme_courses(p_programme_name text, p_codes text[])
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_uni uuid := public.my_university();
  v_prog uuid;
  v_keep text[];
  v_added int := 0;
  v_removed int := 0;
begin
  if not public.campus_admin(v_uni) then raise exception 'not_allowed' using errcode = '42501'; end if;

  select id into v_prog from public.programmes
   where university_id = v_uni and lower(name) = lower(trim(coalesce(p_programme_name, '')));
  if v_prog is null then raise exception 'unknown_programme' using errcode = 'P0001'; end if;

  -- The app sends short codes (CS101); the database keys are 'CUOM:CS101'. Only this campus's courses count.
  select coalesce(array_agg(c.code), '{}') into v_keep
    from public.courses c
   where c.university_id = v_uni
     and upper(c.short_code) in (select upper(trim(x)) from unnest(coalesce(p_codes, '{}')) as x);

  with gone as (
    delete from public.programme_courses
     where programme_id = v_prog and course_code <> all (v_keep)
    returning course_code
  ), un as (
    delete from public.enrollments e
     using gone g, public.profiles p
     where e.course_code = g.course_code and e.student_id = p.id
       and p.programme_id = v_prog and p.role = 'student'
    returning 1
  )
  select count(*) into v_removed from gone;

  with ins as (
    insert into public.programme_courses (programme_id, course_code)
    select v_prog, k from unnest(v_keep) as k
    on conflict do nothing
    returning 1
  )
  select count(*) into v_added from ins;

  perform public.log_audit('PROGRAMME_COURSES', format('%s: +%s -%s', trim(p_programme_name), v_added, v_removed));
  return jsonb_build_object('added', v_added, 'removed', v_removed);
end $$;
revoke all on function public.set_programme_courses(text, text[]) from public, anon;
grant execute on function public.set_programme_courses(text, text[]) to authenticated;

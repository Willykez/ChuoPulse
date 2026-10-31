-- Assign the lecturers of ONE course (the campus administrator's "Assign lecturers" sheet).
-- The older set_teaching_assignments() replaces everything one lecturer teaches; this replaces
-- everyone who teaches one course. Both write the same table: teaching_assignments.
-- Safe to run twice.

create or replace function public.set_course_lecturers(p_course text, p_lecturers uuid[])
returns void language plpgsql security definer set search_path = public as $$
declare
  v_course  public.courses%rowtype;
  v_ids     uuid[] := coalesce(array(select distinct x from unnest(coalesce(p_lecturers, '{}'::uuid[])) x), '{}');
  v_current uuid[];
  v_added   uuid[];
  v_removed uuid[];
  v_busy    text;
begin
  -- Only an administrator (with the second step done) of the campus that owns the course.
  if not public.is_admin() then raise exception 'not_allowed' using errcode = '42501'; end if;

  -- The app sends the short code (CS210); the stored code carries the campus prefix (CUOM:CS210).
  -- Only this campus's courses are looked at, so another campus's course cannot be found by name.
  select * into v_course from public.courses c
   where c.university_id = public.my_university()
     and (c.code = trim(p_course) or lower(c.short_code) = lower(trim(p_course)))
   for update;
  if not found then raise exception 'course_not_found' using errcode = 'P0001'; end if;

  -- Everyone chosen must be an approved lecturer of this same campus. All or nothing.
  if exists (
    select 1 from unnest(v_ids) i
    where not exists (
      select 1 from public.profiles p
      where p.id = i and p.role = 'lecturer' and p.status = 'approved'
        and p.university_id = public.my_university())
  ) then
    raise exception 'not_a_lecturer' using errcode = 'P0001';
  end if;

  v_current := array(select lecturer_id from public.teaching_assignments where course_code = v_course.code);
  v_added   := array(select x from unnest(v_ids) x where not (x = any (v_current)));
  v_removed := array(select x from unnest(v_current) x where not (x = any (v_ids)));

  -- Do not leave a live class without a lecturer who teaches it: the class has to be moved or
  -- cancelled first. The names come back in the error detail so the app can say who.
  select string_agg(distinct p.full_name, ', ') into v_busy
  from public.class_sessions s
  join public.profiles p on p.id = s.lecturer_id
  where s.course_code = v_course.code and s.status <> 'cancelled' and s.lecturer_id = any (v_removed);
  if v_busy is not null then
    raise exception 'lecturer_has_classes' using errcode = 'P0001', detail = v_busy;
  end if;

  delete from public.teaching_assignments where course_code = v_course.code and lecturer_id = any (v_removed);
  insert into public.teaching_assignments (lecturer_id, course_code)
  select x, v_course.code from unnest(v_added) x
  on conflict do nothing;

  if cardinality(v_added) > 0 or cardinality(v_removed) > 0 then
    perform public.log_audit('COURSE_LECTURERS_SET',
      format('%s: +%s -%s', v_course.short_code, cardinality(v_added), cardinality(v_removed)));
    if cardinality(v_added) > 0 then
      perform public.notify_users(v_added, 'You now teach ' || v_course.short_code,
        'The Academic Registry assigned you to ' || v_course.short_code || '. You can post notices and upload materials for it.',
        'announcement', false);
    end if;
    if cardinality(v_removed) > 0 then
      perform public.notify_users(v_removed, 'You no longer teach ' || v_course.short_code,
        'The Academic Registry changed the lecturers of ' || v_course.short_code || '.',
        'announcement', false);
    end if;
  end if;
end $$;

revoke all on function public.set_course_lecturers(text, uuid[]) from public, anon;
grant execute on function public.set_course_lecturers(text, uuid[]) to authenticated;

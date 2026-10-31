-- =====================================================================================
-- Class representatives + what the app needs to show everything from the cloud.  Safe to run twice.
--
-- A class representative is an APPROVED STUDENT with a mark (profiles.is_class_rep). Their "class" is
-- their programme. A representative may, for courses of their own programme only:
--   * post announcements to the programme or to one course (never campus-wide, never Urgent)
--   * add, change, cancel and delete timetable sessions (with the course's own lecturer)
--   * upload study materials
--   * post assignments (deadlines)
-- A representative may NOT create quizzes, see other students' work, or grade anything.
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

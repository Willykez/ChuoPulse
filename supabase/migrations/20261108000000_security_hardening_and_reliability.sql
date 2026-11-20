-- =====================================================================================
-- CampusConnect: security hardening, correctness fixes and reliability.   Safe to run twice.
--
-- Every item below was REPRODUCED against a copy of this schema before it was fixed (see
-- AUDIT_REPORT.md and tests/). Summary:
--
--  SECURITY
--   S1  make_manager_profile() was executable by anyone (even signed-out) -> platform administrator.
--   S2  The manager's e-mail was claimable by public sign-up when "Confirm email" is off.
--   S3  staff_directory / class_list leaked other campuses' rows through error messages
--       (views without security_barrier).
--   S4  A row could point at somebody else's storage file and so make it readable
--       (submissions, study_materials, announcement attachments).
--   S5  Daily limits could be bypassed by sending a back-dated created_at / submitted_at.
--   S6  Room clash check looked across ALL campuses (blocked other campuses, leaked course codes).
--   S7  Maintenance mode was only a screen in the app; the database still served everything.
--   S8  Pause + resume turned a never-approved person into an approved student.
--   S9  Admin password resets / e-mail changes / deletions left no audit trail; sessions survived.
--
--  RELIABILITY
--   R1  platform_wipe_campus() failed with a foreign-key error whenever a student had a programme.
--   R2  Files of deleted rows / wiped campuses were orphaned in Storage. Now queued and swept.
--   R3  Quiz auto-submit housekeeping scanned and locked every attempt on every answer save.
--   R4  Publishing a draft announcement notified nobody.
--   R5  Removing a course from a programme left its students enrolled.
--   R6  Missing indexes on the hottest lookups; AI quota counted in UTC and charged failed calls.
--
--  NEW: erase_person_data (privacy requests), revoke_user_sessions, is_late on submissions,
--       lecturer clash check, notification helpers, platform_health(), storage cleanup queue.
-- =====================================================================================

-- ---------- 0. From now on new functions are PRIVATE unless granted -------------------------
-- (Postgres gives EXECUTE to PUBLIC on every new function; the earlier schema-level
--  "alter default privileges ... in schema" cannot remove that built-in default.)
alter default privileges revoke execute on functions from public;

insert into public.app_settings (key, value) values
  ('orphan_sweep', 'off'),              -- 'on': files nobody references for 24 h are queued for deletion
  ('enforce_lecturer_clash', 'on')      -- 'off': allow one lecturer in two classes at the same time
on conflict (key) do nothing;

-- ---------- 1. Columns and tables ------------------------------------------------------------
alter table public.profiles    add column if not exists suspended_from public.account_status;
alter table public.submissions add column if not exists is_late boolean not null default false;

create table if not exists public.storage_cleanup_queue (
  id         bigint generated always as identity primary key,
  bucket     text not null check (bucket in ('materials', 'submissions', 'announcements')),
  path       text not null,
  reason     text not null default 'row_deleted',
  queued_at  timestamptz not null default now(),
  attempts   int  not null default 0,
  last_error text,
  unique (bucket, path)
);
alter table public.storage_cleanup_queue enable row level security;   -- no policy: closed to every client
revoke all on public.storage_cleanup_queue from public, anon, authenticated;

-- ---------- 2. Indexes for the lookups every request makes --------------------------------------
create index if not exists courses_university_idx        on public.courses (university_id);
create index if not exists programme_courses_course_idx  on public.programme_courses (course_code);
create index if not exists profiles_programme_idx        on public.profiles (programme_id) where programme_id is not null;
create index if not exists profiles_uni_role_idx         on public.profiles (university_id, role, status);
create index if not exists sessions_lecturer_idx         on public.class_sessions (lecturer_id, day_of_week);
create index if not exists sessions_venue_idx            on public.class_sessions (day_of_week, lower(venue)) where status <> 'cancelled';
create index if not exists announcements_attachment_idx  on public.announcements (attachment_path) where attachment_path is not null;
create index if not exists materials_uploader_idx        on public.study_materials (uploader_id, created_at);
create index if not exists deadlines_lecturer_idx        on public.deadlines (lecturer_id, created_at);
create index if not exists submissions_student_idx       on public.submissions (student_id, submitted_at);
create index if not exists submissions_deadline_idx      on public.submissions (deadline_id);
create index if not exists deadline_done_student_idx     on public.deadline_done (student_id);
create index if not exists notifications_unread_idx      on public.notifications (user_id) where not is_read;
create index if not exists audit_university_idx          on public.audit_log (university_id, at desc);
create index if not exists quiz_attempts_open_idx        on public.quiz_attempts (deadline_at) where status = 'in_progress';
create index if not exists quizzes_creator_idx           on public.quizzes (created_by);
create index if not exists quiz_answers_question_idx     on public.quiz_answers (question_id);

-- ---------- 3. Patched functions (each is the previous definition plus the fix named above) ----
create or replace function public.protect_profile_columns()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is not null and not public.is_internal() and not public.is_admin() then
    if new.id is distinct from old.id
       or new.email is distinct from old.email
       or new.email_verified is distinct from old.email_verified
       or new.university_id is distinct from old.university_id
       or new.is_platform_admin is distinct from old.is_platform_admin
       or new.role is distinct from old.role
       or new.requested_role is distinct from old.requested_role
       or new.status is distinct from old.status
       or new.student_number is distinct from old.student_number
       or new.staff_number is distinct from old.staff_number
       or new.programme_id is distinct from old.programme_id
       or new.cohort is distinct from old.cohort
       or new.approved_by is distinct from old.approved_by
       or new.approved_at is distinct from old.approved_at
       or new.suspended_from is distinct from old.suspended_from then
      raise exception 'not_allowed: only the registry can change this' using errcode = '42501';
    end if;
  end if;
  return new;
end $$;

create or replace function public.sessions_before_write()
returns trigger language plpgsql security definer set search_path = public as $$
declare v_clash record; v_uni uuid; v_lec_name text;
begin
  if tg_op = 'UPDATE' and auth.uid() is not null and not public.is_internal() and not public.is_admin() then
    if new.course_code is distinct from old.course_code or new.lecturer_id is distinct from old.lecturer_id
       or new.approval is distinct from old.approval then
      raise exception 'not_allowed' using errcode = '42501';
    end if;
  end if;

  if tg_op = 'UPDATE' then
    -- A room change or cancellation must say why. The reason is shown to students.
    if ((new.venue is distinct from old.venue and new.venue is distinct from old.original_venue)
        or (new.status = 'cancelled' and old.status <> 'cancelled'))
       and length(trim(coalesce(new.change_reason, ''))) = 0 then
      raise exception 'reason_required' using errcode = 'P0001';
    end if;
    if new.venue is distinct from old.venue and old.original_venue is null then
      new.original_venue := old.venue;
    end if;
    if new.venue is distinct from old.venue and new.status = 'scheduled' then
      new.status := 'changed';
    end if;
    new.updated_at := now();
  end if;

  if tg_op = 'INSERT' and auth.uid() is not null then
    new.created_at := now(); new.updated_at := now();   -- never taken from the client
  end if;

  select c.university_id into v_uni from public.courses c where c.code = new.course_code;

  if new.status <> 'cancelled' then
    -- No two live sessions in one room at overlapping times. ONLY INSIDE THIS CAMPUS: another
    -- campus's "LH1" is a different room, and its course codes must never be revealed here.
    select s.id, s.course_code into v_clash
    from public.class_sessions s
    join public.courses c on c.code = s.course_code
    where c.university_id is not distinct from v_uni
      and s.id is distinct from new.id
      and s.status <> 'cancelled'
      and s.day_of_week = new.day_of_week
      and lower(s.venue) = lower(new.venue)
      and s.start_time < new.end_time and new.start_time < s.end_time
    limit 1;
    if found then
      raise exception 'venue_busy: % is busy at that time (%)', new.venue, v_clash.course_code using errcode = 'P0001';
    end if;

    -- A lecturer cannot teach two classes at once (switch off with app_settings.enforce_lecturer_clash = 'off').
    if public.setting_text('enforce_lecturer_clash', 'on') = 'on'
       and exists (select 1 from public.profiles p where p.id = new.lecturer_id and p.role = 'lecturer') then
      select s.id, s.course_code into v_clash
      from public.class_sessions s
      where s.lecturer_id = new.lecturer_id
        and s.id is distinct from new.id
        and s.status <> 'cancelled'
        and s.day_of_week = new.day_of_week
        and s.start_time < new.end_time and new.start_time < s.end_time
      limit 1;
      if found then
        select full_name into v_lec_name from public.profiles where id = new.lecturer_id;
        raise exception 'lecturer_busy: % already has a class at that time (%)', v_lec_name, v_clash.course_code using errcode = 'P0001';
      end if;
    end if;
  end if;
  return new;
end $$;

create or replace function public.announcements_before_insert()
returns trigger language plpgsql security definer set search_path = public as $$
declare v_role public.app_role;
begin
  select role into v_role from public.profiles where id = new.author_id;

  -- Columns the server owns. Without this a client could back-date created_at so that today's
  -- limits (which count rows created today) never see the row.
  if auth.uid() is not null then
    new.created_at := now();
    new.retracted_at := null; new.retracted_by := null; new.reminded_at := null;
    if new.attachment_path is not null and split_part(new.attachment_path, '/', 1) <> new.author_id::text then
      raise exception 'bad_storage_path' using errcode = 'P0001';
    end if;
  end if;

  -- "On behalf of" must be a lecturer of the same campus who teaches the course.
  if new.responsible_lecturer_id is not null and new.responsible_lecturer_id <> new.author_id then
    if not exists (
      select 1 from public.profiles p
      where p.id = new.responsible_lecturer_id and p.role = 'lecturer' and p.status = 'approved'
        and p.university_id = public.university_of(new.author_id)
        and (new.audience <> 'course' or exists (
              select 1 from public.teaching_assignments t where t.lecturer_id = p.id and t.course_code = new.course_code))
    ) then
      raise exception 'responsible_not_for_course' using errcode = 'P0001';
    end if;
  end if;

  perform public.check_limit(new.author_id, 'announcements_per_day',
    (select count(*) from public.announcements where author_id = new.author_id and created_at >= public.today_start()));

  if new.priority = 'urgent' then
    -- Urgent stays meaningful: the registry freely, a lecturer only for their own course, and rationed.
    if v_role = 'lecturer' and new.audience <> 'course' then
      raise exception 'urgent_course_only' using errcode = 'P0001';
    end if;
    perform public.check_limit(new.author_id, 'urgent_per_day',
      (select count(*) from public.announcements
        where author_id = new.author_id and priority = 'urgent' and created_at >= public.today_start()));
  end if;
  return new;
end $$;

create or replace function public.announcements_before_update()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is not null and not public.is_internal() then
    if new.author_id is distinct from old.author_id
       or new.audience is distinct from old.audience
       or new.programme_id is distinct from old.programme_id
       or new.course_code is distinct from old.course_code
       or new.created_at is distinct from old.created_at
       or new.retracted_at is distinct from old.retracted_at
       or new.retracted_by is distinct from old.retracted_by
       or new.reminded_at is distinct from old.reminded_at
       or new.responsible_lecturer_id is distinct from old.responsible_lecturer_id then
      raise exception 'not_allowed: use retract_announcement / remind_unread' using errcode = '42501';
    end if;
    if new.approval is distinct from old.approval and not public.is_admin() then
      -- An author may publish their OWN draft (inserting as 'approved' was already allowed, so this
      -- gives them no new power); every other change of approval stays with the registry.
      if not (old.approval = 'draft' and new.approval = 'approved' and old.author_id = auth.uid() and old.retracted_at is null) then
        raise exception 'not_allowed: only the registry changes approval' using errcode = '42501';
      end if;
    end if;
    if new.attachment_path is distinct from old.attachment_path and new.attachment_path is not null
       and split_part(new.attachment_path, '/', 1) <> old.author_id::text then
      raise exception 'bad_storage_path' using errcode = 'P0001';
    end if;
    -- Editing may not be used to slip past the Urgent rules.
    if new.priority = 'urgent' and old.priority <> 'urgent' and not public.is_admin() then
      raise exception 'urgent_requires_registry' using errcode = 'P0001';
    end if;
  end if;
  return new;
end $$;

create or replace function public.announcements_after_change()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if tg_op = 'DELETE' then
    perform public.log_audit('ANNOUNCEMENT_DELETED', old.title);
    return old;
  end if;
  if new.title is distinct from old.title or new.body is distinct from old.body then
    perform public.log_audit('ANNOUNCEMENT_EDITED', new.title);
  end if;
  -- A draft that gets published (or a held notice the registry approves) used to reach nobody.
  if new.approval = 'approved' and old.approval <> 'approved' and new.retracted_at is null then
    perform public.log_audit('ANNOUNCEMENT_PUBLISHED', format('%s -> %s (%s)', new.title, new.audience, new.priority));
    perform public.notify_users(
      array(select r from public.audience_recipients(new.audience, new.programme_id, new.course_code, public.university_of(new.author_id)) r
            where r <> new.author_id),
      case when new.priority = 'urgent' then 'Urgent: ' || new.title else new.title end,
      'A new notice was published for you.', 'announcement', new.priority = 'urgent');
  end if;
  return new;
end $$;

create or replace function public.materials_before_insert()
returns trigger language plpgsql security definer set search_path = public as $$
declare v_actual bigint; v_mime text;
begin
  if auth.uid() is not null then
    new.created_at := now();
    -- The file must sit in the uploader's own folder of this course: <COURSE>/<uploader uid>/<file>.
    -- Otherwise a row could point at somebody else's file and so make it readable.
    if split_part(new.storage_path, '/', 2) <> new.uploader_id::text
       or split_part(new.storage_path, '/', 1) not in (new.course_code, coalesce((select c.short_code from public.courses c where c.code = new.course_code), new.course_code)) then
      raise exception 'bad_storage_path' using errcode = 'P0001';
    end if;
  end if;
  perform public.check_limit(new.uploader_id, 'materials_per_day',
    (select count(*) from public.study_materials where uploader_id = new.uploader_id and created_at >= public.today_start()));

  -- Trust the size storage recorded, not the number the app claims.
  select (metadata ->> 'size')::bigint, nullif(metadata ->> 'mimetype', '') into v_actual, v_mime
  from storage.objects where bucket_id = 'materials' and name = new.storage_path;
  if v_actual is null then
    raise exception 'file_not_uploaded' using errcode = 'P0001';
  end if;
  new.size_bytes := v_actual;
  new.mime_type := coalesce(v_mime, new.mime_type);   -- what storage saw, not what the app claims
  perform public.check_size(new.uploader_id, 'material_max_bytes', v_actual);
  return new;
end $$;

create or replace function public.deadlines_before_write()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if tg_op = 'INSERT' then
    if auth.uid() is not null then new.created_at := now(); end if;
    perform public.check_limit(new.lecturer_id, 'deadlines_per_day',
      (select count(*) from public.deadlines where lecturer_id = new.lecturer_id and created_at >= public.today_start()));
  elsif auth.uid() is not null and not public.is_internal() then
    if new.lecturer_id is distinct from old.lecturer_id or new.course_code is distinct from old.course_code
       or new.created_at is distinct from old.created_at then
      raise exception 'not_allowed' using errcode = '42501';
    end if;
    if new.approval is distinct from old.approval and not public.is_admin() then
      raise exception 'not_allowed: only the registry changes approval' using errcode = '42501';
    end if;
  end if;
  return new;
end $$;

create or replace function public.submissions_before_write()
returns trigger language plpgsql security definer set search_path = public as $$
declare v_actual bigint; v_mime text; v_due timestamptz;
begin
  if tg_op = 'INSERT' then
    if auth.uid() is not null then
      new.submitted_at := now();
      -- <student uid>/<deadline id>/<file>: the file has to be in the student's own folder.
      if split_part(new.storage_path, '/', 1) <> new.student_id::text then
        raise exception 'bad_storage_path' using errcode = 'P0001';
      end if;
    end if;
    perform public.check_limit(new.student_id, 'submissions_per_day',
      (select count(*) from public.submissions where student_id = new.student_id and submitted_at >= public.today_start()));
    select (metadata ->> 'size')::bigint, nullif(metadata ->> 'mimetype', '') into v_actual, v_mime
    from storage.objects where bucket_id = 'submissions' and name = new.storage_path;
    if v_actual is null then
      raise exception 'file_not_uploaded' using errcode = 'P0001';
    end if;
    new.size_bytes := v_actual;
    new.mime_type := coalesce(v_mime, new.mime_type);
    perform public.check_size(new.student_id, 'submission_max_bytes', v_actual);
    select d.due_at into v_due from public.deadlines d where d.id = new.deadline_id;
    new.is_late := v_due is not null and new.submitted_at > v_due;
    new.status := 'pending_review';
    new.grade := null; new.feedback := null; new.graded_by := null; new.graded_at := null;
  elsif auth.uid() is not null and not public.is_internal() then
    -- Markers may only set grade, feedback and status.
    if new.student_id is distinct from old.student_id or new.deadline_id is distinct from old.deadline_id
       or new.course_code is distinct from old.course_code or new.storage_path is distinct from old.storage_path
       or new.size_bytes is distinct from old.size_bytes or new.notes is distinct from old.notes then
      raise exception 'not_allowed' using errcode = '42501';
    end if;
    if new.grade is distinct from old.grade then
      new.graded_by := auth.uid();
      new.graded_at := now();
      new.status := 'approved';
    end if;
  end if;
  return new;
end $$;

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

  -- The app manager's address is reserved: only the app manager may attach it to anything.
  if not c.is_platform_admin and lower(trim(p_email)) =
       lower(coalesce((select value from public.app_settings where key = 'platform_manager_email'), '')) then
    raise exception 'email_reserved' using errcode = 'P0001';
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
  -- Made by service role, so log_audit() (which uses the caller's JWT) cannot be used: write the row directly.
  insert into public.audit_log (university_id, actor_id, actor_name, actor_role, action, details)
  values (v_uni, c.id, c.full_name, c.role::text,
          case when p_overwrite then 'ACCOUNT_CREATED_BY_ADMIN' else 'ACCOUNT_ADOPTED_BY_ADMIN' end,
          format('%s as %s (%s)', v_name, v_role, lower(trim(p_email))));
  return jsonb_build_object('status', case when p_overwrite then 'created' else 'adopted' end,
                            'user_id', v_user);
end $$;

create or replace function public.platform_wipe_campus(p_university uuid, p_scope text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_paths text[] := '{}';
  v_material_paths text[] := '{}';
  v_submission_paths text[] := '{}';
  v_announcement_paths text[] := '{}';
  v_all boolean := p_scope = 'everything';
  v_people boolean := p_scope in ('people', 'everything');
  v_catalog boolean := p_scope in ('catalog', 'everything');
  v_ann boolean; v_time boolean; v_mat boolean; v_works boolean;
begin
  if not public.is_platform_admin() then raise exception 'not_allowed' using errcode = '42501'; end if;
  if not exists (select 1 from public.universities where id = p_university and not is_platform) then
    raise exception 'unknown campus' using errcode = 'P0001';
  end if;
  if p_scope not in ('announcements', 'timetable', 'materials', 'works', 'notifications', 'catalog', 'people', 'everything') then
    raise exception 'unknown scope' using errcode = 'P0001';
  end if;
  -- Removing people or the catalogue also removes whatever depends on them.
  v_ann   := v_all or v_people or v_catalog or p_scope = 'announcements';
  v_time  := v_all or v_people or v_catalog or p_scope = 'timetable';
  v_mat   := v_all or v_people or v_catalog or p_scope = 'materials';
  v_works := v_all or v_people or v_catalog or p_scope = 'works';

  perform set_config('app.internal', '1', true);
  if v_works then
    v_submission_paths := coalesce((select array_agg(s.storage_path) from public.submissions s
      join public.profiles p on p.id = s.student_id where p.university_id = p_university), '{}');
    v_paths := v_paths || v_submission_paths;
    delete from public.submissions where student_id in (select id from public.profiles where university_id = p_university);
    delete from public.deadlines where lecturer_id in (select id from public.profiles where university_id = p_university);
  end if;
  if v_mat then
    v_material_paths := coalesce((select array_agg(m.storage_path) from public.study_materials m
      join public.profiles p on p.id = m.uploader_id where p.university_id = p_university), '{}');
    v_paths := v_paths || v_material_paths;
    delete from public.study_materials where uploader_id in (select id from public.profiles where university_id = p_university);
  end if;
  if v_ann then
    v_announcement_paths := coalesce((select array_agg(a.attachment_path) from public.announcements a
      join public.profiles p on p.id = a.author_id where p.university_id = p_university and a.attachment_path is not null), '{}');
    v_paths := v_paths || v_announcement_paths;
    delete from public.announcements where author_id in (select id from public.profiles where university_id = p_university);
  end if;
  if v_time then
    delete from public.class_sessions where lecturer_id in (select id from public.profiles where university_id = p_university);
  end if;
  if p_scope = 'notifications' or v_people or v_all then
    delete from public.notifications where user_id in (select id from public.profiles where university_id = p_university);
  end if;
  if v_catalog then
    -- profiles.programme_id has no ON DELETE rule: without this the whole wipe failed with a
    -- foreign-key error as soon as one student belonged to a programme.
    update public.profiles set programme_id = null where university_id = p_university and programme_id is not null;
    delete from public.enrollments where course_code in (select code from public.courses where university_id = p_university);
    delete from public.teaching_assignments where course_code in (select code from public.courses where university_id = p_university);
    delete from public.programmes where university_id = p_university;
    delete from public.courses where university_id = p_university;
    delete from public.rooms where university_id = p_university;
    delete from public.university_students where university_id = p_university;
    delete from public.university_staff where university_id = p_university;
  end if;
  if v_people then
    -- Deleting the sign-in account removes the profile with it.
    delete from auth.users where id in (
      select id from public.profiles
      where university_id = p_university and not is_platform_admin and (v_all or role <> 'admin'));
  end if;
  perform set_config('app.internal', '', true);

  -- The files can only be removed through the Storage API. Remember them in the SAME transaction,
  -- so a crash after this commit can never leave files nobody knows about.
  insert into public.storage_cleanup_queue (bucket, path, reason)
  select 'materials', x, 'campus_wipe' from unnest(v_material_paths) x
  union all select 'submissions', x, 'campus_wipe' from unnest(v_submission_paths) x
  union all select 'announcements', x, 'campus_wipe' from unnest(v_announcement_paths) x
  on conflict (bucket, path) do nothing;

  perform public.log_audit('CAMPUS_DATA_DELETED', p_scope || ' at ' || (select code from public.universities where id = p_university));
  return jsonb_build_object(
    'storage_paths_to_remove', to_jsonb(v_paths),
    'storage_objects_to_remove', jsonb_build_object(
      'materials', to_jsonb(v_material_paths),
      'submissions', to_jsonb(v_submission_paths),
      'announcements', to_jsonb(v_announcement_paths)
    )
  );
end $$;

create or replace function public.suspend_account(p_user uuid, p_reason text)
returns void language plpgsql security definer set search_path = public as $$
begin
  if not public.is_admin() then raise exception 'not_allowed' using errcode = '42501'; end if;
  if p_user = auth.uid() then raise exception 'cannot_suspend_self' using errcode = 'P0001'; end if;
  if not public.same_university(p_user) then raise exception 'not_allowed' using errcode = '42501'; end if;
  perform set_config('app.internal', '1', true);
  update public.profiles set status = 'suspended', suspended_from = status where id = p_user and status in ('approved', 'pending');
  delete from public.devices where user_id = p_user;
  perform set_config('app.internal', '', true);
  perform public.log_audit('ACCOUNT_SUSPEND_REASON', coalesce(p_reason, ''));
end $$;

create or replace function public.admin_set_status(p_user uuid, p_pause boolean, p_reason text default '')
returns void language plpgsql security definer set search_path = public as $$
begin
  if not public.can_manage_user(p_user) then raise exception 'not_allowed' using errcode = '42501'; end if;
  perform set_config('app.internal', '1', true);
  if p_pause then
    update public.profiles set status = 'suspended', suspended_from = status where id = p_user and status in ('approved', 'pending');
    delete from public.devices where user_id = p_user;
  else
    -- Back to what they were. Resuming used to turn a never-approved (pending) person into an
    -- approved student, skipping the registry's approval and enrolment completely.
    update public.profiles set
      status = coalesce(suspended_from, case when approved_at is not null then 'approved'::public.account_status
                                             else 'pending'::public.account_status end),
      suspended_from = null
    where id = p_user and status = 'suspended';
  end if;
  perform set_config('app.internal', '', true);
  perform public.log_audit(case when p_pause then 'ACCOUNT_PAUSED' else 'ACCOUNT_RESUMED' end, coalesce(p_reason, ''));
end $$;

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
      if exists (select 1 from public.class_sessions s where s.lecturer_id = p_user and s.status <> 'cancelled') then
        perform set_config('app.internal', '', true);
        raise exception 'lecturer_has_classes' using errcode = 'P0001';
      end if;
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
  -- The app manager's address is RESERVED. It used to promote whoever registered it first, and with
  -- "Confirm email" switched off that meant a stranger became the platform administrator.
  -- Public sign-up always carries sign-up data, so it is refused. An account made from the dashboard
  -- (no sign-up data) may exist, but stays profile-less until the owner runs owner_make_manager().
  if v_mgr is not null and lower(new.email) = lower(v_mgr) then
    if v_prov or meta ? 'university' or meta ? 'id_number' or meta ? 'is_staff' then
      raise exception 'email_reserved' using errcode = 'P0001';
    end if;
    return new;
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

-- Confirming an e-mail no longer promotes anybody (it used to make the manager profile for the manager's address).
create or replace function public.sync_email_verified()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if new.email_confirmed_at is not null and old.email_confirmed_at is null then
    update public.profiles set email_verified = true where id = new.id;
  end if;
  return new;
end $$;

create or replace function public.export_my_data()
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare uid uuid := auth.uid();
begin
  if uid is null then raise exception 'not_signed_in' using errcode = '42501'; end if;
  return jsonb_build_object(
    'exported_at', now(),
    'profile', (select to_jsonb(p) - 'approved_by' from public.profiles p where p.id = uid),
    'enrollments', coalesce((select jsonb_agg(course_code) from public.enrollments where student_id = uid), '[]'),
    'teaching', coalesce((select jsonb_agg(course_code) from public.teaching_assignments where lecturer_id = uid), '[]'),
    'notifications', coalesce((select jsonb_agg(to_jsonb(n)) from public.notifications n where n.user_id = uid), '[]'),
    'deadline_ticks', coalesce((select jsonb_agg(deadline_id) from public.deadline_done where student_id = uid), '[]'),
    'announcements_opened', (select count(*) from public.announcement_reads where user_id = uid),
    'quiz_attempts', coalesce((select jsonb_agg(jsonb_build_object('quiz_id', a.quiz_id, 'started_at', a.started_at,
                                'status', a.status, 'marked', a.marked)) from public.quiz_attempts a where a.student_id = uid), '[]'),
    'submissions', coalesce((select jsonb_agg(to_jsonb(s) - 'storage_path') from public.submissions s where s.student_id = uid), '[]'),
    'devices', coalesce((select jsonb_agg(jsonb_build_object('label', label, 'last_seen_at', last_seen_at)) from public.devices where user_id = uid), '[]'));
end $$;

create or replace function public.import_courses(p_rows jsonb)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_uni uuid := public.my_university();
  r jsonb; v_code text; v_title text; v_programme text; v_prog uuid; v_key text;
  v_added int := 0; v_skipped int := 0;
begin
  if not public.campus_admin(v_uni) then raise exception 'not_allowed' using errcode = '42501'; end if;
  if jsonb_typeof(coalesce(p_rows, '[]'::jsonb)) <> 'array' or jsonb_array_length(coalesce(p_rows, '[]'::jsonb)) > 2000 then
    raise exception 'too_many_rows' using errcode = 'P0001';
  end if;
  for r in select * from jsonb_array_elements(coalesce(p_rows, '[]'::jsonb)) loop
    v_code := upper(regexp_replace(coalesce(r ->> 'code', ''), '\s', '', 'g'));
    v_title := trim(coalesce(r ->> 'title', ''));
    v_programme := nullif(trim(coalesce(r ->> 'programme', '')), '');
    if length(v_code) < 2 or length(v_title) < 2 then v_skipped := v_skipped + 1; continue; end if;

    insert into public.courses (university_id, short_code, title) values (v_uni, v_code, v_title)
    on conflict (code) do update set title = excluded.title
    returning code into v_key;

    if v_programme is not null then
      insert into public.programmes (university_id, name) values (v_uni, v_programme)
      on conflict (university_id, name) do nothing;
      select id into v_prog from public.programmes where university_id = v_uni and name = v_programme;
      insert into public.programme_courses (programme_id, course_code) values (v_prog, v_key) on conflict do nothing;
    end if;
    v_added := v_added + 1;
  end loop;
  perform public.log_audit('COURSES_IMPORTED', format('%s added, %s skipped', v_added, v_skipped));
  return jsonb_build_object('added', v_added, 'skipped', v_skipped);
end $$;


-- ---------- 4. Who is "active": a campus that is switched off serves nobody (S7) -----------------
-- The app manager put a campus into maintenance, but only the app honoured it. Now the database
-- does too: an approved person of a paused campus is not active, student, lecturer or administrator.
-- (They can still read their own profile and my_campus_paused(), so the app can say why.)
create or replace function public.is_active()
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.profiles p join public.universities u on u.id = p.university_id
                 where p.id = auth.uid() and p.status = 'approved' and (u.is_platform or not u.under_maintenance))
$$;

create or replace function public.is_student()
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.profiles p join public.universities u on u.id = p.university_id
                 where p.id = auth.uid() and p.status = 'approved' and p.role = 'student'
                   and (u.is_platform or not u.under_maintenance))
$$;

create or replace function public.is_lecturer()
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.profiles p join public.universities u on u.id = p.university_id
                 where p.id = auth.uid() and p.status = 'approved' and p.role = 'lecturer'
                   and (u.is_platform or not u.under_maintenance))
$$;

create or replace function public.is_admin()
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.profiles p join public.universities u on u.id = p.university_id
                 where p.id = auth.uid() and p.status = 'approved' and p.role = 'admin'
                   and (u.is_platform or not u.under_maintenance))
     and (public.setting_text('require_admin_mfa', 'true') <> 'true'
          or coalesce(auth.jwt() ->> 'aal', '') = 'aal2')
$$;

create or replace function public.is_class_rep()
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.profiles p join public.universities u on u.id = p.university_id
                 where p.id = auth.uid() and p.status = 'approved' and p.role = 'student'
                   and p.is_class_rep and p.programme_id is not null
                   and (u.is_platform or not u.under_maintenance))
$$;

-- ---------- 5. Views: rows of other campuses must never reach a caller's own predicate (S3) -----------
alter view public.staff_directory set (security_barrier = true);
alter view public.class_list      set (security_barrier = true);

-- ---------- 6. Storage cleanup queue (R2) ----------------------------------------------------------
create or replace function public.queue_storage_delete()
returns trigger language plpgsql security definer set search_path = public as $$
declare v_path text := coalesce(to_jsonb(old) ->> 'storage_path', to_jsonb(old) ->> 'attachment_path');
begin
  if v_path is not null and length(v_path) > 0 then
    insert into public.storage_cleanup_queue (bucket, path, reason) values (tg_argv[0], v_path, 'row_deleted')
    on conflict (bucket, path) do nothing;
  end if;
  return old;
end $$;

drop trigger if exists materials_queue_files on public.study_materials;
create trigger materials_queue_files after delete on public.study_materials
  for each row execute function public.queue_storage_delete('materials');
drop trigger if exists submissions_queue_files on public.submissions;
create trigger submissions_queue_files after delete on public.submissions
  for each row execute function public.queue_storage_delete('submissions');
drop trigger if exists announcements_queue_files on public.announcements;
create trigger announcements_queue_files after delete on public.announcements
  for each row execute function public.queue_storage_delete('announcements');

create or replace function public.queue_replaced_attachment()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if old.attachment_path is not null and old.attachment_path is distinct from new.attachment_path then
    insert into public.storage_cleanup_queue (bucket, path, reason) values ('announcements', old.attachment_path, 'attachment_replaced')
    on conflict (bucket, path) do nothing;
  end if;
  return new;
end $$;
drop trigger if exists announcements_queue_replaced on public.announcements;
create trigger announcements_queue_replaced after update of attachment_path on public.announcements
  for each row execute function public.queue_replaced_attachment();

-- What the cleanup worker should delete next. Entries that a live row references again (the same
-- path re-used) are dropped from the queue instead, and everything waits 5 minutes first, so a
-- re-upload to the same path can never be deleted by mistake.
create or replace function public.storage_cleanup_next(p_limit int default 200)
returns table (id bigint, bucket text, path text)
language plpgsql security definer set search_path = public as $$
begin
  delete from public.storage_cleanup_queue q
   where (q.bucket = 'materials'     and exists (select 1 from public.study_materials m where m.storage_path = q.path))
      or (q.bucket = 'submissions'   and exists (select 1 from public.submissions s where s.storage_path = q.path))
      or (q.bucket = 'announcements' and exists (select 1 from public.announcements a where a.attachment_path = q.path));
  return query
    select q.id, q.bucket, q.path from public.storage_cleanup_queue q
     where q.queued_at <= now() - interval '5 minutes' and q.attempts < 8
     order by q.id limit greatest(1, least(coalesce(p_limit, 200), 1000));
end $$;

create or replace function public.storage_cleanup_done(p_ids bigint[])
returns int language plpgsql security definer set search_path = public as $$
declare v int;
begin
  delete from public.storage_cleanup_queue where id = any (coalesce(p_ids, '{}'));
  get diagnostics v = row_count;
  return v;
end $$;

create or replace function public.storage_cleanup_done_paths(p_bucket text, p_paths text[])
returns int language plpgsql security definer set search_path = public as $$
declare v int;
begin
  delete from public.storage_cleanup_queue where bucket = p_bucket and path = any (coalesce(p_paths, '{}'));
  get diagnostics v = row_count;
  return v;
end $$;

create or replace function public.storage_cleanup_failed(p_id bigint, p_error text)
returns void language sql security definer set search_path = public as $$
  update public.storage_cleanup_queue set attempts = attempts + 1, last_error = left(coalesce(p_error, ''), 300) where id = p_id
$$;

-- Files that no row references any more (uploaded and never used, or left by an older version).
-- OFF by default: switch on with  update app_settings set value = 'on' where key = 'orphan_sweep';
-- once you are sure nothing in the app keeps files in these buckets without a row.
create or replace function public.storage_enqueue_orphans(p_older_than interval default interval '24 hours')
returns int language plpgsql security definer set search_path = public as $$
declare v int := 0;
begin
  if public.setting_text('orphan_sweep', 'off') <> 'on' then return 0; end if;
  insert into public.storage_cleanup_queue (bucket, path, reason)
  select o.bucket_id, o.name, 'orphan' from storage.objects o
   where o.bucket_id in ('materials', 'submissions', 'announcements')
     and o.created_at < now() - p_older_than
     and not exists (select 1 from public.study_materials m where o.bucket_id = 'materials'     and m.storage_path = o.name)
     and not exists (select 1 from public.submissions s     where o.bucket_id = 'submissions'   and s.storage_path = o.name)
     and not exists (select 1 from public.announcements a   where o.bucket_id = 'announcements' and a.attachment_path = o.name)
  on conflict (bucket, path) do nothing;
  get diagnostics v = row_count;
  return v;
end $$;

-- ---------- 7. Quiz housekeeping that scales (R3) -------------------------------------------------
-- Same result as before, but it only touches attempts that really expired (partial index) and never
-- waits for rows another request is already finalising.
create or replace function public.finalize_expired_quiz_attempts()
returns integer language plpgsql security definer set search_path = public as $$
declare v_count integer;
begin
  update public.quiz_attempts a
     set status = 'auto_submitted', submitted_at = a.deadline_at
   where a.id in (select x.id from public.quiz_attempts x
                   where x.status = 'in_progress' and x.deadline_at + interval '10 seconds' <= now()   -- the 10 s are the hand-in grace
                   for update skip locked);
  get diagnostics v_count = row_count;
  return v_count;
end $$;

-- ---------- 8. Courses removed from a programme take their students with them (R5) -----------------
create or replace function public.programme_courses_unenrol()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  delete from public.enrollments e using public.profiles p
   where e.student_id = p.id and e.course_code = old.course_code
     and p.programme_id = old.programme_id and p.role = 'student';
  return old;
end $$;
drop trigger if exists programme_courses_ad on public.programme_courses;
create trigger programme_courses_ad after delete on public.programme_courses
  for each row execute function public.programme_courses_unenrol();

-- ---------- 9. AI quota: one day = one Tanzanian day, and failed calls are not charged (R6) ---------
create or replace function public.ai_take_use(p_user uuid, p_limit int)
returns boolean language plpgsql security definer set search_path = public as $$
declare v int; v_day date := (now() at time zone 'Africa/Dar_es_Salaam')::date;
begin
  if coalesce(p_limit, 0) <= 0 then return false; end if;
  insert into public.ai_usage (user_id, day, used) values (p_user, v_day, 1)
  on conflict (user_id, day) do update set used = public.ai_usage.used + 1 where public.ai_usage.used < p_limit
  returning used into v;
  return v is not null;      -- null = already at the limit: nothing is counted
end $$;

create or replace function public.ai_refund_use(p_user uuid)
returns void language sql security definer set search_path = public as $$
  update public.ai_usage set used = greatest(used - 1, 0)
   where user_id = p_user and day = (now() at time zone 'Africa/Dar_es_Salaam')::date
$$;

-- Only the last four characters of the key are ever shown (eight used to be).
create or replace function public.ai_status()
returns table (enabled boolean, provider text, model text, has_key boolean, key_hint text, updated_at timestamptz)
language plpgsql stable security definer set search_path = public as $$
declare s public.ai_settings%rowtype;
begin
  if auth.uid() is null then raise exception 'not_allowed' using errcode = '42501'; end if;
  select * into s from public.ai_settings where id = 1;
  return query select s.enabled, s.provider, s.model, length(s.api_key) > 0,
    case when public.is_platform_admin() and length(s.api_key) > 8 then '…' || right(s.api_key, 4) else '' end,
    s.updated_at;
end $$;

-- ---------- 10. The owner's way to create the app manager (S2) ---------------------------------------
-- SQL editor only. Sets the password itself (so nobody who registered the address earlier keeps
-- access), ends every existing session, and attaches the manager profile.
--   select public.owner_make_manager('you@gmail.com', 'a-long-new-password');
create or replace function public.owner_make_manager(p_email text, p_password text, p_staff_number text default 'APP-001')
returns text language plpgsql security definer set search_path = public, extensions as $$
declare v_id uuid;
begin
  if length(coalesce(p_password, '')) < 12 then
    raise exception 'use a password of at least 12 characters' using errcode = 'P0001';
  end if;
  select id into v_id from auth.users where lower(email) = lower(trim(p_email));
  if v_id is null then
    raise exception 'no sign-in account with that email: create it first (Authentication -> Users -> Add user, Auto Confirm ticked)' using errcode = 'P0001';
  end if;
  update auth.users
     set encrypted_password = extensions.crypt(p_password, extensions.gen_salt('bf')),
         email_confirmed_at = coalesce(email_confirmed_at, now()), updated_at = now()
   where id = v_id;
  if to_regclass('auth.sessions') is not null then delete from auth.sessions where user_id = v_id; end if;
  insert into public.app_settings (key, value) values ('platform_manager_email', lower(trim(p_email)))
  on conflict (key) do update set value = excluded.value;
  return public.attach_profile(p_email, 'PLATFORM', 'ChuoPulse Manager', 'admin', p_staff_number);
end $$;

-- ---------- 11. Account actions leave a trail, and end sessions (S9) -----------------------------------
create or replace function public.log_account_action(p_target uuid, p_action text, p_details text default '')
returns void language plpgsql security definer set search_path = public as $$
declare t public.profiles%rowtype;
begin
  if p_action not in ('PASSWORD_RESET', 'LOGIN_EMAIL_CHANGED', 'ACCOUNT_DELETED', 'ACCOUNT_ERASED', 'ACCOUNT_SESSIONS_ENDED') then
    raise exception 'unknown_action' using errcode = 'P0001';
  end if;
  if not public.can_manage_user(p_target) then raise exception 'not_allowed' using errcode = '42501'; end if;
  select * into t from public.profiles where id = p_target;
  perform public.log_audit(p_action, format('%s (%s)%s', t.full_name, t.email,
            case when length(trim(coalesce(p_details, ''))) > 0 then ': ' || left(trim(p_details), 200) else '' end));
end $$;

create or replace function public.revoke_user_sessions(p_user uuid)
returns int language plpgsql security definer set search_path = public as $$
declare v int := 0;
begin
  if to_regclass('auth.sessions') is not null then
    delete from auth.sessions where user_id = p_user;     -- refresh tokens go with their session
    get diagnostics v = row_count;
  end if;
  return v;
end $$;

-- ---------- 12. Privacy: erase a person but keep the academic record intact ---------------------------
-- Used by the admin-manage-user function (action "erase") after the database has confirmed that the
-- caller may manage this person. Their name, e-mail and numbers are replaced; the account can no
-- longer sign in; personal by-products (devices, notifications, read marks, ticks, enrolments) go.
-- Submissions, grades and notices they wrote stay, attributed to "Deleted user". NOTE: the audit log
-- is append-only by design and keeps the name that was current when each entry was written.
create or replace function public.erase_person_data(p_user uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare t public.profiles%rowtype; v_tag text;
begin
  select * into t from public.profiles where id = p_user for update;
  if not found then return jsonb_build_object('status', 'no_profile'); end if;
  if t.is_platform_admin then raise exception 'not_allowed' using errcode = '42501'; end if;
  v_tag := substr(replace(p_user::text, '-', ''), 1, 12);

  perform set_config('app.internal', '1', true);
  update public.profiles set
    full_name = 'Deleted user', email = 'erased-' || v_tag || '@invalid.invalid',
    student_number = case when t.student_number is not null then 'erased-' || v_tag end,
    staff_number   = case when t.staff_number   is not null then 'erased-' || v_tag end,
    department = null, cohort = null, is_class_rep = false, status = 'rejected', suspended_from = null
  where id = p_user;
  perform set_config('app.internal', '', true);

  delete from public.devices             where user_id = p_user;
  delete from public.notifications       where user_id = p_user;
  delete from public.announcement_reads  where user_id = p_user;
  delete from public.deadline_done       where student_id = p_user;
  delete from public.enrollments         where student_id = p_user;
  delete from public.teaching_assignments where lecturer_id = p_user;
  update public.data_deletion_requests set status = 'done' where user_id = p_user;
  return jsonb_build_object('status', 'erased', 'user_id', p_user);
end $$;

-- ---------- 13. Small helpers the app can use ----------------------------------------------------------
create or replace function public.unread_notification_count()
returns int language sql stable security definer set search_path = public as $$
  select count(*)::int from public.notifications where user_id = auth.uid() and not is_read
$$;

create or replace function public.mark_all_notifications_read()
returns int language plpgsql security definer set search_path = public as $$
declare v int;
begin
  if auth.uid() is null then raise exception 'not_signed_in' using errcode = '42501'; end if;
  update public.notifications set is_read = true where user_id = auth.uid() and not is_read;
  get diagnostics v = row_count;
  return v;
end $$;

-- Read notices older than p_days are removed (unread ones after a year). Run daily by pg_cron when installed.
create or replace function public.prune_notifications(p_days int default 90)
returns int language plpgsql security definer set search_path = public as $$
declare v int;
begin
  delete from public.notifications
   where (is_read and created_at < now() - make_interval(days => greatest(coalesce(p_days, 90), 7)))
      or created_at < now() - interval '365 days';
  get diagnostics v = row_count;
  return v;
end $$;

-- ---------- 14. One call that tells the app manager what needs attention ---------------------------------
create or replace function public.platform_health()
returns table (check_name text, severity text, detail text)
language plpgsql stable security definer set search_path = public as $$
declare n bigint; m bigint; v_ai boolean;
begin
  if not public.is_platform_admin() then raise exception 'not_allowed' using errcode = '42501'; end if;

  select count(*) into n from auth.users u left join public.profiles p on p.id = u.id where p.id is null;
  return query select 'sign-in accounts without a profile', case when n = 0 then 'ok' else 'warn' end,
    n || ' account(s) can do nothing until attached or removed';

  select count(*) into n from public.profiles where role = 'admin' and is_platform_admin and status = 'approved';
  return query select 'app manager accounts', case when n >= 1 then 'ok' else 'error' end, n || ' approved';

  select count(*) into n from public.profiles where status = 'pending' and created_at < now() - interval '7 days';
  return query select 'sign-ups waiting more than 7 days', case when n = 0 then 'ok' else 'info' end, n || ' waiting for a campus administrator';

  select count(*) into n from public.data_deletion_requests where status = 'open';
  return query select 'open data-deletion requests', case when n = 0 then 'ok' else 'warn' end, n || ' open';

  select count(*) filter (where attempts < 8), count(*) filter (where attempts >= 8) into n, m from public.storage_cleanup_queue;
  return query select 'files waiting to be removed from storage', case when m > 0 then 'warn' else 'ok' end,
    n || ' queued, ' || m || ' failed 8 times (see last_error)';

  select count(*) into n from public.quiz_attempts where status = 'in_progress' and deadline_at + interval '1 minute' < now();
  return query select 'quiz attempts past their time but not closed', case when n = 0 then 'ok' else 'warn' end,
    n || ' (they close on the next answer/submit, or schedule finalize_expired_quiz_attempts)';

  select count(*) into n from public.universities where under_maintenance and not is_platform;
  return query select 'campuses paused', case when n = 0 then 'ok' else 'info' end, n || ' paused';

  return query select 'administrator second step (MFA)', case when public.setting_text('require_admin_mfa', 'true') = 'true' then 'ok' else 'error' end,
    'require_admin_mfa = ' || public.setting_text('require_admin_mfa', 'true');

  select (length(api_key) > 0 and enabled) into v_ai from public.ai_settings where id = 1;
  return query select 'AI analysis', 'info', case when coalesce(v_ai, false) then 'configured and on' else 'not configured or switched off' end;
end $$;

-- ---------- 15. Scheduled jobs, only if pg_cron is installed (Database -> Extensions -> pg_cron) ---------------
do $$
begin
  if exists (select 1 from pg_extension where extname = 'pg_cron') then
    perform cron.schedule('cc-finalize-quizzes',   '* * * * *',  $c$select public.finalize_expired_quiz_attempts()$c$);
    perform cron.schedule('cc-prune-notifications', '17 3 * * *', $c$select public.prune_notifications(90)$c$);
    perform cron.schedule('cc-enqueue-orphans',     '43 3 * * *', $c$select public.storage_enqueue_orphans('24 hours')$c$);
  end if;
exception when others then
  raise notice 'pg_cron scheduling skipped: %', sqlerrm;
end $$;

-- ---------- 16. PRIVILEGES: nothing is callable unless it is meant to be (S1) -----------------------------------
-- Everything in public loses EXECUTE for "everyone" and for signed-out visitors. Signed-in users
-- keep exactly the functions that were granted to them explicitly. Trigger functions are never callable.
do $$
declare r record;
begin
  for r in select p.oid::regprocedure as sig, (p.prorettype = 'trigger'::regtype::oid) as is_trigger
             from pg_proc p join pg_namespace n on n.oid = p.pronamespace
            where n.nspname = 'public' and p.prokind = 'f'
  loop
    execute format('revoke execute on function %s from public, anon', r.sig);
    if r.is_trigger then
      execute format('revoke execute on function %s from authenticated', r.sig);
    end if;
  end loop;
end $$;

-- The one that could make anybody the platform administrator.
revoke all on function public.make_manager_profile(uuid, text) from public, anon, authenticated;

-- Server-only (service role / SQL editor / cron)
revoke all on function
  public.owner_make_manager(text, text, text), public.revoke_user_sessions(uuid), public.erase_person_data(uuid),
  public.ai_take_use(uuid, int), public.ai_refund_use(uuid), public.prune_notifications(int),
  public.storage_cleanup_next(int), public.storage_cleanup_done(bigint[]), public.storage_cleanup_done_paths(text, text[]),
  public.storage_cleanup_failed(bigint, text), public.storage_enqueue_orphans(interval),
  public.queue_storage_delete(), public.queue_replaced_attachment(), public.programme_courses_unenrol()
  from public, anon, authenticated;
grant execute on function
  public.revoke_user_sessions(uuid), public.erase_person_data(uuid), public.ai_take_use(uuid, int), public.ai_refund_use(uuid),
  public.storage_cleanup_next(int), public.storage_cleanup_done(bigint[]), public.storage_cleanup_done_paths(text, text[]),
  public.storage_cleanup_failed(bigint, text)
  to service_role;

-- Signed-in app
grant execute on function
  public.log_account_action(uuid, text, text), public.unread_notification_count(), public.mark_all_notifications_read(),
  public.platform_health()
  to authenticated;

-- Self-check: if the lock-down did not take effect, this migration stops and rolls back.
do $$
begin
  if has_function_privilege('anon', 'public.make_manager_profile(uuid,text)', 'execute')
     or has_function_privilege('authenticated', 'public.make_manager_profile(uuid,text)', 'execute') then
    raise exception 'privilege lock-down failed: make_manager_profile is still callable';
  end if;
end $$;

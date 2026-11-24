-- Removes the quiz feature. Safe on a fresh project (nothing to drop) and on an existing one (drops its data).
-- The two functions that used to mention quizzes are re-created without them.

do $$
begin
  if exists (select 1 from pg_extension where extname = 'pg_cron') then
    perform cron.unschedule('cc-finalize-quizzes');
  end if;
exception when others then
  null;
end $$;

drop table if exists public.quiz_answers, public.quiz_attempts, public.quiz_answer_keys, public.quiz_questions, public.quizzes cascade;

drop function if exists public.store_quiz_answer(uuid,uuid,text);
drop function if exists public.delete_quiz(uuid);
drop function if exists public.can_manage_quiz(uuid);
drop function if exists public.can_see_quiz(uuid);
drop function if exists public.has_quiz_attempt(uuid);
drop function if exists public.can_see_attempt(uuid);
drop function if exists public.create_quiz(text,text,text,integer,timestamp with time zone,timestamp with time zone,jsonb);
drop function if exists public.save_quiz_answer(uuid,uuid,text);
drop function if exists public.quiz_attempt_score(uuid);
drop function if exists public.finalize_expired_quiz_attempts();
drop function if exists public.reschedule_quiz(uuid,integer,timestamp with time zone,timestamp with time zone);
drop function if exists public.start_quiz_attempt(uuid);
drop function if exists public.submit_quiz_attempt(uuid,jsonb);
drop function if exists public.grade_quiz_attempt(uuid,jsonb);

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
    'submissions', coalesce((select jsonb_agg(to_jsonb(s) - 'storage_path') from public.submissions s where s.student_id = uid), '[]'),
    'devices', coalesce((select jsonb_agg(jsonb_build_object('label', label, 'last_seen_at', last_seen_at)) from public.devices where user_id = uid), '[]'));
end $$;

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

  select count(*) into n from public.universities where under_maintenance and not is_platform;
  return query select 'campuses paused', case when n = 0 then 'ok' else 'info' end, n || ' paused';

  return query select 'administrator second step (MFA)', case when public.setting_text('require_admin_mfa', 'true') = 'true' then 'ok' else 'error' end,
    'require_admin_mfa = ' || public.setting_text('require_admin_mfa', 'true');

  select (length(api_key) > 0 and enabled) into v_ai from public.ai_settings where id = 1;
  return query select 'AI analysis', 'info', case when coalesce(v_ai, false) then 'configured and on' else 'not configured or switched off' end;
end $$;

revoke all on function public.export_my_data(), public.platform_health() from public, anon;
grant execute on function public.export_my_data(), public.platform_health() to authenticated;

-- Timed online quizzes (multiple choice and written answers).
-- Rules enforced here, not in the app:
--  * students never read the answer key, and see the questions only after pressing Start;
--  * the clock is the database's: an attempt ends at start + duration (never after the quiz closes),
--    answers sent after that are refused, and a final answer is accepted for 10 seconds of grace;
--  * one attempt per student; only the lecturer who teaches the course (or a campus administrator)
--    sets, reschedules, deletes and marks.
-- All writes go through the functions at the bottom. Safe to run twice.

-- ---------- Tables ----------

create table if not exists public.quizzes (
  id               uuid primary key default gen_random_uuid(),
  course_code      text not null references public.courses(code) on delete cascade,
  created_by       uuid not null references public.profiles(id) on delete cascade,
  title            text not null check (length(trim(title)) between 1 and 200),
  instructions     text not null default '',
  duration_minutes int  not null check (duration_minutes between 1 and 180),
  opens_at         timestamptz not null,
  closes_at        timestamptz not null,
  created_at       timestamptz not null default now(),
  check (closes_at > opens_at),
  check (closes_at - opens_at >= duration_minutes * interval '1 minute')
);
create index if not exists quizzes_course_idx on public.quizzes (course_code);

create table if not exists public.quiz_questions (
  id        uuid primary key default gen_random_uuid(),
  quiz_id   uuid not null references public.quizzes(id) on delete cascade,
  position  int  not null,
  kind      text not null check (kind in ('multiple_choice', 'short_answer')),
  body      text not null check (length(trim(body)) > 0),
  options   jsonb not null default '[]'::jsonb,
  points    int  not null check (points between 1 and 100),
  unique (quiz_id, position)
);

-- Kept apart from the questions so that students can read questions but never the answers.
create table if not exists public.quiz_answer_keys (
  question_id   uuid primary key references public.quiz_questions(id) on delete cascade,
  correct_option int,
  model_answer  text not null default ''
);

create table if not exists public.quiz_attempts (
  id           uuid primary key default gen_random_uuid(),
  quiz_id      uuid not null references public.quizzes(id) on delete cascade,
  student_id   uuid not null references public.profiles(id) on delete cascade,
  started_at   timestamptz not null default now(),
  deadline_at  timestamptz not null,
  submitted_at timestamptz,
  status       text not null default 'in_progress' check (status in ('in_progress', 'submitted', 'auto_submitted')),
  marked       boolean not null default false,
  unique (quiz_id, student_id)
);
create index if not exists quiz_attempts_student_idx on public.quiz_attempts (student_id);

create table if not exists public.quiz_answers (
  attempt_id     uuid not null references public.quiz_attempts(id) on delete cascade,
  question_id    uuid not null references public.quiz_questions(id) on delete cascade,
  answer         text not null,
  awarded_points int check (awarded_points >= 0),
  primary key (attempt_id, question_id)
);

alter table public.quizzes          enable row level security;
alter table public.quiz_questions   enable row level security;
alter table public.quiz_answer_keys enable row level security;
alter table public.quiz_attempts    enable row level security;
alter table public.quiz_answers     enable row level security;

-- ---------- Who may see what (security definer so policies never recurse) ----------

-- The lecturer who teaches the course, or an administrator of the campus that owns it.
create or replace function public.can_manage_quiz(p_quiz uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.quizzes q join public.courses c on c.code = q.course_code
    where q.id = p_quiz and c.university_id = public.my_university()
      and (public.is_admin() or public.teaches(q.course_code)))
$$;

create or replace function public.can_see_quiz(p_quiz uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.quizzes q join public.courses c on c.code = q.course_code
    where q.id = p_quiz and c.university_id = public.my_university()
      and (public.is_admin() or public.teaches(q.course_code) or public.enrolled_in(q.course_code)))
$$;

create or replace function public.has_quiz_attempt(p_quiz uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.quiz_attempts where quiz_id = p_quiz and student_id = auth.uid())
$$;

create or replace function public.can_see_attempt(p_attempt uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.quiz_attempts a
    where a.id = p_attempt and (a.student_id = auth.uid() or public.can_manage_quiz(a.quiz_id)))
$$;

drop policy if exists quizzes_read on public.quizzes;
create policy quizzes_read on public.quizzes for select to authenticated using (public.can_see_quiz(id));

-- Questions: the people who run the quiz, and a student only once they have started.
drop policy if exists quiz_questions_read on public.quiz_questions;
create policy quiz_questions_read on public.quiz_questions for select to authenticated
  using (public.can_manage_quiz(quiz_id) or (public.has_quiz_attempt(quiz_id) and public.can_see_quiz(quiz_id)));

drop policy if exists quiz_answer_keys_read on public.quiz_answer_keys;
create policy quiz_answer_keys_read on public.quiz_answer_keys for select to authenticated
  using (exists (select 1 from public.quiz_questions qq where qq.id = question_id and public.can_manage_quiz(qq.quiz_id)));

drop policy if exists quiz_attempts_read on public.quiz_attempts;
create policy quiz_attempts_read on public.quiz_attempts for select to authenticated
  using (student_id = auth.uid() or public.can_manage_quiz(quiz_id));

drop policy if exists quiz_answers_read on public.quiz_answers;
create policy quiz_answers_read on public.quiz_answers for select to authenticated
  using (public.can_see_attempt(attempt_id));

-- Nobody writes these tables directly: only the functions below, which check everything.
revoke all on public.quizzes, public.quiz_questions, public.quiz_answer_keys, public.quiz_attempts, public.quiz_answers
  from public, anon, authenticated;
grant select on public.quizzes, public.quiz_questions, public.quiz_answer_keys, public.quiz_attempts, public.quiz_answers
  to authenticated;

-- ---------- Housekeeping: attempts whose timer ran out ----------

create or replace function public.finalize_expired_quiz_attempts()
returns integer language plpgsql security definer set search_path = public as $$
declare v_count integer;
begin
  update public.quiz_attempts
     set status = 'auto_submitted', submitted_at = deadline_at
   where status = 'in_progress' and deadline_at + interval '10 seconds' <= now();   -- the 10 s are the hand-in grace
  get diagnostics v_count = row_count;
  return v_count;
end $$;
-- Run it every minute for a tidy list (pg_cron or a scheduled Edge Function). Correctness does not
-- depend on it: every function below finalises expired attempts before it acts.
revoke all on function public.finalize_expired_quiz_attempts() from public, anon, authenticated;

-- ---------- Set, reschedule, delete (lecturer / administrator) ----------

-- p_questions: [{"kind":"multiple_choice","body":"...","options":["a","b"],"correct":1,"points":2},
--               {"kind":"short_answer","body":"...","model_answer":"...","points":4}]
create or replace function public.create_quiz(
  p_course text, p_title text, p_instructions text, p_duration int,
  p_opens timestamptz, p_closes timestamptz, p_questions jsonb)
returns uuid language plpgsql security definer set search_path = public as $$
declare
  v_course public.courses%rowtype;
  v_quiz   uuid;
  v_q      jsonb;
  v_qid    uuid;
  v_pos    int := 0;
  v_kind   text;
  v_opts   jsonb;
begin
  if not (public.is_admin() or public.is_lecturer()) then raise exception 'not_allowed' using errcode = '42501'; end if;

  select * into v_course from public.courses c
   where c.university_id = public.my_university()
     and (c.code = trim(p_course) or lower(c.short_code) = lower(trim(p_course)));
  if not found then raise exception 'course_not_found' using errcode = 'P0001'; end if;
  -- A lecturer may only set quizzes for courses they teach; an administrator for any course of the campus.
  if not public.is_admin() and not public.teaches(v_course.code) then raise exception 'not_allowed' using errcode = '42501'; end if;

  if length(trim(coalesce(p_title, ''))) = 0 then raise exception 'title_required' using errcode = 'P0001'; end if;
  if p_questions is null or jsonb_typeof(p_questions) <> 'array' or jsonb_array_length(p_questions) not between 1 and 50 then
    raise exception 'no_questions' using errcode = 'P0001';
  end if;
  if p_duration not between 1 and 180 then raise exception 'bad_duration' using errcode = 'P0001'; end if;
  if p_closes <= p_opens or p_closes <= now() or p_closes - p_opens < p_duration * interval '1 minute' then
    raise exception 'bad_window' using errcode = 'P0001';
  end if;

  -- Check every question before writing anything.
  for v_q in select * from jsonb_array_elements(p_questions) loop
    v_kind := v_q ->> 'kind';
    v_opts := coalesce(v_q -> 'options', '[]'::jsonb);
    if v_kind not in ('multiple_choice', 'short_answer')
       or length(trim(coalesce(v_q ->> 'body', ''))) = 0
       or coalesce((v_q ->> 'points')::int, 0) not between 1 and 100 then
      raise exception 'bad_question' using errcode = 'P0001';
    end if;
    if v_kind = 'multiple_choice' then
      if jsonb_typeof(v_opts) <> 'array' or jsonb_array_length(v_opts) not between 2 and 6
         or exists (select 1 from jsonb_array_elements_text(v_opts) o where length(trim(o)) = 0)
         or coalesce((v_q ->> 'correct')::int, -1) not between 0 and jsonb_array_length(v_opts) - 1 then
        raise exception 'bad_question' using errcode = 'P0001';
      end if;
    end if;
  end loop;

  insert into public.quizzes (course_code, created_by, title, instructions, duration_minutes, opens_at, closes_at)
  values (v_course.code, auth.uid(), trim(p_title), coalesce(p_instructions, ''), p_duration, p_opens, p_closes)
  returning id into v_quiz;

  for v_q in select * from jsonb_array_elements(p_questions) loop
    v_pos := v_pos + 1;
    insert into public.quiz_questions (quiz_id, position, kind, body, options, points)
    values (v_quiz, v_pos, v_q ->> 'kind', trim(v_q ->> 'body'),
            case when v_q ->> 'kind' = 'multiple_choice' then v_q -> 'options' else '[]'::jsonb end,
            (v_q ->> 'points')::int)
    returning id into v_qid;
    insert into public.quiz_answer_keys (question_id, correct_option, model_answer)
    values (v_qid,
            case when v_q ->> 'kind' = 'multiple_choice' then (v_q ->> 'correct')::int end,
            coalesce(v_q ->> 'model_answer', ''));
  end loop;

  perform public.notify_users(
    array(select e.student_id from public.enrollments e where e.course_code = v_course.code),
    'New quiz: ' || v_course.short_code,
    trim(p_title) || ' opens ' || to_char(p_opens at time zone 'Africa/Dar_es_Salaam', 'DD Mon YYYY HH24:MI') ||
      ' EAT. You get ' || p_duration || ' minutes once you start.',
    'deadline_alert', false);
  perform public.log_audit('QUIZ_CREATED', format('%s: "%s", %s questions, %s min', v_course.short_code, trim(p_title), jsonb_array_length(p_questions), p_duration));
  return v_quiz;
end $$;

create or replace function public.reschedule_quiz(p_quiz uuid, p_duration int, p_opens timestamptz, p_closes timestamptz)
returns void language plpgsql security definer set search_path = public as $$
declare v_quiz public.quizzes%rowtype; v_short text;
begin
  select * into v_quiz from public.quizzes where id = p_quiz;
  if not found or not public.can_manage_quiz(p_quiz) then raise exception 'not_allowed' using errcode = '42501'; end if;
  if p_duration not between 1 and 180 then raise exception 'bad_duration' using errcode = 'P0001'; end if;
  if p_closes <= p_opens or p_closes <= now() or p_closes - p_opens < p_duration * interval '1 minute' then
    raise exception 'bad_window' using errcode = 'P0001';
  end if;
  -- Attempts already running keep the deadline they were given.
  update public.quizzes set duration_minutes = p_duration, opens_at = p_opens, closes_at = p_closes where id = p_quiz;
  select short_code into v_short from public.courses where code = v_quiz.course_code;
  perform public.notify_users(
    array(select e.student_id from public.enrollments e where e.course_code = v_quiz.course_code),
    'Quiz rescheduled: ' || v_short,
    v_quiz.title || ' now opens ' || to_char(p_opens at time zone 'Africa/Dar_es_Salaam', 'DD Mon YYYY HH24:MI') ||
      ' EAT and closes ' || to_char(p_closes at time zone 'Africa/Dar_es_Salaam', 'DD Mon YYYY HH24:MI') || ' EAT.',
    'deadline_alert', false);
  perform public.log_audit('QUIZ_RESCHEDULED', format('%s: "%s"', v_short, v_quiz.title));
end $$;

create or replace function public.delete_quiz(p_quiz uuid)
returns void language plpgsql security definer set search_path = public as $$
declare v_title text;
begin
  if not public.can_manage_quiz(p_quiz) then raise exception 'not_allowed' using errcode = '42501'; end if;
  select title into v_title from public.quizzes where id = p_quiz;
  delete from public.quizzes where id = p_quiz;   -- questions, keys, attempts and answers go with it
  perform public.log_audit('QUIZ_DELETED', format('"%s" and its attempts', v_title));
end $$;

-- ---------- Taking a quiz (student) ----------

create or replace function public.start_quiz_attempt(p_quiz uuid)
returns table (attempt_id uuid, deadline_at timestamptz) language plpgsql security definer set search_path = public as $$
declare v_quiz public.quizzes%rowtype; v_att public.quiz_attempts%rowtype;
begin
  if not public.is_student() then raise exception 'not_allowed' using errcode = '42501'; end if;
  select * into v_quiz from public.quizzes where id = p_quiz;
  if not found or not public.can_see_quiz(p_quiz) then raise exception 'quiz_not_found' using errcode = 'P0001'; end if;
  perform public.finalize_expired_quiz_attempts();

  select * into v_att from public.quiz_attempts a where a.quiz_id = p_quiz and a.student_id = auth.uid();
  if found then
    if v_att.status = 'in_progress' and v_att.deadline_at > now() then return query select v_att.id, v_att.deadline_at; return; end if;
    raise exception 'already_submitted' using errcode = 'P0001';
  end if;

  if now() < v_quiz.opens_at then raise exception 'not_open_yet' using errcode = 'P0001'; end if;
  if now() >= v_quiz.closes_at then raise exception 'closed' using errcode = 'P0001'; end if;

  insert into public.quiz_attempts (quiz_id, student_id, started_at, deadline_at)
  values (p_quiz, auth.uid(), now(), least(now() + v_quiz.duration_minutes * interval '1 minute', v_quiz.closes_at))
  returning * into v_att;
  return query select v_att.id, v_att.deadline_at;
end $$;

-- Checks one answer against its question and stores it (blank clears it). Internal.
create or replace function public.store_quiz_answer(p_attempt uuid, p_question uuid, p_answer text)
returns void language plpgsql security definer set search_path = public as $$
declare v_q public.quiz_questions%rowtype; v_quiz uuid; v_clean text;
begin
  select quiz_id into v_quiz from public.quiz_attempts where id = p_attempt;
  select * into v_q from public.quiz_questions where id = p_question and quiz_id = v_quiz;
  if not found then raise exception 'question_not_found' using errcode = 'P0001'; end if;
  if v_q.kind = 'multiple_choice' then
    if trim(coalesce(p_answer, '')) !~ '^[0-9]+$' or trim(p_answer)::int >= jsonb_array_length(v_q.options) then
      v_clean := null;
    else
      v_clean := trim(p_answer)::int::text;
    end if;
  else
    v_clean := left(coalesce(p_answer, ''), 2000);
  end if;
  if v_clean is null or length(trim(v_clean)) = 0 then
    delete from public.quiz_answers where attempt_id = p_attempt and question_id = p_question;
  else
    insert into public.quiz_answers (attempt_id, question_id, answer) values (p_attempt, p_question, v_clean)
    on conflict (attempt_id, question_id) do update set answer = excluded.answer;
  end if;
end $$;
revoke all on function public.store_quiz_answer(uuid, uuid, text) from public, anon, authenticated;

create or replace function public.save_quiz_answer(p_attempt uuid, p_question uuid, p_answer text)
returns void language plpgsql security definer set search_path = public as $$
declare v_att public.quiz_attempts%rowtype;
begin
  perform public.finalize_expired_quiz_attempts();
  select * into v_att from public.quiz_attempts where id = p_attempt and student_id = auth.uid();
  if not found or not public.is_student() then raise exception 'attempt_not_found' using errcode = 'P0001'; end if;
  if v_att.status = 'auto_submitted' then raise exception 'time_up' using errcode = 'P0001'; end if;
  if v_att.status <> 'in_progress' then raise exception 'already_submitted' using errcode = 'P0001'; end if;
  -- Within the 10 s grace the attempt is still open for the final hand-in, but not for ordinary saves.
  if now() >= v_att.deadline_at then raise exception 'time_up' using errcode = 'P0001'; end if;
  perform public.store_quiz_answer(p_attempt, p_question, p_answer);
end $$;

-- p_answers: {"<question uuid>": "answer", ...}: the last answers still on the screen. They count for
-- 10 seconds after the timer ends, then the attempt is closed with what it already had.
create or replace function public.submit_quiz_attempt(p_attempt uuid, p_answers jsonb default '{}'::jsonb)
returns void language plpgsql security definer set search_path = public as $$
declare v_att public.quiz_attempts%rowtype; v_quiz public.quizzes%rowtype; v_pair record; v_expired boolean;
begin
  select * into v_att from public.quiz_attempts where id = p_attempt and student_id = auth.uid();
  if not found or not public.is_student() then raise exception 'attempt_not_found' using errcode = 'P0001'; end if;
  if v_att.status <> 'in_progress' then return; end if;   -- already handed in: nothing to do

  if now() <= v_att.deadline_at + interval '10 seconds' and p_answers is not null and jsonb_typeof(p_answers) = 'object' then
    for v_pair in select key, value from jsonb_each_text(p_answers) loop
      begin
        perform public.store_quiz_answer(p_attempt, v_pair.key::uuid, v_pair.value);
      exception when invalid_text_representation or raise_exception then
        null;   -- a malformed entry is skipped; it must not lose the rest
      end;
    end loop;
  end if;

  v_expired := now() >= v_att.deadline_at;
  update public.quiz_attempts
     set status = case when v_expired then 'auto_submitted' else 'submitted' end,
         submitted_at = least(now(), deadline_at)
   where id = p_attempt;

  select * into v_quiz from public.quizzes where id = v_att.quiz_id;
  perform public.notify_users(array[v_quiz.created_by], 'Quiz submitted: ' || (select short_code from public.courses where code = v_quiz.course_code),
    (select full_name from public.profiles where id = auth.uid()) || ' finished ' || v_quiz.title || '.',
    'submission_received', false);
end $$;

-- ---------- Marking and scores ----------

create or replace function public.grade_quiz_attempt(p_attempt uuid, p_points jsonb)
returns void language plpgsql security definer set search_path = public as $$
declare v_att public.quiz_attempts%rowtype; v_quiz public.quizzes%rowtype; v_pair record; v_max int;
begin
  perform public.finalize_expired_quiz_attempts();
  select * into v_att from public.quiz_attempts where id = p_attempt;
  if not found or not public.can_manage_quiz(v_att.quiz_id) then raise exception 'not_allowed' using errcode = '42501'; end if;
  if v_att.status = 'in_progress' then raise exception 'not_allowed' using errcode = '42501'; end if;
  select * into v_quiz from public.quizzes where id = v_att.quiz_id;

  for v_pair in select key, value from jsonb_each_text(coalesce(p_points, '{}'::jsonb)) loop
    select qq.points into v_max from public.quiz_questions qq
     where qq.id = v_pair.key::uuid and qq.quiz_id = v_att.quiz_id and qq.kind = 'short_answer';
    if v_max is null then continue; end if;   -- not a written question of this quiz: ignored
    insert into public.quiz_answers (attempt_id, question_id, answer, awarded_points)
    values (p_attempt, v_pair.key::uuid, '', greatest(0, least(v_pair.value::int, v_max)))
    on conflict (attempt_id, question_id) do update set awarded_points = excluded.awarded_points;
  end loop;
  update public.quiz_attempts set marked = true where id = p_attempt;

  perform public.notify_users(array[v_att.student_id], 'Quiz marked: ' || (select short_code from public.courses where code = v_quiz.course_code),
    v_quiz.title || ' has been marked. Open it to see your score.', 'grade_posted', false);
  perform public.log_audit('QUIZ_MARKED', format('Marked an attempt of "%s"', v_quiz.title));
end $$;

-- The score of a finished attempt: multiple choice at once, written answers once the lecturer has marked.
create or replace function public.quiz_attempt_score(p_attempt uuid)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_att public.quiz_attempts%rowtype; v_cp int; v_ct int; v_wp int; v_wt int;
begin
  perform public.finalize_expired_quiz_attempts();
  select * into v_att from public.quiz_attempts where id = p_attempt;
  if not found or not public.can_see_attempt(p_attempt) then raise exception 'not_allowed' using errcode = '42501'; end if;
  if v_att.status = 'in_progress' then raise exception 'not_allowed' using errcode = '42501'; end if;

  select coalesce(sum(qq.points) filter (where qq.kind = 'multiple_choice'), 0),
         coalesce(sum(qq.points) filter (where qq.kind = 'short_answer'), 0),
         coalesce(sum(qq.points) filter (where qq.kind = 'multiple_choice' and a.answer = k.correct_option::text), 0),
         coalesce(sum(a.awarded_points) filter (where qq.kind = 'short_answer'), 0)
    into v_ct, v_wt, v_cp, v_wp
  from public.quiz_questions qq
  join public.quiz_answer_keys k on k.question_id = qq.id
  left join public.quiz_answers a on a.question_id = qq.id and a.attempt_id = p_attempt
  where qq.quiz_id = v_att.quiz_id;

  return jsonb_build_object('choice_points', v_cp, 'choice_total', v_ct, 'written_points', v_wp, 'written_total', v_wt,
                            'complete', v_wt = 0 or v_att.marked);
end $$;

revoke all on function
  public.can_manage_quiz(uuid), public.can_see_quiz(uuid), public.has_quiz_attempt(uuid), public.can_see_attempt(uuid),
  public.create_quiz(text, text, text, int, timestamptz, timestamptz, jsonb),
  public.reschedule_quiz(uuid, int, timestamptz, timestamptz), public.delete_quiz(uuid),
  public.start_quiz_attempt(uuid), public.save_quiz_answer(uuid, uuid, text), public.submit_quiz_attempt(uuid, jsonb),
  public.grade_quiz_attempt(uuid, jsonb), public.quiz_attempt_score(uuid)
  from public, anon;
grant execute on function
  public.can_manage_quiz(uuid), public.can_see_quiz(uuid), public.has_quiz_attempt(uuid), public.can_see_attempt(uuid),
  public.create_quiz(text, text, text, int, timestamptz, timestamptz, jsonb),
  public.reschedule_quiz(uuid, int, timestamptz, timestamptz), public.delete_quiz(uuid),
  public.start_quiz_attempt(uuid), public.save_quiz_answer(uuid, uuid, text), public.submit_quiz_attempt(uuid, jsonb),
  public.grade_quiz_attempt(uuid, jsonb), public.quiz_attempt_score(uuid)
  to authenticated;

\pset tuples_only on
\set QUIET on
create table if not exists test.results (n serial, label text, pass boolean);
grant all on test.results to anon, authenticated; grant usage on all sequences in schema test to anon, authenticated;
create or replace function test.check(l text, ok boolean) returns text language plpgsql as $$
begin insert into test.results(label, pass) values (l, coalesce(ok, false)); return case when ok then 'PASS ' else 'FAIL ' end || l; end $$;
grant execute on all functions in schema test to anon, authenticated;
delete from test.results;
begin;
-- The exact calls CampusRepository.applyTimetable makes, in the same order, as a campus administrator.
select test.login(test.id('adm_c'));
select test.check('T1 programme insert', test.try($q$insert into public.programmes (university_id, name) values ((select university_id from public.profiles where id = test.id('adm_c')), 'BSc IT')$q$) = 'OK');
select test.check('T2 import_courses creates courses and links the programme', test.try($q$select public.import_courses('[{"code":"IS202","title":"Systems Analysis","programme":"BSc IT"},{"code":"IS210","title":"Web Design","programme":"BSc IT"}]'::jsonb)$q$) = 'OK');
select test.check('T3 room insert', test.try($q$insert into public.rooms (university_id, name) values ((select university_id from public.profiles where id = test.id('adm_c')), 'LH7')$q$) = 'OK');
select test.check('T4 set_course_lecturers (existing + new ids)', test.try(format($q$select public.set_course_lecturers('IS202', array[%L]::uuid[])$q$, test.id('lec_c'))) = 'OK');
select test.check('T5 first session inserts', test.try(format($q$insert into public.class_sessions (course_code, lecturer_id, day_of_week, start_time, end_time, venue, kind) values ('CUOM:IS202', %L, 2, '09:00', '11:00', 'LH7', 'lecture')$q$, test.id('lec_c'))) = 'OK');
select test.check('T6 the same room overlapping is refused with a venue_busy message the app maps', test.try(format($q$insert into public.class_sessions (course_code, lecturer_id, day_of_week, start_time, end_time, venue, kind) values ('CUOM:IS210', %L, 2, '10:00', '12:00', 'lh7', 'lecture')$q$, test.id('lec_c2'))) like 'ERR: venue_busy%');
select test.check('T7 the same lecturer at the same time elsewhere is refused with lecturer_busy', test.try(format($q$insert into public.class_sessions (course_code, lecturer_id, day_of_week, start_time, end_time, venue, kind) values ('CUOM:IS210', %L, 2, '10:00', '12:00', 'LH1', 'lecture')$q$, test.id('lec_c'))) like 'ERR: lecturer_busy%');
select test.check('T8 a clean second session (other day) inserts', test.try(format($q$insert into public.class_sessions (course_code, lecturer_id, day_of_week, start_time, end_time, venue, kind) values ('CUOM:IS210', %L, 3, '08:00', '10:00', 'LH7', 'lab')$q$, test.id('lec_c'))) = 'OK');
select test.logout();
select test.check('T9 new courses exist with campus-prefixed codes', (select count(*) from public.courses where code in ('CUOM:IS202','CUOM:IS210')) = 2);
select test.check('T10 both courses are linked to the new programme', (select count(*) from public.programme_courses pc join public.programmes g on g.id = pc.programme_id where g.name = 'BSc IT') = 2);
select test.check('T11 the lecturer was assigned', exists (select 1 from public.teaching_assignments where lecturer_id = test.id('lec_c') and course_code = 'CUOM:IS202'));
select test.check('T12 exactly the 2 clean sessions exist (refused ones left nothing behind)', (select count(*) from public.class_sessions where course_code in ('CUOM:IS202','CUOM:IS210')) = 2);
-- permissions: nobody but a campus administrator can do this
select test.login(test.id('lec_c'));
select test.check('T13 a lecturer cannot insert programmes, rooms or import courses', test.try($q$insert into public.programmes (university_id, name) values ((select university_id from public.profiles where id = test.id('lec_c')), 'Sneaky')$q$) like 'ERR%' and test.try($q$select public.import_courses('[{"code":"ZZ1","title":"x"}]'::jsonb)$q$) like 'ERR%');
select test.logout(); select test.login(test.id('adm_u'));
select test.check('T14 another campus administrator cannot add sessions to this campus', test.try(format($q$insert into public.class_sessions (course_code, lecturer_id, day_of_week, start_time, end_time, venue) values ('CUOM:IS202', %L, 4, '09:00', '10:00', 'X1')$q$, test.id('lec_c'))) like 'ERR%');
select test.logout();
select test.login(test.id('stu1'));
select test.check('T15 students see the new timetable entries for their courses only after enrolment (no leak of other-programme sessions)', (select count(*) from public.class_sessions where course_code = 'CUOM:IS202') = 0);
select test.logout();
select (select count(*) filter (where pass) from test.results) || ' passed, ' || (select count(*) filter (where not pass) from test.results) || ' failed';
select '   ' || label from test.results where not pass order by n;
rollback;

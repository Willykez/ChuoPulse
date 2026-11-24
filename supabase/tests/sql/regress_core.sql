\pset tuples_only on
\set QUIET on
create table if not exists test.results (n serial, label text, pass boolean);
grant all on test.results to anon, authenticated; grant usage on all sequences in schema test to anon, authenticated;
create or replace function test.check(l text, ok boolean) returns text language plpgsql as $$
begin insert into test.results(label, pass) values (l, coalesce(ok, false)); return case when ok then 'PASS ' else 'FAIL ' end || l; end $$;
grant execute on all functions in schema test to anon, authenticated;
delete from test.results;

begin;
-- ===== 1. sign-up -> approval -> enrolment
insert into auth.users (id,email,email_confirmed_at,raw_user_meta_data)
 values ('20000000-0000-0000-0000-000000000001','new.student@test.dev',null,'{"university":"CUOM","full_name":"New Student","id_number":"CUOM/900","programme":"BSc Computer Science"}');
select test.check('1a signup creates PENDING student with programme', (select status='pending' and role='student' and programme_id is not null from public.profiles where id='20000000-0000-0000-0000-000000000001'));
select test.login(test.id('adm_c'));
select test.check('1b campus admin approves', test.try($q$select public.approve_account('20000000-0000-0000-0000-000000000001','student')$q$)='OK');
select test.logout();
select test.check('1c approved + enrolled in 2 programme courses', (select status='approved' from public.profiles where id='20000000-0000-0000-0000-000000000001') and (select count(*) from public.enrollments where student_id='20000000-0000-0000-0000-000000000001')=2);
select test.check('1d welcome notification', exists(select 1 from public.notifications where user_id='20000000-0000-0000-0000-000000000001' and type='approval'));
select test.login(test.id('adm_u'));
select test.check('1e OTHER campus admin cannot approve/see this person', test.try($q$select public.approve_account('20000000-0000-0000-0000-000000000001','student')$q$) like 'ERR%' and (select count(*) from public.profiles where id='20000000-0000-0000-0000-000000000001')=0);
select test.logout();

-- ===== 2. announcements
select test.login(test.id('lec_c'));
select test.check('2a lecturer posts course notice', test.try($q$insert into public.announcements (id,author_id,title,body,audience,course_code) values ('a0000000-0000-0000-0000-000000000001',test.id('lec_c'),'Test notice','body','course','CUOM:CS210')$q$)='OK');
select test.check('2b lecturer cannot post campus-wide', test.try($q$insert into public.announcements (author_id,title,body,audience) values (test.id('lec_c'),'Campus wide','body','campus')$q$) like 'ERR%');
select test.logout(); select test.login(test.id('stu1'));
select test.check('2c enrolled student sees notice', (select count(*) from public.announcements where id='a0000000-0000-0000-0000-000000000001')=1);
select test.check('2d student got notification', (select count(*) from public.notifications where title='Test notice')=1);
select test.check('2e student marks read', test.try($q$insert into public.announcement_reads (announcement_id,user_id) values ('a0000000-0000-0000-0000-000000000001',test.id('stu1'))$q$)='OK');
select test.check('2f student cannot post notice', test.try($q$insert into public.announcements (author_id,title,body,audience,course_code) values (test.id('stu1'),'Student notice','body','course','CUOM:CS210')$q$) like 'ERR%');
select test.logout(); select test.login(test.id('stu_u'));
select test.check('2g other campus student sees nothing', (select count(*) from public.announcements)=0 and (select count(*) from public.notifications where title='Test notice')=0);
select test.logout(); select test.login(test.id('lec_c'));
select test.check('2h stats: delivered 3 (stu1, stu2, rep1... + new student) opened 1', (select delivered>=3 and opened=1 from public.announcement_stats('a0000000-0000-0000-0000-000000000001')));
select test.check('2i remind_unread works once', public.remind_unread('a0000000-0000-0000-0000-000000000001')>0 and public.remind_unread('a0000000-0000-0000-0000-000000000001')=0);
select test.logout();
-- class rep
select test.login(test.id('rep1'));
select test.check('2j rep posts programme notice', test.try(format($q$insert into public.announcements (author_id,title,body,audience,programme_id) values (test.id('rep1'),'Rep notice','body','programme',%L)$q$, (select programme_id from public.profiles where id=test.id('rep1'))))='OK');
select test.check('2k rep cannot post campus', test.try($q$insert into public.announcements (author_id,title,body,audience) values (test.id('rep1'),'Rep campus','body','campus')$q$) like 'ERR%');
select test.check('2l rep cannot post urgent', test.try(format($q$insert into public.announcements (author_id,title,body,audience,programme_id,priority) values (test.id('rep1'),'Rep urgent','body','programme',%L,'urgent')$q$, (select programme_id from public.profiles where id=test.id('rep1')))) like 'ERR%');
select test.logout();

-- ===== 3. timetable
select test.login(test.id('lec_c'));
select test.check('3a create session', test.try($q$insert into public.class_sessions (id,course_code,lecturer_id,day_of_week,start_time,end_time,venue) values ('c0000000-0000-0000-0000-000000000001','CUOM:CS210',test.id('lec_c'),2,'09:00','11:00','LH1')$q$)='OK');
select test.check('3b same room overlapping rejected', test.try($q$insert into public.class_sessions (course_code,lecturer_id,day_of_week,start_time,end_time,venue) values ('CUOM:CS220',test.id('lec_c2'),2,'10:00','12:00','lh1')$q$) like 'ERR: venue_busy%');
select test.check('3c venue change needs reason', test.try($q$update public.class_sessions set venue='LH2' where id='c0000000-0000-0000-0000-000000000001'$q$) like 'ERR: reason_required%');
select test.check('3d venue change with reason ok', test.try($q$update public.class_sessions set venue='LH2', change_reason='Projector broken' where id='c0000000-0000-0000-0000-000000000001'$q$)='OK');
select test.check('3e status became changed, original kept', (select status='changed' and original_venue='LH1' from public.class_sessions where id='c0000000-0000-0000-0000-000000000001'));
select test.logout(); select test.login(test.id('stu1'));
select test.check('3f students notified of room change', (select count(*) from public.notifications where title like 'Room changed%')=1);
select test.check('3g student sees session', (select count(*) from public.class_sessions)=1);
select test.logout();

-- ===== 4. deadlines + submissions
select test.login(test.id('lec_c'));
select test.check('4a deadline created', test.try($q$insert into public.deadlines (id,course_code,lecturer_id,title,due_at) values ('d0000000-0000-0000-0000-000000000001','CUOM:CS210',test.id('lec_c'),'Essay',now()+interval '3 days')$q$)='OK');
select test.logout();
insert into storage.objects (bucket_id,name,owner,metadata) values ('submissions', test.id('stu1')::text||'/d0000000-0000-0000-0000-000000000001/essay.pdf', test.id('stu1'), '{"size":2048,"mimetype":"application/pdf"}');
select test.login(test.id('stu1'));
select test.check('4b student submits own file', test.try(format($q$insert into public.submissions (id,deadline_id,course_code,student_id,storage_path,file_name,mime_type,size_bytes) values ('50000000-0000-0000-0000-000000000001','d0000000-0000-0000-0000-000000000001','CUOM:CS210',test.id('stu1'),%L,'essay.pdf','application/pdf',1)$q$, test.id('stu1')::text||'/d0000000-0000-0000-0000-000000000001/essay.pdf'))='OK');
select test.check('4c size taken from storage not client', (select size_bytes=2048 and status='pending_review' from public.submissions where id='50000000-0000-0000-0000-000000000001'));
select test.check('4d student ticks deadline done', test.try($q$insert into public.deadline_done (deadline_id,student_id) values ('d0000000-0000-0000-0000-000000000001',test.id('stu1'))$q$)='OK');
select test.logout(); select test.login(test.id('stu2'));
select test.check('4e other student cannot see the submission', (select count(*) from public.submissions)=0);
select test.logout(); select test.login(test.id('lec_c2'));
select test.check('4f lecturer of ANOTHER course cannot see it', (select count(*) from public.submissions)=0);
select test.logout(); select test.login(test.id('lec_c'));
select test.check('4g teaching lecturer sees it', (select count(*) from public.submissions)=1);
select test.check('4h grading', test.try($q$update public.submissions set grade='A', feedback='Good' where id='50000000-0000-0000-0000-000000000001'$q$)='OK');
select test.check('4i done count (not who)', public.deadline_done_count('d0000000-0000-0000-0000-000000000001')=1);
select test.check('4j lecturer cannot read private ticks', (select count(*) from public.deadline_done)=0);
select test.logout();
select test.check('4k grade notification', exists(select 1 from public.notifications where user_id=test.id('stu1') and type='grade_posted'));

-- ===== 5. materials
insert into storage.objects (bucket_id,name,owner,metadata) values ('materials','CUOM:CS210/'||test.id('lec_c')::text||'/notes.pdf', test.id('lec_c'), '{"size":4096,"mimetype":"application/pdf"}');
select test.login(test.id('lec_c'));
select test.check('5a lecturer uploads material row', test.try(format($q$insert into public.study_materials (course_code,title,uploader_id,storage_path,file_name,mime_type,size_bytes) values ('CUOM:CS210','Notes',test.id('lec_c'),%L,'notes.pdf','application/pdf',1)$q$, 'CUOM:CS210/'||test.id('lec_c')::text||'/notes.pdf'))='OK');
select test.logout(); select test.login(test.id('stu1'));
select test.check('5b enrolled student sees material', (select count(*) from public.study_materials)=1);
select test.check('5c ...and can read the file in storage', (select count(*) from storage.objects where bucket_id='materials')=1);
select test.logout(); select test.login(test.id('stu_u'));
select test.check('5d other campus: no material, no file', (select count(*) from public.study_materials)=0 and (select count(*) from storage.objects where bucket_id='materials')=0);
select test.logout();

-- ===== 7. admin tools
select test.login(test.id('adm_c'));
select test.check('7a set_class_rep', test.try($q$select public.set_class_rep(test.id('stu2'), true)$q$)='OK');
select test.check('7b set_course_lecturers', test.try($q$select public.set_course_lecturers('CS220', array[test.id('lec_c2')])$q$)='OK');
select test.check('7c import_courses', (public.import_courses('[{"code":"CS300","title":"Compilers","programme":"BSc Computer Science"}]'::jsonb)->>'added')::int=1);
select test.check('7d new course enrolled students automatically', (select count(*) from public.enrollments where course_code='CUOM:CS300')>=3);
select test.check('7e set_programme_courses', test.try($q$select public.set_programme_courses('BSc Computer Science', array['CS210','CS220'])$q$)='OK');
select test.check('7f dropped course un-enrols students', (select count(*) from public.enrollments where course_code='CUOM:CS300')=0);
select test.check('7g admin lists own campus people only', (select count(*) from public.admin_list_people() where email like '%udsm%' or email='stu.u@test.dev')=0);
select test.check('7h admin notify people', (select public.admin_notify_people('students', null, 'Hello all', 'Welcome back everybody'))>=3);
select test.check('7i audit log is campus scoped', (select count(*) from public.audit_log where university_id <> public.my_university())=0 and (select count(*) from public.audit_log)>0);
select test.logout();

-- ===== 8. self-service
select test.login(test.id('stu1'));
select test.check('8a my_limits', (public.my_limits()->>'submission_max_bytes')::bigint=15728640);
select test.check('8b register 3 devices ok, 4th refused', test.try($q$select public.register_device('k1','p1')$q$)='OK' and test.try($q$select public.register_device('k2','p2')$q$)='OK' and test.try($q$select public.register_device('k3','p3')$q$)='OK' and test.try($q$select public.register_device('k4','p4')$q$) like 'ERR: limit_reached%');
select test.check('8c export_my_data', (public.export_my_data()->'profile'->>'email')='stu1@test.dev');
select test.check('8d request deletion', test.try($q$select public.request_data_deletion()$q$)='OK');
select test.check('8e student cannot call admin RPCs', test.try($q$select public.admin_list_people()$q$) like 'ERR%' and test.try($q$select public.platform_campus_summary()$q$) like 'ERR%' and test.try($q$select public.import_courses('[]'::jsonb)$q$) like 'ERR%');
select test.check('8f student cannot read audit log / profiles of others', (select count(*) from public.audit_log)=0 and (select count(*) from public.profiles)=1);
select test.check('8g student cannot change own role', test.try($q$update public.profiles set role='admin' where id=test.id('stu1')$q$) like 'ERR%');
select test.logout();

-- ===== 9. admin without 2nd step is powerless
select test.login(test.id('adm_c'),'aal1');
select test.check('9a admin at aal1 cannot use admin RPCs', test.try($q$select public.admin_list_people()$q$) like 'ERR%');
select test.logout();

-- ===== 10. app manager
select test.login(test.id('mgr'));
select test.check('10a campus summary', (select count(*) from public.platform_campus_summary())=2);
select test.check('10b manager sees no campus content', (select count(*) from public.announcements)=0 and (select count(*) from public.profiles where university_id <> public.my_university())=0);
select test.check('10c create campus', test.try($q$select public.platform_create_university('NEWU','New University')$q$)='OK');
select test.logout();

select (select count(*) filter (where pass) from test.results) || ' passed, ' || (select count(*) filter (where not pass) from test.results) || ' failed';
select '   ' || label from test.results where not pass order by n;
rollback;

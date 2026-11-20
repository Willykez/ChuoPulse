\pset tuples_only on
\set QUIET on
delete from test.results;
begin;
-- helper data
insert into storage.objects (bucket_id,name,owner,metadata) values
 ('submissions', test.id('stu1')::text||'/d1/a.pdf', test.id('stu1'), '{"size":2048,"mimetype":"application/pdf"}'),
 ('materials','CUOM:CS210/'||test.id('lec_c')::text||'/n.pdf', test.id('lec_c'), '{"size":4096,"mimetype":"application/pdf"}'),
 ('announcements', test.id('lec_c')::text||'/att.pdf', test.id('lec_c'), '{"size":100,"mimetype":"application/pdf"}'),
 ('submissions', test.id('stu_u')::text||'/d1/u.pdf', test.id('stu_u'), '{"size":10,"mimetype":"application/pdf"}'),
 ('materials','UDSM:CS210/'||test.id('lec_u')::text||'/u.pdf', test.id('lec_u'), '{"size":10,"mimetype":"application/pdf"}');

-- ===== A. security fixes
select test.login(test.id('lec_c'));
select test.logout();
select test.login(test.id('lec_u'));
select test.check('A1a UDSM books LH1', test.try($q$insert into public.class_sessions (course_code,lecturer_id,day_of_week,start_time,end_time,venue) values ('UDSM:CS210',test.id('lec_u'),1,'09:00','11:00','LH1')$q$)='OK');
select test.logout(); select test.login(test.id('lec_c'));
select test.check('A1b CUOM books same room/time', test.try($q$insert into public.class_sessions (id,course_code,lecturer_id,day_of_week,start_time,end_time,venue) values ('c0000000-0000-0000-0000-000000000001','CUOM:CS210',test.id('lec_c'),1,'09:30','10:30','LH1')$q$)='OK');
select test.check('A1c same campus clash still caught, no foreign code leaked', test.try($q$insert into public.class_sessions (course_code,lecturer_id,day_of_week,start_time,end_time,venue) values ('CUOM:CS220',test.id('lec_c2'),1,'10:00','11:00','LH1')$q$) = 'ERR: venue_busy: LH1 is busy at that time (CUOM:CS210)');
select test.check('A1d lecturer cannot teach two classes at once', test.try($q$insert into public.class_sessions (course_code,lecturer_id,day_of_week,start_time,end_time,venue) values ('CUOM:CS220',test.id('lec_c'),1,'10:00','11:00','LH9')$q$) like 'ERR: lecturer_busy%');
select test.logout();
update public.app_settings set value='off' where key='enforce_lecturer_clash';
select test.login(test.id('lec_c'));
select test.check('A1e ...unless the setting is off', test.try($q$insert into public.class_sessions (course_code,lecturer_id,day_of_week,start_time,end_time,venue) values ('CUOM:CS220',test.id('lec_c'),1,'10:00','11:00','LH9')$q$)='OK');
select test.logout();
update public.app_settings set value='on' where key='enforce_lecturer_clash';

select test.login(test.id('lec_c'));
select test.check('A2 back-dated rows cannot dodge limits (urgent)', 
  (select count(*) filter (where r like 'ERR%') from (select test.try(format($q$insert into public.announcements (author_id,title,body,audience,course_code,priority,created_at) values (test.id('lec_c'),'U %s','b','course','CUOM:CS210','urgent', now()-interval '3 days')$q$, g)) r from generate_series(1,5) g) x)=3);
select test.check('A2b created_at forced to now()', (select bool_and(created_at > now()-interval '1 minute') from public.announcements where title like 'U %'));
select test.check('A3 announcement attachment must be author''s own folder', test.try($q$insert into public.announcements (author_id,title,body,audience,course_code,attachment_path) values (test.id('lec_c'),'Att','b','course','CUOM:CS210',test.id('lec_c2')::text||'/x.pdf')$q$) like 'ERR: bad_storage_path%'
   and test.try($q$insert into public.announcements (id,author_id,title,body,audience,course_code,attachment_path) values ('a0000000-0000-0000-0000-000000000009',test.id('lec_c'),'Att ok','b','course','CUOM:CS210',test.id('lec_c')::text||'/att.pdf')$q$)='OK');
select test.logout();

select test.login(test.id('stu1'));
select test.check('A4 submission path hijack refused', test.try(format($q$insert into public.submissions (deadline_id,course_code,student_id,storage_path,file_name,mime_type,size_bytes) values ((select id from public.deadlines limit 1),'CUOM:CS210',%L,%L,'x','application/pdf',1)$q$, test.id('stu1'), test.id('stu2')::text||'/d1/z.pdf')) like 'ERR%');
select test.logout();

-- ===== B. drafts, late flag
select test.login(test.id('lec_c'));
select test.check('B1 draft does not notify', test.try($q$insert into public.announcements (id,author_id,title,body,audience,course_code,approval) values ('a0000000-0000-0000-0000-000000000002',test.id('lec_c'),'Draft notice','b','course','CUOM:CS210','draft')$q$)='OK'
  and (select count(*) from public.notifications where title='Draft notice')=0);
select test.check('B2a author publishes own draft', test.try($q$update public.announcements set approval='approved' where id='a0000000-0000-0000-0000-000000000002'$q$)='OK');
select test.logout();
select test.check('B2b ...and students are notified', (select count(*) from public.notifications where title='Draft notice')>=2);
select test.login(test.id('lec_c'));
select test.check('B3 author cannot set rejected/pending_review/approve others', test.try($q$update public.announcements set approval='rejected' where id='a0000000-0000-0000-0000-000000000002'$q$) like 'ERR%');
insert into public.deadlines (id,course_code,lecturer_id,title,due_at) values ('d0000000-0000-0000-0000-000000000001','CUOM:CS210',test.id('lec_c'),'Past due',now()-interval '1 hour');
select test.logout(); select test.login(test.id('stu1'));
select test.check('B4a late submission accepted', test.try(format($q$insert into public.submissions (id,deadline_id,course_code,student_id,storage_path,file_name,mime_type,size_bytes) values ('50000000-0000-0000-0000-000000000001','d0000000-0000-0000-0000-000000000001','CUOM:CS210',%L,%L,'a.pdf','image/png',1)$q$, test.id('stu1'), test.id('stu1')::text||'/d1/a.pdf'))='OK');
select test.check('B4b ...flagged late, mime type taken from storage', (select is_late and mime_type='application/pdf' from public.submissions where id='50000000-0000-0000-0000-000000000001'));
select test.logout();

-- ===== C. pause / resume
insert into auth.users (id,email,raw_user_meta_data) values ('20000000-0000-0000-0000-000000000001','pend@test.dev','{"university":"CUOM","full_name":"Pending Person","id_number":"CUOM/900","programme":"BSc Computer Science"}');
select test.login(test.id('adm_c'));
select public.admin_set_status('20000000-0000-0000-0000-000000000001', true, 'x'); select public.admin_set_status('20000000-0000-0000-0000-000000000001', false);
select public.admin_set_status(test.id('stu2'), true, 'x');
select test.check('C1 suspend shows suspended', (select status='suspended' from public.profiles where id=test.id('stu2')));
select public.admin_set_status(test.id('stu2'), false);
select test.logout();
select test.check('C2 pending stays pending after pause+resume', (select status='pending' and suspended_from is null from public.profiles where id='20000000-0000-0000-0000-000000000001'));
select test.check('C3 approved goes back to approved', (select status='approved' from public.profiles where id=test.id('stu2')));

-- ===== D. role change guard
select test.login(test.id('adm_c'));
select test.check('D1 lecturer with live classes cannot be turned into student', test.try(format($q$select public.admin_change_role(test.id('lec_c'),'student','CUOM/777',%L)$q$, (select id from public.programmes where university_id=public.my_university()))) like 'ERR: lecturer_has_classes%');
select test.logout();

-- ===== E. storage queue
select test.login(test.id('lec_c'));
insert into public.study_materials (id,course_code,title,uploader_id,storage_path,file_name,mime_type,size_bytes) values ('e0000000-0000-0000-0000-000000000001','CUOM:CS210','Notes',test.id('lec_c'),'CUOM:CS210/'||test.id('lec_c')::text||'/n.pdf','n.pdf','x',1);
delete from public.study_materials where id='e0000000-0000-0000-0000-000000000001';
select test.logout();
select test.check('E1 deleting a row queues its file', exists(select 1 from public.storage_cleanup_queue where bucket='materials' and path='CUOM:CS210/'||test.id('lec_c')::text||'/n.pdf'));
select test.check('E2 not handed out before the 5 minute grace', (select count(*) from public.storage_cleanup_next(100))=0);
update public.storage_cleanup_queue set queued_at = now()-interval '10 minutes';
select test.check('E3 handed out after the grace', (select count(*) from public.storage_cleanup_next(100))=1);
-- re-use of the same path: a live row references it again -> dropped from queue, never deleted
select test.login(test.id('lec_c'));
insert into public.study_materials (id,course_code,title,uploader_id,storage_path,file_name,mime_type,size_bytes) values ('e0000000-0000-0000-0000-000000000002','CUOM:CS210','Notes again',test.id('lec_c'),'CUOM:CS210/'||test.id('lec_c')::text||'/n.pdf','n.pdf','x',1);
select test.logout();
select test.check('E4a re-used path is not handed out', (select count(*) from public.storage_cleanup_next(100))=0);
select test.check('E4b ...and is dropped from the queue', (select count(*) from public.storage_cleanup_queue where path like '%n.pdf')=0);
select test.check('E5 clients cannot touch the queue', (select test.login(test.id('lec_c')) is not null) and test.try($q$select * from public.storage_cleanup_queue$q$) like 'ERR%' and test.try($q$select * from public.storage_cleanup_next(1)$q$) like 'ERR%');
select test.logout();
select test.check('E6 orphan sweeper is off by default', public.storage_enqueue_orphans('0 seconds')=0);
update public.app_settings set value='on' where key='orphan_sweep';
update storage.objects set created_at = now()-interval '3 days';
select test.check('E7 sweeper (on) queues unreferenced files only', public.storage_enqueue_orphans('24 hours')>=1
  and not exists (select 1 from public.storage_cleanup_queue where path = 'CUOM:CS210/'||test.id('lec_c')::text||'/n.pdf')
  and not exists (select 1 from public.storage_cleanup_queue where path = test.id('stu1')::text||'/d1/a.pdf'));
update public.app_settings set value='off' where key='orphan_sweep';
delete from public.storage_cleanup_queue;

-- ===== F. maintenance mode now enforced
select test.login(test.id('mgr')); select public.platform_set_maintenance((select id from public.universities where code='UDSM'), true, 'x'); select test.logout();
select test.login(test.id('lec_u'));
select test.check('F1 paused campus: lecturer cannot post', test.try($q$insert into public.announcements (author_id,title,body,audience,course_code) values (test.id('lec_u'),'Paused','b','course','UDSM:CS210')$q$) like 'ERR%');
select test.logout(); select test.login(test.id('adm_u'));
select test.check('F2 paused campus: admin cannot act', test.try($q$select public.admin_list_people()$q$) like 'ERR%');
select test.logout(); select test.login(test.id('stu_u'));
select test.check('F3 paused campus: student can still read own profile & ask if paused', (select count(*) from public.profiles)=1 and public.my_campus_paused());
select test.logout();
select test.login(test.id('mgr')); select public.platform_set_maintenance((select id from public.universities where code='UDSM'), false, ''); select test.logout();
select test.login(test.id('lec_u'));
select test.check('F4 back online: works again', test.try($q$insert into public.announcements (author_id,title,body,audience,course_code) values (test.id('lec_u'),'Online','b','course','UDSM:CS210')$q$)='OK');
select test.logout();

-- ===== G. manager account protection
select test.check('G1 public sign-up with manager address refused', test.try($q$insert into auth.users (id,email,email_confirmed_at,raw_user_meta_data) values ('30000000-0000-0000-0000-000000000001','abcplan2023@gmail.com',now(),'{"university":"CUOM","full_name":"Evil","id_number":"E1"}')$q$) like 'ERR: email_reserved%');
select test.check('G2 dashboard-created manager address stays profile-less', test.try($q$insert into auth.users (id,email,email_confirmed_at) values ('30000000-0000-0000-0000-000000000002','abcplan2023@gmail.com',now())$q$)='OK'
  and not exists (select 1 from public.profiles where id='30000000-0000-0000-0000-000000000002'));
insert into auth.sessions (user_id) values ('30000000-0000-0000-0000-000000000002');
select public.owner_make_manager('abcplan2023@gmail.com','a-very-long-password-123');
select test.check('G3 owner_make_manager: profile attached, squatter session ended, password set', exists (select 1 from public.profiles where id='30000000-0000-0000-0000-000000000002' and is_platform_admin and status='approved')
  and not exists (select 1 from auth.sessions where user_id='30000000-0000-0000-0000-000000000002')
  and (select encrypted_password = extensions.crypt('a-very-long-password-123', encrypted_password) from auth.users where id='30000000-0000-0000-0000-000000000002'));
select test.check('G4 campus admin cannot provision the reserved address', test.try($q$select public.provision_profile(test.id('adm_c'), '30000000-0000-0000-0000-000000000002', 'abcplan2023@gmail.com', 'CUOM', 'Some Person', 'student', 'CUOM/555', 'BSc Computer Science', true)$q$) like 'ERR: email_reserved%');
select test.logout();
select (select count(*) filter (where pass) from test.results) || ' passed, ' || (select count(*) filter (where not pass) from test.results) || ' failed';
select '   ' || label from test.results where not pass order by n;
rollback;

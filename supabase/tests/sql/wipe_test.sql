\pset tuples_only on
\set QUIET on
begin;
insert into storage.objects (bucket_id,name,owner,metadata) values
 ('submissions', test.id('stu1')::text||'/d1/a.pdf', test.id('stu1'), '{"size":2048,"mimetype":"application/pdf"}'),
 ('materials','CUOM:CS210/'||test.id('lec_c')::text||'/n.pdf', test.id('lec_c'), '{"size":4096,"mimetype":"application/pdf"}'),
 ('announcements', test.id('lec_c')::text||'/att.pdf', test.id('lec_c'), '{"size":100,"mimetype":"application/pdf"}'),
 ('materials','UDSM:CS210/'||test.id('lec_u')::text||'/u.pdf', test.id('lec_u'), '{"size":10,"mimetype":"application/pdf"}');
select test.login(test.id('lec_c'));
insert into public.announcements (author_id,title,body,audience,course_code,attachment_path) values (test.id('lec_c'),'Hello','b','course','CUOM:CS210',test.id('lec_c')::text||'/att.pdf');
insert into public.class_sessions (course_code,lecturer_id,day_of_week,start_time,end_time,venue) values ('CUOM:CS210',test.id('lec_c'),1,'09:00','10:00','LH1');
insert into public.study_materials (course_code,title,uploader_id,storage_path,file_name,mime_type,size_bytes) values ('CUOM:CS210','Notes',test.id('lec_c'),'CUOM:CS210/'||test.id('lec_c')::text||'/n.pdf','n.pdf','x',1);
insert into public.deadlines (id,course_code,lecturer_id,title,due_at) values ('d0000000-0000-0000-0000-000000000001','CUOM:CS210',test.id('lec_c'),'Essay',now()+interval '3 days');
insert into public.processed_documents (course_code,uploader_id,title,file_name) values ('CUOM:CS210',test.id('lec_c'),'Doc','d.pdf');
select test.logout(); select test.login(test.id('stu1'));
insert into public.submissions (deadline_id,course_code,student_id,storage_path,file_name,mime_type,size_bytes) values ('d0000000-0000-0000-0000-000000000001','CUOM:CS210',test.id('stu1'),test.id('stu1')::text||'/d1/a.pdf','a.pdf','application/pdf',1);
insert into public.deadline_done (deadline_id,student_id) values ('d0000000-0000-0000-0000-000000000001',test.id('stu1'));
select public.register_device('k','p');
select test.logout();
select 'rows before: profiles(CUOM)=' || (select count(*) from public.profiles where university_id=(select id from public.universities where code='CUOM')) || ' announcements=' || (select count(*) from public.announcements) || ' submissions=' || (select count(*) from public.submissions);
select test.login(test.id('mgr'));
select 'WIPE everything: ' || test.try($q$select public.platform_wipe_campus((select id from public.universities where code='CUOM'),'everything')$q$);
select test.logout();
select 'after: CUOM profiles=' || (select count(*) from public.profiles where university_id=(select id from public.universities where code='CUOM'))
  || ' programmes=' || (select count(*) from public.programmes where university_id=(select id from public.universities where code='CUOM'))
  || ' courses=' || (select count(*) from public.courses where code like 'CUOM:%')
  || ' | UDSM untouched: profiles=' || (select count(*) from public.profiles where university_id=(select id from public.universities where code='UDSM')) || ' courses=' || (select count(*) from public.courses where code like 'UDSM:%');
select 'queued files: ' || coalesce((select string_agg(bucket, ',' order by bucket) from public.storage_cleanup_queue), 'n/a (no queue in this schema)');
rollback;

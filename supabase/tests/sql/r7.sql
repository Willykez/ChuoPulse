\pset tuples_only on
begin;
select test.login(test.id('mgr'));
select public.platform_set_maintenance((select id from public.universities where code='UDSM'), true, 'offline');
select test.logout(); select test.login(test.id('stu_u'));
select 'paused campus: is_student()=' || public.is_student() || ' is_active()=' || public.is_active() || ' my_campus_paused()=' || public.my_campus_paused();
select 'paused campus student sees announcements/materials: ' || (select count(*) from public.announcements) || '/' || (select count(*) from public.study_materials);
select test.logout(); select test.login(test.id('adm_u'));
select 'paused campus admin can approve/notify: ' || test.try($q$select public.admin_notify_people('all', null, 'hello there', 'hello there')$q$);
rollback;

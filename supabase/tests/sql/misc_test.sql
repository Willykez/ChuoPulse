\pset tuples_only on
\set QUIET on
delete from test.results;
begin;
insert into auth.sessions (user_id) values (test.id('stu1')),(test.id('stu1')),(test.id('stu2'));
select test.login(test.id('stu1')); select public.register_device('k','p'); select public.request_data_deletion(); select test.logout();

-- log_account_action: only for people the caller may manage, only whitelisted actions
select test.login(test.id('adm_c'));
select test.check('M1a campus admin logs a password reset for own student', test.try($q$select public.log_account_action(test.id('stu1'),'PASSWORD_RESET','by admin')$q$)='OK');
select test.check('M1b ...and the audit entry names the person', exists(select 1 from public.audit_log where action='PASSWORD_RESET' and details like '%Student One%'));
select test.check('M2 ...not for another campus person / not for self / not unknown action', test.try($q$select public.log_account_action(test.id('stu_u'),'PASSWORD_RESET')$q$) like 'ERR%'
   and test.try($q$select public.log_account_action(test.id('adm_c'),'PASSWORD_RESET')$q$) like 'ERR%'
   and test.try($q$select public.log_account_action(test.id('stu1'),'DROP_EVERYTHING')$q$) like 'ERR%');
select test.logout();
select test.login(test.id('stu1'));
select test.check('M3 a student cannot call the audit/erase/session helpers', test.try($q$select public.log_account_action(test.id('stu2'),'PASSWORD_RESET')$q$) like 'ERR%'
  and test.try($q$select public.erase_person_data(test.id('stu2'))$q$) like 'ERR%' and test.try($q$select public.revoke_user_sessions(test.id('stu2'))$q$) like 'ERR%'
  and test.try($q$select public.ai_take_use(test.id('stu1'),100)$q$) like 'ERR%');
select public.mark_all_notifications_read();
select test.check('M4 notification helpers', public.unread_notification_count()=0);
select test.logout();

-- sessions
select test.check('M5a revoke_user_sessions reports 2', public.revoke_user_sessions(test.id('stu1'))=2);
select test.check('M5b ...only that person''s sessions went', (select count(*) from auth.sessions)=1);

-- erase
select test.check('M6a erase reports erased', (public.erase_person_data(test.id('stu1'))->>'status')='erased');
select test.check('M6b profile anonymised and locked', (select full_name='Deleted user' and status='rejected' and email like 'erased-%@invalid.invalid' and student_number like 'erased-%' and not is_class_rep from public.profiles where id=test.id('stu1')));
select test.check('M7 ...by-products gone, request closed, work kept', (select count(*) from public.devices where user_id=test.id('stu1'))=0
  and (select count(*) from public.enrollments where student_id=test.id('stu1'))=0
  and (select status from public.data_deletion_requests where user_id=test.id('stu1'))='done');
select test.check('M8 manager account cannot be erased', test.try($q$select public.erase_person_data(test.id('mgr'))$q$) like 'ERR%');

-- AI quota
select test.check('M9 AI quota: 3 allowed, 4th refused, refund gives one back', public.ai_take_use(test.id('stu2'),3) and public.ai_take_use(test.id('stu2'),3) and public.ai_take_use(test.id('stu2'),3));
select test.check('M9b over limit not counted', not public.ai_take_use(test.id('stu2'),3) and (select used from public.ai_usage where user_id=test.id('stu2'))=3);
select public.ai_refund_use(test.id('stu2'));
select test.check('M9c after refund one more allowed', public.ai_take_use(test.id('stu2'),3));
select test.check('M9d limit 0 always refuses', not public.ai_take_use(test.id('stu1'),0));

-- health + ai_status
select test.login(test.id('mgr'));
select test.check('M10 platform_health works for the manager', (select count(*) from public.platform_health())>=8);
select test.logout(); select test.login(test.id('adm_c'));
select test.check('M11 ...and is refused to everybody else', test.try($q$select * from public.platform_health()$q$) like 'ERR%');
select test.logout();
update public.ai_settings set api_key='AIzaSyABCDEFGHIJKLMNOPQRSTUVWXYZ123456';
select test.login(test.id('mgr'));
select test.check('M12 key hint shows only last 4', (select key_hint from public.ai_status())='…3456');
select test.logout(); select test.login(test.id('stu2'));
select test.check('M13 students get no hint', (select key_hint from public.ai_status())='');
select test.logout();
select (select count(*) filter (where pass) from test.results) || ' passed, ' || (select count(*) filter (where not pass) from test.results) || ' failed';
select '   ' || label from test.results where not pass order by n;
rollback;

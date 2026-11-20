\pset tuples_only on
begin; select test.login(test.id('stu1'));
select 'staff_directory, hidden UDSM names: ' || test.try($q$select * from public.staff_directory where case when full_name like '%Udsm%' then length(full_name)/(length(full_name)-length(full_name)) else 1 end = 1$q$);
select 'staff_directory, platform manager:  ' || test.try($q$select * from public.staff_directory where case when full_name like '%Manager%' then length(full_name)/(length(full_name)-length(full_name)) else 1 end = 1$q$);
select 'class_list, other-campus student numbers: ' || test.try($q$select * from public.class_list where case when student_number like 'UDSM%' then length(student_number)/(length(student_number)-length(student_number)) else 1 end = 1$q$);
rollback;
select 'view options: ' || relname || ' ' || coalesce(reloptions::text,'none') from pg_class where relname in ('staff_directory','class_list');

-- Paste into the Supabase SQL editor to see what state the database is in.
select 'trigger on auth.users' as check, tgname as value from pg_trigger
  where tgrelid = 'auth.users'::regclass and not tgisinternal;
select 'platform campus' as check, code as value from public.universities where is_platform;
select 'manager email' as check, value from public.app_settings where key = 'platform_manager_email';
select 'can_provision exists' as check, (to_regproc('public.can_provision(text,text)') is not null)::text as value;
select 'sign-in accounts WITHOUT a profile' as check, u.email as value
  from auth.users u left join public.profiles p on p.id = u.id where p.id is null;
select 'campuses' as check, code || ' - ' || name as value from public.universities where not is_platform;
select 'admins' as check, p.email || ' @ ' || coalesce(u.code,'?') || case when p.is_platform_admin then ' (manager)' else '' end as value
  from public.profiles p join public.universities u on u.id = p.university_id where p.role = 'admin';

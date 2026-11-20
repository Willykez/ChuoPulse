-- =====================================================================================
-- CampusConnect diagnostics + SECURITY AUDIT. Read-only. Paste into the SQL editor and run.
-- Every section should say OK. Anything else says what to do.
-- =====================================================================================

-- 1. Functions that signed-out visitors can call. Must be ZERO rows.
select '1. FUNCTIONS CALLABLE WITHOUT SIGNING IN (must be empty)' as check, p.oid::regprocedure::text as function
from pg_proc p join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public' and p.prokind = 'f' and has_function_privilege('anon', p.oid, 'execute');

-- 2. Dangerous functions that must never be callable from the app. Must be ZERO rows.
select '2. SERVER-ONLY FUNCTION EXPOSED TO THE APP (must be empty)' as check, p.oid::regprocedure::text as function
from pg_proc p join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.proname in ('make_manager_profile','attach_profile','owner_make_manager','owner_set_password','provision_profile',
                    'bootstrap_first_admin','erase_person_data','revoke_user_sessions','ai_take_use','ai_refund_use',
                    'notify_users','log_audit','storage_cleanup_next','storage_cleanup_done','storage_cleanup_failed',
                    'storage_enqueue_orphans','prune_notifications','pending_unconfirmed_user')
  and (has_function_privilege('authenticated', p.oid, 'execute') or has_function_privilege('anon', p.oid, 'execute'));

-- 3. Tables in public without row-level security. Must be ZERO rows.
select '3. TABLE WITHOUT ROW LEVEL SECURITY (must be empty)' as check, c.relname::text as "table"
from pg_class c join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public' and c.relkind = 'r' and not c.relrowsecurity;

-- 4. Views that skip the caller's own security. Each needs security_barrier (or security_invoker).
select '4. VIEW WITHOUT security_barrier / security_invoker' as check, c.relname::text as view
from pg_class c join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public' and c.relkind = 'v'
  and not (coalesce(c.reloptions::text, '') like '%security_barrier=true%' or coalesce(c.reloptions::text, '') like '%security_invoker=true%');

-- 5. Anyone who can sign in but has no profile (cannot do anything; remove or attach).
select '5. SIGN-IN ACCOUNT WITHOUT A PROFILE' as check, u.email, u.created_at
from auth.users u left join public.profiles p on p.id = u.id
where p.id is null order by u.created_at;

-- 6. The app manager: there must be at least one, and the reserved address must be one of them.
select '6. APP MANAGERS' as check, count(*)::text || ' approved' as status,
       case when count(*) >= 1 then 'OK' else 'NO MANAGER: create the account in the dashboard, then  select public.owner_make_manager(''you@gmail.com'',''a-long-password'');' end as note
from public.profiles where is_platform_admin and status = 'approved';

-- 7. Settings that matter.
select '7. SETTING' as check, key, value,
       case when key = 'require_admin_mfa' and value <> 'true' then 'DANGER: administrators are not asked for their second step' else 'OK' end as note
from public.app_settings where key in ('require_admin_mfa','orphan_sweep','enforce_lecturer_clash','platform_manager_email') order by key;

-- 8. Storage files waiting to be removed.
select '8. STORAGE CLEANUP QUEUE' as check,
       count(*) filter (where attempts < 8) as waiting, count(*) filter (where attempts >= 8) as failed_8_times,
       case when count(*) filter (where attempts >= 8) > 0 then 'look at last_error in storage_cleanup_queue' else 'OK' end as note
from public.storage_cleanup_queue;

-- 9. Quiz attempts that ran out of time but were never closed.
select '9. QUIZ ATTEMPTS PAST THEIR TIME, NOT CLOSED' as check, count(*) as stuck,
       case when count(*) = 0 then 'OK' else 'schedule finalize_expired_quiz_attempts() every minute (pg_cron)' end as note
from public.quiz_attempts where status = 'in_progress' and deadline_at + interval '1 minute' < now();

-- 10. People per campus and role.
select '10. PEOPLE' as check, u.code, p.role::text, p.status::text, count(*) as people
from public.profiles p join public.universities u on u.id = p.university_id
group by u.code, p.role, p.status order by u.code, p.role, p.status;

-- 11. Open privacy requests (process each with the admin-manage-user "erase" action).
select '11. OPEN DATA-DELETION REQUESTS' as check, count(*) as open from public.data_deletion_requests where status = 'open';

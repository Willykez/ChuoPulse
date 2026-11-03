-- =====================================================================================
-- No more emailed sign-up codes. A person who registers is created as PENDING and an administrator
-- approves them. This lets the app finish a sign-up even if "Confirm email" is still ON in the
-- Supabase dashboard (turning it OFF is still recommended). Safe to run again.
--
-- Only the Edge Function `confirm-signup` (service role) can call this. It only ever returns an
-- account that is unconfirmed AND still waiting for approval, never an approved or paused one.
-- =====================================================================================
create or replace function public.pending_unconfirmed_user(p_email text)
returns uuid language sql stable security definer set search_path = public, auth as $$
  select u.id
    from auth.users u
    join public.profiles p on p.id = u.id
   where lower(u.email) = lower(trim(p_email))
     and u.email_confirmed_at is null
     and p.status = 'pending'
   limit 1;
$$;
revoke all on function public.pending_unconfirmed_user(text) from public, anon, authenticated;
grant execute on function public.pending_unconfirmed_user(text) to service_role;

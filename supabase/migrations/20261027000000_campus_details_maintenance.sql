-- Campus details and offline (maintenance) mode.
-- Safe to run more than once (CI re-applies every migration to prove that).

alter table public.universities add column if not exists location          text    not null default '';
alter table public.universities add column if not exists contact_email     text    not null default '';
alter table public.universities add column if not exists contact_phone     text    not null default '';
alter table public.universities add column if not exists under_maintenance boolean not null default false;
alter table public.universities add column if not exists maintenance_note  text    not null default '';

-- The app manager puts one campus offline, or brings it back. Everyone at that campus is paused
-- in the app until this is switched off again.
create or replace function public.platform_set_maintenance(p_university uuid, p_on boolean, p_note text)
returns void language plpgsql security definer set search_path = public as $$
begin
  if not public.is_platform_admin() then raise exception 'not_allowed' using errcode = '42501'; end if;
  update public.universities
     set under_maintenance = coalesce(p_on, false),
         maintenance_note  = case when coalesce(p_on, false) then left(trim(coalesce(p_note, '')), 300) else '' end
   where id = p_university and not is_platform;
  if not found then raise exception 'unknown campus' using errcode = 'P0001'; end if;
  perform public.log_audit(case when coalesce(p_on, false) then 'CAMPUS_OFFLINE' else 'CAMPUS_ONLINE' end,
                           (select code from public.universities where id = p_university));
end $$;

-- The app manager can change everything about a campus; a campus administrator only the contact details
-- of their own campus (name stays with the app manager).
create or replace function public.update_campus_details(
  p_university uuid, p_name text, p_location text, p_email text, p_phone text)
returns void language plpgsql security definer set search_path = public as $$
declare v_manager boolean := public.is_platform_admin();
begin
  if not v_manager and not public.campus_admin(p_university) then
    raise exception 'not_allowed' using errcode = '42501';
  end if;
  update public.universities
     set name          = case when v_manager and length(trim(coalesce(p_name, ''))) >= 2 then trim(p_name) else name end,
         location      = left(trim(coalesce(p_location, '')), 120),
         contact_email = left(trim(coalesce(p_email, '')), 120),
         contact_phone = left(trim(coalesce(p_phone, '')), 40)
   where id = p_university and not is_platform;
  if not found then raise exception 'unknown campus' using errcode = 'P0001'; end if;
  perform public.log_audit('CAMPUS_DETAILS_EDITED', (select code from public.universities where id = p_university));
end $$;

-- Lets the app ask "is my campus paused?" cheaply, even right after sign-in.
create or replace function public.my_campus_paused()
returns boolean language sql stable security definer set search_path = public as $$
  select coalesce((select u.under_maintenance from public.universities u
                    where u.id = public.my_university() and not public.is_platform_admin()), false);
$$;

grant execute on function
  public.platform_set_maintenance(uuid, boolean, text),
  public.update_campus_details(uuid, text, text, text, text),
  public.my_campus_paused()
  to authenticated;

-- Minimal Supabase-compatible harness (roles, auth, storage, extensions) for testing the migrations
do $$ begin
  create role anon nologin noinherit;                 exception when duplicate_object then null; end $$;
do $$ begin
  create role authenticated nologin noinherit;        exception when duplicate_object then null; end $$;
do $$ begin
  create role service_role nologin noinherit bypassrls; exception when duplicate_object then null; end $$;
do $$ begin
  create role supabase_auth_admin nologin noinherit;  exception when duplicate_object then null; end $$;

create schema if not exists extensions;
create extension if not exists pgcrypto with schema extensions;
grant usage on schema public, extensions to anon, authenticated, service_role;

create schema if not exists auth;
create table if not exists auth.users (
  id uuid primary key default gen_random_uuid(),
  email text unique,
  encrypted_password text,
  email_confirmed_at timestamptz,
  raw_app_meta_data jsonb default '{}'::jsonb,
  raw_user_meta_data jsonb default '{}'::jsonb,
  banned_until timestamptz,
  created_at timestamptz default now(),
  updated_at timestamptz default now()
);
create table if not exists auth.sessions (id uuid primary key default gen_random_uuid(), user_id uuid references auth.users(id) on delete cascade);
create or replace function auth.uid() returns uuid language sql stable as $$
  select nullif(coalesce(current_setting('request.jwt.claim.sub', true),
         (nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub')), '')::uuid $$;
create or replace function auth.jwt() returns jsonb language sql stable as $$
  select coalesce(nullif(current_setting('request.jwt.claims', true), '')::jsonb, '{}'::jsonb) $$;
create or replace function auth.role() returns text language sql stable as $$
  select coalesce(nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'role', 'anon') $$;
grant usage on schema auth to anon, authenticated, service_role, supabase_auth_admin;
grant execute on function auth.uid(), auth.jwt(), auth.role() to anon, authenticated, service_role;

create schema if not exists storage;
create table if not exists storage.buckets (
  id text primary key, name text not null, public boolean default false,
  file_size_limit bigint, allowed_mime_types text[], created_at timestamptz default now());
create table if not exists storage.objects (
  id uuid primary key default gen_random_uuid(), bucket_id text references storage.buckets(id),
  name text, owner uuid, metadata jsonb, created_at timestamptz default now(), updated_at timestamptz default now(),
  unique (bucket_id, name));
alter table storage.objects enable row level security;
create or replace function storage.foldername(name text) returns text[] language sql immutable as $$
  select case when position('/' in name) = 0 then '{}'::text[]
              else (string_to_array(name, '/'))[1:array_length(string_to_array(name, '/'),1)-1] end $$;
grant usage on schema storage to anon, authenticated, service_role;
grant select, insert, update, delete on storage.objects to authenticated;
grant select on storage.buckets to authenticated;
grant all on all tables in schema storage to service_role;

-- Supabase's default privileges on public (what the platform ships with)
alter default privileges in schema public grant all on tables    to anon, authenticated, service_role;
alter default privileges in schema public grant all on functions to anon, authenticated, service_role;
alter default privileges in schema public grant all on sequences to anon, authenticated, service_role;

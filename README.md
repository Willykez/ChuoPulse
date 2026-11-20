# ChuoPulse

Android app (Kotlin + Jetpack Compose) for university campuses in Tanzania: timetables, announcements,
course materials, deadlines, quizzes and submissions. The backend is Supabase.

## Layout

```
app/                         Android application
supabase/migrations/         Schema, row-level security, limits, audit, hardening (latest: 20261108)
supabase/functions/          Edge Functions: admin-create-user, admin-manage-user, ai-analyze,
                             campus-wipe-files, confirm-signup, storage-cleanup
supabase/diagnostics.sql     Read-only health and security audit (run it after every deploy)
supabase/tests/              Database and Edge Function tests (run_all.sh, edge/run.ts)
.github/workflows/           Build, backend deploy, signed release
.env.example                 Build settings template
```

## Build the app

1. Create a Supabase project, then `supabase link --project-ref YOUR_PROJECT_REF && supabase db push`.
2. Copy `.env.example` to `.env`; set `SUPABASE_URL` and `SUPABASE_ANON_KEY` (publishable/anon key only, never the service-role key).
3. `./gradlew :app:assembleDebug`. A release build fails fast if the two values are missing.

## Deploy the backend

Run the **Deploy backend** workflow (or `supabase db push` and `supabase functions deploy <name>`;
`confirm-signup` needs `--no-verify-jwt`). Then:

* Run `supabase/diagnostics.sql`: checks 1-4 must return no rows.
* Dashboard: Authentication -> Providers -> Email -> **Confirm email OFF** (people wait for an administrator to approve them).
* Set the secret `CLEANUP_SECRET` and schedule the `storage-cleanup` function every 15 minutes
  (header `x-cleanup-secret`). It removes files of deleted rows and wiped campuses.
* Enable the `pg_cron` extension to auto-close quiz attempts and prune old notifications.

## App manager

The manager's address is reserved in the database and nobody can claim it by signing up. Create the account in the
dashboard (Authentication -> Users -> Add user, "Auto Confirm User" ticked), then run once in the SQL editor:

```sql
select public.owner_make_manager('abcplan2023@gmail.com', 'a-long-new-password');
```

This sets the password, ends any existing session on that account and attaches the manager profile.
Administrators then enrol an authenticator app on first sign-in.

## Release

Repository secrets: `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_ACCESS_TOKEN`, `SUPABASE_DB_PASSWORD`,
`SUPABASE_PROJECT_ID`, `KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`, optional `AI_API_KEY`,
`AI_PROVIDER`, `AI_MODEL`, `CLEANUP_SECRET`. Push a tag such as `v1.0.1` to publish a signed APK and AAB.

## Security model

The server enforces campus membership, account approval, enrolment, audience and role through row-level security on
every query; the app only filters for convenience. Never put the service-role key, database password or access token in
the app, in `.env` or in Git. The session is stored encrypted with the Android Keystore; app backup is off.

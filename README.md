# ChuoPulse

Android app (Kotlin + Jetpack Compose) for university campuses in Tanzania: timetables, announcements,
course materials, deadlines, quizzes and submissions. The backend is Supabase.

## Layout

```
app/                         Android application
supabase/migrations/         5 files: core_schema, accounts_and_roles, admin_tools, security_hardening, remove_quizzes
supabase/functions/          Edge Functions: admin-create-user, admin-manage-user, ai-analyze, ai-timetable,
                             campus-wipe-files, confirm-signup, storage-cleanup (+ _shared)
supabase/diagnostics.sql     Read-only health and security audit (run it after every deploy)
supabase/tests/              Database and Edge Function tests (run_all.sh, edge/run.ts)
tests/jvm/                   Logic tests for offline cache, document reader and timetable assistant (run.sh)
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
* Enable the `pg_cron` extension to prune old notifications and queue unused files.

## Features worth knowing

* **Offline:** every sync is cached on the phone. Without a connection the app shows the last synced timetable,
  announcements and deadlines, with the sync time on the Profile screen. Changes need a connection. Signing out clears the cache.
* **AI timetable assistant** (campus administrators, Campus setup): upload a timetable or course list (PDF, Word, Excel, CSV,
  photo). The `ai-timetable` function proposes programmes, courses, lecturer assignments and weekly sessions; the administrator
  ticks, edits or removes each item, clashes with the existing timetable are flagged, and only approved items are added.
  It uses the same AI key and daily allowance as document analysis.
* **Document viewer:** PDF pages, images, Word (`.docx`, `.odt`) with headings, lists and tables, spreadsheets
  (`.xlsx`, `.ods`, `.csv`, `.tsv`) as a grid with sheet tabs, zip listings, slides and text. Legacy `.doc`, `.xls`, `.ppt` show
  the text that can be recovered and offer "open in another app".
* **Submissions:** filters by course and status, summary counts, typed file icons and a Late marker.

## Existing project (migrations were merged from 15 files into 4)

Your database already has the 15 old migration names in its history. Tell the CLI the 11 removed ones are gone
(this only edits the history table, it runs no SQL), then push:

```
supabase migration repair --status reverted 20261024000000 20261025000000 20261026000000 20261027000000 20261031000000 20261101000000 20261102000000 20261103000000 20261105000000 20261106000000 20261107000000
supabase db push
```

`20261021000000`, `20261030000000` and `20261104000000` keep their numbers, so they stay recorded as applied.
A brand-new project needs nothing special: `supabase db push` applies all five in order.
`20261109000000_remove_quizzes.sql` deletes the old quiz tables and their data on an existing project.

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

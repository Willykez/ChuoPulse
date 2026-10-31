# ChuoPulse

Android app (Kotlin + Jetpack Compose) for university campuses in Tanzania: timetables, announcements,
course materials, deadlines, quizzes and submissions. The backend is Supabase.

## Project layout

```
app/                         Android application
supabase/migrations/         Database schema, row-level security, role limits, audit rules
supabase/functions/          Edge Function: campus-wipe-files
.github/workflows/           Build, database deploy, function deploy, signed release
.env.example                 Build settings template
```

## Build the app

1. Create a Supabase project and apply the schema:
   `supabase link --project-ref YOUR_PROJECT_REF && supabase db push`
2. Copy `.env.example` to `.env` and set `SUPABASE_URL` and `SUPABASE_ANON_KEY`
   (the publishable/anon key only; never the service-role key).
3. Build: `./gradlew :app:assembleDebug` (a release build fails fast if the two values are missing).
4. Deploy the edge function: `supabase functions deploy campus-wipe-files`.

## App manager login

The manager's email (`abcplan2023@gmail.com`) is registered by the migration `20261030000000_platform_manager.sql`.
The password is never stored in Git or in the app. Create the account in the Supabase dashboard:
Authentication -> Users -> Add user -> email above, a strong password, "Auto Confirm User" ticked.
It becomes the app manager automatically. Administrators then enrol an authenticator app on first sign-in.

## Release (GitHub Actions)

Repository secrets: `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_ACCESS_TOKEN`, `SUPABASE_DB_PASSWORD`,
`SUPABASE_PROJECT_ID`, `KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`.
Push a tag such as `v1.0.1` to build and publish a signed APK and AAB.

## Security model

The server enforces university membership, account approval, course enrolment, programme audience and
role through row-level security on every query. The app only filters for convenience.
Never put the service-role key, database password or access token in the app, in `.env` or in Git.

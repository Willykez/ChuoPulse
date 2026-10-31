# ChuoPulse: fixing accounts, OTP emails, campuses and admins

## Why things were broken
1. **"Database error creating new user"** (dashboard → Add user): the sign-up trigger rejected every account that
   had no university in its sign-up data. The dashboard sends none. Fixed by `20261031000000_fix_accounts_and_admins.sql`.
2. **No OTP / reset codes**: Supabase's built-in email sender only delivers to members of your Supabase
   organization and only 2 emails/hour. Everyone else silently gets nothing (and sign-ups can roll back). Needs custom SMTP.
3. **Reset code is a link, not digits**: the default "Reset password" / "Confirm signup" templates send a link.
   The app expects the code, so the templates must contain `{{ .Token }}`.
4. **Campuses/admins only lived in the phone's memory.** Now created on the server (RPC + Edge Function).

## Do these in order

### A. Database (SQL editor, paste whole file, or `supabase db push`)
Run `supabase/migrations/20261031000000_fix_accounts_and_admins.sql`, then `supabase/diagnostics.sql` to check.

### B. Deploy the new Edge Function
`supabase functions deploy admin-create-user` (the service key is injected by Supabase; nothing to add to the app).

### C. Email (Dashboard → Authentication)
1. **Emails → SMTP Settings**: enable custom SMTP (Brevo, Gmail app-password, or any provider). Sender must be a verified address.
2. **Rate Limits**: raise "emails per hour" to match your provider.
3. **Emails → Templates**: in *Confirm signup* and *Reset password* put the code in the body, e.g.
   `<p>Your ChuoPulse code: <b>{{ .Token }}</b></p>`
4. **Sign In / Providers → Email**: keep "Confirm email" ON. Set OTP length to 6 (the app also accepts 6-8).
5. **URL Configuration**: add `campusconnect://login-callback` to Redirect URLs.

### D. Create yourself (works even before email is set up)
1. Dashboard → Authentication → Users → Add user → your email + password, **Auto Confirm** ticked. This now succeeds.
2. SQL editor, make it the app manager (or a campus admin):
   ```sql
   -- app manager (runs all campuses)
   select public.attach_profile('YOU@gmail.com', 'PLATFORM', 'Your Name', 'admin', 'APP-001');
   ```
   (`abcplan2023@gmail.com` is still the automatic manager email. Change it with
   `update public.app_settings set value='YOU@gmail.com' where key='platform_manager_email';` before creating that user.)
3. Sign in to the app; it asks you to enrol an authenticator app (admins need it). Then **New campus** in the app creates the
   campus and its first administrator in one step; administrators add lecturers/students (or approve sign-ups).

## Still local-only (not part of this fix)
Announcements, timetable, deadlines, materials, quizzes, notifications, programmes/courses/rooms, campus maintenance & details.
Lecturer course assignment on approval (approved now; courses need the catalogue sync).

---

## Owner control (works even with no email at all)
SQL editor only, never reachable from the app:
```sql
select public.owner_set_password('you@gmail.com', 'A-new-strong-password');   -- set/reset any password, no email needed
select public.attach_profile('you@gmail.com','PLATFORM','Your Name','admin','APP-001');  -- app manager
select public.attach_profile('x@gmail.com','CUOM','Full Name','admin','STAFF-001');      -- campus admin
select * from public.owner_people();   -- everybody, with campus, role, status, class rep
```
Run `supabase/migrations/20261101000000_class_reps_and_sync.sql` after the earlier migrations.

## Cloud sync and class representatives (new)
* Announcements, timetable, assignments (deadlines), materials, submissions, notifications, quizzes, programmes,
  courses and rooms are now saved on the server and read back; edits by one person appear for the others
  within about a minute (and straight after any save or when you tap Sync).
* Class representative = approved student with a mark. Admin or a lecturer of that programme sets it
  (Profile → Class representatives). A rep can post notices (programme/course only, never Urgent or campus-wide),
  upload materials, post assignments and edit timetable sessions, only for courses of their own programme.
  Never quizzes, grading, or other students' work. Enforced by the database, not just the screens.
* Lecturers' courses: choose them when approving a lecturer, or later with "Assign lecturers"; both save to the server.

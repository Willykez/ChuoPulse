-- Two campuses, every role, realistic data. Runs as superuser (no JWT => triggers treat as system).
create schema if not exists test;
create or replace function test.login(p_user uuid, p_aal text default 'aal2') returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', p_user, 'role', 'authenticated', 'aal', p_aal)::text, true);
  perform set_config('role', 'authenticated', true);
end $$;
create or replace function test.anon() returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', '', true);
  perform set_config('role', 'anon', true);
end $$;
create or replace function test.logout() returns void language plpgsql as $$
begin
  perform set_config('role', 'postgres', true);
  perform set_config('request.jwt.claims', '', true);
end $$;
grant usage on schema test to anon, authenticated;
grant execute on all functions in schema test to anon, authenticated;

-- ids
create table if not exists test.ids (k text primary key, id uuid not null);
insert into test.ids values
 ('mgr','10000000-0000-0000-0000-000000000001'),
 ('adm_c','10000000-0000-0000-0000-000000000011'),
 ('adm_u','10000000-0000-0000-0000-000000000012'),
 ('lec_c','10000000-0000-0000-0000-000000000021'),
 ('lec_c2','10000000-0000-0000-0000-000000000022'),
 ('lec_u','10000000-0000-0000-0000-000000000023'),
 ('stu1','10000000-0000-0000-0000-000000000031'),
 ('stu2','10000000-0000-0000-0000-000000000032'),
 ('stu_u','10000000-0000-0000-0000-000000000033'),
 ('rep1','10000000-0000-0000-0000-000000000034')
on conflict do nothing;
create or replace function test.id(p text) returns uuid language sql stable as $$ select id from test.ids where k = p $$;

insert into public.universities (code, name) values ('CUOM','Catholic University of Mbeya'), ('UDSM','University of Dar es Salaam') on conflict do nothing;

insert into auth.users (id, email, email_confirmed_at) values
 (test.id('mgr'),'mgr@test.dev',now()),(test.id('adm_c'),'adm.c@test.dev',now()),(test.id('adm_u'),'adm.u@test.dev',now()),
 (test.id('lec_c'),'lec.c@test.dev',now()),(test.id('lec_c2'),'lec.c2@test.dev',now()),(test.id('lec_u'),'lec.u@test.dev',now()),
 (test.id('stu1'),'stu1@test.dev',now()),(test.id('stu2'),'stu2@test.dev',now()),(test.id('stu_u'),'stu.u@test.dev',now()),
 (test.id('rep1'),'rep1@test.dev',now());

select public.attach_profile('mgr@test.dev','PLATFORM','Mgr Manager','admin','APP-777');
select public.attach_profile('adm.c@test.dev','CUOM','Adm Cuom','admin','STAFF-A1');
select public.attach_profile('adm.u@test.dev','UDSM','Adm Udsm','admin','STAFF-A2');
select public.attach_profile('lec.c@test.dev','CUOM','Lec Cuom One','lecturer','STAFF-L1');
select public.attach_profile('lec.c2@test.dev','CUOM','Lec Cuom Two','lecturer','STAFF-L2');
select public.attach_profile('lec.u@test.dev','UDSM','Lec Udsm One','lecturer','STAFF-L3');
select public.attach_profile('stu1@test.dev','CUOM','Student One','student','CUOM/001');
select public.attach_profile('stu2@test.dev','CUOM','Student Two','student','CUOM/002');
select public.attach_profile('stu.u@test.dev','UDSM','Student Udsm','student','UDSM/001');
select public.attach_profile('rep1@test.dev','CUOM','Rep Person','student','CUOM/003');

-- catalogue
insert into public.programmes (university_id, name) select id,'BSc Computer Science' from public.universities where code in ('CUOM','UDSM');
insert into public.courses (university_id, short_code, title) select id,'CS210','Databases' from public.universities where code in ('CUOM','UDSM');
insert into public.courses (university_id, short_code, title) select id,'CS220','Networks' from public.universities where code='CUOM';
insert into public.programme_courses select g.id, c.code from public.programmes g join public.courses c on c.university_id=g.university_id;
update public.profiles set programme_id = (select g.id from public.programmes g where g.university_id=profiles.university_id)
 where role='student';
insert into public.enrollments select p.id, pc.course_code from public.profiles p join public.programme_courses pc on pc.programme_id=p.programme_id where p.role='student';
insert into public.teaching_assignments values (test.id('lec_c'),'CUOM:CS210'),(test.id('lec_c'),'CUOM:CS220'),(test.id('lec_c2'),'CUOM:CS220'),(test.id('lec_u'),'UDSM:CS210');
update public.profiles set is_class_rep = true where id = test.id('rep1');
insert into public.rooms (university_id,name) select id,'LH1' from public.universities where code in ('CUOM','UDSM');

-- Processed AI summaries: Supabase is the only cloud source of truth; Room is the offline cache.
create table if not exists public.processed_documents (
  id uuid primary key default gen_random_uuid(),
  course_code text not null references public.courses(code) on delete cascade,
  uploader_id uuid not null references public.profiles(id) on delete cascade,
  title text not null check (length(trim(title)) >= 2),
  file_name text not null,
  file_size bigint not null default 0 check (file_size >= 0),
  mime_type text not null default 'application/octet-stream',
  course_title text not null default '',
  university_name text not null default '',
  uploader_name text not null default '',
  document_timestamp bigint not null default (extract(epoch from now()) * 1000)::bigint,
  extracted_summary text not null default '',
  key_topics text not null default '',
  learning_objectives text not null default '',
  study_tips text not null default '',
  word_count integer not null default 0 check (word_count >= 0),
  page_count integer not null default 0 check (page_count >= 0),
  reading_time_minutes integer not null default 0 check (reading_time_minutes >= 0),
  extraction_status text not null default 'PENDING'
    check (extraction_status in ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
  category text not null default 'Notes'
    check (category in ('Notes', 'Syllabus', 'Assignments', 'Exam Revision', 'Reference')),
  tags text not null default '',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists processed_documents_course_idx on public.processed_documents(course_code, document_timestamp desc);
create index if not exists processed_documents_uploader_idx on public.processed_documents(uploader_id);
alter table public.processed_documents enable row level security;

drop policy if exists cc_processed_docs_read on public.processed_documents;
create policy cc_processed_docs_read on public.processed_documents for select to authenticated
  using (public.admin_of(uploader_id) or uploader_id = auth.uid()
    or (public.is_active() and (public.enrolled_in(course_code) or public.teaches(course_code))));
drop policy if exists cc_processed_docs_insert on public.processed_documents;
create policy cc_processed_docs_insert on public.processed_documents for insert to authenticated
  with check (uploader_id = auth.uid() and (public.admin_of(uploader_id)
    or (public.is_lecturer() and public.teaches(course_code))));
drop policy if exists cc_processed_docs_update on public.processed_documents;
create policy cc_processed_docs_update on public.processed_documents for update to authenticated
  using (public.admin_of(uploader_id) or uploader_id = auth.uid())
  with check (public.admin_of(uploader_id) or uploader_id = auth.uid());
drop policy if exists cc_processed_docs_delete on public.processed_documents;
create policy cc_processed_docs_delete on public.processed_documents for delete to authenticated
  using (public.admin_of(uploader_id) or uploader_id = auth.uid());

grant select, insert, update, delete on public.processed_documents to authenticated;

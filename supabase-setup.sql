-- اجرا در Supabase ← SQL Editor ← New query ← Run
create table if not exists public.kv (
  key        text primary key,
  value      text not null default '',
  updated_at timestamptz not null default now()
);

alter table public.kv enable row level security;

drop policy if exists "kv read"   on public.kv;
drop policy if exists "kv insert" on public.kv;
drop policy if exists "kv update" on public.kv;

create policy "kv read"   on public.kv for select to anon using (key in ('meetings','heyats'));
create policy "kv insert" on public.kv for insert to anon with check (key in ('meetings','heyats'));
create policy "kv update" on public.kv for update to anon using (key in ('meetings','heyats')) with check (key in ('meetings','heyats'));
-- حذف ردیف برای کاربران مجاز نیست (policy ندارد)

insert into public.kv (key, value) values ('meetings', '{"meetings":[]}') on conflict (key) do nothing;

-- ============ انتقادات و پیشنهادات ============
create table if not exists public.feedback (
  id         bigint generated always as identity primary key,
  text       text not null check (char_length(text) between 1 and 1000),
  created_at timestamptz not null default now()
);

alter table public.feedback enable row level security;

drop policy if exists "feedback insert" on public.feedback;
create policy "feedback insert" on public.feedback for insert to anon
  with check (char_length(text) between 1 and 1000);
-- عمداً policy برای select / delete نیست: کاربران عادی نمی‌توانند پیام‌ها را بخوانند

-- خواندن پیام‌ها فقط با رمز مدیر (رمز را اگر خواستید عوض کنید: دو جا، '110118128')
create or replace function public.list_feedback(p_pass text)
returns setof public.feedback
language plpgsql security definer set search_path = public as $$
begin
  if p_pass is distinct from '110118128' then raise exception 'forbidden'; end if;
  return query select * from public.feedback order by created_at desc limit 500;
end $$;

create or replace function public.delete_feedback(p_pass text, p_id bigint)
returns void
language plpgsql security definer set search_path = public as $$
begin
  if p_pass is distinct from '110118128' then raise exception 'forbidden'; end if;
  delete from public.feedback where id = p_id;
end $$;

revoke all on function public.list_feedback(text) from public;
revoke all on function public.delete_feedback(text, bigint) from public;
grant execute on function public.list_feedback(text) to anon;
grant execute on function public.delete_feedback(text, bigint) to anon;

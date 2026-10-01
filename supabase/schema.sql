-- Watch Party - Supabase PostgreSQL schema
-- المرحلة 2: الجداول، الفهارس، Row Level Security، والـ Triggers.
--
-- طريقة الاستخدام:
-- 1) افتح Supabase Dashboard.
-- 2) اذهب إلى SQL Editor.
-- 3) انسخ هذا الملف كاملًا وشغّله مرة واحدة على مشروعك.
--
-- ملاحظات مهمة:
-- - هذا الملف لا يحتوي أي أسرار أو مفاتيح.
-- - نفّذه من SQL Editor بصلاحيات مالك المشروع حتى تُنشأ سياسات RLS والدوال.
-- - Backend سيستخدم Service/DB credentials عبر متغيرات البيئة فقط، وليس داخل SQL.

begin;

-- نحتاج pgcrypto لتوليد UUID عشوائي داخل PostgreSQL.
create extension if not exists pgcrypto;

-- -----------------------------------------------------------------------------
-- 1) دوال مساعدة عامة
-- -----------------------------------------------------------------------------

-- تحديث updated_at تلقائيًا عند تعديل الصف.
create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

-- توليد كود دعوة قصير وآمن نسبيًا للاستخدام في الرابط.
create or replace function public.generate_invite_code(code_length integer default 10)
returns text
language plpgsql
as $$
declare
  alphabet text := 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
  result text := '';
  i integer;
begin
  if code_length < 6 or code_length > 32 then
    raise exception 'invite code length must be between 6 and 32';
  end if;

  for i in 1..code_length loop
    result := result || substr(alphabet, 1 + floor(random() * length(alphabet))::integer, 1);
  end loop;

  return result;
end;
$$;

-- دالة Trigger لضمان أن invite_code فريد بدون الاعتماد على المنطق في التطبيق فقط.
create or replace function public.set_room_invite_code()
returns trigger
language plpgsql
set search_path = public
as $$
declare
  candidate text;
  attempts integer := 0;
begin
  if new.invite_code is not null and length(trim(new.invite_code)) > 0 then
    new.invite_code = trim(new.invite_code);
    return new;
  end if;

  loop
    attempts := attempts + 1;
    candidate := public.generate_invite_code(10);

    if not exists (select 1 from public.rooms r where r.invite_code = candidate) then
      new.invite_code := candidate;
      return new;
    end if;

    if attempts >= 10 then
      raise exception 'could not generate unique invite code after % attempts', attempts;
    end if;
  end loop;
end;
$$;

-- إنشاء profile تلقائيًا عند إنشاء مستخدم Supabase Auth.
-- التعليق بالعربية: هذه الدالة تعمل بصلاحيات definer لأن auth.users في schema مختلف.
create or replace function public.handle_new_auth_user()
returns trigger
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  metadata jsonb;
  app_metadata jsonb;
  chosen_name text;
  chosen_avatar text;
  guest_flag boolean;
begin
  metadata := coalesce(new.raw_user_meta_data, '{}'::jsonb);
  app_metadata := coalesce(new.raw_app_meta_data, '{}'::jsonb);

  chosen_name := coalesce(
    nullif(trim(metadata ->> 'display_name'), ''),
    nullif(trim(metadata ->> 'name'), ''),
    nullif(trim(metadata ->> 'full_name'), ''),
    nullif(trim(split_part(coalesce(new.email, ''), '@', 1)), ''),
    'مستخدم'
  );

  chosen_avatar := nullif(trim(coalesce(metadata ->> 'avatar_url', metadata ->> 'picture')), '');

  guest_flag := coalesce(lower(metadata ->> 'is_guest') in ('true', '1', 'yes', 'on'), false)
    or coalesce(lower(metadata ->> 'guest') in ('true', '1', 'yes', 'on'), false)
    or coalesce((app_metadata ->> 'provider') = 'anonymous', false)
    or coalesce(lower(app_metadata ->> 'is_anonymous') in ('true', '1', 'yes', 'on'), false);

  insert into public.profiles (id, display_name, avatar_url, is_guest)
  values (new.id, left(chosen_name, 80), chosen_avatar, guest_flag)
  on conflict (id) do update
    set display_name = excluded.display_name,
        avatar_url = excluded.avatar_url,
        is_guest = public.profiles.is_guest or excluded.is_guest,
        updated_at = now();

  return new;
end;
$$;

-- -----------------------------------------------------------------------------
-- 2) الجداول
-- -----------------------------------------------------------------------------

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null check (char_length(trim(display_name)) between 1 and 80),
  avatar_url text,
  is_guest boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),

  constraint profiles_avatar_url_length check (avatar_url is null or char_length(avatar_url) <= 2048),
  constraint profiles_display_name_no_control_chars check (display_name !~ '[[:cntrl:]]')
);

create table if not exists public.rooms (
  id uuid primary key default gen_random_uuid(),
  invite_code text not null unique,
  name text not null check (char_length(trim(name)) between 3 and 100),
  host_id uuid not null references public.profiles(id) on delete restrict,
  is_public boolean not null default true,
  password_hash text,
  control_mode text not null default 'HOST_ONLY',
  current_video_url text,
  current_video_type text,
  playback_status text not null default 'PAUSED',
  playback_position_seconds numeric(12, 3) not null default 0,
  playback_updated_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  closed_at timestamptz,

  constraint rooms_invite_code_format check (invite_code ~ '^[A-Za-z0-9_-]{6,32}$'),
  constraint rooms_name_no_control_chars check (name !~ '[[:cntrl:]]'),
  constraint rooms_password_hash_length check (password_hash is null or char_length(password_hash) between 20 and 255),
  constraint rooms_control_mode_check check (control_mode in ('HOST_ONLY', 'MEMBERS_WITH_PERMISSION', 'EVERYONE')),
  constraint rooms_current_video_type_check check (current_video_type is null or current_video_type in ('YOUTUBE', 'MP4')),
  constraint rooms_playback_status_check check (playback_status in ('PLAYING', 'PAUSED')),
  constraint rooms_playback_position_non_negative check (playback_position_seconds >= 0),
  constraint rooms_current_video_url_length check (current_video_url is null or char_length(current_video_url) <= 2048),
  constraint rooms_closed_after_created check (closed_at is null or closed_at >= created_at)
);

create table if not exists public.room_members (
  id uuid primary key default gen_random_uuid(),
  room_id uuid not null references public.rooms(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  role text not null default 'MEMBER',
  can_control boolean not null default false,
  is_connected boolean not null default false,
  joined_at timestamptz not null default now(),
  left_at timestamptz,
  last_seen_at timestamptz not null default now(),

  constraint room_members_room_user_unique unique (room_id, user_id),
  constraint room_members_role_check check (role in ('HOST', 'MEMBER')),
  constraint room_members_left_after_joined check (left_at is null or left_at >= joined_at)
);

create table if not exists public.messages (
  id uuid primary key default gen_random_uuid(),
  room_id uuid not null references public.rooms(id) on delete cascade,
  sender_id uuid not null references public.profiles(id) on delete cascade,
  message_type text not null default 'CHAT',
  content text not null,
  created_at timestamptz not null default now(),
  deleted_at timestamptz,

  constraint messages_type_check check (message_type in ('CHAT', 'EMOJI', 'SYSTEM')),
  constraint messages_content_length check (char_length(content) between 1 and 1000),
  constraint messages_deleted_after_created check (deleted_at is null or deleted_at >= created_at)
);

create table if not exists public.playlist_items (
  id uuid primary key default gen_random_uuid(),
  room_id uuid not null references public.rooms(id) on delete cascade,
  added_by uuid not null references public.profiles(id) on delete cascade,
  url text not null,
  video_type text not null,
  title text,
  position integer not null,
  status text not null default 'QUEUED',
  created_at timestamptz not null default now(),
  started_at timestamptz,
  ended_at timestamptz,

  constraint playlist_items_room_position_unique unique (room_id, position),
  constraint playlist_items_video_type_check check (video_type in ('YOUTUBE', 'MP4')),
  constraint playlist_items_status_check check (status in ('QUEUED', 'PLAYING', 'PLAYED', 'SKIPPED')),
  constraint playlist_items_position_positive check (position > 0),
  constraint playlist_items_url_length check (char_length(url) between 1 and 2048),
  constraint playlist_items_title_length check (title is null or char_length(title) <= 200),
  constraint playlist_items_started_after_created check (started_at is null or started_at >= created_at),
  constraint playlist_items_ended_after_started check (ended_at is null or started_at is null or ended_at >= started_at)
);

-- مزامنة عضوية المضيف تلقائيًا مع جدول room_members.
-- التعليق بالعربية: عند إنشاء غرفة أو نقل الإدارة، نضمن أن المضيف عضو بصلاحية تحكم.
create or replace function public.sync_room_host_membership()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if tg_op = 'UPDATE' and old.host_id is distinct from new.host_id then
    update public.room_members
    set role = 'MEMBER',
        last_seen_at = now()
    where room_id = new.id
      and user_id = old.host_id;
  end if;

  insert into public.room_members (room_id, user_id, role, can_control, is_connected, joined_at, left_at, last_seen_at)
  values (new.id, new.host_id, 'HOST', true, false, now(), null, now())
  on conflict (room_id, user_id) do update
    set role = 'HOST',
        can_control = true,
        left_at = null,
        last_seen_at = now();

  return new;
end;
$$;

-- -----------------------------------------------------------------------------
-- 3) Triggers
-- -----------------------------------------------------------------------------

drop trigger if exists set_profiles_updated_at on public.profiles;
create trigger set_profiles_updated_at
before update on public.profiles
for each row execute function public.set_updated_at();

drop trigger if exists set_rooms_updated_at on public.rooms;
create trigger set_rooms_updated_at
before update on public.rooms
for each row execute function public.set_updated_at();

drop trigger if exists set_rooms_invite_code on public.rooms;
create trigger set_rooms_invite_code
before insert on public.rooms
for each row execute function public.set_room_invite_code();

drop trigger if exists sync_rooms_host_membership on public.rooms;
create trigger sync_rooms_host_membership
after insert or update of host_id on public.rooms
for each row execute function public.sync_room_host_membership();

-- Trigger على auth.users لإنشاء profile تلقائيًا عند التسجيل.
drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
after insert on auth.users
for each row execute function public.handle_new_auth_user();

-- -----------------------------------------------------------------------------
-- 4) دوال RLS مساعدة
-- -----------------------------------------------------------------------------
-- التعليق بالعربية: نستخدم SECURITY DEFINER لتجنب recursion في سياسات RLS،
-- خصوصًا عند فحص عضوية room_members من داخل سياسات نفس الجدول.

create or replace function public.is_room_member(target_room_id uuid, target_user_id uuid default auth.uid())
returns boolean
language sql
security definer
stable
set search_path = public
as $$
  select exists (
    select 1
    from public.room_members rm
    where rm.room_id = target_room_id
      and rm.user_id = target_user_id
      and rm.left_at is null
  );
$$;

create or replace function public.is_room_host(target_room_id uuid, target_user_id uuid default auth.uid())
returns boolean
language sql
security definer
stable
set search_path = public
as $$
  select exists (
    select 1
    from public.rooms r
    where r.id = target_room_id
      and r.host_id = target_user_id
      and r.closed_at is null
  );
$$;

create or replace function public.can_control_room(target_room_id uuid, target_user_id uuid default auth.uid())
returns boolean
language sql
security definer
stable
set search_path = public
as $$
  select exists (
    select 1
    from public.rooms r
    left join public.room_members rm
      on rm.room_id = r.id
     and rm.user_id = target_user_id
     and rm.left_at is null
    where r.id = target_room_id
      and r.closed_at is null
      and (
        r.host_id = target_user_id
        or (
          rm.user_id is not null
          and (
            r.control_mode = 'EVERYONE'
            or (r.control_mode = 'MEMBERS_WITH_PERMISSION' and coalesce(rm.can_control, false) = true)
          )
        )
      )
  );
$$;

create or replace function public.same_room_as_profile(target_profile_id uuid, target_user_id uuid default auth.uid())
returns boolean
language sql
security definer
stable
set search_path = public
as $$
  select exists (
    select 1
    from public.room_members me
    join public.room_members other_member
      on other_member.room_id = me.room_id
    where me.user_id = target_user_id
      and me.left_at is null
      and other_member.user_id = target_profile_id
      and other_member.left_at is null
  );
$$;

create or replace function public.can_join_room_without_password(target_room_id uuid)
returns boolean
language sql
security definer
stable
set search_path = public
as $$
  select exists (
    select 1
    from public.rooms r
    where r.id = target_room_id
      and r.closed_at is null
      and r.is_public = true
      and r.password_hash is null
  );
$$;

-- -----------------------------------------------------------------------------
-- 5) فهارس الأداء
-- -----------------------------------------------------------------------------

create index if not exists idx_profiles_display_name on public.profiles using btree (display_name);

create index if not exists idx_rooms_invite_code on public.rooms using btree (invite_code);
create index if not exists idx_rooms_host_id on public.rooms using btree (host_id);
create index if not exists idx_rooms_public_open_created on public.rooms using btree (is_public, closed_at, created_at desc);
create index if not exists idx_rooms_playback_updated_at on public.rooms using btree (playback_updated_at desc);

create index if not exists idx_room_members_room_user on public.room_members using btree (room_id, user_id);
create index if not exists idx_room_members_user_room on public.room_members using btree (user_id, room_id);
create index if not exists idx_room_members_room_connected on public.room_members using btree (room_id, is_connected);
create index if not exists idx_room_members_room_role on public.room_members using btree (room_id, role);

create index if not exists idx_messages_room_created_desc on public.messages using btree (room_id, created_at desc);
create index if not exists idx_messages_sender_created_desc on public.messages using btree (sender_id, created_at desc);

create index if not exists idx_playlist_items_room_position on public.playlist_items using btree (room_id, position);
create index if not exists idx_playlist_items_room_status_position on public.playlist_items using btree (room_id, status, position);
create index if not exists idx_playlist_items_added_by_created on public.playlist_items using btree (added_by, created_at desc);

-- -----------------------------------------------------------------------------
-- 6) تفعيل Row Level Security
-- -----------------------------------------------------------------------------

alter table public.profiles enable row level security;
alter table public.rooms enable row level security;
alter table public.room_members enable row level security;
alter table public.messages enable row level security;
alter table public.playlist_items enable row level security;

-- نجبر RLS حتى على مالك الجدول عند استخدام أدوار غير service_role.
alter table public.profiles force row level security;
alter table public.rooms force row level security;
alter table public.room_members force row level security;
alter table public.messages force row level security;
alter table public.playlist_items force row level security;

-- -----------------------------------------------------------------------------
-- 7) سياسات profiles
-- -----------------------------------------------------------------------------

drop policy if exists "profiles_select_self_or_same_room" on public.profiles;
create policy "profiles_select_self_or_same_room"
on public.profiles
for select
to authenticated
using (
  id = auth.uid()
  or public.same_room_as_profile(id, auth.uid())
);

drop policy if exists "profiles_insert_self" on public.profiles;
create policy "profiles_insert_self"
on public.profiles
for insert
to authenticated
with check (id = auth.uid());

drop policy if exists "profiles_update_self" on public.profiles;
create policy "profiles_update_self"
on public.profiles
for update
to authenticated
using (id = auth.uid())
with check (id = auth.uid());

-- لا توجد سياسة DELETE: حذف الحساب يتم عبر Supabase Auth فقط.

-- -----------------------------------------------------------------------------
-- 8) سياسات rooms
-- -----------------------------------------------------------------------------

drop policy if exists "rooms_select_public_or_member" on public.rooms;
drop policy if exists "rooms_select_member_or_host" on public.rooms;
create policy "rooms_select_member_or_host"
on public.rooms
for select
to authenticated
using (
  closed_at is null
  and (
    host_id = auth.uid()
    or public.is_room_member(id, auth.uid())
  )
);

drop policy if exists "rooms_insert_authenticated_host" on public.rooms;
create policy "rooms_insert_authenticated_host"
on public.rooms
for insert
to authenticated
with check (host_id = auth.uid());

drop policy if exists "rooms_update_host_only" on public.rooms;
create policy "rooms_update_host_only"
on public.rooms
for update
to authenticated
using (public.is_room_host(id, auth.uid()))
with check (
  -- USING يتحقق من أن الصف القديم يخص المضيف الحالي.
  -- WITH CHECK يتحقق من أن المضيف الجديد، إن تغيّر، عضو في نفس الغرفة.
  -- نسمح للمضيف أيضًا بإغلاق الغرفة عبر closed_at.
  host_id = auth.uid()
  or public.is_room_member(id, host_id)
);

-- لا توجد سياسة DELETE: نستخدم closed_at للإغلاق المنطقي بدل الحذف.

-- -----------------------------------------------------------------------------
-- 9) سياسات room_members
-- -----------------------------------------------------------------------------

drop policy if exists "room_members_select_same_room" on public.room_members;
create policy "room_members_select_same_room"
on public.room_members
for select
to authenticated
using (public.is_room_member(room_id, auth.uid()));

drop policy if exists "room_members_insert_self" on public.room_members;
drop policy if exists "room_members_insert_self_public_room" on public.room_members;
create policy "room_members_insert_self_public_room"
on public.room_members
for insert
to authenticated
with check (
  -- الانضمام المباشر عبر Supabase مسموح فقط للغرف العامة بلا كلمة سر.
  -- الغرف المحمية تُعالج عبر Backend حتى يتم التحقق من كلمة السر قبل إدخال العضوية.
  user_id = auth.uid()
  and role = 'MEMBER'
  and can_control = false
  and public.can_join_room_without_password(room_id)
);

drop policy if exists "room_members_update_self_or_host" on public.room_members;
drop policy if exists "room_members_update_host_only" on public.room_members;
create policy "room_members_update_host_only"
on public.room_members
for update
to authenticated
using (public.is_room_host(room_id, auth.uid()))
with check (public.is_room_host(room_id, auth.uid()));

drop policy if exists "room_members_delete_host_only" on public.room_members;
create policy "room_members_delete_host_only"
on public.room_members
for delete
to authenticated
using (public.is_room_host(room_id, auth.uid()));

-- -----------------------------------------------------------------------------
-- 10) سياسات messages
-- -----------------------------------------------------------------------------

drop policy if exists "messages_select_room_members" on public.messages;
create policy "messages_select_room_members"
on public.messages
for select
to authenticated
using (public.is_room_member(room_id, auth.uid()));

drop policy if exists "messages_insert_room_members_self" on public.messages;
create policy "messages_insert_room_members_self"
on public.messages
for insert
to authenticated
with check (
  sender_id = auth.uid()
  and public.is_room_member(room_id, auth.uid())
  and message_type in ('CHAT', 'EMOJI')
);

drop policy if exists "messages_update_host_soft_delete" on public.messages;
create policy "messages_update_host_soft_delete"
on public.messages
for update
to authenticated
using (public.is_room_host(room_id, auth.uid()))
with check (public.is_room_host(room_id, auth.uid()));

-- لا توجد سياسة DELETE مباشرة؛ يمكن للمضيف تنفيذ حذف منطقي عبر deleted_at.

-- -----------------------------------------------------------------------------
-- 11) سياسات playlist_items
-- -----------------------------------------------------------------------------

drop policy if exists "playlist_select_room_members" on public.playlist_items;
create policy "playlist_select_room_members"
on public.playlist_items
for select
to authenticated
using (public.is_room_member(room_id, auth.uid()));

drop policy if exists "playlist_insert_controllers" on public.playlist_items;
create policy "playlist_insert_controllers"
on public.playlist_items
for insert
to authenticated
with check (
  added_by = auth.uid()
  and public.is_room_member(room_id, auth.uid())
  and public.can_control_room(room_id, auth.uid())
);

drop policy if exists "playlist_update_controllers" on public.playlist_items;
create policy "playlist_update_controllers"
on public.playlist_items
for update
to authenticated
using (public.can_control_room(room_id, auth.uid()))
with check (public.can_control_room(room_id, auth.uid()));

drop policy if exists "playlist_delete_host_only" on public.playlist_items;
create policy "playlist_delete_host_only"
on public.playlist_items
for delete
to authenticated
using (public.is_room_host(room_id, auth.uid()));

-- -----------------------------------------------------------------------------
-- 12) صلاحيات الوصول الأساسية لأدوار Supabase
-- -----------------------------------------------------------------------------
-- RLS يحدد ما يمكن الوصول إليه، وهذه grants تسمح للتطبيق باستخدام الجداول عبر Supabase.

grant usage on schema public to anon, authenticated;

grant select, insert, update on public.profiles to authenticated;
grant select, insert, update on public.rooms to authenticated;
grant select, insert, update, delete on public.room_members to authenticated;
grant select, insert, update on public.messages to authenticated;
grant select, insert, update, delete on public.playlist_items to authenticated;

grant execute on function public.is_room_member(uuid, uuid) to authenticated;
grant execute on function public.is_room_host(uuid, uuid) to authenticated;
grant execute on function public.can_control_room(uuid, uuid) to authenticated;
grant execute on function public.same_room_as_profile(uuid, uuid) to authenticated;
grant execute on function public.can_join_room_without_password(uuid) to authenticated;

commit;

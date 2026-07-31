-- Kefe - Supabase senkron semasi (adim 7: tablolar + RLS)
-- =========================================================================
--
-- Bu dosya Supabase SQL editorunde CALISTIRILIR. Tekrar calistirilabilir:
-- tablolar "if not exists", politikalar once "drop ... if exists" ile.
--
-- GUVENLIK MODELI. Tek Supabase hesabi, iki cihaz ayni e-postayla girer -
-- yani ayni auth.uid(). Her satir user_id ile sahibine baglanir; user_id
-- varsayilani auth.uid() oldugu icin istemci onu gondermez bile, PostgREST
-- doldurur. RLS "auth.uid() = user_id" hem okumayi hem yazmayi o hesaba
-- kilitler: anahtar sizsa bile RLS olmadan tek satir okunmaz. Tablo acilmadan
-- once politikasi yazilir - bu dosya ikisini birlikte kurar.
--
-- NE SENKRONLANIR. Yalniz kullanicinin girdigi GERCEK veri ve turetilemeyen
-- gecmis. Cihaz-yerel olan (jeton, ayarlar, canli fiyat onbellegi) ve
-- islemlerden yeniden hesaplanabilen (pozisyonun miktar/maliyet/degeri, canli
-- fiyat) sunucuya YAZILMAZ - ikinci telefon onlari kendi defterinden uretir.
--
-- ESITLEME ALANLARI. updated_at: cakismada son yazan kazanir (epoch ms).
-- deleted_at: mezar tasi - silme bir yazmadir, satir yerinde kalir. Hard delete
-- cogaltilamazdi: es cevrimdisiyken silinen kayit, o dondugunde geri dirilirdi.
--
-- Tablolar arasi yabanci anahtar YOK (position_id, goal_id, member_id duz
-- metin): push sirasi serbest kalsin, referans butunlugunu uygulama tutar.
-- Tek FK user_id -> auth.users: hesap silinince satirlar da temizlensin.

-- =========================================================================
-- 1. members - iki profil (Burak Can / Merve). Kimlikler SABIT
--    (member_owner / member_partner), iki cihazda ayni satira duser.
--    role/permission/lastSeen cok kullanicili modelden kalmaydi, tasinmaz.
--    Mezar tasi yok: iki profil silinmez.
-- =========================================================================
create table if not exists public.members (
    id         text  primary key,
    user_id    uuid  not null default auth.uid() references auth.users (id) on delete cascade,
    name       text  not null,
    initials   text  not null,
    sort_order bigint not null default 0,
    updated_at bigint not null default 0
);

alter table public.members enable row level security;
drop policy if exists members_own on public.members;
create policy members_own on public.members
    for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

create index if not exists members_user_updated on public.members (user_id, updated_at);
grant select, insert, update, delete on public.members to authenticated;

-- =========================================================================
-- 2. positions - varlik satirinin META'si. Miktar/maliyet/deger ve canli fiyat
--    YOK: onlar defterden ve cihazin kendi fiyat cekiminden turetilir.
--    unit_price + manual_price tasinir cunku ELLE fiyatli varlikta bu
--    kullanici verisidir, hicbir kaynaktan gelmez.
-- =========================================================================
create table if not exists public.positions (
    id           text   primary key,
    user_id      uuid   not null default auth.uid() references auth.users (id) on delete cascade,
    name         text   not null,
    asset_class  text   not null,
    subtype      text,
    karat        text,
    unit         text   not null,
    unit_price   double precision not null default 0,
    manual_price boolean not null default false,
    updated_at   bigint not null default 0,
    deleted_at   bigint
);

alter table public.positions enable row level security;
drop policy if exists positions_own on public.positions;
create policy positions_own on public.positions
    for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

create index if not exists positions_user_updated on public.positions (user_id, updated_at);
grant select, insert, update, delete on public.positions to authenticated;

-- =========================================================================
-- 3. transactions - islem defteri, uygulamanin tek gercek kaynagi. sync_state
--    tasinmaz: o bu cihazin "gonderdim mi" isareti, paylasilan veri degil.
-- =========================================================================
create table if not exists public.transactions (
    id                 text   primary key,
    user_id            uuid   not null default auth.uid() references auth.users (id) on delete cascade,
    position_id        text   not null,
    date_year          bigint not null,
    date_month         bigint not null,
    date_day           bigint not null,
    side               text   not null,
    quantity           double precision not null,
    unit_price         double precision not null,
    fee                double precision not null default 0,
    note               text,
    storage            text,
    added_by_member_id text   not null,
    updated_at         bigint not null default 0,
    deleted_at         bigint
);

alter table public.transactions enable row level security;
drop policy if exists transactions_own on public.transactions;
create policy transactions_own on public.transactions
    for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

create index if not exists transactions_user_updated on public.transactions (user_id, updated_at);
grant select, insert, update, delete on public.transactions to authenticated;

-- =========================================================================
-- 4. goals - hedefler. estimated_* tasinmaz: tutar/katki/fiyattan hesaplanan
--    tahmin, cihaz kendi uretir.
-- =========================================================================
create table if not exists public.goals (
    id                   text   primary key,
    user_id              uuid   not null default auth.uid() references auth.users (id) on delete cascade,
    name                 text   not null,
    icon_key             text   not null,
    amount               double precision not null,
    unit                 text   not null,
    target_year          bigint not null,
    target_month         bigint not null,
    target_day           bigint not null,
    monthly_contribution double precision not null,
    is_main              boolean not null default false,
    allocation           text   not null default 'AllWealth',
    status               text   not null default 'Active',
    sort_order           bigint not null default 0,
    updated_at           bigint not null default 0,
    deleted_at           bigint
);

alter table public.goals enable row level security;
drop policy if exists goals_own on public.goals;
create policy goals_own on public.goals
    for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

create index if not exists goals_user_updated on public.goals (user_id, updated_at);
grant select, insert, update, delete on public.goals to authenticated;

-- =========================================================================
-- 5. goal_assets - varlik -> hedef atamasi. position_id anahtar: bir varlik en
--    fazla bir hedefe.
-- =========================================================================
-- quantity: atanan miktar; -1 = TUM VARLIK. Sonradan eklendi (yerelde 5.sqm),
-- o yuzden ayri bir "add column if not exists" ile gelir: tablo zaten
-- olusturulmus hesaplarda create table if not exists hicbir sey yapmaz ve
-- kolon eksik kalirdi - push 400 doner, senkron sessizce durur.
create table if not exists public.goal_assets (
    position_id text   primary key,
    user_id     uuid   not null default auth.uid() references auth.users (id) on delete cascade,
    goal_id     text   not null,
    quantity    double precision not null default -1,
    updated_at  bigint not null default 0,
    deleted_at  bigint
);

alter table public.goal_assets
    add column if not exists quantity double precision not null default -1;

alter table public.goal_assets enable row level security;
drop policy if exists goal_assets_own on public.goal_assets;
create policy goal_assets_own on public.goal_assets
    for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

create index if not exists goal_assets_user_updated on public.goal_assets (user_id, updated_at);
grant select, insert, update, delete on public.goal_assets to authenticated;

-- =========================================================================
-- 6. daily_snapshots - gunluk net deger fotograflari (gecmis grafigi). Gecmis
--    fiyatlar hicbir yerde tutulmadigi icin turetilemez: ikinci telefon bunlar
--    olmadan gecmis grafigi cizemez. Anahtar dogal: (kullanici, tarih). Mezar
--    tasi yok - fotograf silinmez, gun icinde tazelenir.
-- =========================================================================
create table if not exists public.daily_snapshots (
    user_id     uuid   not null default auth.uid() references auth.users (id) on delete cascade,
    date_year   bigint not null,
    date_month  bigint not null,
    date_day    bigint not null,
    total_value double precision not null,
    principal   double precision not null,
    updated_at  bigint not null default 0,
    primary key (user_id, date_year, date_month, date_day)
);

alter table public.daily_snapshots enable row level security;
drop policy if exists daily_snapshots_own on public.daily_snapshots;
create policy daily_snapshots_own on public.daily_snapshots
    for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

create index if not exists daily_snapshots_user_updated on public.daily_snapshots (user_id, updated_at);
grant select, insert, update, delete on public.daily_snapshots to authenticated;

-- =========================================================================
-- 7. activity_events - "kim ne ekledi" akisi. seq tasinmaz: o cihaza ozgu artan
--    sayac; sira sunucuda occurred tarihine gore kurulur, cihaz ceker.
--    Akisin bir kismi (elle fiyat, hedef guncelleme) defterde iz birakmaz -
--    bu yuzden turetmek yetmez, saklanir.
-- =========================================================================
create table if not exists public.activity_events (
    id              text   primary key,
    user_id         uuid   not null default auth.uid() references auth.users (id) on delete cascade,
    member_id       text   not null,
    kind            text   not null,
    description     text   not null,
    amount          double precision,
    is_manual_price boolean not null default false,
    occurred_year   bigint not null,
    occurred_month  bigint not null,
    occurred_day    bigint not null,
    time_label      text,
    updated_at      bigint not null default 0,
    deleted_at      bigint
);

alter table public.activity_events enable row level security;
drop policy if exists activity_events_own on public.activity_events;
create policy activity_events_own on public.activity_events
    for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

create index if not exists activity_events_user_updated on public.activity_events (user_id, updated_at);
grant select, insert, update, delete on public.activity_events to authenticated;

-- =========================================================================
-- LWW KORUMA (adim 10, pull ile geldi). Push, PostgREST upsert'iyle satiri
-- KOSULSUZ ezer - son push kazanir, zaman damgasina bakmaz. Bos ya da eski bir
-- cihazin push'u sunucudaki yeni veriyi ezerdi. Bu trigger gelen satir
-- sunucudakinden ESKI/esitse guncellemeyi yok sayar; boylece cakisma cozumu hem
-- push'ta hem pull'da AYNI olur: updated_at buyuk olan kazanir. INSERT'e gerek
-- yok - yeni satirda OLD yoktur.
-- =========================================================================
create or replace function public.kefe_lww_guard() returns trigger as $$
begin
    if NEW.updated_at <= OLD.updated_at then
        return OLD;   -- gelen daha eski/esit: sunucudakini koru
    end if;
    return NEW;
end;
$$ language plpgsql;

drop trigger if exists members_lww on public.members;
create trigger members_lww before update on public.members
    for each row execute function public.kefe_lww_guard();

drop trigger if exists positions_lww on public.positions;
create trigger positions_lww before update on public.positions
    for each row execute function public.kefe_lww_guard();

drop trigger if exists transactions_lww on public.transactions;
create trigger transactions_lww before update on public.transactions
    for each row execute function public.kefe_lww_guard();

drop trigger if exists goals_lww on public.goals;
create trigger goals_lww before update on public.goals
    for each row execute function public.kefe_lww_guard();

drop trigger if exists goal_assets_lww on public.goal_assets;
create trigger goal_assets_lww before update on public.goal_assets
    for each row execute function public.kefe_lww_guard();

drop trigger if exists daily_snapshots_lww on public.daily_snapshots;
create trigger daily_snapshots_lww before update on public.daily_snapshots
    for each row execute function public.kefe_lww_guard();

drop trigger if exists activity_events_lww on public.activity_events;
create trigger activity_events_lww before update on public.activity_events
    for each row execute function public.kefe_lww_guard();

-- =========================================================================
-- GERCEK ZAMANLI (adim 11). Realtime, degisiklikleri mantiksal cogaltmadan
-- okur: bir tablo "supabase_realtime" yayinina EKLENMEDEN o tablonun olaylari
-- hic akmaz. Istemci baglanir, join basarili doner ve hicbir sey gelmez -
-- sessiz basarisizlik; bir sorun yasanirsa ilk bakilacak yer burasi.
--
-- REPLICA IDENTITY FULL KONMADI: gelen olayin icindeki satiri okumuyoruz, olay
-- yalnizca "degisti" sinyali (uygulamayi pull yapar). Birincil anahtar yeter;
-- FULL her guncellemede eski satirin tamamini WAL'a yazdirirdi.
--
-- Dongu idempotent: zaten ekli tabloyu yeniden eklemek hata verirdi.
-- =========================================================================
do $$
declare t text;
begin
    foreach t in array array['members', 'positions', 'transactions', 'goals',
                             'goal_assets', 'daily_snapshots', 'activity_events'] loop
        if not exists (
            select 1 from pg_publication_tables
            where pubname = 'supabase_realtime'
              and schemaname = 'public'
              and tablename = t
        ) then
            execute format('alter publication supabase_realtime add table public.%I', t);
        end if;
    end loop;
end $$;

-- =========================================================================
-- Bildirimler (adim 12) BU DOSYADA YOK - kendi adiminda eklenir.
-- =========================================================================

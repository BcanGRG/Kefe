-- Kefe - aylik plan, gelir, gider ve butce tablolari (esitleme)
-- =========================================================================
--
-- NEYDI. Yerelde 12.sqm ile gelen dort tablo (plan_items, income_entries,
-- expense_entries, expense_budgets) yalniz cihazda yasiyordu: ikinci telefon
-- plani gormuyor, "Bu cihazı sıfırla" onlari geri gelmemek uzere siliyordu,
-- "Hesaptakileri kullan" da onlara dokunmuyordu.
--
-- NEDEN BOYLE. Oteki tablolarin kaliplari aynen:
--   - Kolonlar istemcinin DTO'lariyla (SyncDtos.kt) birebir, snake_case.
--   - ANAHTAR (user_id, id): plan satiri, gelir ve butce kimlikleri ICERIKTEN
--     turer (pi_2026_10_gold_gram, inc_2026_10_member_owner_Salary,
--     eb_2026_10_Groceries) ve her HESAPTA ayni cikar. Yalniz id anahtar
--     olsaydi ikinci bir hesabin upsert'i birincinin satiriyla cakisip RLS'e
--     takilirdi (bkz. 20260926_composite_keys.sql). Harcama kimligi UUID;
--     ayni bicim tutarlilik icin.
--   - mode / kind / category DUZ METIN, CHECK YOK: daha yeni bir surumun
--     ekledigi bir kategori eski bir telefonun push'unu reddettirmemeli.
--     Esleme istemcide, savunmaci.
--   - Tablolar arasi FK yok (goal_id, member_id duz metin); tek FK user_id.
--   - updated_at: son yazan kazanir (epoch ms); deleted_at: mezar tasi.
--
-- TEKRAR CALISTIRILABILIR: tablolar "if not exists", politikalar ve
-- tetikleyiciler once "drop ... if exists", yayina ekleme yalniz uye degilse.
-- kefe_lww_guard() BURADA YENIDEN TANIMLANMAZ: canlidaki tanima (search_path
-- dahil) dokunulmasin; schema.sql'de tanimli, yalniz kullanilir.
--
-- SIRA: bu dosya istemcinin plan esitlemesi yayina cikmadan ONCE uygulanir.
-- Tablolar yokken yeni istemcinin push'u ve pull'u 404 ile patlar, cip
-- "Eşitlenemiyor"a duser; yayinda olmayan tabloyu isteyen realtime aboneligi
-- de reddedilir.
-- =========================================================================

-- -------------------------------------------------------------------------
-- 8. plan_items - aylik yatirim plani. Yalniz NIYET: "Ekim'de 10 gr gram
--    altin". Yapilip yapilmadigi islem defterinden turetilir, tasinmaz.
-- -------------------------------------------------------------------------
create table if not exists public.plan_items (
    id                 text   not null,
    user_id            uuid   not null default auth.uid() references auth.users (id) on delete cascade,
    period_year        bigint not null,
    period_month       bigint not null,
    asset_key          text   not null,
    asset_name         text   not null,
    mode               text   not null,
    target             double precision not null,
    goal_id            text,
    unit_price_at_plan double precision,
    updated_at         bigint not null default 0,
    deleted_at         bigint,
    primary key (user_id, id)
);

alter table public.plan_items enable row level security;
drop policy if exists plan_items_own on public.plan_items;
create policy plan_items_own on public.plan_items
    for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

create index if not exists plan_items_user_updated on public.plan_items (user_id, updated_at);
grant select, insert, update, delete on public.plan_items to authenticated;

-- -------------------------------------------------------------------------
-- 9. income_entries - aylik gelir, KISI bazli (maas kimin). member_id duz
--    metin (member_owner / member_partner).
-- -------------------------------------------------------------------------
create table if not exists public.income_entries (
    id           text   not null,
    user_id      uuid   not null default auth.uid() references auth.users (id) on delete cascade,
    period_year  bigint not null,
    period_month bigint not null,
    member_id    text   not null,
    kind         text   not null,
    amount       double precision not null,
    updated_at   bigint not null default 0,
    deleted_at   bigint,
    primary key (user_id, id)
);

alter table public.income_entries enable row level security;
drop policy if exists income_entries_own on public.income_entries;
create policy income_entries_own on public.income_entries
    for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

create index if not exists income_entries_user_updated on public.income_entries (user_id, updated_at);
grant select, insert, update, delete on public.income_entries to authenticated;

-- -------------------------------------------------------------------------
-- 10. expense_entries - tek tek harcamalar, HANENIN. Kimlik UUID: ayni gun
--     ayni tutarda iki market alisverisi iki satirdir. created_at ayni gun
--     icindeki sira (islemlerdeki gibi).
-- -------------------------------------------------------------------------
create table if not exists public.expense_entries (
    id                 text   not null,
    user_id            uuid   not null default auth.uid() references auth.users (id) on delete cascade,
    date_year          bigint not null,
    date_month         bigint not null,
    date_day           bigint not null,
    category           text   not null,
    amount             double precision not null,
    note               text,
    added_by_member_id text,
    created_at         bigint not null default 0,
    updated_at         bigint not null default 0,
    deleted_at         bigint,
    primary key (user_id, id)
);

alter table public.expense_entries enable row level security;
drop policy if exists expense_entries_own on public.expense_entries;
create policy expense_entries_own on public.expense_entries
    for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

create index if not exists expense_entries_user_updated on public.expense_entries (user_id, updated_at);
grant select, insert, update, delete on public.expense_entries to authenticated;

-- -------------------------------------------------------------------------
-- 11. expense_budgets - ay basina kategori butcesi, HANENIN. "Ekim'in market
--     butcesi" tektir: kimlik icerikten (eb_<yyyy>_<mm>_<kategori>).
-- -------------------------------------------------------------------------
create table if not exists public.expense_budgets (
    id           text   not null,
    user_id      uuid   not null default auth.uid() references auth.users (id) on delete cascade,
    period_year  bigint not null,
    period_month bigint not null,
    category     text   not null,
    amount       double precision not null,
    updated_at   bigint not null default 0,
    deleted_at   bigint,
    primary key (user_id, id)
);

alter table public.expense_budgets enable row level security;
drop policy if exists expense_budgets_own on public.expense_budgets;
create policy expense_budgets_own on public.expense_budgets
    for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

create index if not exists expense_budgets_user_updated on public.expense_budgets (user_id, updated_at);
grant select, insert, update, delete on public.expense_budgets to authenticated;

-- -------------------------------------------------------------------------
-- LWW KORUMA. Push'un upsert'i satiri kosulsuz ezerdi; tetikleyici gelen
-- satir sunucudakinden eski/esitse guncellemeyi yok sayar (bkz. schema.sql).
-- -------------------------------------------------------------------------
drop trigger if exists plan_items_lww on public.plan_items;
create trigger plan_items_lww before update on public.plan_items
    for each row execute function public.kefe_lww_guard();

drop trigger if exists income_entries_lww on public.income_entries;
create trigger income_entries_lww before update on public.income_entries
    for each row execute function public.kefe_lww_guard();

drop trigger if exists expense_entries_lww on public.expense_entries;
create trigger expense_entries_lww before update on public.expense_entries
    for each row execute function public.kefe_lww_guard();

drop trigger if exists expense_budgets_lww on public.expense_budgets;
create trigger expense_budgets_lww before update on public.expense_budgets
    for each row execute function public.kefe_lww_guard();

-- -------------------------------------------------------------------------
-- GERCEK ZAMANLI. Yayina eklenmeyen tablonun olaylari hic akmaz ve istemci
-- onu istedigi icin abonelik reddedilir (bkz. RealtimeApi.RealtimeTables).
-- Zaten uye olan tablo yeniden eklenmez: ekleme hata verirdi.
-- -------------------------------------------------------------------------
do $$
declare t text;
begin
    foreach t in array array['plan_items', 'income_entries', 'expense_entries', 'expense_budgets'] loop
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

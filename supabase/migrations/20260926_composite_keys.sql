-- Kefe - bilesik birincil anahtarlar (members, positions, goal_assets)
-- =========================================================================
--
-- NEYDI. Bu uc tablonun anahtari yalniz satir kimligiydi (id / position_id).
-- Istemci bu kimlikleri HER CIHAZDA AYNI uretir: member_owner, member_partner,
-- pos_<varlik>. Ikinci bir hesap (esin kendi e-postasiyla actigi hesap dahil)
-- member_owner'i upsert edince PostgREST'in merge-duplicates'i birincil
-- anahtardan BIRINCI hesabin satiriyla cakisiyor, ON CONFLICT DO UPDATE o
-- satiri RLS'in "auth.uid() = user_id" kosulunu gecemedigi icin reddediyordu.
-- Sonuc: ikinci hesabin her push'u patliyor, cip sonsuza dek "Eşitlenemiyor".
--
-- COZUM. Anahtar (user_id, kimlik) olur: her hesap kendi member_owner'ina,
-- kendi pos_gold_quarter'ina sahip. Istemci degismez - her satir user_id'yi
-- zaten acikca gonderiyor (bkz. SyncDtos.kt), cakisma hedefi tam anahtar.
--
-- UUID kimlikli tablolar (transactions, goals, activity_events) degismez:
-- rastgele kimlikler hesaplar arasinda cakismaz.
--
-- TEKRAR CALISTIRILABILIR: anahtar zaten user_id iceriyorsa tabloya dokunulmaz.
-- Kisitin adi varsayilmaz (members_pkey olmayabilir), katalogdan okunur.
--
-- CANLI DDL: veri tasiyan tablolarda anahtar degisimi. Once yedek alin. Mevcut
-- satirlar yeni anahtarla cakismaz - eski anahtar zaten daha dardi.
-- =========================================================================

do $$
declare
    target record;
    pkey_name text;
    has_user_id boolean;
begin
    for target in
        select * from (values
            ('members',     'id'),
            ('positions',   'id'),
            ('goal_assets', 'position_id')
        ) as t(tbl, key_col)
    loop
        select c.conname,
               exists (
                   select 1
                   from unnest(c.conkey) as k(attnum)
                   join pg_attribute a
                     on a.attrelid = c.conrelid and a.attnum = k.attnum
                   where a.attname = 'user_id'
               )
          into pkey_name, has_user_id
          from pg_constraint c
         where c.conrelid = format('public.%I', target.tbl)::regclass
           and c.contype = 'p';

        if pkey_name is null then
            execute format(
                'alter table public.%I add primary key (user_id, %I)',
                target.tbl, target.key_col
            );
        elsif not has_user_id then
            execute format(
                'alter table public.%I drop constraint %I, add primary key (user_id, %I)',
                target.tbl, pkey_name, target.key_col
            );
        end if;
    end loop;
end $$;

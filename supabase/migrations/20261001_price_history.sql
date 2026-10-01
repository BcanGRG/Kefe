-- Kefe - hesap uzerinden paylasilan gunluk fiyat gecmisi
-- =========================================================================
--
-- NEYDI. Haftalik ve aylik degisim, cihazdaki price_history tablosundan 7 ve
-- 30 gun onceki fiyati arar. O tablo yalniz uygulamanin ACILDIGI gunleri
-- tutuyordu ve cihazlar arasinda paylasilmiyordu: kullanici telefon
-- degistirince 25 Agu - 7 Eyl arasi bosluk kaldi, "Ay" butun altin ve dovizde
-- "—" gorundu; iki telefon ayni varlik icin farkli degisim gosterebiliyordu.
--
-- NEDEN BOYLE. Her telefon gordugu gunluk fiyati buraya yazar; oteki telefon
-- gunde bir kez ceker ve YALNIZ KENDINDE OLMAYAN gunleri doldurur (kendi
-- gozlemini ezmez). Esitleme motorundan AYRI bir yol: satirlar kullanicinin
-- kaydi degil, gozlem; mezar tasi, LWW ve "Hesaptakileri kullan" gerekmez.
--
--   - ANAHTAR (user_id, asset_key, day): ayni gun ikinci yazim birlestirir.
--   - Gercek zamanli yayin YOK: fiyat gecmisi aninda gerekmez, gunluk cekim yeter.
--   - Tek FK user_id; asset_key duz metin (yeni bir varlik turu eski bir
--     telefonun yazimini reddettirmemeli).
--
-- TEKRAR CALISTIRILABILIR.
-- =========================================================================

create table if not exists public.price_history (
    user_id    uuid             not null default auth.uid() references auth.users (id) on delete cascade,
    asset_key  text             not null,
    day        date             not null,
    price      double precision not null,
    primary key (user_id, asset_key, day)
);

alter table public.price_history enable row level security;
drop policy if exists price_history_own on public.price_history;
create policy price_history_own on public.price_history
    for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

create index if not exists price_history_user_day on public.price_history (user_id, day);
grant select, insert, update on public.price_history to authenticated;

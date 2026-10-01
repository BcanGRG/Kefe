-- Kefe - kura bagli hedef ve harcanan hedef (goals'a uc kolon)
-- =========================================================================
--
-- NEYDI. Hedef tutari HER ZAMAN TL idi; "€3.000" o gunun kuruyla TL'ye
-- cevrilip donuyordu. Yurtdisi tatili gibi dovizle odenecek hedef euro
-- yukselince gercek maliyetinin altinda kaliyordu. Hedefe ayrilan para
-- harcandiginda da bunu soyleyecek bir alan yoktu.
--
-- NEDEN YENI KOLON. Istemcide unit ve status enum olarak okunuyor; eski bir
-- surum bilinmeyen degerde ("Eur", "Spent") cokerdi. Yeni bilgi ayri kolonlarda;
-- eski kolonlar eski telefonun tanidigi degerleri tasir (bkz. istemcide 13.sqm).
--   anchor_unit   - "Eur" | "Usd" | "GoldGram"; null = TL sabit
--   anchor_amount - tutar o birimde
--   spent_at      - hedefin parasi harcandi (epoch ms)
--
-- Eski telefonun push'u bu kolonlari gondermez; merge-duplicates yalniz gelen
-- kolonlari yazdigi icin sunucudaki deger korunur.
--
-- SIRA: bu dosya yeni istemciden ONCE uygulanir (1 Eki 2026'da uygulandi).
-- TEKRAR CALISTIRILABILIR.
-- =========================================================================

alter table public.goals add column if not exists anchor_unit text;
alter table public.goals add column if not exists anchor_amount double precision;
alter table public.goals add column if not exists spent_at bigint;

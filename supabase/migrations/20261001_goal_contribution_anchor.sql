-- Kefe - aylik katki hedefin biriminde (goals.contribution_anchor)
-- =========================================================================
-- Hedef euro olabiliyordu (20261001_goal_anchor.sql) ama aylik katki hep TL idi.
-- contribution_anchor: aylik katki hedefin biriminde (250 = €250/ay); null = TL.
-- Eski telefonun push'u bu kolonu gondermez; sunucudaki deger korunur.
-- SIRA: yeni istemciden ONCE (1 Eki 2026'da uygulandi). TEKRAR CALISTIRILABILIR.
-- =========================================================================

alter table public.goals add column if not exists contribution_anchor double precision;

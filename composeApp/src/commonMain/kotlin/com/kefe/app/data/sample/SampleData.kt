package com.kefe.app.data.sample

import com.kefe.app.domain.model.ActivityEvent
import com.kefe.app.domain.model.AllocationSlice
import com.kefe.app.domain.model.ActivityKind
import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.Goal
import com.kefe.app.domain.model.GoalAllocation
import com.kefe.app.domain.model.GoalStatus
import com.kefe.app.domain.model.GoalUnit
import com.kefe.app.domain.model.GoldSubtype
import com.kefe.app.domain.model.Karat
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.Member
import com.kefe.app.domain.model.MemberPermission
import com.kefe.app.domain.model.MemberRole
import com.kefe.app.domain.model.Portfolio
import com.kefe.app.domain.model.PortfolioTotals
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.Price
import com.kefe.app.domain.model.PriceSource
import com.kefe.app.domain.model.QuantityUnit
import com.kefe.app.domain.model.SyncState
import com.kefe.app.domain.model.TradeSide
import com.kefe.app.domain.model.Transaction

/** Tasarim teslimindeki ornek portfoy. Tum ekranlar bu veriyle beslenir. */
object SampleData {

    const val MemberVolkanId: String = "m_volkan"
    const val MemberAyseId: String = "m_ayse"

    val members: List<Member> = listOf(
        Member(
            id = MemberVolkanId,
            name = "Volkan",
            initials = "VK",
            role = MemberRole.Owner,
            permission = MemberPermission.CanEdit,
            lastSeen = "şu anda çevrimiçi",
        ),
        Member(
            id = MemberAyseId,
            name = "Ayşe",
            initials = "AY",
            role = MemberRole.Member,
            permission = MemberPermission.CanEdit,
            lastSeen = "2 saat önce görüldü",
        ),
    )

    val portfolio: Portfolio = Portfolio(
        id = "p_ortak",
        name = "Ortak Birikim",
        currency = "TRY",
        memberIds = listOf(MemberVolkanId, MemberAyseId),
    )

    fun memberOf(id: String): Member? = members.firstOrNull { it.id == id }

    // --- Pozisyonlar -------------------------------------------------------

    const val PositionQuarterGoldId: String = "pos_ceyrek"

    val positions: List<Position> = listOf(
        Position(
            id = "pos_bilezik22",
            name = "22 Ayar Bilezik",
            assetClass = AssetClass.Gold,
            subtype = GoldSubtype.Jewelry,
            karat = Karat.K22,
            quantity = 62.4,
            unit = QuantityUnit.Gram,
            unitPrice = 15308.0,
            value = 955200.0,
            cost = 806000.0,
            dailyChangePercent = 0.41,
        ),
        Position(
            id = PositionQuarterGoldId,
            name = "Çeyrek Altın",
            assetClass = AssetClass.Gold,
            subtype = GoldSubtype.Quarter,
            quantity = 8.0,
            unit = QuantityUnit.Piece,
            unitPrice = 26500.0,
            value = 212000.0,
            cost = 167500.0,
            dailyChangePercent = 0.42,
        ),
        Position(
            id = "pos_tam",
            name = "Tam Altın",
            assetClass = AssetClass.Gold,
            subtype = GoldSubtype.Full,
            quantity = 2.0,
            unit = QuantityUnit.Piece,
            unitPrice = 106000.0,
            value = 212000.0,
            cost = 171000.0,
            dailyChangePercent = 0.38,
        ),
        Position(
            id = "pos_taki14",
            name = "14 Ayar Takı",
            assetClass = AssetClass.Gold,
            subtype = GoldSubtype.Jewelry,
            karat = Karat.K14,
            quantity = 15.0,
            unit = QuantityUnit.Gram,
            unitPrice = 9720.0,
            value = 145800.0,
            cost = 118500.0,
            dailyChangePercent = 0.40,
        ),
        Position(
            id = "pos_anneanne",
            name = "Anneannemin Bileziği",
            assetClass = AssetClass.Gold,
            subtype = GoldSubtype.Jewelry,
            karat = Karat.K22,
            quantity = 48.0,
            unit = QuantityUnit.Gram,
            unitPrice = 6667.0,
            value = 320000.0,
            cost = 240000.0,
            manualPrice = true,
            dailyChangePercent = -0.80,
        ),
        Position(
            id = "pos_afa",
            name = "AFA · Ak Portföy Altın",
            assetClass = AssetClass.Fund,
            quantity = 12400.0,
            unit = QuantityUnit.Share,
            unitPrice = 24.60,
            value = 305000.0,
            cost = 268000.0,
            dailyChangePercent = 0.52,
        ),
        Position(
            id = "pos_tte",
            name = "TTE · Türkiye Teknoloji Değişim Fonu",
            assetClass = AssetClass.Fund,
            quantity = 8900.0,
            unit = QuantityUnit.Share,
            unitPrice = 21.40,
            value = 190500.0,
            cost = 214000.0,
            manualPrice = true,
            dailyChangePercent = -2.40,
        ),
        Position(
            id = "pos_ipv",
            name = "IPV · İş Portföy Değişken",
            assetClass = AssetClass.Fund,
            quantity = 5100.0,
            unit = QuantityUnit.Share,
            unitPrice = 24.41,
            value = 124500.0,
            cost = 112000.0,
            dailyChangePercent = 0.18,
        ),
        Position(
            id = "pos_vadeli",
            name = "Vadeli Mevduat",
            assetClass = AssetClass.Cash,
            quantity = 250000.0,
            unit = QuantityUnit.Currency,
            unitPrice = 1.0,
            value = 250000.0,
            cost = 240000.0,
            dailyChangePercent = 0.0,
        ),
        Position(
            id = "pos_vadesiz",
            name = "Vadesiz Hesap",
            assetClass = AssetClass.Cash,
            quantity = 85000.0,
            unit = QuantityUnit.Currency,
            unitPrice = 1.0,
            value = 85000.0,
            cost = 85000.0,
            dailyChangePercent = 0.0,
        ),
        Position(
            id = "pos_usd",
            name = "Amerikan Doları",
            assetClass = AssetClass.Fx,
            quantity = 3200.0,
            unit = QuantityUnit.Currency,
            unitPrice = 62.00,
            value = 198400.0,
            cost = 172000.0,
            dailyChangePercent = 0.24,
        ),
        Position(
            id = "pos_eur",
            name = "Euro",
            assetClass = AssetClass.Fx,
            quantity = 1500.0,
            unit = QuantityUnit.Currency,
            unitPrice = 82.67,
            value = 124000.0,
            cost = 112400.0,
            dailyChangePercent = -0.15,
        ),
        Position(
            id = "pos_gumus",
            name = "Gram Gümüş",
            assetClass = AssetClass.Silver,
            quantity = 120.0,
            unit = QuantityUnit.Gram,
            unitPrice = 483.0,
            value = 58000.0,
            cost = 62000.0,
            dailyChangePercent = -1.20,
        ),
    )

    val totals: PortfolioTotals = PortfolioTotals(
        totalValue = 3180400.0,
        todayChange = 12400.0,
        todayChangePercent = 0.29,
        profit = 412000.0,
        profitPercent = 14.9,
        principal = 2768400.0,
        monthAdded = 45000.0,
        monthTarget = 50000.0,
    )

    /** Buyukten kucuge - tasarimda dagilim bu sirayla gosteriliyor. */
    val allocation: List<AllocationSlice> = listOf(
        AllocationSlice(AssetClass.Gold, 1845000.0, 58.1),
        AllocationSlice(AssetClass.Fund, 620000.0, 19.5),
        AllocationSlice(AssetClass.Cash, 335000.0, 10.5),
        AllocationSlice(AssetClass.Fx, 322400.0, 10.1),
        AllocationSlice(AssetClass.Silver, 58000.0, 1.8),
    )

    // --- Islemler ----------------------------------------------------------

    /**
     * Ornek portfoyun islem defteri - tum pozisyonlar tek listede.
     *
     * KURAL: her pozisyonun islem toplami, o pozisyonun elle yazilmis quantity ve
     * cost degerlerini BIREBIR tutar. Hesaplar her blogun basinda yazili.
     * Iscilik/komisyon alista maliyete eklenir, satista hasilattan duser.
     */
    val transactions: List<Transaction> = listOf(
        // --- 22 Ayar Bilezik · 62,4 gr · maliyet 806.000 ---
        // 20,0 x 11.000 + 1.500 =  221.500
        // 18,0 x 13.100 +   700 =  236.500
        // 14,4 x 13.750         =  198.000
        // 10,0 x 14.900 + 1.000 =  150.000
        // toplam: 62,4 gr  ·  806.000
        Transaction(
            id = "tx_bilezik_1",
            positionId = "pos_bilezik22",
            date = KefeDate(2023, 9, 15),
            side = TradeSide.Buy,
            quantity = 20.0,
            unitPrice = 11000.0,
            fee = 1500.0,
            note = "Nişan hediyesi bilezikler",
            storage = "Banka kasası",
            addedByMemberId = MemberVolkanId,
        ),
        Transaction(
            id = "tx_bilezik_2",
            positionId = "pos_bilezik22",
            date = KefeDate(2024, 8, 20),
            side = TradeSide.Buy,
            quantity = 18.0,
            unitPrice = 13100.0,
            fee = 700.0,
            storage = "Banka kasası",
            addedByMemberId = MemberAyseId,
        ),
        Transaction(
            id = "tx_bilezik_3",
            positionId = "pos_bilezik22",
            date = KefeDate(2025, 4, 10),
            side = TradeSide.Buy,
            quantity = 14.4,
            unitPrice = 13750.0,
            note = "Kuyumcu Ali · iscilik yok",
            storage = "Evdeki kasa",
            addedByMemberId = MemberVolkanId,
        ),
        Transaction(
            id = "tx_bilezik_4",
            positionId = "pos_bilezik22",
            date = KefeDate(2026, 2, 5),
            side = TradeSide.Buy,
            quantity = 10.0,
            unitPrice = 14900.0,
            fee = 1000.0,
            storage = "Evdeki kasa",
            addedByMemberId = MemberAyseId,
        ),

        // --- Tam Altin · 2 adet · maliyet 171.000 ---
        // 1 x 74.500 + 500 =  75.000
        // 1 x 95.200 + 800 =  96.000
        // toplam: 2 adet  ·  171.000
        Transaction(
            id = "tx_tam_1",
            positionId = "pos_tam",
            date = KefeDate(2024, 5, 8),
            side = TradeSide.Buy,
            quantity = 1.0,
            unitPrice = 74500.0,
            fee = 500.0,
            note = "Sünnet takısı",
            storage = "Banka kasası",
            addedByMemberId = MemberVolkanId,
        ),
        Transaction(
            id = "tx_tam_2",
            positionId = "pos_tam",
            date = KefeDate(2025, 10, 22),
            side = TradeSide.Buy,
            quantity = 1.0,
            unitPrice = 95200.0,
            fee = 800.0,
            storage = "Banka kasası",
            addedByMemberId = MemberAyseId,
        ),

        // --- AFA · 12.400 pay · maliyet 268.000 ---
        // Fonda iscilik/komisyon yok.
        // 5.000 x 19,60 =  98.000
        // 3.400 x 21,80 =  74.120
        // 2.800 x 23,70 =  66.360
        // 1.200 x 24,60 =  29.520   (aktivitedeki 29.520 TL'lik alim)
        // toplam: 12.400 pay  ·  268.000
        Transaction(
            id = "tx_afa_1",
            positionId = "pos_afa",
            date = KefeDate(2024, 4, 3),
            side = TradeSide.Buy,
            quantity = 5000.0,
            unitPrice = 19.60,
            note = "Fona ilk giriş",
            addedByMemberId = MemberVolkanId,
        ),
        Transaction(
            id = "tx_afa_2",
            positionId = "pos_afa",
            date = KefeDate(2025, 1, 15),
            side = TradeSide.Buy,
            quantity = 3400.0,
            unitPrice = 21.80,
            addedByMemberId = MemberAyseId,
        ),
        Transaction(
            id = "tx_afa_3",
            positionId = "pos_afa",
            date = KefeDate(2026, 3, 9),
            side = TradeSide.Buy,
            quantity = 2800.0,
            unitPrice = 23.70,
            addedByMemberId = MemberVolkanId,
        ),
        Transaction(
            id = "tx_afa_4",
            positionId = "pos_afa",
            date = KefeDate(2026, 7, 24),
            side = TradeSide.Buy,
            quantity = 1200.0,
            unitPrice = 24.60,
            note = "Aylık düzenli alım",
            addedByMemberId = MemberAyseId,
        ),

        // --- Amerikan Dolari · 3.200 USD · maliyet 172.000 ---
        // 1.000 x 45,00 =  45.000
        // 1.200 x 55,00 =  66.000
        // 1.000 x 61,00 =  61.000
        // toplam: 3.200 USD  ·  172.000
        Transaction(
            id = "tx_usd_1",
            positionId = "pos_usd",
            date = KefeDate(2023, 11, 10),
            side = TradeSide.Buy,
            quantity = 1000.0,
            unitPrice = 45.0,
            storage = "Vadesiz döviz hesabı",
            addedByMemberId = MemberVolkanId,
        ),
        Transaction(
            id = "tx_usd_2",
            positionId = "pos_usd",
            date = KefeDate(2024, 9, 5),
            side = TradeSide.Buy,
            quantity = 1200.0,
            unitPrice = 55.0,
            storage = "Vadesiz döviz hesabı",
            addedByMemberId = MemberAyseId,
        ),
        Transaction(
            id = "tx_usd_3",
            positionId = "pos_usd",
            date = KefeDate(2025, 6, 18),
            side = TradeSide.Buy,
            quantity = 1000.0,
            unitPrice = 61.0,
            note = "Tatil için ayrıldı",
            storage = "Vadesiz döviz hesabı",
            addedByMemberId = MemberVolkanId,
        ),

        // --- Gram Gumus · net 120 gr · net maliyet 62.000 · SATIS ICEREN DEFTER ---
        // Alimlar:  80 x 470 + 400 = 38.000
        //          100 x 545 + 500 = 55.000
        //   ara toplam: 180 gr · 93.000  ->  ortalama 516,667 TL/gr
        // Satis 1:  48 x 590 - 320 = 28.000 hasilat
        //           maliyet dusen: 516,667 x 48 = 24.800  ->  realized +3.200
        // Satis 2:  12 x 481       =  5.772 hasilat  (aktivitedeki satis)
        //           maliyet dusen: 516,667 x 12 =  6.200  ->  realized   -428
        // Kalan: 180 - 60 = 120 gr  ·  93.000 - 24.800 - 6.200 = 62.000
        // Kesinlesmis kar: +3.200 - 428 = +2.772
        Transaction(
            id = "tx_gumus_1",
            positionId = "pos_gumus",
            date = KefeDate(2024, 7, 19),
            side = TradeSide.Buy,
            quantity = 80.0,
            unitPrice = 470.0,
            fee = 400.0,
            note = "Gümüş denemesi",
            storage = "Evdeki kasa",
            addedByMemberId = MemberVolkanId,
        ),
        Transaction(
            id = "tx_gumus_2",
            positionId = "pos_gumus",
            date = KefeDate(2025, 5, 23),
            side = TradeSide.Buy,
            quantity = 100.0,
            unitPrice = 545.0,
            fee = 500.0,
            storage = "Evdeki kasa",
            addedByMemberId = MemberAyseId,
        ),
        Transaction(
            id = "tx_gumus_3",
            positionId = "pos_gumus",
            date = KefeDate(2026, 1, 14),
            side = TradeSide.Sell,
            quantity = 48.0,
            unitPrice = 590.0,
            fee = 320.0,
            note = "Yükselişte bir kısmı bozduruldu",
            storage = "Evdeki kasa",
            addedByMemberId = MemberVolkanId,
        ),
        Transaction(
            id = "tx_gumus_4",
            positionId = "pos_gumus",
            date = KefeDate(2026, 7, 24),
            side = TradeSide.Sell,
            quantity = 12.0,
            unitPrice = 481.0,
            note = "Zararına küçük satış",
            storage = "Evdeki kasa",
            addedByMemberId = MemberVolkanId,
        ),

        // --- Ceyrek Altin · 8 adet · maliyet 167.500 ---
        // 2 x 15.200        =  30.400
        // 1 x 17.600        =  17.600
        // 2 x 19.800 + 300  =  39.900
        // 1 x 26.200        =  26.200
        // 2 x 26.400 + 600  =  53.400
        // toplam: 8 adet  ·  167.500  ->  ortalama 20.937,5 (tasarimda 20.940)
        Transaction(
            id = "tx_ceyrek_1",
            positionId = PositionQuarterGoldId,
            date = KefeDate(2024, 2, 22),
            side = TradeSide.Buy,
            quantity = 2.0,
            unitPrice = 15200.0,
            note = "İlk alım",
            storage = "Evdeki kasa",
            addedByMemberId = MemberVolkanId,
        ),
        Transaction(
            id = "tx_ceyrek_2",
            positionId = PositionQuarterGoldId,
            date = KefeDate(2024, 6, 14),
            side = TradeSide.Buy,
            quantity = 1.0,
            unitPrice = 17600.0,
            storage = "Evdeki kasa",
            addedByMemberId = MemberAyseId,
        ),
        Transaction(
            id = "tx_ceyrek_3",
            positionId = PositionQuarterGoldId,
            date = KefeDate(2024, 11, 3),
            side = TradeSide.Buy,
            quantity = 2.0,
            unitPrice = 19800.0,
            fee = 300.0,
            note = "Düğün takısı bozdurup alındı",
            storage = "Banka kasası",
            addedByMemberId = MemberVolkanId,
        ),
        Transaction(
            id = "tx_ceyrek_4",
            positionId = PositionQuarterGoldId,
            date = KefeDate(2025, 3, 18),
            side = TradeSide.Buy,
            quantity = 1.0,
            unitPrice = 26200.0,
            storage = "Banka kasası",
            addedByMemberId = MemberAyseId,
        ),
        Transaction(
            id = "tx_ceyrek_5",
            positionId = PositionQuarterGoldId,
            date = KefeDate(2026, 7, 12),
            side = TradeSide.Buy,
            quantity = 2.0,
            unitPrice = 26400.0,
            fee = 600.0,
            note = "Kuyumcu Ali · pazar",
            storage = "Evdeki kasa",
            addedByMemberId = MemberAyseId,
            syncState = SyncState.Pending,
        ),
    )

    /** Geriye uyum: ceyrek altin defteri artik genel listeden suzuluyor. */
    val quarterGoldTransactions: List<Transaction> =
        transactions.filter { it.positionId == PositionQuarterGoldId }

    val quarterGoldAveragePrice: Double = 20940.0

    val quarterGoldFirstBuy: KefeDate = KefeDate(2024, 2, 22)

    fun transactionsOf(positionId: String): List<Transaction> =
        transactions.filter { it.positionId == positionId }

    // --- Hedefler ----------------------------------------------------------

    val goals: List<Goal> = listOf(
        Goal(
            id = "goal_ev",
            name = "Ev",
            iconKey = "ev",
            amount = 7800000.0,
            unit = GoalUnit.Try,
            targetDate = KefeDate(2028, 12, 1),
            monthlyContribution = 50000.0,
            isMain = true,
            allocation = GoalAllocation.AllWealth,
            status = GoalStatus.Active,
            order = 0,
            estimatedArrival = KefeDate(2029, 3, 1),
        ),
        Goal(
            id = "goal_araba",
            name = "Araba",
            iconKey = "araba",
            amount = 1200000.0,
            unit = GoalUnit.Try,
            targetDate = KefeDate(2027, 6, 1),
            monthlyContribution = 15000.0,
            isMain = false,
            allocation = GoalAllocation.FixedShare,
            status = GoalStatus.Active,
            order = 1,
            estimatedArrival = KefeDate(2027, 2, 1),
        ),
        Goal(
            id = "goal_tatil",
            name = "Yaz tatili",
            iconKey = "ucak",
            amount = 180000.0,
            unit = GoalUnit.Try,
            targetDate = KefeDate(2026, 7, 1),
            monthlyContribution = 8000.0,
            isMain = false,
            allocation = GoalAllocation.FixedShare,
            // Tahmini varis hedef tarihini gectigi icin gecikmis sayilir
            status = GoalStatus.Overdue,
            order = 2,
            estimatedArrival = KefeDate(2026, 8, 1),
        ),
        Goal(
            id = "goal_buzdolabi",
            name = "Buzdolabı",
            iconKey = "buzdolabi",
            amount = 48000.0,
            unit = GoalUnit.Try,
            targetDate = KefeDate(2026, 3, 1),
            monthlyContribution = 0.0,
            isMain = false,
            allocation = GoalAllocation.FixedShare,
            status = GoalStatus.Completed,
            order = 3,
            estimatedArrival = null,
        ),
    )

    val mainGoal: Goal = goals.first { it.isMain }

    // --- Piyasa ------------------------------------------------------------

    val prices: List<Price> = listOf(
        Price("gold_gram", "Gram Altın", 16640.0, 16700.0, 0.38, "14:32", PriceSource.FreeMarket, false, AssetClass.Gold),
        Price("gold_quarter", "Çeyrek Altın", 26300.0, 26500.0, 0.42, "14:32", PriceSource.FreeMarket, false, AssetClass.Gold),
        Price("gold_half", "Yarım Altın", 52600.0, 53000.0, 0.42, "14:32", PriceSource.FreeMarket, false, AssetClass.Gold),
        Price("gold_full", "Tam Altın", 105200.0, 106000.0, 0.38, "14:32", PriceSource.FreeMarket, false, AssetClass.Gold),
        Price("gold_ata", "Ata Altın", 108900.0, 109800.0, 0.40, "14:32", PriceSource.FreeMarket, false, AssetClass.Gold),
        Price("gold_k22", "22 Ayar", 15240.0, 15308.0, 0.41, "14:32", PriceSource.FreeMarket, false, AssetClass.Gold),
        Price("gold_k18", "18 Ayar", 12480.0, 12530.0, 0.39, "14:32", PriceSource.FreeMarket, false, AssetClass.Gold),
        Price("gold_k14", "14 Ayar", 9680.0, 9720.0, 0.40, "14:32", PriceSource.FreeMarket, false, AssetClass.Gold),
        Price("silver_gram", "Gram Gümüş", 481.0, 483.0, -1.20, "14:32", PriceSource.FreeMarket, false, AssetClass.Silver),
        Price("usd_try", "USD/TRY", 61.94, 62.00, 0.24, "14:32", PriceSource.FreeMarket, false, AssetClass.Fx),
        Price("eur_try", "EUR/TRY", 82.58, 82.67, -0.15, "14:32", PriceSource.FreeMarket, false, AssetClass.Fx),
        Price("fund_afa", "AFA", null, 24.60, 0.52, "09:00", PriceSource.Tefas, false, AssetClass.Fund),
        Price("fund_ipv", "IPV", null, 24.41, 0.18, "09:00", PriceSource.Tefas, false, AssetClass.Fund),
        Price("fund_tte", "TTE", null, 21.40, -2.40, "dün 21:30", PriceSource.Manual, true, AssetClass.Fund),
    )

    // --- Aktivite ----------------------------------------------------------

    val activity: List<ActivityEvent> = listOf(
        ActivityEvent(
            id = "act_1",
            memberId = MemberAyseId,
            kind = ActivityKind.AddTransaction,
            description = "2 Çeyrek Altın ekledi",
            amount = 52800.0,
            timeLabel = "2 sa önce",
            dayGroup = "BUGÜN",
        ),
        ActivityEvent(
            id = "act_2",
            memberId = MemberVolkanId,
            kind = ActivityKind.ManualPrice,
            description = "TTE fiyatını elle güncelledi",
            amount = null,
            timeLabel = "5 sa önce",
            dayGroup = "BUGÜN",
            isManualPrice = true,
        ),
        ActivityEvent(
            id = "act_3",
            memberId = MemberAyseId,
            kind = ActivityKind.GoalUpdate,
            description = "\"Ev\" hedefini 7.800.000 olarak güncelledi",
            amount = null,
            timeLabel = "21:40",
            dayGroup = "DÜN",
        ),
        ActivityEvent(
            id = "act_4",
            memberId = MemberVolkanId,
            kind = ActivityKind.ExcludeFromGoals,
            description = "Anneannemin Bileziği'ni hedeflerden çıkardı",
            amount = null,
            timeLabel = "18:05",
            dayGroup = "DÜN",
        ),
        ActivityEvent(
            id = "act_5",
            memberId = MemberVolkanId,
            kind = ActivityKind.SellTransaction,
            description = "12 gr Gram Gümüş sattı",
            amount = 5772.0,
            timeLabel = "11:20",
            dayGroup = "3 GÜN ÖNCE",
        ),
        ActivityEvent(
            id = "act_6",
            memberId = MemberAyseId,
            kind = ActivityKind.AddTransaction,
            description = "AFA fonuna 1.200 pay ekledi",
            amount = 29520.0,
            timeLabel = "09:15",
            dayGroup = "3 GÜN ÖNCE",
        ),
    )
}


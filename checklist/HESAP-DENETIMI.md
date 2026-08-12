# Hesaplama Denetimi

Uygulamadaki **bütün** finansal hesaplamaların satır satır denetimi. Amaç: her
rakamın kusursuz olması.

## Yöntem

15 bağımsız denetçi ajan kod tabanını 13 alana bölerek taradı (değerleme, maliyet
esası, getiri/XIRR, dönemsel değişim, özet/dağılım, hedefler ×3 mercek, tarih,
para formatı, fiyat API'leri, fiyat tazeliği, kalıcılık/yedek, ekranlar arası
tutarlılık). Her bulgu ardından, bulgudan habersiz **şüpheci hakem ajanlara**
verildi; hakemin görevi bulguyu çürütmekti. Yalnız somut kod kanıtıyla yeniden
üretilebilen bulgular bu listeye girdi.

- **113 ham bulgu** → **92 doğrulandı**, **21 çürütüldü**
- Tekilleştirme sonrası **~55 ayrı kusur**
- Testler de denetlendi: beklenen değerler bağımsız yeniden hesaplandı
- `ILERLEME.md`'de belgelenmiş iş kuralları kodla karşılaştırıldı; kural ile kod
  çeliştiğinde bu bir bulgu sayıldı

Ayrıca en ağır beş bulgu elle, kod okunarak ayrıca teyit edildi.

---

## P0 — Sessiz veri bozulması

Kullanıcının verisini geri dönülemez biçimde bozan, hiçbir uyarı vermeyen
kusurlar. Önce bunlar.

### P0.1 · Elle fiyat girişi değeri 1000 kat küçültüyor — ✅ ÇÖZÜLDÜ

`ui/screens/market/MarketViewModel.kt:237-245` (+ `:109`)

`openEdit` alanı `Money.number(ask, priceDecimals(ask))` ile dolduruyor. 10.000 TL
üstü fiyatlarda `priceDecimals` sıfır döndüğü için metin `"41.457"` oluyor —
binlik noktalı, **virgülsüz**. `toTurkishDoubleOrNull` ise yalnız *virgül varsa*
noktaları temizliyor; virgülsüz dalda metni olduğu gibi `toDoubleOrNull`'a
veriyor ve `"41.457"` geçerli bir Double olarak **41,457** dönüyor.
`commitManualPrice` bunu pozitif bulup kaydediyor.

Ata altını **₺41.457,30 yerine ₺41,46** olarak yazılıyor. Kullanıcının alanda
hiçbir şey değiştirmesi gerekmiyor — sayfayı açıp Kaydet'e basmak yeterli.
Çeyrek, Yarım, Tam ve Ata satırlarının hepsi 10.000 eşiğinin üstünde olduğu için
bu yola düşüyor. İkinci bir tetikleyici de var: alana bir kez dokunulduğunda
`String.asAmountInput()` (`Fields.kt:416-429`) ilk gördüğü noktayı ondalık ayırıcı
sayıp virgüle çeviriyor.

Fonksiyonun kendi yorumu ("nokta binlik ayracı olabileceğinden yalnız virgüllü
girdide temizlik yapılır") mantığı ters kurmuş: noktanın binlik ayracı olma
ihtimali tam da virgülün olmadığı durumda en yüksek. Aynı işi yapan ikinci kopya
(`AddTransactionUiState.kt:453 parseTrNumber`) doğru yazılmış — iki kopya
ayrışmış. Bu fonksiyonun hiç birim testi yok.

Bu ekran `MarketViewModel.kt:20-22`'de "uygulamanın emniyet supabı" diye
tanımlanmış; elle girilen fiyat tüm portföy değerlemesini besliyor.

**Yapıldı** (`Read a manually entered price at its real size`): iki ayrıştırıcı
`Money.parseTrAmountOrNull` altında birleştirildi ve nokta artık her zaman
binlik ayracı; alan `Money.plain` ile gruplanmamış metinle dolduruluyor.
`TrAmountTest` (8 test) ve `ManualPriceRoundTripTest` (3 test) eklendi — düzeltme
geri alındığında ikisi de düşüyor, doğrulandı.

### P0.2 · Hedefi güncellemek tüm varlık atamalarını siliyor — ✅ ÇÖZÜLDÜ

`sqldelight/.../Goal.sq:87-90`

`upsertGoal` `INSERT OR REPLACE` kullanıyor. Üstündeki yorum "Hedefin çocuk
tablosu yok; REPLACE burada güvenli (silinecek bağlı satır yok)" diyor — **bu
bilgi yanlış**: `GoalAsset.sq:29` satırında
`goalId TEXT NOT NULL REFERENCES goals(id) ON DELETE CASCADE` var.

Aynı dosyanın 25 satır yukarısında, senkron yolu için yazılmış yorum doğruyu
söylüyor: *"goals'ta OR REPLACE KULLANILMAZ - goal_assets goalId'ye CASCADE
bağlı, silinip yeniden yazılınca atamalar uçar. İki adım."* Kural biliniyor,
senkron yoluna uygulanmış, **yerel yazma yoluna uygulanmamış**.
`ILERLEME.md:491-493` de aynı kuralı yazıyor.

SQLite'ta REPLACE çakışan satırı önce SİLER; yabancı anahtar zorlaması üç
sürücüde de açık (`DatabaseDriver.android.kt:42`, `.ios.kt:25-28`,
`.desktop.kt:37-39`), dolayısıyla CASCADE tetikleniyor.

Tetikleyen sıradan eylemler: hedefi düzenleyip kaydetmek
(`GoalsViewModel.kt:251`), başka bir hedefi ana hedef yapmak (`:249`), hedefi
tamamlandı işaretlemek (`GoalDetailViewModel.kt:137`). Üçünde de hedefin bütün
`goal_assets` satırları uçuyor, `goalWealth()` 0 dönüyor, ilerleme **%0**'a
düşüyor.

Doğrulama ajanı bunu ayrı bir SQLite şemasında yeniden üretti: `foreign_keys=ON`
iken atama sayısı 1 → 0; `OFF` iken 1 → 1. Testlerin yakalamama sebebi de bu —
`SoftDeleteTest.kt:52` test veritabanını yabancı anahtar zorlaması kapalı
kuruyor.

**Yapıldı** (`Keep a goal's asset assignments when the goal is saved`):
`upsertGoal` senkron yolundaki gibi `insertOrIgnoreGoal` + `applyGoalMeta`
olarak ikiye bölündü ve tek transaction içine alındı; `goals` üzerinde hiçbir
`INSERT OR REPLACE` kalmadı; yanıltıcı yorum düzeltildi.
`GoalAssignmentSurvivalTest` (6 test) yabancı anahtar zorlamasını üç platform
sürücüsü gibi **açık** kuruyor ve ona güvenmeden önce açık olduğunu ayrıca
sınıyor. Eski SQL geri konduğunda dört test düşüyor, doğrulandı.

### P0.3 · Gram altında ayar pozisyon kimliğinde taşınmıyor: 22 ile 24 ayar birleşiyor — ✅ ÇÖZÜLDÜ

`ui/screens/transaction/AddTransactionViewModel.kt:948`

`assetKeyOf()` altın dalında ayarı **yalnız Jewelry** için kimliğe koyuyor;
diğer formlarda `selectedSubtype.priceKey()` kullanılıyor ve
`GoldSubtype.Gram.priceKey()` ayardan bağımsız her zaman `"gold_gram"` dönüyor.
Oysa `Position.matches()` etkin ayarı kıyaslıyor — yani 24 ayar gram pozisyonu
varken 22 ayar gram seçimi eşleşmiyor, `newPositionId()` = `"pos_gold_gram"`
üretiliyor ve bu kimlik zaten var.

`upsertPosition`, `insertOrIgnorePosition` + `updatePositionMeta` çiftini
çalıştırdığı için mevcut satırın adı, alt türü ve **karatı** üzerine yazılıyor;
işlem aynı `positionId`'ye yazılıp `recomputePosition` iki defteri tek pozisyonda
topluyor. `Position.priceKey()` artık yeni karatı okuduğu için **eski miktar da
yeni ayarın kotasyonuyla** değerleniyor.

Somut: elde 100 gr 24 ayar altın (maliyet 100×6.000 = 600.000). Kullanıcı 10 gr
22 ayar ekliyor → pozisyon "22 ayar Gram Altın", miktar 110 gr, değer
110 × 5.593 ≈ **615.230 TL**. Doğrusu iki ayrı satır: 100×6.115 + 10×5.593 ≈
**667.430 TL**. Ekranda ~52.000 TL'lik sahte kayıp ve iki varlığın defteri geri
dönülemez şekilde birleşmiş oluyor. Aynı çakışma Bullion (Has/Külçe) için de
var.

`ValuationTest.ayarPozisyonuAnahtardanAYIRIR` yalnız `Position.priceKey()`'i
ölçüyor, pozisyon kimliğini ölçmediği için bu hatayı yakalamıyor — test **yanlış
güven veriyor**.

**Yapıldı** (`Give each gold karat its own position`): kimlik kuralı test
edilebilir bir `goldAssetKey(subtype, karat)` fonksiyonuna çıkarıldı; ayar
sorulan formlarda kimlik `karat.priceKey()`'den türüyor, Has/Külçe kendi
anahtarını aldı. 24 ayar gramın kimliği değişmedi, mevcut pozisyonlar
etkilenmiyor. `GoldAssetKeyTest` (6 test) eklendi — eski mantıkta üçü düşüyor.

### P0.4 · Hedef tutarı bilimsel gösterime düşüp yok oluyor — ✅ ÇÖZÜLDÜ

`ui/format/Money.kt:233-237` · `ui/screens/goals/GoalsUiState.kt:68-69` ·
`GoalsViewModel.kt:182`

`rawAmount` kesirli değerde `value.toString()` kullanıyor. JVM/Android'de
`Double.toString`, |x| ≥ 10⁷ veya < 10⁻³ için **bilimsel gösterim** döner.
`parseAmount` ise `filter { it.isDigit() || it == ',' }` ile 'E', '-', '+' ve
noktayı atıp kalan rakamları birleştiriyor — tamamen farklı bir sayı üretiyor.

Gerçekçi zincir: gram altın 6.242,71 TL iken 2.000 gr hedef kaydediliyor →
`amount = 12.485.419,999999998` (kayan nokta artığı, tam sayı değil).
Düzenlemek için açılıyor:

```
rawAmount(12485419.999999998) → "1.2485419999999998E7" → "1,2485419999999998E7"
parseAmount → "1,24854199999999987" → 1,2485 TL
convertTo(GoldGram) → 0,0002 → rawAmount(2.0E-4) → "2,0E-4" → parseAmount → 2,04 gram
Kaydet → 2,04 × 6242,71 = ₺12.735
```

**₺12.485.420 → ₺12.735.** Kullanıcı tutara hiç dokunmadı. Aynı bozulma düz TL
hedefinde de mümkün ("12500000,5" → 1,25 TL). `BackupCodec.kt:65` aynı sorunu
CSV yedeğinde taşıyor ("1,5E7").

**Yapıldı** (`Stop a goal amount from vanishing into scientific notation`):
`rawAmount` artık `Money.plain` ile, değerin gerçekten taşıdığı hane sayısı
kadar ve bilimsel gösterimsiz yazıyor — bu aynı zamanda `0,30000000000000004`
gibi kayan nokta kuyruğunu da alandan çıkarıyor. `parseAmount` ortak
ayrıştırıcıya bağlandı: okunamayan metin artık başka bir sayıya dönüşmüyor,
sıfıra düşüp mevcut `amount <= 0` kontrolüne yakalanıyor. `RawAmountTest`
(9 test) eklendi — eski kodda üçü düşüyor.

### P0.5 · Kur gelmeden hedef kaydedilince tutar 5.000 kat sapıyor — ✅ ÇÖZÜLDÜ

`ui/screens/goals/GoalsUiState.kt:72-79` · `GoalsViewModel.kt:145-154, 206-233`

`rateOf` fiyat 0 ise **1.0** dönüyor ("bölme hatası olmasın"). Bu, 1 gram altını
1 TL'ye eşitliyor ve hem yazma hem okuma yönünde tutarı bozuyor. Kurun 0
olabileceğini kodun kendisi kabul ediyor (`GoalsViewModel.kt:43-45` yorumu ve
`:145-146`'daki `takeIf { it > 0.0 }` koruması).

- **Yazma:** çevrimdışı ilk açılışta "Gram altın" birimiyle 400 yazılır →
  `amountInTry() = 400 × 1.0 = 400 TL`. Beklenen 400 × ~5.000 =
  **2.000.000 TL**. Hedef bir daha 0,08 gram olarak açılır.
- **Yarış durumu (daha ağır):** `amount=2.000.000, unit=GoldGram` bir hedef, kur
  henüz 0 iken açılır → alanda "2.000.000 gr altın" yazar. Sonra `observePrices`
  kuru 5.000 yapar ama `amountText`'i yeniden çevirmez. Kullanıcı sadece adı
  düzeltip kaydeder → `amount = 2.000.000 × 5.000 = 10.000.000.000 TL`.

`save()`'in tek doğrulaması `amount <= 0.0`; kur geçerliliği hiç sorgulanmıyor.
Telafi eden kod yok — birim segmentleri kur 0 iken de aktif.

**Yapıldı** (`Refuse to price a goal before the rate is known`): `rateOrNull`
null dönüyor, kur bilinmiyorken kayıt engelleniyor, altın/dolar çipleri kilitli
ve soluk çiziliyor, çevrilemeyen birim `pendingUnit` ile ertelenip kur gelince
tamamlanıyor. `GoalRateTest` (8 test) eklendi — eski kodda beşi düşüyor.

### P0.6 · Öksüz atama yedeği geri yüklemeyi tümden çökertiyor — ✅ ÇÖZÜLDÜ

`data/repository/SqlDelightPortfolioRepository.kt:499` (+ `:366`, `:741-814`)

`deleteGoal` ve `deletePosition` yalnızca **yumuşak silme** yapıyor; `goal_assets`
satırlarına dokunulmuyor ve CASCADE yalnız gerçek DELETE'te çalıştığı için atama
satırı canlı kalıyor. `exportBackup` bu satırı yedeğe yazıyor — oysa aynı
yedekteki `goals` ve `positions` listeleri (`deletedAt IS NULL`) o kaydı
içermiyor.

`restoreBackup`'taki `assignPositionToGoal` öksüz satırı yazmaya çalışınca
`FOREIGN KEY constraint failed` atıyor. Çağrı `database.transaction` içinde
olduğundan **tüm geri yükleme geri alınıyor**: kullanıcının yedeği kalıcı olarak
geri yüklenemez hale geliyor (`SettingsViewModel.kt:169` sadece `BackupFailed`
gösteriyor).

Tetikleyici tamamen sıradan: bir hedefe varlık ata, sonra o hedefi (ya da o
varlığı) sil, sonra yedek al.

İlgili: `#212` — yumuşak silinen hedefe bağlı atamalar canlı kaldığı için
`observeGoalAssets` onları filtrelemiyor; pozisyon var olmayan bir hedefe atanmış
kalıyor ve sonraki alımları da yutuyor.

**Yapıldı** (`Keep a deleted goal from breaking the backup`): iki silme yolu da
atamaları mezar taşlıyor (`clearGoalAssignments` / `clearPositionAssignment`);
`restoreBackup` hedefi ya da varlığı dosyada olmayan atamayı atlıyor — böylece
öksüz satır taşıyan **mevcut** yedekler de yüklenebiliyor. Üç test eklendi,
eskisinde üçü de düşüyor.

---

## Değişim hesapları — ayrı inceleme (9 Ağu 2026, Pazar)

Kullanıcı pazar günü değişimlerin %0 olması gerektiğini, bir şeyin tutmadığını
bildirdi. **Sezgi doğru çıktı: iki kesin hata** bulundu ve düzeltildi, biri de
kullanıcının tarif ettiği rakamın ta kendisiydi. **İki konu karar bekliyor.**

### Bulunan ve düzeltilen: aynı pencere, üç ekranda iki farklı cevap

`Price.changeIn(Day)` ham `changePercent` okuyordu; `Position.changeIn(Day)`
ise `valuedAt` içinden `todayChangePercent(today)` kapısından geçiyordu. Borsa
kapalıyken aynı veriden iki cevap çıkıyordu — cuma kapanışından kalma bir
kotasyonla, pazar günü:

| Ekran | Gösterdiği | Doğrusu |
|---|---|---|
| Piyasa | **+1,50%** (cumanın hareketi) | 0,00% |
| Özet · piyasa kartı | **+1,50%** | 0,00% |
| Varlıklar | 0,00% | ✓ |
| Özet · "bugün" | ₺0 | ✓ |

Özet ekranında çelişki **tek bakışta** görünüyordu: piyasa kartı dolu, hemen
üstündeki "bugün" satırı boş. Kapı artık `Price.changeIn`'in içinde;
`DailyChangeAcrossScreensTest` üç ekranın aynı sayıyı verdiğini sabitliyor.

### Sonradan ölçüldü ve düzeltildi: donmuş kotasyon "bugün" sayılıyordu

Kaynak 9 Ağustos 2026'da ölçüldüğünde `today.json` bütün altın türlerine
`Change` gönderiyordu — CEYREKALTIN 2.09, YARIMALTIN 2.09, TAMALTIN 2.09,
ATAALTIN 2.09, YIA 2.09, 18AYARALTIN 2.09, 14AYARALTIN 2.09, GRA 2.59, HAS 2.59,
GUMUS 3.57; 86 sembolden yalnızca biri sıfırdı. Buradan "yedek yolun dayandığı
varsayım artık geçerli değil" sonucu çıkarılmıştı.

**Bu sonuç yanlıştı ve 12 Ağustos'ta ölçümle düzeltildi.** 9 Ağustos bir
pazardı; o gördüğümüz 2.09'lar cumadan kalma, hafta sonu boyunca taşınan
değerlerdi. Aynı uç bir iş günü sabahı (12 Ağustos 10:16) sorulduğunda 86
sembolden **16'sı** sıfır dönüyor ve **11'i sikke altın**: CEYREKALTIN,
YARIMALTIN, TAMALTIN, ATAALTIN, RESATALTIN, HAMITALTIN, BESLIALTIN,
IKIBUCUKALTIN, GREMSEALTIN, 14AYARALTIN, 18AYARALTIN. Sıfır olmayanlar GRA
(0.82) ve CUMHURIYETALTINI (1.29).

Yani **yedek yol hâlâ gerekli** — sikke altınların günlük değişimi bu uçtan
düzenli olarak gelmiyor. Yedek yol yerinde bırakıldı; bu ölçüm onu kaldırmayı
düşünen bir sonraki kişi için buraya yazıldı.

Aynı ölçüm **asıl hatayı** ortaya çıkardı ve telefonda görünen buydu. Uç, piyasa
kapalıyken de `Update_Date`'i her dakika ilerletiyor — pazar günü on iki dakika
boyunca yirmiden fazla kez örneklendi, damga `10:04:01 → 10:16:01` ilerlerken
bütün altın fiyatları ve `Change` alanları **tek bir kez bile** değişmedi. `quoteDate` o damgadan okunduğu için
bugüne eşitleniyor ve **cumanın +%2,09'u pazarın getirisi olarak sayılıyordu**.

Cihazın veritabanı bunu açıkça gösterdi:

| gold_quarter | fiyat |
|---|---|
| 7 Ağu (Cuma) | 10.887,46 |
| 8 Ağu (Cmt) | 10.887,46 |
| 9 Ağu (Paz) | 10.887,46 |

…ve `cached_prices`: `ask=10887.46, changePercent=2.09, quoteDateKey=20260809`.

Damganın yanıtlayamadığı soruyu **fiyatın kendisi** yanıtlıyor: değer önceki
günün kaydıyla aynıysa piyasa o gün oynamamıştır, katkı sıfırdır — kaynak ne
derse desin. Fiyat gerçekten oynadıysa kaynağın rakamı tercih edilmeye devam
ediyor (gerçek önceki kapanışa göre ölçülmüş, daha kesin).

Cihazda doğrulandı: özet, piyasa kartı ve piyasa tablosu bugün altında 0,00%;
hafta penceresi çeyrekte +8,58% (10.027,02 → 10.887,46, elle doğrulandı).

### Yolda görülüp düzeltildi: Has/Külçe gram fiyatıyla değerleniyordu — ✅ ÇÖZÜLDÜ

`GoldSubtype.Bullion.priceKey()` `"gold_gram"` dönüyordu, oysa tahtada ayrı bir
`gold_bullion` (Has Altın) kotasyonu var ve iki fiyat farklı: Has ₺6.627,24,
Gram ₺6.660,55 (9 Ağustos 2026). Has/Külçe tutan bir pozisyon bu yüzden ~%0,5
fazla değerleniyordu.

**Yapıldı** (`Value Has/Külçe from its own quote`): anahtar `"gold_bullion"`
oldu. Kaynak satırı zaten çekiyordu (`LivePriceRemoteDataSource`: `HAS →
gold_bullion`), eksik olan yalnızca anahtardı. Bu, fiyat anahtarını aynı dalda
gram altından ayrılan pozisyon kimliğiyle de hizalıyor. İki test eklendi; eski
anahtarla `expected:<gold_bullion> but was:<gold_gram>` düşüyor.

### Çözüldü: §35 seçildi, günlük yalnız dünden hesaplanıyor — ✅

`PriceChange.kt` günlük pencereyi `[bugün−4, bugün−1]` kuruyordu
(`DayDaysBack=1`, `DayTolerance=3`), yani uygulama bir süre açılmamışsa
**birikmiş** hareket tek bir "bugünkü getiri" olarak yazılıyordu. Bu, §35
("günlük değişim yalnız o gün olduysa sayılır") ile §40 ("en son baktığımızdan
bu yana") arasında doğrudan bir çatışmaydı.

**Karar: §35.** Tolerans sıfırlandı — karşılaştırma noktası yalnızca dün. Dünün
kaydı yoksa günlük değişim **hesaplanamaz** ve null döner; uydurulmuş bir rakam
yerine susulur.

Bayatlık kontrolü (`previousDayPrice`) bilerek daha geniş bakmaya devam ediyor
ve bu §35'i **güçlendiriyor**: cumartesi uygulamayı açmayan biri pazar günü
baktığında dünün kaydı yoktur, ama cumanın kaydı durur — fiyat ona eşitse
"bugün oynamadı" diyebiliyoruz. Orayı da daraltmak, kaynağın cumadan donmuş
rakamının yeniden "bugün" sayılmasına yol açardı. İki soru ayrı: *"bugün ne
kadar oynadı"* yalnız dünle, *"bugün hiç oynadı mı"* en son kaydettiğimizle
yanıtlanır.

Hafta ve ay toleransı yerinde kaldı; onlar zaten "yaklaşık şu kadar önce"
sorusunu yanıtlıyor ve kapalı günlere dayanıklı olmaları gerekiyor.

Eski davranışı sabitleyen bir test vardı (`dunYoksaOncekiKayitliGuneBakilir`);
yeni kurala göre yazıldı. Üç yeni test eklendi — eski toleransla ikisi düşüyor.

### Çözüldü: günlük değişim artık "bilinmiyor" diyebiliyor — ✅

`weekChangePercent` ve `monthChangePercent` her zaman nullable'dı, çünkü
"değişmedi" ile "bilmiyoruz" ayrı cevaplar. `dailyChangePercent` değildi ve veri
yokluğu sıfır okunuyordu. İki zararı vardı: "Gün" penceresi hiçbir zaman "—"
olamıyordu, ve o sahte sıfır grup toplamlarına **paya ve paydaya** girip gerçek
oranı sulandırıyordu.

**Yapıldı** (`Let the daily change say "unknown"`): `Price.changePercent` ve
`Position.dailyChangePercent` nullable oldu; depo son dalı artık sıfıra
düşürmüyor — kaynak sustuysa ve dünün kaydı da yoksa cevap null. Önceki güne ait
kotasyon hâlâ sıfır veriyor; onu **biliyoruz**, piyasa bugün oynamadı.

`List<Position>.todayChange` artık hedef kartının zaten kullandığı
`weightedPeriodTotal`'dan geçiyor. Elle yazılmış bir kopyaydı ve iki farkı
vardı: bilinmeyeni sıfır sayıyordu, ve kardeşindeki "dönem başı değeri pozitif
olmalı" koruması yoktu. `PortfolioTotals` bilinmeyeni taşıyor, üç özet düzeni de
"—" çiziyor.

Önbellek kolonu NOT NULL kaldı — orada kaynağın ham rakamı duruyor, etkin değer
zaten okuma anında yeniden hesaplanıyor.

Beş yeni test eklendi; ikisi bilinmeyenin grup yüzdesini sulandırmadığını
doğruluyor. Eski sıfırı sabitleyen iki test yeni kurala göre yazıldı.

### İncelenip sorun bulunmayanlar

- `todayChange()` ağırlıklandırması: her pozisyon dönem başı değerine geri
  çözülüp TL farklar toplanıyor — yüzde ortalaması alınmıyor, doğru.
- `portfolioTotals`: `todayChangePercent` paydası dönem başı toplam
  (`total − dayChange`), kâr/zarar paydası maliyet — ikisi doğru ayrılmış.
- `weightedPeriodTotal`: yüzdesi bilinmeyen pozisyon paya da paydaya da
  girmiyor, doğru.
- Hafta/ay pencereleri: tolerans kapalı günler için zaten tasarlanmış, kotasyon
  günü kuralından etkilenmemeleri doğru.

---

## P1 — Ekranda yanlış rakam

Veri sağlam ama kullanıcının gördüğü sayı yanlış.

### P1.1 · Varlık detayı maliyet hesabını yanlış sorguyla yapıyor — ✅ ÇÖZÜLDÜ

`ui/screens/assets/AssetDetailViewModel.kt:162`

`costBasis()`, ekran sıralamalı listeye uygulanıyor
(`selectTransactionsByPosition`, tarih DESC + UUID). `Transaction.sq:60-69` tam
bu hatayı yazıyla anlatıyor ve `selectTransactionsForCompute` (rowid ASC)
sorgusunu bunun için eklemiş. Depo (`recomputePosition:583`) ve senkron
(`SyncLocalSink:269`) doğru sorguyu kullanıyor; **yalnız detay ekranı
kullanmıyor**.

Aynı gün önce alıp sonra satan kullanıcıda, satışın UUID'si alımdan küçükse
(~%50 olasılık) `costBasis` satışı "elde miktar yok" diye tümden atlıyor. Detay
ekranı ile varlık listesi birbiriyle çelişiyor.

**Yapıldı** (`Read a position's ledger in one order only`): yalnız çağıranı
düzeltmek tuzağı yerinde bırakırdı, o yüzden **ekran sorgusu kaldırıldı**. Artık
tek sorgu var (tarih + `rowid`, kronolojik); detay ekranı "en yeni üstte"
görünümünü o listeyi kendi sıralayarak kuruyor — kararlı sıralama olduğu için
aynı günün kayıtları kendi aralarında kronolojik kalıyor. Sıra artık depo
sözleşmesinin yazılı parçası. `LedgerOrderTest` (3 test) eklendi ve **somut
rakamları** doğruluyor: tek ortak sorguda bozuk sıra iki tarafı birden bozacağı
için "iki yol aynı sonucu veriyor" kontrolü tek başına yetmezdi. Eski sırada
miktar `1.0` yerine `2.0` çıkıyor.

### P1.2 · Aynı gün kronolojik sırası üç ayrı yerde bozuluyor — ✅ ÇÖZÜLDÜ

Aynı kök: sıra `rowid`'e dayanıyor ve rowid üç akışta yeniden atanıyor.

| Nerede | Ne oluyor |
|---|---|
| `AddTransactionViewModel.kt:652` | Düzenleme "önce yaz sonra sil": kayıt tablonun sonuna taşınıyor, aynı günün sırası bozuluyor. Bozulma **garanti**, rastgele değil; diske yazılan pozisyon değerleri bozuluyor |
| `Transaction.sq:99` | Senkron pull `INSERT OR REPLACE` satırı silip yeniden ekliyor → yeni rowid. İki cihaz aynı defterden farklı miktar/maliyet hesaplayabiliyor |
| `SqlDelightPortfolioRepository.kt:453` | Yedekten geri yükleme dosya sırasıyla INSERT ediyor; orijinal kronoloji kayboluyor |

**Yapıldı** (`Carry a transaction's place in the day on the record itself`):
sıra artık kaydın kendi `createdAt` alanında taşınıyor — yedeğe giriyor,
senkronda korunuyor, düzenlemede yeni satıra devrediliyor. Pull `INSERT OR
REPLACE` yerine iki adım (ilk görüşte `createdAt = updatedAt`, sonrasında
dokunulmuyor), geri yükleme damgayı dosyadan alıyor.

Damga **kesin artan**: düz saat okuması yetmiyordu, aynı milisaniyede yazılan
iki kayıt eşitlenip sıra `id` bağına yani UUID'ye düşüyordu — düzeltilmeye
çalışılan hatanın ta kendisi. Bunu testler yakaladı (sabit test saati her kaydı
eşitliyor).

Göç (`8.sqm`) mevcut satırları `updatedAt` varsa ondan (eş cihazlar aynı sırayı
türetsin), yoksa `rowid`'den dolduruyor. **Cihazın kendi veritabanında
doğrulandı:** sürüm 8 → 9, 54 işlemin tamamı gerçek zaman damgalarıyla doldu,
sıfır kalan yok.

`LedgerOrderTest` 6 teste çıktı; eski sıralamada düzenleme ve geri yükleme
testleri miktarı `1.0` yerine `2.0` veriyor.

**Sunucu tarafı da kapatıldı** (`Carry the creation stamp over sync too`):
Supabase'deki `transactions` tablosuna `created_at bigint NOT NULL DEFAULT 0`
eklendi, damga her işlemle push ediliyor ve pull'da geri okunuyor.

Mevcut satırların doldurulması ilk denemede **sessizce yutuldu**: `transactions`
tablosundaki `transactions_lww` tetikleyicisi `kefe_lww_guard`'ı `BEFORE UPDATE`
çalıştırıyor ve `NEW.updated_at <= OLD.updated_at` ise `OLD` dönüp yazmayı geri
çeviriyor. Bakım amaçlı doldurma `updated_at`'e dokunmadığı için koşul her
satırda sağlanıyordu; sorgu yine de `success` dönüyordu. `updated_at`'i artırmak
çözüm değil — satır eş cihazlarda "daha yeni" görünüp gereksiz çekme tetiklerdi.
Tetikleyici yalnızca doldurma işlemi boyunca kapatılıp yeniden açıldı.

**Uçtan uca doğrulandı** (cihazda giriş yapıldıktan sonra): sunucuda ve cihazda
16 canlı işlem, kimlik farkı 0, `created_at` ayrışması 0, ve iki tarafın
türettiği **sıralama birebir aynı**. P1.2'nin kalan dar durumu kapandı.

Ayrıca güvenlik danışmanının işaretlediği `kefe_lww_guard` `search_path` uyarısı
giderildi (`harden_kefe_lww_guard_search_path`). Açık kalan tek uyarı, panodan
açılması gereken "Leaked Password Protection" ayarı.

### P1.3 · Masaüstü ve tablette ana hedef ilerlemesi tüm portföyü sayıyor — ✅ ÇÖZÜLDÜ

`SummaryScreenDesktop.kt:214` · `SummaryScreenTablet.kt:250`

`SummaryViewModel` `mainGoalWealth`'i katı atamayla hesaplıyor ve yorumu açık:
*"Katı atama: ana hedef ilerlemesi YALNIZ kendine atanan varlıklardan; atama
yoksa 0"*. Telefon düzeni bunu kullanıyor; masaüstü ve tablet düzenleri
`state.mainGoalWealth` alanını **hiç okumuyor**, yerine `totals.totalValue`
geçiyor.

Aynı veriyle aynı kart üç düzende iki farklı yüzde gösteriyor. Atama hiç
yapılmamışsa telefon %0 derken masaüstü portföyün tamamını hedefe sayıyor — bu,
bilerek kaldırıldığı belgelenen "atama yoksa tüm birikim sayılır" davranışının
geri gelmesi demek.

**Yapıldı** (`Let only one value reach the main goal card`): iki çağrı yerini
düzeltmek hatayı mümkün kılan şekli yerinde bırakırdı, o yüzden **parametre
kaldırıldı**. Üç kart da artık durumu alıp `mainGoalWealth`'i kendi içinde
okuyor; yanlış değer geçmek derlenmiyor.

Alttaki kural `GoalAssetsTest`'te zaten sabitli (atama yoksa 0, yalnız atanan
kısım sayılır). Eksik olan bağlantıydı ve bu projede Compose düzenleri testle
kapsanmıyor — bu yüzden düzeltme iddia edilen değil **yapısal**.

Cihazda görsel doğrulama yapılamadı: yeniden kurulum uygulamayı biyometrik
kilide düşürüyor ve o ekran görüntü almayı engelliyor. Telefon yatay çevrildiğinde
868dp ile tablet düzenine geçtiği için kontrol mümkün — dikey ve yatayda hedef
kartındaki tutar/yüzde artık aynı olmalı.

### P1.4 · Hedef detayı grafiği ve katkı tablosu hedefe değil tüm portföye ait — ✅ ÇÖZÜLDÜ

`GoalDetailViewModel.kt:182-183` · `Projection.kt:57`

`goalProjection`'a **gerçekleşen** seri olarak portföy geneli
`DailySnapshot.totalValue`, **currentWealth** olarak ise katı atamayla hesaplanan
`goalWealth(...)` veriliyor. `Projection.kt`'nin kendi sözleşmesi ("İlk noktası
bugünkü birikimdir, böylece iki eğri kesintisiz birleşir") ve
`ProjectionChart.kt:29-30`'un sözleşmesi ihlal ediliyor: iki seri aynı x'te ama
farklı y'de başlıyor. "Aylık katkı geçmişi" tablosu da tüm portföyün rakamlarını
gösteriyor.

Ek olarak `GoalDetailScreen.kt:913`: gerçekleşen seri günlük, tahmin serisi
aylık, ikisi eşit genişlikte yuvalarda çiziliyor — zaman ekseni birimi karışık.

**Yapıldı** (`Keep the goal screen to the goal's own numbers`):

- **Gerçekleşen eğri kaldırıldı.** Çizilecek bir hedef geçmişi yok: fotoğraflar
  portföy geneli ve hedefin geçmişi onlardan türetilemiyor. Portföy eğrisini
  hedef başlığı altında çizmek doğru olmayan bir şey söylemek — varlık
  detayındaki eksen etiketlerinde de aynı karar verilmişti. `GoalProjection`
  artık fotoğraf **almıyor**, yani yanlış seriyi geri geçmek derlenmiyor. Günlük
  seri gidince karışık zaman ekseni de gitti; kalan her şey aylık.
- **Katkı sütunu korundu ve doğrulandı:** artık yalnız hedefin kendi
  varlıklarını sayıyor (`monthlyContributions` verilmeyen pozisyonun işlemlerini
  zaten eliyor — `ContributionsTest.bilinmeyenPozisyonunIslemiAtlanir`).
- **Ay sonu ve getiri sütunları "bilinmiyor"** olarak dönüyor. İkisi de nullable
  ve ekran zaten "—" çiziyordu; tasarım bu durumu öngörmüş. Portföyün ay sonu
  değerini hedefin rakamı gibi okutmak, hiç rakam vermemekten kötü.
- Hedef ekranı artık fotoğrafları dinlemiyor (kullanan kalmadı).

**Sonraki iş:** gerçek bir hedef geçmişi defterden ve `price_history`'den
yeniden kurulabilir (atanan varlıkların o gündeki miktarı × o günkü fiyat) ve
hem eğriyi hem ay sonu sütununu geri getirir. Ayrı bir iş olarak duruyor.

### P1.5 · Kur yokken TL toplamı dolar/euro/gram diye gösteriliyor — ✅ ÇÖZÜLDÜ

`ui/screens/summary/SummaryViewModel.kt:275-277` · `SummaryUiState.kt:36-43, 95`

`usdTry = board.byKey("usd_try")?.ask ?: 1.0` (eur ve gold_gram aynı). Başlangıç
değeri de `UnitRates(1.0, 1.0, 1.0)`. `formatTotal` içindeki `safeDiv` yalnız
`rate <= 0` durumunda devreye giriyor — eksik kur 1.0'a düştüğü için koruma
**hiç çalışmıyor**.

Fiyat tahtası yüklenmeden "$" çipine dokunulursa ₺3.180.400 → **"$ 3.180.400"**
yazıyor (doğrusu ~$51.297). Ekran `Ready` durumuna pozisyonlarla geçtiği, fiyat
beklenmediği için bu pencere gerçekten yaşanıyor. `safeDiv`'in varlığı eksik
kurda "—" gösterme niyetini kanıtlıyor; 1.0 yedeği bu niyeti boşa çıkarıyor.

**Yapıldı** (`Say "—" when a rate has not arrived yet`): `UnitRates` alanları
nullable oldu — hedef editöründe (P0.5) uygulanan kalıbın aynısı. Bilinmeyen kur
"—" yazıyor ve çipi kilitli çiziliyor, yani hesaplanamayacak bir çevrim
seçilemiyor. TL hiç beklemiyor: kuru her zaman belli. İşe yaramayan `safeDiv`
kaldırıldı. `UnitRatesTest` (8 test) eklendi — eski 1.0 yedeğiyle
`TL tutari dolar diye yazildi: $ 3.180.400` diye düşüyor.

### P1.6 · Elle girilen fiyat kaynağın günlük değişimini taşımaya devam ediyor — ✅ ÇÖZÜLDÜ

`data/repository/SqlDelightPriceRepository.kt:117-124`

Manuel bindirme `weekChangePercent` ve `monthChangePercent`'i null'luyor ama
`changePercent` ve `quoteDate`'i **dokunulmadan bırakıyor**. `Valuation.kt:123`
kapısı `quoteDate == today` olduğu için, önbellek o gün tazelendiyse (uygulama
açılınca olağan) kaynağın yüzdesi kullanıcının manuel fiyatına uygulanıp
"bugünkü getiri"ye giriyor.

`ILERLEME.md §35` kuralı açık: *"elle girilen fiyatın günlük hareketi yoktur…
doğru katkısı sıfır."* 100 gr altın manuel ₺6.500 girildiğinde
+₺13.618 sahte günlük getiri görünüyor. Piyasa ekranı manuel satırda değişimi
gizliyor (`MarketViewModel:202`) ama portföy toplamı gizlemiyor — ekran ile
toplam da çelişiyor.

**Yapıldı** (`Stop a manually entered price from carrying the source's daily
move`): bindirme artık `changePercent` ve `quoteDate`'i de temizliyor.
`quoteDate = null` kodun bu iş için zaten belgelediği mekanizma — "günü
bilinmeyen kotasyon bugün sayılmaz". `ManualPriceChangeTest` (3 test) eklendi;
eski kodda günlük katkı %2,14 çıkıyor.

### P1.7 · Gram/dolar cinsinden hedef piyasayla güncellenmiyor — ✅ ÇÖZÜLDÜ (vaat düzeltildi)

`domain/model/Goal.kt:42` · `GoalsViewModel.kt:208, 233`

`goal.amount` kaydedilirken TL'ye çevrilip **donduruluyor**. `goal.unit` alanı tüm
kod tabanında yalnız editörde, kalıcılıkta ve senkronda okunuyor — ilerleme,
projeksiyon, kilometre taşları ve senaryo hesaplarının **hiçbiri** birimi ve
güncel kuru dikkate almıyor. `GoalDetailViewModel`'de `goal.unit` referansı hiç
yok.

Gram 5.000 TL iken 400 gr hedef → 2.000.000 TL yazılır. Gram 7.500'e çıkınca
hedefin gerçek karşılığı 3.000.000 olmalı ama payda 2.000.000 kalıyor;
400 gramlık birikim **%150** gösteriliyor.

Bu, `GoalEditSheet.kt:259`'da kullanıcıya verilen sözle doğrudan çelişiyor:
*"Hedefi altın veya dolar cinsinden sabitlerseniz hedef de piyasayla birlikte
güncellenir."*

**Yapıldı** (`Say what the goal unit actually does`) — **iki seçenekten ikincisi
uygulandı.** Bilgi kutusu artık ne olduğunu yazıyor: tutarı gram/dolar cinsinden
girebilirsiniz, bugünkü kurla TL'ye çevrilip kaydedilir ve TL tutarı sonradan
piyasayla değişmez. `Goal.amount` ve `Goal.unit` modelde aynı şekilde
belgelendi. `GoalUnitTest` (3 test) kararı **yazılı** hale getiriyor: birim
ilerlemeyi, kilometre taşlarını ve projeksiyonu etkilemiyor.

**Neden bu seçenek:** hedefi gerçekten altına/dolara çapalamak bir hata
düzeltmesi değil, bir **özellik**. Tutarın birim cinsinden saklanmasını ve
paydanın okuma anında güncel kurla çevrilmesini ister; bu da kalıcılığa,
senkrona, yedeğe ve hedef çizen her ekrana (~20 çağrı yeri) dokunur. Ayrıca
mevcut TL-dışı hedefler için geçmiş kur bilinmediğinden göç doğru yapılamaz.
Denetim kapsamında doğru olan, uygulamanın **tutmadığı bir sözü vermemesi**.
Çapa istenirse `GoalUnitTest` düşerek yapılacak işi işaret edecek.

---

## P2 — Tutarsızlık ve kenar durumlar — ✅ TAMAMI ÇÖZÜLDÜ

Yirmi iki kusurun hepsi kapatıldı. Her başlığın altında ne yapıldığı ve
**neden öyle** yapıldığı yazıyor.

### Hedef atama defteri — ✅ ÇÖZÜLDÜ

| # | Dosya | Kusur |
|---|---|---|
| ~~206~~ | `GoalAssets.kt:163` | **SATIŞ** işleminde "başka hedef" dalı `isSell`'i hiç dikkate almıyor: satış kaydında başka hedef seçmek o hedefin ilerlemesini **artırıyor** |
| ~~200~~ | `GoalAssets.kt:31` | `effectiveQuantity` kırpması okuma anında yapılıp saklanan miktarı düşürmüyor; kırpma monoton değil, pozisyon miktarı yükselince hedef eski büyük miktarı yeniden saymaya başlıyor |
| ~~11~~ | `AddTransactionViewModel.kt:721` | Düzenleme `applyGoalSelection`'ı ikinci kez uyguluyor, eski kaydın etkisi geri alınmıyor: satış düzenlemede hedef kalıcı olarak eksik sayıyor |
| ~~12~~ | `SqlDelightPortfolioRepository.kt:247` | İşlem silme atama etkisini geri almıyor |

**Yapıldı** (`Give a transaction back what it took from a goal`): dördü tek bir
kök nedene bağlanıyordu — atama her kayıtta artırılıp azaltılıyor ama hiçbir
yerde *bu kaydın katkısı neydi* yazmıyordu.

Katkı artık kaydın kendi alanı: `transactions.goalId` + `goalDelta` (9.sqm,
Supabase'de de aynı iki kolon). Silme `-goalDelta` uyguluyor. Düzenleme zaten
"önce yeni kaydı yaz, sonra eskisini sil" sırasıyla çalıştığı için doğru sonuca
kendiliğinden varıyor.

**Delta neden, "önceki değeri geri yaz" neden değil:** mutlak geri yazma yalnız
son kayıt geri alınırsa doğrudur; araya başka kayıt girerse eski bir değeri
diriltir. Delta toplanabilir, sıradan bağımsız.

**Satış artık seçiciye bakmıyor.** Satış ancak varlığın *içinde bulunduğu*
hedeften düşebilir. Seçici "Hedefsiz" dese bile düşürüyor — kırpma böylece
kalıcılaşıyor ve #200'ün monotonluk sorunu ortadan kalkıyor: 10'un 6'sı
satılınca hedef 4 sayıyor ve sonradan 4 tane daha alınsa da 4 kalıyor.

**Kabul edilen sınır:** varlığı başka hedefe taşıyan bir kayıt geri alınırsa
varlık eski hedefine dönmez, atamasız kalır. Eski hedefin adı hiçbir yerde
saklanmıyor ve saklamak bütün bir geri-alma günlüğü demek olurdu.
`GoalAssetsTest` + `GoalAssignmentRevertTest` bu kararı yazılı tutuyor.

### Hedef durum alanları — ✅ ÇÖZÜLDÜ

- ~~**`GoalStatus.Overdue` hiç üretilmiyor**~~ — **turetildi.** Onu yazacak tek
  sorgu hiç çağrılmıyordu; tarihi geçmiş gerçek bir hedef ekranda hiç "gecikti"
  görünmüyordu. Artık `Goal.isOverdue(today)`. Saklamak zaten yanlıştı: gün
  dönünce bayatlar. Enum değeri kaldırıldı, kalmış satırlar 10.sqm ile Active'e
  çekildi (kolon adaptörü tanımadığı bir metinle karşılaşırsa hedefler hiç
  okunamazdı).
- ~~**`estimatedArrival` hiç hesaplanmıyor**~~ — **hesaplandı.** Yalnız okunuyor,
  kendine kopyalanıyor ve geri yüklemede boşaltılıyordu; kart herkese "Tahmini
  varış henüz hesaplanmadı" diyordu. `goalProjection` bunu zaten biliyor;
  ekranlar ona soruyor. Aylık katkı yoksa varış yoktur ve ekran bunu açıkça
  yazıyor. Kalıcı alan kaldırıldı — birikim her gün değişiyor, saklanan tahmin
  ertesi gün bayat.
- ~~**`GoalAllocation` ölü alan**~~ — **kaldırıldı.** Kalıcı, senkronda, yedekte
  ve editörde taşınıyordu ama hiçbir hesap okumuyordu; iki seçenek arasında
  sayısal fark sıfırdı. Editördeki toggle daha önce kaldırılmıştı. Sunucudaki
  kolon NOT NULL olduğu için DTO alanı sabit değerle gönderilmeye devam ediyor.
- ~~**Ana hedef tekliği tamamlanmışları atlıyor**~~ — **düzeltildi.** Bayrak
  yalnız açık hedeflerde temizleniyordu; tamamlanmış bir hedef ana hedef olarak
  kalabiliyordu. `otherGoalCount` de "açık sayıdan bir eksik" diyordu — ana
  hedef tamamlanmışsa o sayıda zaten yok, eksiltmek sayıyı bir düşürüyordu.
  Artık ana hedefin kendisi dışlanıyor.

**Not:** üç eski kolon (`allocation`, `estimatedYear/Month/Day`) tabloda
bırakıldı, hiçbir sorgu yazmıyor. SQLite'ta kolon düşürmek tabloyu yeniden
kurmak demek — veri taşıyan bir tabloda gereksiz risk.

### Ücret (fee) yönü — ✅ ÇÖZÜLDÜ

`total = quantity * unitPrice + fee` işlemin yönüne bakmıyordu. Uygulamanın
bütün defter matematiği satışta ücreti **hasılattan düşüyor** (`costBasis`,
`xirr`). 1 adet @150, fee 10 satış: ekranda **160**, defterde **140**. "Toplam"
kutusunun yorumu "Bu rakam KAYDEDİLECEK tutar" diyor ama değildi.

**Yapıldı** (`Take the fee off a sale instead of adding it to it`): üç yerde de
yön okunuyor (`Transaction.total`, `AddTransactionUiState.total`, silme
aktivitesi). Rakam Aktivite akışına da yazıldığı için sadece bakılan değil,
saklanan bir sayıydı. `FeeDirectionTest`.

### Dönemsel değişim ve tazelik — ✅ ÇÖZÜLDÜ

| # | Dosya | Kusur |
|---|---|---|
| ~~79~~ | `ChangePeriod.kt:26` | ✅ Piyasa ekranı "Gün"de ham `changePercent` okuyor, Varlıklar/Özet ise kotasyon-günü kuralından geçiriyor |
| ~~62~~ | `SqlDelightPriceRepository.kt:140` | ✅ "Sıfırı verilmedi saymak yanlış tetiklenemez" iddiası doğru değil |
| ~~63~~ | `PriceChange.kt:48` | ✅ Türetilen "günlük" pencere `[bugün-4, bugün-1]` |
| ~~65~~ | `SqlDelightPriceRepository.kt:153` | ✅ Tazelik en yeni satırdan: kısmi çekimde tek taze satır bütün bayatları maskeliyor |
| ~~81~~ | `Position.kt:19` | ✅ `dailyChangePercent` non-null olduğu için "Gün" hiç "—" olamıyor |
| ~~67~~ | `SqlDelightPriceRepository.kt:252` | ✅ Geçmiş satırı kotasyonun işlem günüyle değil çekim günüyle yazılıyor |
| ~~68~~ | `SqlDelightPriceRepository.kt:209` | ✅ Saat geriye giderse yenileme kilitleniyor |

**Yapıldı** (`Stop one fresh price from speaking for the whole board`):

- **#65** — tazelik artık **en eski** satıra bakıyor. Etiket bütün tahta için
  konuşuyor; en eski satır bayatsa tahta bayattır.
- **#68** — saat geriye gidebilir (saat dilimi, elle düzeltme, ağ senkronu).
  Geçen süre negatif çıkınca "az önce çekildi" sayılıyor ve ekrana "86.400 sn
  sonra deneyin" yazılıyordu; yenileme gerçek zaman damgayı yakalayana kadar
  kilitliydi. Negatif süre artık "az önce" sayılmıyor.
- **#67** — geçmiş satırı **kotasyonun işlem gününe** yazılıyor. Pazar günü
  çekilen bir hisse fiyatı cumaya aittir; bugüne yazmak cumartesi ve pazar için
  uydurma satırlar üretiyor, o satırlar sonra "dünkü fiyat" diye okunup günlük
  değişimin ölçüsünü kaydırıyordu.
- **#62** — kod yorumu gerçeğe uyduruldu. Yorum "artık bütün altın türlerine
  Change geliyor" diyordu; o ölçüm **pazar günü** yapılmıştı ve gördüğümüz
  değerler cumadan kalmaydı. İş günü ölçümünde 86 sembolden 16'sı sıfır ve 11'i
  sikke altın — **yedek yol hâlâ gerekli**. Yorum artık "sıfır = verilmedi"
  saymanın bilinen sınırını da yazıyor: geçmiş tablosu günün kapanışını değil,
  uygulamanın o gün ilk açıldığı andaki fiyatı tutuyor.

`PriceFreshnessTest`.

### Fiyat kaynakları — ✅ ÇÖZÜLDÜ

**Yapıldı** (`Guard the price feeds against their own bad days`):

- **TEFAS sıfır fiyat** — aynı fonksiyon `history` üretirken sıfır satırları
  zaten eliyor, diğer üç kaynakta da koruma var, burada yoktu. Sıfır gelirse
  sağlam önbellek fiyatının üzerine 0 yazılıp fon pozisyonu sıfırlanıyor,
  **%-100** gösteriliyordu.
- **`StockApi` indeks kayması** — zaman damgası dizisinde `mapNotNull`, kapanış
  dizisinde `map` vardı; tek bozuk damga iki diziyi kaydırıyor ve o noktadan
  sonraki her kapanış bir önceki günün tarihine yazılıyordu. İkisi de artık
  `map`, eşleşme indeks bazında.
- **`Retry.kt`** — adına ve kendi belgesine uymuyordu: 404 gibi kalıcı yanıtlar
  üç kez deneniyor, `CancellationException` de yutuluyordu (ekran kapandığında
  iptal edilen istek ölmeyi reddedip iki kez daha ağa çıkıyordu).
- **Yabancı hissenin yüzdesi** — kaynağın rakamı kendi para biriminde. New
  York'ta %2 yükselen bir hisse, dolar %1 düştüyse TL'de %1 yükselmiştir;
  `todayChange()` o yüzdeyle TL değerini geriye çözdüğü için kur hareketi
  sessizce kayboluyordu. Geçmiş tablosu TL fiyat tuttuğundan doğru cevabı zaten
  biliyor, artık o tercih ediliyor. İlk günde geçmiş yoksa kaynağın rakamına
  düşülüyor — kur hareketini saymaz ama hiçbir şeyden iyidir.

`PriceSourceGuardTest`.

### Tarih — ✅ ÇÖZÜLDÜ

**Yapıldı** (`Keep the calendar from inventing days`):

- **`plusMonths` ay sonu kıskacı** — `31 Ocak + 1 ay = 31 Şubat` üretiyordu.
  Hiçbir şey patlamıyor: `toEpochDay` günü 1..31'e çekip kabul ediyor ve
  sessizce başka bir güne taşıyor, `dateKeyOf` var olmayan bir anahtar yazıyor,
  ve projeksiyon üzerinden bu geçersiz tarih `goals` tablosuna kalıcı
  yazılabiliyordu. Gün artık ayın uzunluğuna kısılıyor (artık yıl dahil).
- **`parseIsoDate`** aynı sebeple `2026-02-30` kabul ediyordu.
- **Hedef tarihi seçici** gün bazında kıyaslıyordu ama yalnız ay-yıl gösteriyor:
  hedefin günü ayın başındaysa (varsayılan 1) **içinde bulunulan ay hiç
  seçilemiyordu**. Kıyas ay bazına alındı.
- **Saat dilimi** — kotasyon günleri kaynağın takviminden geliyor ve kaynakların
  hepsi Türkiye saatiyle çalışıyor; "bugün" ise cihazın takviminden geliyordu.
  Türkiye dışındaki bir cihazda akşam saatlerinde ikisi ayrışıyor, kotasyon-günü
  kapısı kapanıyor ve bütün altın/döviz satırlarının günlük katkısı 0'a
  düşüyordu. Artık bir **piyasa günü** var: duvar saatinden sabit UTC+3 ile
  türetiliyor (Türkiye 2016'dan beri yaz saati uygulamıyor). Fiyat ve değerleme
  yolları onu soruyor; **işlem tarihleri cihazın gününü kullanmaya devam
  ediyor** — kaydı giren kişinin kastettiği gün odur.

Bu, `toEpochDay`'in tersini gerektirdi; kod tabanında yoktu ve yokluğu şemaya
kadar yansımıştı (tarihler yıl/ay/gün üçlüleri halinde saklanıyor).
`DateEdgeTest`.

### Katkı tablosu — ✅ ÇÖZÜLDÜ

`Contributions.kt:62-63` — `total` **filtrelenmiş** dilim listesinden
hesaplanıyordu, oysa o liste net çıkışla kapanan sınıfları zaten atıyor (çubukta
negatif dilim çizilemez). 20.000 altın satılıp 5.000 fon alınan bir ay
"+5.000 biriktirdin" diyordu; gerçekte portföyden **net 15.000 çıkmıştı**.
Dosyanın kendi tanımı "portföye giren net para" ve getiri satırı bu rakamı
doğrudan bir para tutarı olarak kullanıyor.

**Yapıldı** (`Count what left the portfolio in the monthly total`): toplam
elenmemiş kovadan geliyor, dilimler yalnız çizilebilecek olanı gösteriyor. Eski
davranışı sabitleyen test gerekçesiyle birlikte güncellendi.

### İşlem ekleme — ✅ ÇÖZÜLDÜ

`AddTransactionViewModel.kt:784` — 1. adımda girilen gram, `quantityText` doluysa
sessizce yok sayılıyordu: 2. adıma bir kez geçildikten sonra geri dönüp gramı
düzeltmek işe yaramıyor, eski değer kalıyordu.

**Yapıldı**: gramla ölçülen varlıkta 1. adımdaki gram ile 2. adımdaki miktar
aynı sayıdır; gram değişince miktar da tazeleniyor. Kullanıcı 2. adımda miktarı
elle değiştirirse orası kendi başına kalıyor.

---

## P3 — Kozmetik ve savunma amaçlı sertleştirme — ✅ TAMAMI ÇÖZÜLDÜ

Yirmi kusurun hepsi kapatıldı, artı planın 1. aşamasında açık bırakılan
`BackupCodec` maddesi.

### Money — sayı biçimlendirmenin sözleşmesi

| Dosya | Kusur | Yapıldı |
|---|---|---|
| ~~`Money.kt:188`~~ | Yuvarlama half-up değil **half-even** (banker's) | Yarım **yukarı** yuvarlanıyor — TR finans geleneği. `kotlin.math.round` çifte yuvarlıyordu (`round(12,5) = 12`), yani ₺0,125 "₺0,12" çıkıyordu. Uygulama içinde tutarlıydı ama hiçbir yerde yazmıyordu |
| ~~`Money.kt:163`~~ | Bağıl tolerans 10 milyon TL üstünde gerçek kuruşu yutuyor | Tolerans artık gösterilebilecek en küçük birimin altında tavanlı. `1e-9 × değer` 10 milyonda tam bir kuruşa ulaşıyordu; o büyüklükte bir double'ın gerçek hatası ~2e-9, yani tolerans zaten fazlasıyla cömertti |
| ~~`Money.kt:93`~~ | `tlSigned`'da 1 TL altı gerçek değişim `+₺0` yazılıyor | `tl`'deki kural buraya da geldi. İşaret yazıldığı için sonuç daha kötüydü: "bir değişim oldu" deyip büyüklüğünü sıfır gösteren bir satır |
| ~~`Money.kt:132`~~ | `compact` sınırı: `999.950` → `"1.000B"`; M dalında `,0` bastırılmıyor | Kademe **yuvarlamadan sonra** seçiliyor. "1.000B" var olmayan bir birimdi |

**İkili tabanın kendi sınırı duruyor ve testte yazılı:** 1,005 bellekte
1,00499… olduğu için hangi kural uygulanırsa uygulansın aşağı yuvarlanır. Test
yalnız ikili tabanda **tam yarım** olan ve iki kuralın ayrıştığı değerleri
kullanıyor.

### Ekranda yuvarlanıp kaybolan haneler

| Dosya | Kusur | Yapıldı |
|---|---|---|
| ~~`SummaryScreen.kt:781`~~ | Gerçek hedef tutarı `compact`'in eksen varsayılanıyla yazılıyor | Bin aralığında bir ondalık. ₺19.587'lik hedef "20B" oluyordu — kullanıcının kendi yazdığı rakamda yarım yüzde sapma |
| ~~`GoalsScreen.kt:298`~~ | İlerleme yüzdesi yuvarlanınca hedefe ulaşılmadan **%100** yazıyor | Yuvarlama kalıyor, üstüne 99 tavanı. Aşağı kırpmak da denendi ve **sıradan değerleri bozuyor**: `progress` bir Float, 0,35 bellekte 0,34999999, yani %35 "%34" görünürdü |
| ~~`GoalEditSheet.kt:277`~~ | Gram önizlemesi 0 ondalık | Dört haneye kadar. "Hedef 0 gr altın · Bugünkü kurla ₺2.000" iki rakam aynı anda doğru olamaz |
| ~~`AddTransactionViewModel.kt:793`~~ | `stepQuantity` kayan nokta artığını alana yazıyor | Sonuç kademeye hizalanıyor. 0,1 ikili tabanda tam durmadığı için üç dokunuş `0,30000000000000004` veriyor, kullanıcı kendi yazmadığı bir kuyruğu siliyordu |

### Etiketler ve sınırsız yıllıklandırma

| Dosya | Kusur | Yapıldı |
|---|---|---|
| ~~`AssetDetailUiState.kt:77`~~ | 30 günlük ay varsayımı 360-364 günü "1 yıl" diye etiketliyor | Yıl önce 365'ten alınıyor, ay kalan günden. Otuz günlük ay her yıl beş gün hata biriktiriyordu |
| ~~`Returns.kt:100`~~ | Kısa elde tutmada sınırsız yıllıklandırma | Bir aydan kısa tutuşta cevap **yok**. Yıllıklandırmak üsse çıkarmaktır: üç günlük %2, 121'inci kuvvete çıkıyor ve `+14.213.458.746.011.397,12%` gibi rakamlar düşüyordu. Yanlış değil — **anlamsız** |
| ~~`PriceChange.kt:139`~~ | `percent = -100` girdisinde payda sıfır | Payda önce kontrol ediliyor. `previous <= 0` sonsuzu yakalamıyordu; toplam sessizce sonsuz/NaN oluyordu. Girdi bugün üretilemiyor — bu, o kapının kapalı kalması için |

### Depo ve şema

| Dosya | Kusur | Yapıldı |
|---|---|---|
| ~~`SqlDelightPortfolioRepository.kt:429`~~ | Geri yüklemede üye **adları** alınmıyor | İki adım: ekleme satırın **varlığını**, güncelleme **içeriğini** garanti eder. Kurulum iki üyeyi aynı kimliklerle tohumluyor, `INSERT OR IGNORE` çakışınca sessizce atlıyordu |
| ~~`SqlDelightPortfolioRepository.kt:748`~~ | Pozisyon silme kaydındaki tutar bayat birim fiyatla | Güncel fiyatla — elle girilen önce. `positions.unitPrice` pozisyon kurulduğunda yazılıp bir daha güncellenmiyor |
| ~~`SqlDelightPriceRepository.kt:300`~~ | `setManualPrice` `updatedAtEpochSeconds = 0` yazıyor | Gerçek damga. Sıfır, "hiç girilmedi" demekti — elle girilen bir fiyatın yaşını sorabilmek için gereken tek şey |
| ~~`Price.sq:40`~~ | `manual_prices` şeması ile `2.sqm` göçü ayrışıyor | Tablo kanonik üç kolona yeniden kuruldu (11.sqm). Göçten geçen cihazda beş, temiz kurulumda üç kolon vardı; bugün kimse dokunmadığı için görünmüyordu — **ilk dokunan sorgu cihazların yarısında patlardı** |

**İlk yazdığım geri yükleme testi hatalı kodda da geçiyordu**: `renameMember`
bir UPDATE, çakışması gereken satırı hiç yaratmıyor. Test satırı gerçekten
tohumlayacak şekilde düzeltildi ve düzeltme olmadan **düşüyor**.

Her iki göç de gerçek veriyle denendi: cihazdan alınan veritabanı kopyası ve
beş kolonlu bir taklit üzerinde; elle girilen fiyatlar taşınıyor.

### Özet ekranının kendi içindeki çelişkiler

| Dosya | Kusur | Yapıldı |
|---|---|---|
| ~~`SummaryUiState.kt:36`~~ | Hero çevrimi `ask`, portföy değeri `bid` — makas iki kez düşülüyor | İki taraf da aynı kotasyon tarafını kullanıyor. Aynı varlığın iki farklı fiyatını tek bölmede kullanmak, hangi sayının ne demek olduğunu belirsizleştirir |
| ~~`SummaryViewModel.kt:288`~~ | Aynı kotasyon Özet'te kuruşlu, Piyasa'da kuruşsuz | Hane sayısı Piyasa'nın kuralından geliyor. Ata altını Özet'te "₺45.375,74", Piyasa'da "₺45.376" yazıyordu |
| ~~`SummaryScreenDesktop.kt:482`~~ | Masaüstü/tablet legendi `collapseDonutSlices`'ı uygulamıyor | Legend halkanın çizdiği listeyi gösteriyor. Altı varlık sınıfında aynı kartın iki yarısı birbirini tutmuyordu |
| ~~`SampleData.kt:59, 208`~~ | `value ≠ quantity × unitPrice`; `todayChange` ile `todayChangePercent` tutmuyor | Değerler tam, toplamlar **pozisyonlardan türüyor**. "+₺12.400 · %0,29" ne pozisyonlarla ne kendisiyle tutuyordu (12.400 / 3.168.000 = %0,39). Daha canlı bir gün isteniyorsa doğru yer pozisyonların kendi yüzdeleri — artık gerçekten toplamı belirliyorlar |
| ~~`SummaryViewModel.kt:158`~~ | `clock.today()` yalnız veri emisyonunda okunuyor | Dakikada bir bakan, **yalnız gün değişince** emisyon veren bir sayaç. Gece boyunca açık kalan uygulama dünün ayına göre hesaplamaya devam ediyordu |

### Plandan devreden madde

`BackupCodec` CSV sayıları `toString()` ile yazıyordu ve büyük/çok küçük
değerlerde üsse geçiyordu: 12.500.000'lik bir işlem "1,25E7" olarak çıkıyor ve
tr-TR Excel'de sayı değil **metin** olarak açılıyordu. Aynı kusur hedef
tutarında veri kaybına yol açmıştı (P0.4); burada dosya dışarıya gittiği için
sonuç sessiz ama kalıcı. Altı hane, sondaki sıfırlar yazılmıyor.

---

## Çürütülenler

Hakem ajanların somut kod kanıtıyla reddettiği 21 iddia. Öne çıkanlar:

- **`todayChange()`'de -100 koruması yok** — mekanik doğru ama girdi
  üretilemiyor: `PriceChange.kt:78, 91` `latest > 0` ve `reference.price > 0`
  şartı koyduğu için türetilen yüzde matematiksel olarak kesin `> -100`; kaynak
  yollarında da `price <= 0` elemesi var; TEFAS -100 üretse bile
  `Valuation.kt:94` pozisyonu hiç değerlemiyor. *(Yine de P3'te savunma amaçlı
  kayıtlı.)*
- **`Money.format` NaN/Infinity korumasız** — her bölme noktası zaten korumalı;
  spekülatif.
- **`delta()` işareti yuvarlamadan önce seçiyor** — bilinçli tasarım: `+0,00%`
  (küçük ama pozitif) ile `0,00%` (gerçek sıfır) ayırt edilebilir kalıyor.
- **`SummaryViewModel` yarış durumu** — tüm toplayıcılar aynı `viewModelScope` /
  `Dispatchers.Main.immediate` üzerinde; araya coroutine giremiyor.
- **`xirr` bisection üst sınırı 10.0** ve **çok köklü akış** — Newton pratikte
  yakınsıyor; davranış muhafazakâr (yanlış sayı yerine hiç sayı yok).
- **`JsonPrimitives` binlik ayraç**, **TCMB `<Unit>`**, **`parseIsoDate` geçersiz
  gün** — bugünkü kaynak biçimleriyle tetiklenmiyor.

---

## Düzeltme planı

Sıra bilinçli: her aşama bir öncekinin açtığı zemini kullanıyor.

**Aşama 1 — Veri kaybını durdur (P0)**
1. ✅ `toTurkishDoubleOrNull` + `parseTrNumber` tek ortak ayrıştırıcıda
   birleştir; Piyasa alanını ham metinle tohumla · testleri yaz
2. ✅ `upsertGoal`'ü iki adıma böl; `goals` üzerindeki tüm `INSERT OR REPLACE`'i
   temizle; yanlış yorumu düzelt; test veritabanını FK açık kur
3. ✅ `assetKeyOf`'u ayar duyarlı yap; Bullion'a ayrı anahtar; testi
   `newPositionId()` üzerinden yaz
4. ✅ `rawAmount`/`parseAmount` çiftini bilimsel gösterime karşı kapat
   (BackupCodec hâlâ açık — P3'teki `BackupCodec.kt:65` ayrıca kapatılmalı)
5. ✅ `rateOf`'u null'lanabilir yap; kur yokken TL dışı birim kilitli
6. ✅ Yumuşak silmede `goal_assets` mezar taşlama + yedek filtresi

**Aşama 2 — Tek doğruluk kaynağı (P1)** — ✅ tamamı
7. ✅ Aynı gün sırası için kalıcı `createdAt` kolonu + göç; dört akışı
   (ekran, düzenleme, senkron, yedek) ona bağla
8. ✅ `AssetDetailViewModel`'i compute sorgusuna geçir
9. ✅ `mainGoalWealth`'i masaüstü/tablet düzenlerine bağla
10. ✅ Hedef projeksiyonunda gerçekleşen seriyi de hedef bazına indir
11. ✅ `UnitRates` null'lanabilir, `formatTotal` "—" gösteriyor
12. ✅ Manuel bindirmede `quoteDate = null` ve `changePercent = null`
13. ✅ Vaat kaldırıldı (canlı çapa bir ÖZELLİK, hata değil — gerekçe P1.7'de)

**Aşama 3 — Tutarsızlıkları kapat (P2)** — ✅ tamamı
14. ✅ `Transaction.total`'ı yön duyarlı yap
15. ✅ Hedef atama defterini yeniden kur (satış dalı, kırpma monotonluğu,
    düzenleme ve silmede geri alma) — katkı kaydın kendi alanında (9.sqm)
16. ✅ `Overdue` ve `estimatedArrival` hesaplandı, `GoalAllocation` kaldırıldı
17. ✅ Piyasa ekranı kotasyon-günü kuralına bağlandı; `dailyChangePercent`
    nullable
18. ✅ TEFAS sıfır fiyat koruması; `retryOnTransient` gerçekten transient;
    `StockApi` indeks kayması; yabancı hissenin TL değişimi
19. ✅ `plusMonths` ay sonu kıskacı; `parseIsoDate` ay uzunluğu; piyasa günü
20. ✅ `Contributions.total`'ı filtreden önce hesapla

**Aşama 4 — Cila (P3)** — ✅ tamamı
21. ✅ `Money` yuvarlama sözleşmesi yarım-yukarı olarak netleşti ve belgelendi;
    tolerans tavanlandı; ondalık/sınır davranışları düzeltildi
22. ✅ Örnek veri kendi içinde tutarlı (toplamlar pozisyonlardan türüyor);
    `manual_prices` şema ayrışması 11.sqm ile kapatıldı
23. ✅ `BackupCodec` CSV'si bilimsel gösterime düşmüyor (1. aşamadan devreden)

**Her aşamada:** düzeltilen her kusur için önce başarısız olan bir test yaz.
Denetimde ortaya çıkan üç test, kod yanlışken de geçiyordu
(`ValuationTest.ayarPozisyonuAnahtardanAYIRIR`, `TefasDateTest`, FK'siz kurulan
`SoftDeleteTest`) — yanlış güven veren test, testsizlikten daha tehlikeli.

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

Kaynak 9 Ağustos 2026'da yeniden ölçüldü. **Yedek yolun dayandığı varsayım artık
geçerli değil:** `today.json` bütün altın türlerine `Change` gönderiyor —
CEYREKALTIN 2.09, YARIMALTIN 2.09, TAMALTIN 2.09, ATAALTIN 2.09, YIA 2.09,
18AYARALTIN 2.09, 14AYARALTIN 2.09, GRA 2.59, HAS 2.59, GUMUS 3.57. 86 sembolden
yalnızca biri sıfır.

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
eklendi (göç: `add_created_at_to_transactions`), damga her işlemle push ediliyor
ve pull'da geri okunuyor. Kolon eklenmeden önce yazılmış satırlar 0 dönüyor ve
istemci `updated_at`'e düşüyor — o değer bütün cihazlarda aynı olduğu için sıra
yine uyuşuyor. P1.2'nin kalan dar durumu böylece kapandı.

### P1.3 · Masaüstü ve tablette ana hedef ilerlemesi tüm portföyü sayıyor

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

### P1.4 · Hedef detayı grafiği ve katkı tablosu hedefe değil tüm portföye ait

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

### P1.5 · Kur yokken TL toplamı dolar/euro/gram diye gösteriliyor

`ui/screens/summary/SummaryViewModel.kt:275-277` · `SummaryUiState.kt:36-43, 95`

`usdTry = board.byKey("usd_try")?.ask ?: 1.0` (eur ve gold_gram aynı). Başlangıç
değeri de `UnitRates(1.0, 1.0, 1.0)`. `formatTotal` içindeki `safeDiv` yalnız
`rate <= 0` durumunda devreye giriyor — eksik kur 1.0'a düştüğü için koruma
**hiç çalışmıyor**.

Fiyat tahtası yüklenmeden "$" çipine dokunulursa ₺3.180.400 → **"$ 3.180.400"**
yazıyor (doğrusu ~$51.297). Ekran `Ready` durumuna pozisyonlarla geçtiği, fiyat
beklenmediği için bu pencere gerçekten yaşanıyor. `safeDiv`'in varlığı eksik
kurda "—" gösterme niyetini kanıtlıyor; 1.0 yedeği bu niyeti boşa çıkarıyor.

### P1.6 · Elle girilen fiyat kaynağın günlük değişimini taşımaya devam ediyor

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

### P1.7 · Gram/dolar cinsinden hedef piyasayla güncellenmiyor

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

**Düzeltme:** ya hedefi birim cinsinden sakla (`amountInUnit` + `unit`) ve payda
okuma anında güncel kurla çevrilsin, ya da bilgi kutusundaki vaat kaldırılsın.

---

## P2 — Tutarsızlık ve kenar durumlar

### Hedef atama defteri

| # | Dosya | Kusur |
|---|---|---|
| 206 | `GoalAssets.kt:163` | **SATIŞ** işleminde "başka hedef" dalı `isSell`'i hiç dikkate almıyor: satış kaydında başka hedef seçmek o hedefin ilerlemesini **artırıyor** |
| 200 | `GoalAssets.kt:31` | `effectiveQuantity` kırpması okuma anında yapılıp saklanan miktarı düşürmüyor; kırpma monoton değil, pozisyon miktarı yükselince hedef eski büyük miktarı yeniden saymaya başlıyor |
| 11 | `AddTransactionViewModel.kt:721` | Düzenleme `applyGoalSelection`'ı ikinci kez uyguluyor, eski kaydın etkisi geri alınmıyor: satış düzenlemede hedef kalıcı olarak eksik sayıyor |
| 12 | `SqlDelightPortfolioRepository.kt:247` | İşlem silme atama etkisini geri almıyor |

### Hedef durum alanları

- **`GoalStatus.Overdue` hiç üretilmiyor** (`Goal.kt:35`) — yalnız örnek veride
  var; `updateGoalStatus` sorgusu hiç çağrılmıyor.
- **`estimatedArrival` hiç hesaplanmıyor** (`GoalsViewModel.kt:241`) — yalnız
  okunuyor/yazılıyor/kendine kopyalanıyor. Üreten kod yolu yok. Buna karşılık
  `ILERLEME.md:349-350` iş kuralını "hesaplanır" diye kayda geçirmiş. Geri
  yükleme alanı boşaltıyor.
- **`GoalAllocation` (AllWealth/FixedShare) ölü alan** (`Goal.kt:30`) — kalıcı,
  senkronda, yedekte ve editörde taşınıyor ama hiçbir hesap onu okumuyor; iki
  seçenek arasında sayısal fark sıfır. Yorumlar hâlâ eski kuralı anlatıyor.
- **Ana hedef tekliği tamamlanmışları atlıyor** (`GoalsViewModel.kt:246`,
  `SummaryViewModel.kt:150, 171`) — tamamlanmış bir `isMain` hedef ikinci ana
  hedef olarak kalabiliyor; `otherGoalCount` off-by-one.

### Ücret (fee) yönü

`Transaction.kt:16` · `AddTransactionUiState.kt:342` ·
`SqlDelightPortfolioRepository.kt:273, 635`

`total = quantity * unitPrice + fee` işlemin yönüne bakmıyor. Uygulamanın bütün
defter matematiği satışta ücreti **hasılattan düşüyor** (`CostBasis.kt:86`,
`Returns.kt:111`). 1 adet @150, fee 10 satış: ekranda **160**, defterde **140**.
Aynı işlem iki ekranda iki farklı tutarla görünüyor; "Toplam" kutusunun yorumu
"Bu rakam KAYDEDİLECEK tutar" diyor ama değil.

### Dönemsel değişim ve tazelik

| # | Dosya | Kusur |
|---|---|---|
| ~~79~~ | `ChangePeriod.kt:26` | ✅ **ÇÖZÜLDÜ** — Piyasa ekranı "Gün"de ham `changePercent` okuyor, Varlıklar/Özet ise kotasyon-günü kuralından geçiriyor. Hafta sonu iki ekran farklı |
| 62 | `SqlDelightPriceRepository.kt:140` | "Sıfırı verilmedi saymak yanlış tetiklenemez" iddiası doğru değil: `price_history` günün kapanışını değil uygulamanın açıldığı andaki fiyatı tutuyor, kaynağın geçerli sıfırı eziliyor |
| ~~63~~ | `PriceChange.kt:48` | ✅ **ÇÖZÜLDÜ** — Türetilen "günlük" pencere `[bugün-4, bugün-1]`; 4 güne kadar hareket tek "günlük" değişim olarak bugüne yazılıyor — §35 ile çelişiyor |
| 65 | `SqlDelightPriceRepository.kt:153` | Tazelik en yeni satırdan hesaplanıyor: kısmi çekimde tek taze satır bütün bayat satırları maskeliyor, "Fresh" kalıyor |
| ~~81~~ | `Position.kt:19` | ✅ **ÇÖZÜLDÜ** — `dailyChangePercent` non-null olduğu için "Gün" penceresi hiç "—" olamıyor; bilinmeyen sıfır sayılıp grup yüzdesini sulandırıyor |
| 67 | `SqlDelightPriceRepository.kt:252` | Geçmiş satırı kotasyonun işlem günüyle değil çekim günüyle yazılıyor |
| 68 | `SqlDelightPriceRepository.kt:209` | Saat geriye giderse yenileme kilitleniyor, anlamsız bekleme süresi yazılıyor |

### Fiyat kaynakları

- **TEFAS sıfır fiyat kontrolü yok** (`TefasApi.kt:74`) — aynı fonksiyon `history`
  üretirken sıfır satırları eliyor, diğer üç API'de de koruma var. Sıfır gelirse
  sağlam önbellek fiyatının üzerine 0 yazılıp fon pozisyonu sıfırlanıyor,
  **%-100** gösteriliyor.
- **Yabancı hissenin yüzdesi kendi para biriminde ama TL hesabına giriyor**
  (`LivePriceRemoteDataSource.kt:170`) — yorum "yüzde çevrilmez, gösterilendir"
  diyor ama `todayChange()` o yüzdeyle TL değerini geriye çözüp TL farkı
  topluyor.
- **`StockApi.kt:118`** zaman damgası dizisinde `mapNotNull`, kapanış dizisinde
  `map` → indeksler kayabiliyor.
- **`Retry.kt:26`** `retryOnTransient` adının ve kendi belgesinin aksine **tüm**
  istisnaları tekrarlıyor; 404 gibi kalıcı yanıtlar üç kez deneniyor,
  `CancellationException` de yutuluyor.

### Tarih

- **`KefeDate.plusMonths` ay sonu kıskacı yapmıyor** (`:64`) — `31 Ocak + 1 ay =
  31 Şubat`. `toEpochDay` bunu `coerceIn` ile kabul edip başka bir güne taşıyor
  (`:50`), `dateKeyOf` var olmayan bir anahtar yazıyor. `Projection.kt:83`
  üzerinden geçersiz tarih `goals` tablosuna kalıcı yazılabiliyor.
- **Saat dilimi** (`Valuation.kt:124`) — `quoteDate` kaynağın takviminden
  (Türkiye/borsa), `today` cihazın yerel takviminden. Türkiye dışındaki cihazda
  akşam saatlerinde bütün altın/döviz satırlarının günlük katkısı 0'a düşüyor.
- **`parseIsoDate`** (`TefasApi.kt:138`) ay uzunluğunu doğrulamıyor
  (`2026-02-30` kabul).
- **Hedef tarihi** (`GoalsViewModel.kt:103`) gün bazında kıyaslanıyor ama seçici
  ay-yıl granülerliğinde: içinde bulunulan ay hiç seçilemiyor.

### Katkı tablosu

`Contributions.kt:62-63` — `slices` filtresi `it.value > 0.0` net çıkışla
kapanan sınıfları atıyor ama `total` bu **filtrelenmiş** listeden hesaplanıyor.
`total` artık dosyanın kendi tanımladığı "portföye giren net para" değil; bu
değer getiri formülünde kullanıldığı için doğrudan bir para rakamına dönüşüyor.

### İşlem ekleme

`AddTransactionViewModel.kt:784` — 1. adımda girilen gram, `quantityText` doluysa
sessizce yok sayılıyor. Geri dönüp gramı düzeltmek işe yaramıyor, eski değer
kalıyor.

---

## P3 — Kozmetik ve savunma amaçlı sertleştirme

| Dosya | Kusur |
|---|---|
| `Money.kt:188` | Yuvarlama half-up değil **half-even** (banker's): `Money.tl(0.125)` → `₺0,12`. Uygulama içi tutarlı ama TR finans geleneği half-up ve hiçbir yerde belgelenmemiş |
| `Money.kt:163` | Bağıl tolerans (`1e-9 × a`) 10 milyon TL üstünde gerçek kuruşu yutuyor — `ILERLEME §24` "kuruş hep görünür" der |
| `Money.kt:93` | `tlSigned`'da 1 TL altı gerçek değişim `+₺0` yazılıyor |
| `Money.kt:132` | `compact` sınırı: `999.950` → `"1.000B"`; M dalında `,0` bastırılmıyor |
| `SummaryScreen.kt:781` | Gerçek hedef tutarı `compact`'in eksen varsayılanıyla (0 ondalık) yazılıyor |
| `GoalsScreen.kt:298` | İlerleme yüzdesi 0 ondalıkla yuvarlanınca hedefe ulaşılmadan **%100** yazıyor |
| `GoalEditSheet.kt:277` | Gram önizlemesi 0 ondalık: "Hedef 0 gr altın · Bugünkü kurla ₺2.000" |
| `AddTransactionViewModel.kt:793` | `stepQuantity` kayan nokta artığını alana yazıyor: `0,30000000000000004` |
| `AssetDetailUiState.kt:77` | `holdingLabel` 30 günlük ay varsayımıyla 360-364 günü "1 yıl" diye etiketliyor |
| `Returns.kt:100` | Kısa elde tutmada sınırsız yıllıklandırma — `ILERLEME.md` sonunda `+14.213.458.746.011.397,12%` örneğiyle zaten kayıtlı |
| `PriceChange.kt:139` | `percent = -100` girdisinde payda sıfır (girdi bugün üretilemiyor; savunma amaçlı) |
| `SqlDelightPortfolioRepository.kt:429` | Geri yüklemede üye **adları** alınmıyor (`INSERT OR IGNORE`), profil isimleri sessizce kayboluyor |
| `SqlDelightPortfolioRepository.kt:748` | Pozisyon silme kaydındaki tutar bayat birim fiyatla hesaplanıyor |
| `SqlDelightPriceRepository.kt:300` | `setManualPrice` `updatedAtEpochSeconds = 0` yazıyor |
| `Price.sq:40` | `manual_prices` şeması ile `2.sqm` göçü ayrışıyor: göçten geçen cihazda fazladan kolonlar |
| `SampleData.kt:59, 208` | Örnek portföyde `value ≠ quantity × unitPrice`; `todayChange` ile `todayChangePercent` birbirini tutmuyor |
| `SummaryViewModel.kt:288` | Aynı kotasyon Özet'te kuruşlu, Piyasa'da kuruşsuz |
| `SummaryUiState.kt:36` | Hero çevrimi satış (ask) kuruyla, portföy değeri alış (bid) fiyatıyla — makas iki kez düşülüyor |
| `SummaryScreenDesktop.kt:482` | Masaüstü/tablet dağılım legendi `collapseDonutSlices`'ı uygulamıyor: halka ile legend ayrışıyor |
| `SummaryViewModel.kt:158` | `clock.today()` yalnız veri emisyonunda okunuyor; ay/gün dönümünde "Bu ay eklenen" bayatlıyor |

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

**Aşama 2 — Tek doğruluk kaynağı (P1)**
7. Aynı gün sırası için kalıcı `sequence`/`createdAt` kolonu + göç; dört akışı
   (ekran, düzenleme, senkron, yedek) ona bağla
8. `AssetDetailViewModel`'i compute sorgusuna geçir
9. `mainGoalWealth`'i masaüstü/tablet düzenlerine bağla
10. Hedef projeksiyonunda gerçekleşen seriyi de hedef bazına indir
11. `UnitRates` yedeğini 0.0 yap, `formatTotal` "—" göstersin
12. Manuel bindirmede `quoteDate = null`, `changePercent = 0.0`
13. Hedef birimini canlı tut (`amountInUnit` + `unit`) ya da vaadi kaldır

**Aşama 3 — Tutarsızlıkları kapat (P2)**
14. `Transaction.total`'ı yön duyarlı yap
15. Hedef atama defterini yeniden kur (satış dalı, kırpma monotonluğu, düzenleme
    ve silmede geri alma)
16. `Overdue` / `estimatedArrival` / `GoalAllocation` — ya hesapla ya kaldır
17. Piyasa ekranını kotasyon-günü kuralına bağla; "bilinmiyor" durumunu
    `dailyChangePercent` için de nullable yap
18. TEFAS sıfır fiyat koruması; `retryOnTransient`'i gerçekten transient yap
19. `plusMonths` ay sonu kıskacı; `toEpochDay` geçersiz günü reddetsin
20. `Contributions.total`'ı filtreden önce hesapla

**Aşama 4 — Cila (P3)**
21. `Money` yuvarlama sözleşmesini netleştir ve belgele; tolerans tabanını
    mutlak sınırla; ondalık/sınır davranışlarını düzelt
22. Örnek veriyi kendi içinde tutarlı hale getir; `manual_prices` şema
    ayrışmasını kapat

**Her aşamada:** düzeltilen her kusur için önce başarısız olan bir test yaz.
Denetimde ortaya çıkan üç test, kod yanlışken de geçiyordu
(`ValuationTest.ayarPozisyonuAnahtardanAYIRIR`, `TefasDateTest`, FK'siz kurulan
`SoftDeleteTest`) — yanlış güven veren test, testsizlikten daha tehlikeli.

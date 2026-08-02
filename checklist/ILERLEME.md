# Kefe — İlerleme

Uygulama iki kişilik: kullanıcı ve eşi. **Tek Supabase hesabı, iki cihaz aynı
e-postayla girer, her cihaz bir profile sabitlenir.** Bu karar davet akışını,
üye izinlerini ve alan adı ihtiyacını ortadan kaldırdı.

Bir adım ancak emülatörde **ve** testle doğrulandıysa işaretlenir. Ekran
görüntüsüne bakıp "olmuş" denmez.

---

## Bu tur

| # | Adım | Durum |
|---|---|---|
| 1 | Açılış animasyonu + tema uyumu | ✅ **bitti** |
| 2 | Parmak izi kilidi | ✅ **bitti** |
| 3 | Yenileme kısıtlamasının ekranda görünmesi | ✅ **bitti** |
| 4 | İki profil | ✅ **bitti** |
| 5 | Çok kullanıcılı iskeletin sökülmesi | ✅ **bitti** |
| 6 | Ayarlar temizliği ve tamamlanması | ✅ **bitti** |

## Senkron (bu tur)

Sıra bilerek "jeton önce": senkron açılınca oturum jetonu tüm birikim geçmişine
erişim demek. Düz metin jeton + açık senkron = sızıntı. Bu yüzden 8, 7'den önce.

| # | Adım | Durum |
|---|---|---|
| 8 | Jeton Keystore/Keychain'e | ✅ **bitti + canlı doğrulandı** (enc1:, eyJ yok) |
| 7 | Supabase tabloları + RLS | ✅ **bitti + canlı doğrulandı** (upsert 2xx) |
| 9 | Push | ✅ **bitti + canlı doğrulandı** (watermark ilerledi) |
| 9b | Bulut girişi + giriş/kilit fix + boş-durum + resend | ✅ **bitti** |
| 10 | Pull | ✅ **bitti** (LWW guard SQL kullanıcı tekrar çalıştırır) |
| 11 | Gerçek zamanlı | ✅ **bitti + canlı doğrulandı** (olay aktı, ekleme ve silme) |
| 12 | Bildirimler | ⬜ (11'den sonra anlamlı) |

## Kullanım turu (11'den sonra)

Gerçek kullanımda çıkan altı şey. Hepsi emülatörde doğrulandı.

| # | Adım | Durum |
|---|---|---|
| 13 | Fon aramasında rakamlı kodlar (AN1, TP2) | ✅ **bitti + canlı doğrulandı** |
| 14 | Varlıklarda yüzde yerine TL kâr | ✅ **bitti** |
| 15 | Hedef halkası: tutar halkanın altına | ✅ **bitti** |
| 16 | Hedef ataması adet bazında bölünebiliyor | ✅ **bitti** (schema.sql tekrar çalıştırılmalı) |
| 17 | "Çevrimdışı" mantığı — fiyat ile bulut ayrıldı | ✅ **bitti + canlı doğrulandı** |
| 18 | Açılış animasyonu tek akıcı harekete indi | ✅ **bitti** |
| 19 | Hedef ekranı atanan kısmı gösteriyor, miktar sonradan değiştirilebiliyor | ✅ **bitti** |
| 20 | Klavye alanı görünür kılıyor, alanlar arası geçiş var | ✅ **bitti + gerçek cihazda doğrulandı** |
| 21 | Fon fiyatlarında ince ondalık; hesaplar gözden geçirildi | ✅ **bitti + gerçek cihazda doğrulandı** |
| 22 | Varlık detayından ekleme o varlıkla açılıyor | ✅ **bitti** |
| 23 | Geri tuşu alt sayfayı kapatıyor, arkadaki ekranı atmıyor | ✅ **bitti** |
| 24 | Varlık detayı ve listesinde kuruş hep görünür | ✅ **bitti** |
| 25 | Günlük/haftalık/aylık değişim — Piyasa ve Varlıklar | ✅ **bitti** |
| 26 | Gram altında ayar seçilebiliyor (14/18/22/24) | ✅ **bitti** |
| 27 | Varlıklarda tek rakam çifti: Gün/Hafta/Ay/Toplam | ✅ **bitti + gerçek cihazda doğrulandı** |
| 28 | Net değer çipleri grafiği gerçekten değiştiriyor | ✅ **bitti + gerçek cihazda doğrulandı** |
| 29 | Açılışta giriş ekranı parlamıyor, kilit ilk kareden | ✅ **bitti + gerçek cihazda doğrulandı** |
| 30 | Hedef detayı yüklenirken "bulunamadı" demiyor | ✅ **bitti + gerçek cihazda doğrulandı** |
| 31 | Fiyat geçmişine iki yıllık emniyet sınırı | ✅ **bitti** (ölçüldü: 0,53 MB/yıl) |

**Canlı doğrulama (2026-07-30, gerçek cihaz + gerçek Supabase).** E-posta gönderimi
Gmail SMTP + App Password ile açıldı (Resend domain istiyordu; domain alınmadı).
Giriş yapıldi: jeton diskte `enc1:` sifreli (`eyJ` duz-metin yok) → adim 8; push
watermark ilerledi (`lastPushedAt` yazildi, motor bunu ancak TUM upsert'ler 2xx
donunce yazar) → adim 9; upsert'lerin gecmesi kolon/RLS uyumunu → adim 7 dogrular.

---

## 1 · Açılış animasyonu + tema uyumu ✅

**Neydi.** Açılış ekranı yoktu. `targetSdk 37` olduğu için sistemin kendi açılış
penceresi devredeydi ama özelleştirilmemişti: zemin sabit `#15120E` (koyu),
uygulamanın varsayılan teması ise açık. Her açılışta önce koyu bir kare
parlıyordu. Üstüne, "açılış akışı geçildi mi" bilgisi diskten gelene kadar
çizilen boş bir `Box` ikinci bir boş kare koyuyordu.

**Ne yapıldı.**

- `androidx.core:core-splashscreen`; sistem penceresi `setKeepOnScreenCondition`
  ile uygulama ilk karesini çizene kadar tutuluyor. Compose'daki boş `Box`
  yerine artık marka animasyonu var.
- Zemin renkleri kaynağa taşındı: `values/colors.xml` (açık) +
  `values-night/colors.xml` (koyu). Sabit renk kalmadı.
- **Varsayılan tema `System` yapıldı.** Açık sabitken, cihazı koyu modda olan
  biri açılış penceresi ile uygulama arasında hep uyumsuzluk görüyordu.
- `ui/brand/KefeSplash.kt` — zincir sallanıp yerine oturur, ardından "Kefe"
  belirir. `AnimatedVectorDrawable` **yazılmadı**: marka işareti zaten ortak
  kodda ve sürekli bir `progress` parametresi alıyor; ikinci bir çizim, marka
  geometrisinin iki ayrı yerde yaşaması demekti.
- Sistemin açılış penceresi için **ayrı** bir çizim: `ic_splash_mark.xml`.
  Launcher ikonu her zaman koyu zemin üzerinde durur (adaptif ikonun kendi zemin
  katmanı var), açılış penceresinin zemini ise temayla değişiyor — aynı çizimi
  kullanmak açık temada askıların görünmez olması demekti.

**Yol boyunca çıkan iki şey.**

1. Animasyon `sag = 0`'dan başlıyordu; zincir düz çizgiye inince o kare **boş**
   görünüyordu. Sistem penceresi dolu zinciri gösterip bırakınca işaret
   kayboluyor, sonra yeniden sarkıyordu — iki ayrı hareket gibi. Artık durgun
   değerden devralınıp zincire yalnız bir **hız** veriliyor.
2. Sistem penceresi launcher ikonunu kullanınca açık zeminde krem askılar
   kayboluyor, altın da zeminle kaynaşıyordu. Ayrı çizim bunu çözdü.

**Doğrulama.** Emülatörde ardışık kareler yakalandı:

- Açık tema, soğuk açılış: sistem penceresi (açık, koyu altın zincir) → Compose
  animasyonu → Özet. **Koyu kare yok, boş kare yok.**
- Koyu tema, soğuk açılış: aynı sıra, koyu zemin ve açık altın.
- **Arka plandan dönüş: animasyon oynamıyor**, uygulama doğrudan görünüyor.
  Bayrak süreç ömürlü — ekran dönmesi de tekrar oynatmaz.

---

## 2 · Parmak izi kilidi ✅

**Neydi.** Ayarlardaki "Biyometrik kilit" anahtarı diske yazılıyor ve geri
okunuyordu — yani açıp kapattığınızda konumunu hatırlıyordu — ama **hiçbir şey
yapmıyordu**. Projede `androidx.biometric` bağımlılığı bile yoktu. Bu, hiç
yapılmamış olmaktan daha sinsiydi: "hazır değil" demiyor, çalışıyormuş gibi
duruyordu. Buna karşılık kilit ekranı (`LoginStage.Locked`) çizilmiş, hazır
bekliyordu.

**Ne yapıldı.**

- `security/BiometricGate.kt` — `expect class`, `FileTransfer` ile aynı desen.
  Android `BiometricPrompt`, iOS `LAContext`, masaüstü `Unsupported`.
  Activity referansı mevcut köprüden alınıyor; ikinci bir köprü, ekran her
  döndüğünde sızdırılacak ikinci bir Activity demekti.
- `BIOMETRIC_WEAK` **veya** `DEVICE_CREDENTIAL` isteniyor. Yalnız güçlüyü
  (STRONG) sormak yüz tanımayla açılan telefonlarda kilidi kullanılamaz kılardı;
  cihaz kimliğini de kabul etmek kullanıcıya PIN/desen çıkış yolu bırakıyor.
- **"Şifreyle aç" düğmesi kaldırıldı.** Hesabın parolası yok — giriş tek
  kullanımlık e-posta koduyla — yani o düğmenin bir gün işleyecek karşılığı da
  yoktu. Sistem istemi zaten PIN/desen sunuyor.
- `prefsLoaded` bayrağı: kilit varsayılanı açık, bayrak olmasaydı uygulama
  diske hiç bakmadan "kilitli" varsayıp ilk karede parmak izi sorardı.

**Yol boyunca çıkan hata — kilit, kilit değildi.** İlk sürümde iptal edince
uygulama açılıyordu. Sebep eski bir kapıydı: *"bir kez girildiyse giriş ekranı
atlanır"* etkisi kilidi tanımıyordu. Kullanıcı doğruca Özet'e alınıyor, sistem
istemi zaten açılmış uygulamanın üstüne biniyordu; istemden vazgeçen kişi arkada
bekleyen bakiyeyi buluyordu. Etki artık kilide bağlı.

**Kilit kapı değil, perdedir.** Cihazda parmak izi tanımlı değilse ya da donanım
yoksa kullanıcı **içeri alınır**. Bakiyeyi başkasından saklamak için konan bir
özellik, kullanıcıyı kendi verisinden etmemeli.

**Doğrulama.** Emülatörde dört yol ayrı ayrı:

- Parmak izi/PIN yokken → kullanıcı içeri alındı, dışarıda kalmadı.
- PIN kuruldu → kilit ekranı geldi, istem açıldı, PIN girilince uygulama açıldı.
- İstem iptal edildi → **kilit ekranında kalındı**, bakiye maskeli, "Kilitli".
- Kilit ekranındaki parmak izi düğmesi → istem yeniden açıldı, açıldı.
- Ayardan kapatıldı → sonraki açılışta kilit hiç gelmedi.

Test PIN'i emülatörden kaldırıldı.

**Sonraya bırakıldı:** oturum jetonu hâlâ SQLite'ta düz metin. `BiometricPrompt`
bir UI bileşeni, Keystore ayrı bir şifreleme API'si — "aynı altyapı" değiller.
Bugün jeton, içinde veri **olmayan** bir hesaba erişim veriyor; senkron açılınca
tüm birikim geçmişi demek olacak. `SecureStore` işi senkrondan hemen önce.

### 3b · Aynı şerit Piyasa'da da ✅

Kullanıcı isteğiyle: kısıtlanan yenileme uyarısı Piyasa ekranına da eklendi.
Özet'teki `AutoDismissBanner` ortak bir bileşene taşındı
(`KefeAutoDismissBanner` / `KefeBottomBanner`, `Banners.kt`) ve iki ekran da
**aynı** bileşeni kullanıyor — Özet ile Piyasa arasında ikinci bir kopya yok.
Piyasa'da "Yenile"ye arka arkaya basınca altın renkli şerit çıkıyor:
*"Fiyatlar az önce güncellendi — 24 sn sonra tekrar denenebilir."*
Emülatörde doğrulandı.

---

## 4 · İki profil ✅

**Karar.** Tek Supabase hesabı, iki cihaz aynı e-postayla girer, her cihaz bir
profile sabitlenir. Bu, davet akışını ve alan adı ihtiyacını ortadan kaldırır.

**Ne yapıldı.**

- Bootstrap artık **iki** profil kuruyor: `member_owner` + yeni `member_partner`.
  Kimlikler SABIT — iki cihaz kendi bootstrap'ını çalıştırıyor; deterministik id,
  senkron gelince çakışma değil birleşme üretir.
- `PreferenceKeys.ActiveMemberId` — bu cihazın hangi profil olduğu. Cihaza
  aittir, senkronlanmaz.
- `renameMember` — dar bir sorgu; yalnız ad ve baş harfi değiştirir, sortOrder
  ve rolü korur.
- `AddTransactionViewModel` işlemi artık **aktif profile** yazıyor, koşulsuz
  Owner'a değil. Çevrimdışı notundaki isim de "aktif olmayan profil".
- Yeni `ProfileSetupScreen` — "Bu telefon kimin?". İki ad + bu cihazın seçimi.
  Onboarding'e dokunulmadı.
- Kabuk kök seçimi: `activeMemberId == null → ProfileSetup`. `onboarded`'dan
  bağımsız, çünkü iki giriş yolu var ve biri onboarding'i hiç görmüyor.

**Yedek tuzağı (kritik).** Yedek dosyası `settings` tablosunu olduğu gibi
taşıyor. Restore önce tüm settings'i siliyor. İlk düzeltmemde `activeMemberId`
atlanıyordu ama **silme adımı** yüzünden cihazın kendi seçimi de kayboluyordu —
test bunu yakaladı. Artık restore, cihaza ait tercihleri silmeden önce koruyup
sonra geri yazıyor. Beş test bu kuralı kilitliyor; en önemlisi *"Ayşe, Volkan'ın
yedeğini yükleyince telefonu hâlâ Ayşe kalır"*.

**Doğrulama.** 92 test (5 yeni). Emülatörde uçtan uca: temiz kurulumda ProfileSetup
geldi, "Burak Can" / "Merve" girildi, bu telefon **Merve** seçildi, bir çeyrek
eklendi — Son hareketler'de **"Merve · 1 Çeyrek ekledi"** göründü. Kayıt Owner'a
değil aktif profile yazıldı.

---

## 5 · Çok kullanıcılı iskeletin sökülmesi ✅

Adım 4 iki profili kurdu; artık işlevsiz kalan N-kişilik iskelet söküldü.

**Kaldırılanlar.**

- `MemberRole` (Sahip/Üye) ve `MemberPermission` (Düzenleyebilir/Sadece görüntüler)
  — iki eşit profilde karşılıksız. `Member` modelinden `role`, `permission`,
  `lastSeen` alanları gitti.
- `ShareScreen` / `ShareViewModel` / `ShareUiState` → **Profiller** ekranına
  dönüştü: iki profil satırı, ada dokununca yeniden adlandırma, satıra dokununca
  bu telefonu o profile bağlama. Davet kodu (sabit "472915"), elle çizilmiş QR,
  izin segmenti, üye çıkarma — hepsi gitti. Bellek içi `permissionOverrides`/
  `removedMemberIds` hilesi de; artık gerçek yazma var.
- `LoginStage.Start` (yeni portföy / davet kodu adımı) → tek seçenek "yeni
  portföy" olduğu için ayrı adım bir boş duraktı. "Yeni portföy oluştur" artık
  doğrudan tanıtıma geçiyor. Davet alanları (`inviteCode`, `Join`, `canJoin`…)
  temizlendi.

**`DROP COLUMN` yok.** SQLDelight lehçesi sqlite-3-18. `role`/`permission`/
`lastSeen` kolonları tabloda **yerinde kaldı**, yalnız Kotlin tipi (`AS
MemberRole`) düz `TEXT`'e indi ve sorgularda sabit yazıldı. Migration gerekmedi;
diskteki eski değerler ölü veri.

**Korunanlar.** `InviteCodeInput` (kod kutusu) kaldı — Supabase giriş kodu
kullanıyor. Eski yedekler güvende: `BackupCodec` `ignoreUnknownKeys = true`, eski
`role`/`permission` alanları sessizce yok sayılır. Avatar paleti ve `take(2)`
zaten iki kişiye göreydi.

**Doğrulama.** 92 test geçiyor. Emülatörde: temiz kurulumda profil ekranı geldi
(Burak Can / Merve), Özet'e geçildi; Ayarlar → **Profiller** açıldı, "Bu telefon"
işareti Merve'ye ve geri Burak Can'a taşındı, yeniden adlandırma sheet'i açıldı.
Giriş ekranında "Yeni portföy oluştur" doğrudan tanıtıma gidiyor, araya "Başlangıç"
adımı girmiyor.

---

## 6 · Ayarlar temizliği ve tamamlanması ✅

**Kaldırılanlar.**

- **Bildirimler bölümü** (Eş kayıt / Aylık hatırlatıcı / Kilometre taşı) — hiçbir
  altyapı yok, `POST_NOTIFICATIONS` izni bile yoktu. Üç anahtar diske yazılıp
  hiçbir şey yapmıyordu. Gerçek zamanlı senkron gelince geri döner.
- **Para birimi** satırı — `Portfolio.currency` sabit "TRY", hiçbir yerde
  okunmuyordu.
- **Gizlilik / Koşullar** bağlantıları — iki kişilik, mağazaya çıkmayan bir
  uygulamada olmayan bir belgeye bağlantı vermek yanlış. Play'e çıkma anında
  (gerçek URL gerektiğinde) geri gelir.

**Uygulananlar (önce diske yazılıp etkisiz duran anahtarlar).**

- **Kuruşları göster** → `LocalShowCents` CompositionLocal + `moneyTl` yardımcısı.
  Yalnız SATIR İÇİ tutarlar (varlık değerleri, işlem satırları) buna bakar; ana
  toplamlar 0 ondalıkta kalır — ayarın alt metninin sözü bu.
- **Açılışta bakiyeyi gizle** → SummaryViewModel'e bağlandı. Tuzak: `observeAll()`
  her tercih değişiminde emisyon yapıyor; naif bağlama kullanıcının elle açtığı
  bakiyeyi bir sonraki emisyonda kapatırdı. Yalnız İLK emisyonda uygulanır.

**Salt-okunur yapılanlar.** Otomatik güncelleme / Kaynak satırları artık gerçeği
yazan bilgi satırları (chevron yok): "Açılışta ve elle yenilendiğinde",
"Serbest piyasa · TCMB · TEFAS".

**Doldurulan boşluklar.**

- **Son yedek tarihi** — yeni `LastBackupAt` anahtarı; yedek alınınca yazılır,
  satırın sağında "28 Temmuz 2026" gösterilir, yoksa "Henüz alınmadı". Önce hep
  boştu.
- **Sürüm** — "1.0.4" sabiti gitti. build.gradle'de tek `appVersionName`
  değişkeni hem paketin `versionName`'ine hem üretilen `SupabaseConfig.AppVersion`'a
  gidiyor. Ayarlar artık "Kefe 1.0.0" — paketle eşleşiyor.
- **Hesap bölümü** yalnız giriş yapılmışsa çizilir (`signedIn`); önce ölü alandı.

**Doğrulama.** 92 test geçiyor. Emülatörde: Ayarlar'ın yeni hali (Profiller kartı,
salt-okunur fiyat satırları, "Henüz alınmadı", "Kefe 1.0.0", Gizlilik/Koşullar
yok, Hesap bölümü yok). Kuruş anahtarı açıldı → işlem satırı "₺10.101,00" (iki
ondalık), hero "₺9.873" (0 ondalık). Bakiye gizleme açık → yeniden başlatınca
Özet toplamı maskeli geldi.

---

## 8 · Jeton Keystore'a ✅

**Neydi.** Oturum jetonu (erişim + yenileme) `auth_session` tablosunda **düz
metin** duruyordu. İçinde veri olmayan bir hesaba erişim verdiği sürece bu kabul
edilebilirdi; senkron açılınca aynı jeton tüm birikim geçmişi demek olacak.
Cihazdan bir yedek/adb kopyası jetonu ele geçirmeye yeterdi.

**Ne yapıldı.**

- `security/SecureStore.kt` — `expect class`, `BiometricGate` ile aynı desen.
  `protect(plain)` şifreler, `reveal(stored)` çözer.
- **Android** actual: AndroidKeyStore'da AES/GCM. Anahtar Keystore'da doğar ve
  **oradan çıkmaz** — uygulama yalnız şifreleme ister, anahtarı hiç görmez. Bu
  yüzden jeton başka cihaza kopyalansa da çözülemez. GCM her yazımda yeni IV
  üretir; IV gizli değil, şifreli metnin başına yazılır. `enc1:` öneki hangi
  metnin şifreli olduğunu söyler.
- **Geriye uyum.** `reveal`, çözemediği (öneksiz) metni **olduğu gibi** döndürür.
  Bu sürümden önceki düz-metin oturumlar böylece patlamaz; ilk yenilemede
  kendiliğinden şifreliye döner. Yeni tablo yok, migration yok — aynı kolonlara
  şifreli metin yazılır.
- **Anahtar biyometriye bağlanmadı.** `setUserAuthenticationRequired` konsaydı
  arka plandaki sessiz jeton yenileme de parmak izi isterdi. Kilit ayrı özellik.
- Masaüstü/iOS actual şimdilik passthrough (belgelenmiş): JVM'den DPAPI'ye
  güvenilir köprü yok, sahte şifreleme güvenlik yanılsaması verirdi; iOS Keychain
  köprüsü bu makinede derlenmiyor. Asıl hedef cihaz Android.
- Repoya `SecureStore` enjekte edildi: `store()` yazarken `protect`, tüm okuma
  yolları (`observeAuthState`, `validAccessToken`, `signOut`, yenileme) `reveal`.

**Doğrulama.** 92 masaüstü testi geçiyor — round-trip (yaz→oku bütünlüğü)
korunuyor; masaüstü passthrough olduğu için bunlar **kabloyu** doğruluyor. Her
iki platform da derleniyor (desktop + Android). Emülatörde uygulama temiz açıldı:
Koin grafiği `SecureStore`'u üretip repoya enjekte etti (yanlış olsa açılışta
`observeAuthState` çağrısında patlardı). **Açık kalan tek doğrulama:** cihazda
şifrelemenin gerçekten çalıştığı ve diskte `enc1:` görünüp `eyJ…` (JWT) düz metni
kalmadığı — bu, bir sonraki gerçek girişte yakalanacak (auth_session şu an boş;
giriş zaten senkron için şart). Keystore yolu ilk `protect`/`reveal`'de işler.

---

## 7 · Supabase tabloları + RLS ✅

**Neydi.** Senkron için sunucu tarafı boştu: veri koyacak tablo, onları hesaba
kilitleyecek politika yok. Tablo, politikası yazılmadan AÇILMAMALI - RLS'siz bir
tablo, publishable anahtarı eline geçiren herkese açıktır.

**Ne senkronlanır (kullanıcı "hepsi" dedi → 7 tablo).**

- `members`, `positions`, `transactions`, `goals`, `goal_assets` — çekirdek: girilen
  gerçek veri.
- `daily_snapshots` — geçmiş grafiği. Geçmiş fiyatlar hiçbir yerde tutulmadığı
  için turetilemez; ikinci telefon bunlar olmadan geçmişi çizemez.
- `activity_events` — "kim ne ekledi" akışı. Bir kısmı (elle fiyat, hedef
  güncelleme) defterde iz bırakmaz, o yüzden turetmek yetmez.

**Ne YAZILMAZ (turetilir ya da cihaz-yerel).** Pozisyonun miktar/maliyet/değeri
(defterden), canlı fiyat ve günlük değişim (cihazın kendi çekimi), hedefin tahmini
varış tarihi (hesaplanır), işlemdeki `syncState` (bu cihazın "gönderdim mi"
işareti), `members.role/permission/lastSeen` (çok kullanıcılıdan ölü kalıntı),
`portfolios` (sabit `portfolio_local`, iki cihazda birebir aynı — bootstrap kurar,
senkrona gerek yok). Ayrıca jeton, ayarlar, fiyat önbelleği hiç uğramaz.

**Güvenlik modeli.** Tek hesap, iki cihaz aynı e-posta → aynı `auth.uid()`. Her
satır `user_id` ile sahibine bağlı; varsayılanı `auth.uid()` olduğu için istemci
onu göndermez, PostgREST doldurur. RLS `auth.uid() = user_id` hem okumayı hem
yazmayı o hesaba kilitler — anahtar sızsa da RLS olmadan tek satır okunmaz.
Tablolar arası yabancı anahtar YOK (push sırası serbest kalsın); tek FK
`user_id → auth.users` (hesap silinince temizlensin). SQL tekrar çalıştırılabilir
(`if not exists` + `drop policy if exists`). Dosya: `supabase/schema.sql`.

**Yerel taraf (4.sqm, v4→v5).** `members` ve `activity_events` 2.sqm'deki esitleme
alanlarını almamıştı. `members += updatedAt` (yeniden adlandırmada son yazan
kazanır; mezar taşı yok, iki profil silinmez). `activity_events += updatedAt +
deletedAt` (akışta silme var, mezar taşı olmadan es çevrimdışıyken silinen olay
dirilir). `daily_snapshots` zaten `updatedAt`'liydi, dokunulmadı.

**Doğrulama.** `verifyCommonMainKefeDatabaseMigration` geçiyor — migration TAM
olarak `.sq` şemasını üretiyor (v4→v5 tutarlı). 92 test geçiyor. Emülatörde
mevcut veriyle (altın pozisyonu + işlemler) migration çalıştı: çökme yok,
`members.updatedAt` şema metninde yerinde, `members_updated`/`activity_events_updated`
indeksleri oluştu, veri sağ kaldı (Özet "Altın %100"). **Sunucu tarafı:** kullanıcı
`supabase/schema.sql`'i Supabase SQL editöründe çalıştırır (idempotent).

**Sonraya (adım 9, push).** `renameMember` ve `insertActivity` henüz `updatedAt`
yazmıyor (kolon hazır, değer 0); `deleteActivityById` hâlâ hard delete. Push
wiring'inde bunlar gerçek zaman damgası + soft-delete'e bağlanacak.

---

## 9 · Push ✅

**Neydi.** Sema ve RLS hazırdı ama yereldeki değişiklikleri sunucuya taşıyan bir
şey yoktu. 2.sqm'deki karar netti: **ilk esitleme PUSH yönünde** — yerel gerçek,
sunucu boş.

**Mekanizma — watermark.** Her satırın `updatedAt`'i var. Cihaz "en son şuraya
kadar ittim" damgasını (`LastPushedAt`, cihaz-yerel) tutar; push yalnız o damgadan
yeni satırları gönderir. İlk push'ta damga 0, yani her şey gider. Tablo başına
`selectXChangedSince` sorgusu **mezar taşlarını da** getirir (silme de ese
gitmeli). Watermark okumadan ÖNCE `syncStart` yakalanır: push sürerken yazılanlar
bir sonraki tura kalır.

**Bütünlük.** Watermark ancak TÜM tablolar başarıyla gittikten sonra ilerler. İlk
upsert patlarsa (`PostgrestApi` fırlatır) damga durur, bir sonraki tetik baştan
dener. Upsert `merge-duplicates` (idempotent) olduğu için tekrar zararsız — kısmi
gönderim veri bozmaz.

**Tetik — olay güdümlü, ARKA PLAN TICKER'I YOK.** `SyncCoordinator` girişliyken
`SyncLocalSource.localChanges()`'i dinler; bu, 7 senkron tablosunun sayaç-flow'unun
combine'ı — SQLDelight tablo bildirimi olduğu için ekleme kadar **düzenleme ve
silme de** yakalanır. `debounce(1500ms)` ard arda yazmaları (işlem + pozisyon
yeniden hesabı + aktivite) tek push'a toplar. İlk emisyon açılış/giriş push'ini de
kapsar. Cikinca dinleme durur. Push'lar conflated kanaldan **tek tüketici** ile
seri gider.

**Süreç ömürlü.** Koin, Compose ağacında kurulu (Activity yeniden yaratılınca
yeniden kurulur). Coordinator işleri companion'daki tek scope'ta tek sefer başlar
(`claimStart`), yoksa her dönüşte yeni dinleyici sızar ve aynı değişiklik defalarca
push'lanırdı — veritabanının `KefePlatform`'da tutulmasıyla aynı gerekçe.

**Bearer = KULLANICININ jetonu**, anonKey değil: RLS `auth.uid() = user_id` ancak
isteğin kimin adına geldiğini bilirse çalışır. Jeton `validAccessToken()`'dan
gelir (süresi dolduysa yeniler). `user_id` her satırda açıkça gönderilir (bileşik
anahtarlı `daily_snapshots`'ta upsert hedefi tam olsun; RLS zaten yanlışı reddeder).

**Yerel yazma düzeltmeleri.** Push'un "değişti" görebilmesi için: `renameMember`,
`updateGoalOrder`, `insertActivity` artık `updatedAt` yazıyor; `deleteActivityById`
hard delete'ten **soft-delete**'e döndü (feed filtresi `deletedAt IS NULL`).

**Doğrulama.** **98 test** (6 yeni push testi): ilk push tüm tabloları gönderir;
değişiklik yoksa ikinci push boş; watermark sonrası değişen satır yeniden gider;
silinen satır mezar taşıyla gider; jeton yoksa hiçbir şey gitmez; upsert patlarsa
watermark durur ve sonraki push yeniden dener. İki platform derleniyor. Emülatörde
uygulama temiz açıldı (Koin 33 tanım, sync grafiği kuruldu, çökme yok); oturum
olmadığı için henüz push denemiyor. **Açık kalan:** verinin gerçekten Supabase'e
düştüğünün canlı kanıtı — bir sonraki girişte yakalanacak (adım 8'in `enc1:`
kanıtıyla aynı oturum).

---

## 9b · Bulut girişi + giriş/kilit çakışması + boş-durum + resend ✅

**Neydi (kör nokta).** Adım 9 push motorunu kurdu ama iki-profil rescope'u,
onboarding'i geçmiş + oturumu kapanmış bir kullanıcının **giriş yolunu** silmişti:
Ayarlar'daki "Hesap" bölümü yalnız girişliyken çiziliyordu. Yani ikinci telefon
(ya da oturumu düşen biri) hiç giriş yapamıyordu — senkron da hiç başlamıyordu.

**Bulut bölümü.** "Hesap" → **Bulut** bölümüne dönüştü: signed-out iken "Giriş yap
ve senkronu aç", signed-in iken e-posta + son senkron + çıkış. Çıkış artık
kullanıcıyı login köküne ATMIYOR (giriş isteğe bağlı, uygulama çevrimdışı tam
çalışır) — Ayarlar'da kalır, Bulut yeniden "Giriş yap" gösterir.

**Giriş/kilit çakışması (asıl bug).** `LoginKey` ekranı ÇİFT görevli: açılış
biyometrik kilidi (yığın kökü iken) + bulut girişi (Ayarlar'dan itilince). AYNI
`LoginViewModel` ikisine de hizmet ettiği için kilitten arta kalan durum
(`stage=Locked`, `unlocked=true`) itilmiş girişe sızıyordu: "Giriş yap" bir an
kilit ekranını (gerçek cihazda parmak izi istemini) açıp, `unlocked` etkisiyle
`enterApp` çağırıp Özet'e geri atıyordu. Çözüm: **itilmiş LoginKey her zaman temiz
SignIn gösterir** (kök değilse `state`'i SignIn/unlocked=false'a zorlar); kilit
yalnız kök iken. Tuzak: düzeltme bir süre `LoginStage` import'u eksik olduğu için
DERLENMİYORDU — build "başarılı" görünüp eski APK'yı kuruyordu; asıl teşhis
logcat stack-trace ile geldi.

**Boş-durum başlığı.** İlk kayıttan önce (`SummaryStage.Empty`) başlıktaki senkron
çipi (Eşit), göz (gizle) ve yenile ÇİZİLMEZ; pull-to-refresh kapalı — eşlenecek
veri, gizlenecek bakiye, tazelenecek fiyat yok. İlk varlıkla gelirler. (Not: "Eşit"
çipi aslında FİYAT tazeliğini gösteriyor, bulut senkronunu değil — ileride ayrılmalı.)

**Kodu tekrar gönder.** Kod-giriş ekranına "Kodu tekrar gönder" eklendi, 60 sn
geri sayımlı (Supabase OTP aralığıyla uyumlu; foreground tek-seferlik sayaç, arka
plan poll değil). Başarısız resend kod kutusundan atmaz. `AccountFlatButton`
`enabled` aldı. Placeholder `volkan@` → `burak@ornek.com`.

**E-posta (altyapı, kod dışı).** Resend domain istiyordu; domain alınmadı. Çözüm:
Gmail SMTP + App Password (`smtp.gmail.com:587`) — domain gerektirmez, kod
`burockcan2309@gmail.com`'a gelir. İlk hata `535 BadCredentials` App Password'ün
boşluklu girilmesindendi.

**Doğrulama.** 98 masaüstü testi geçiyor; iki platform derleniyor. Emülatörde giriş
ekranı artık kilit yerine e-posta/kod gösteriyor (stack-trace + DBG ile
doğrulandı). Gerçek cihaz + gerçek Supabase'de giriş → adım 8/9 canlı kanıtı
(yukarı bkz). `SyncCoordinator` push hatasında artık tanısal log bırakıyor.

---

## 10 · Pull ✅

**Neydi.** Push tek yon (cihaz → sunucu). Ikinci telefonun senkronu gorunmuyordu:
bir cihazda eklenen altin digerinde belirmiyordu.

**Tam cekim + LWW.** Tetikte her tablonun TUM satirlari cekilir (`selectAll`; RLS o
hesaba kisitlar), gelenler **son-yazan-kazanir** ile uygulanir: satir ancak
yereldekinden yeni (`server.updatedAt > local.updatedAt`) ya da yerelde yoksa
yazilir. Watermark yerine tam cekim - kucuk veride saat-kaymasi/gec-gelen tuzagini
bastan atlar (`SyncLocalSink`). Mezar tasi da uygulanir (gelen `deletedAt` doluysa
yerelde silinir). Turetilen (pozisyon miktar/maliyet/deger) defterden yeniden
kurulur. Hepsi TEK transaction.

**CASCADE tuzagi.** `positions`/`goals` uygulanirken OR REPLACE KULLANILMAZ - satiri
silip CASCADE ile defteri/atamalari goturur. Iki adim: insertOrIgnore + meta
guncelle. Yaprak tablolar (transactions, goal_assets, activity) OR REPLACE guvenli.

**LWW guard (sunucu).** Push, PostgREST upsert'iyle satiri KOSULSUZ ezer - son push
kazanir, damgaya bakmaz. Bos/eski bir cihazin push'u sunucudaki yeni veriyi ezerdi.
`schema.sql`'e BEFORE UPDATE trigger (`kefe_lww_guard`) eklendi: gelen eski/esitse
guncelleme yok sayilir. Boylece cakisma cozumu push'ta da pull'da da AYNI:
updated_at buyuk kazanir. **Kullanici `supabase/schema.sql`'i tekrar calistirir**
(idempotent - trigger'i ekler).

**Tetik.** Giriste bir kez (bos ikinci telefon icin) + her push'tan SONRA (benimkini
gonderdim, seninkini al). Ayri conflated kanal, tek tuketici - seri. Realtime (11)
canli tetigi ekleyecek.

**Dogrulama.** **103 masaustu testi** (5 yeni pull): bos cihaz sunucudan ceker
(pozisyon+islem, miktar defterden hesaplanir); yerel yeni ise korunur (LWW); sunucu
yeni ise uygulanir; mezar tasi silinmis uygulanir; jeton yoksa cekmez. Iki platform
derleniyor, emulatorde temiz acildi. **Canli:** ikinci (bos) cihaz giris yapinca
altin portfoy buluttan gelir - kullaniciyla dogrulanacak.

---

## 11 · Gerçek zamanlı ✅

**Neydi.** Push ve pull iki yönü de kurmuştu ama pull yalnız **iki anda**
tetikleniyordu: girişte bir kez, ve her push'tan sonra. Yani karşı cihazın
yazdığı veri, sen kendi telefonunda bir şey yazana kadar gelmiyordu. İki kişilik
bir uygulamada "eşim çeyrek ekledi, bende görünmüyor" tam olarak buydu.

**Ne yapıldı.** Supabase Realtime (Phoenix WebSocket) üçüncü tetik kaynağı olarak
eklendi: `RealtimeApi` / `SupabaseRealtimeApi`. `PostgrestApi` ile aynı gerekçeyle
elle yazıldı — supabase-kt kendi Ktor sürümünü dayatıyor, ihtiyacımız tek soket.

**SİNYAL, VERİ DEĞİL.** Gelen mesajın içindeki satır okunmuyor; olay yalnız
"değişti" demek, uygulamayı `PullEngine.pullOnce()` yapıyor — adım 10'un tam
çekim + LWW kararıyla aynı çizgi. Payload'ı doğrudan uygulamak ikinci bir
doğruluk yüzeyi açardı: kaçırılan bir olay kalıcı tutarsızlık demek olurdu.

**Soket yalnız ön planda açık.** Kapı = girişli **ve** uygulama ön planda
(`observeAuthState` + `LifecycleResumeEffect` → `setForeground`). Arka planda
soket kapanır; Phoenix'in ~30 sn'lik heartbeat'i kullanıcının bakmadığı bir ekran
için pil yakardı. Bu bir uygulama ticker'ı değil, soketin kendi keepalive'ı ve
yalnız soket açıkken yaşar. Kapı her açıldığında **önce bir pull** istenir —
soket kapalıyken olan değişiklikleri toparlayan şey odur.

**Yankı bilerek kabul edildi.** Kendi push'um sunucuda satır değiştirir, realtime
olayı bana da döner, bir fazladan pull olur. Kendiliğinden söner: debounce
toplar, `PullEngine`'in mutex'i çakışmayı engeller, LWW hiçbir satırı uygulamaz
(damgalar eşit) → yerelde yazma olmaz → yeni push tetiklenmez. Bedeli 7 küçük
`select`; `commit_timestamp`'e bakıp kendi yazmamızı elemek ikinci bir doğruluk
yüzeyi açardı.

**Yol boyunca çıkan üç şey.**

1. **Join yanıtı kabul kanıtı değil.** Supabase istenen yapılandırmayı, tablo
   yayında olmasa bile olduğu gibi geri yansıtıyor: emülatörde "7/7 tablo" dedi
   ve hemen ardından `system/error` ile aboneliği reddetti. İlk log satırı
   "başarılı" gibi okunuyordu; gerçek ölçü `realtimeProblem` satırı. Loglar buna
   göre düzeltildi — "baglandı ama hiç olay gelmiyor" durumu artık konuşuyor.
2. **Sonda testleri hiç koşmuyormuş.** `excludeTestsMatching`, komut satırındaki
   `--tests` içermesini **eziyor**; `--tests "*LivePriceProbeTest"` sessizce
   hiçbir test seçmiyor ve `isFailOnNoMatchingTests = false` yüzünden "BUILD
   SUCCESSFUL" diyordu. Artık hariç tutma `-Pprobe` bayrağına bağlı.
3. Ktor'un kendi `pingInterval`'i **kurulmadı**: Phoenix uygulama düzeyinde kendi
   heartbeat *mesajını* bekliyor, WS ping frame'ini saymıyor.

**Sunucu tarafı.** `supabase/schema.sql`'e 7 tabloyu `supabase_realtime`
yayınına ekleyen idempotent blok eklendi. `replica identity full` **konmadı** —
payload okunmuyor, birincil anahtar yeter. **Kullanıcı `schema.sql`'i tekrar
çalıştırır.**

**Doğrulama.** **119 masaüstü testi** (16 yeni): protokol çerçeveleri (join 7
tabloyu ve kullanıcının jetonunu taşır, heartbeat `phoenix` topic'ine gider,
yalnız `postgres_changes` sinyal sayılır, üstel bekleme tavanda durur) ve tetik
mantığı (kapı açılınca sinyal beklemeden pull; sinyal gelince pull; ard arda
sinyaller tek pull'a toplanır; kapı kapanınca dinleme durur ve sinyal pull
üretmez; soket yalnız girişli **ve** ön planda açılır). İki platform derleniyor.

**Elle sonda** (`-Pprobe --tests "*RealtimeProbeTest" --rerun`) gerçek Supabase'e
bağlandı: `phx_reply status ok` — adres, WebSockets eklentisi ve masaüstü motoru
doğrulandı.

**Emülatörde:** Koin 36 tanım, çökme yok; `realtime bagli` → `join yaniti - 7/7`;
HOME → `dinleme kapali`; geri dön → yeniden bağlandı. Ön plan/arka plan döngüsü
uçtan uca çalışıyor.

**CANLI (2026-07-31, emülatör + gerçek Supabase).** Önce `schema.sql`
çalıştırılmadan denendi ve log tam da beklenen şeyi söyledi: *"abonelik
REDDEDILDI ... table: activity_events"* — sessiz başarısızlığın konuşması buydu.
`schema.sql` çalıştırıldıktan sonra o satır kayboldu; emülatöre bir çeyrek
eklendi → push → sunucu satırı değişti → **`realtime sinyali - pull isteniyor`**
düştü. Kayıt silindiğinde ikinci sinyal geldi: mezar taşı da akıyor. Test kaydı
geri alındı, toplam ₺946.559'a döndü (ekleme öncesiyle birebir), hareket
akışında iz kalmadı.

**Sonraya.** Sinyal yolu kanıtlandı ama iki AYRI cihaz arasındaki gecikme henüz
elle ölçülmedi (kanıt aynı cihazın yankısıyla alındı). Adım 10'un açık canlı
doğrulaması — ikinci boş telefonun buluttan çekmesi — hâlâ duruyor; ikisi tek
oturumda kapanır.

---

## 13 · Fon aramasında rakamlı kodlar ✅

**Neydi.** Kullanıcı fon kutusuna `AN1` yazıyor, hiçbir şey olmuyordu — canlı
arama satırı bile çıkmıyordu. `fundSearchCode` kodu `q.all { it.isLetter() }` ile
süzüyordu; TEFAS kodları ise harf-rakam karışımı olabiliyor (AN1, TP2, GO1).
Amaç "22" gibi salt sayıları elemekti, filtre fazla genişti.

**Ne yapıldı.** Koşul "hepsi harf" yerine "hepsi ASCII harf-rakam **ve** ilk
karakter harf" oldu. Salt sayı eleme amacı korundu, ASCII dışı harf kabul
edilmiyor (TEFAS kodlarında yok; "çeyrek" yazan biri boşuna ağa çıkmasın).

**Doğrulama.** Emülatörde `AN1` → *"TEFAS'ta "AN1" ara"* satırı çıktı, dokununca
gerçek TEFAS'tan **STRATEJİ PORTFÖY BİRİNCİ DEĞİŞKEN FON, ₺108,39** geldi.

---

## 14 · Varlıklarda yüzde yerine TL kâr ✅

**Neydi.** Varlıklar ekranında grup başlığının sağındaki `%95,4` kâr sanılıyordu;
aslında **toplam birikim içindeki pay**dı. Satırlardaki `0,00%` ise günlük
değişimdi ve elle fiyatlanan varlıklarda hep sıfır çıkıyordu. Yani "ne kadar
kazandık" sorusunun karşılığı hiçbir listede yoktu — yalnız varlık detayına
girince görülüyordu. Yüzdeyle arası iyi olmayan biri için ekran sessizdi.

**Ne yapıldı.** Grup başlığı `+₺239.112 · +36,3%`, satırlar `+₺192.971` gösteriyor;
ikisi de kâr/zarar rengiyle. Pay yüzdesi kaldırıldı — Özet'teki "Ne kadarı
nerede" halkasında zaten duruyor, iki yerde tekrar ediyordu.

**Doğrulama.** Emülatörde Altın başlığı `+₺239.112 · +36,3%`, satırlar TL kâr.

---

## 15 · Hedef halkası: tutar halkanın altında ✅

**Neydi.** Halkanın kasesine hem yüzde hem `mevcut / hedef` yazılıyordu. Kase dar
olduğu için büyük hedeflerde tam yazım sığmıyor, kısaltmaya düşüyordu
(`₺946,6B / ₺3,0M`) — en çok bakılan rakam okunması en zor hale geliyordu.

**Ne yapıldı.** Kasede yalnız yüzde. Tutar halkanın **altına**, tam yazımla:
orada genişlik sınırı yok. `KefeGoalRing`'in `centerAmount` parametresi artık
null kabul ediyor.

---

## 16 · Hedef ataması adet bazında ✅

**Neydi.** Atama tam varlıktı: bir pozisyon ya tamamen bir hedefteydi ya hiç.
Pratikte şu oluyordu — Ev'de 10 çeyrek varken 1 tane "Hedefsiz" eklemek, seçici
Hedefsiz'e alındığı için atamayı komple siliyor ve **11 çeyreğin hepsi Ev'den
çıkıyordu**. Kullanıcının beklediği "10'u Ev'de kalsın".

**Ne yapıldı.** `goal_assets` tablosuna `quantity` kolonu (5.sqm, v5→v6).
`-1 = tüm varlık` — mevcut satırlar bunu alır, yani migration davranışı **aynen
korur**. Hedef detayındaki "Varlık seç" listesi de bu anlamda atar: "bu varlığın
tamamı bu hedefi karşılar".

**Kayıt anındaki kural** (`nextAssignedQuantity`, saf fonksiyon):
- **Hedefsiz** → mevcut atamaya **dokunulmaz**. Şikâyetin çözümü tam burası.
- Aynı hedef → miktar bu işlem kadar artar (satışta azalır, negatife inmez).
- Başka hedef → varlık taşınır, miktar bu işlemin miktarı olur.
- "Tüm varlık" ataması aynı hedefte korunur: bir kez "tamamı buraya" denmişse
  sonraki alımlar da oraya sayılır.

**Kırpma okuma anında.** Atanan miktar pozisyonunkini aşamaz: 10 atanmışken 6
tanesi satılırsa hedef 6 sayar. Böylece satışın hangi hedeften düştüğüne dair
ikinci bir hesap defteri tutmak gerekmiyor.

**İlk sürüm sahada TUTMADI — eksik olan neydi.** Kullanıcı 15 çeyreği Ev'deyken
1 tane "Hedefsiz" ekledi ve hedef **16** gösterdi. Sebep: migration mevcut
atamaları `-1` (tüm varlık) olarak korumuştu — bilinçliydi, davranış bozulmasın
diye. Ama "tüm varlık" dediği sürece "Hedefsiz" hiçbir şey ifade etmiyor: hedef
zaten "hepsi"ni sayıyor, yeni alınan da hepsinin içinde. Kural "hedefsizde
atamaya dokunma" olduğu için de hiçbir şey değişmiyordu.

**Eklenen kural.** Hedefsiz bir **alım**, "tüm varlık" atamasını o ana kadarki
miktara **sabitler**. "Tamamı bu hedefe" sözü bugüne kadar alınanlar içindir;
kullanıcı yeni alımın dışarıda kalmasını açıkça istediğinde o söz dondurulur.
Miktarı zaten belli olan atamalara dokunulmaz (orada yeni alım nasılsa
sayılmıyor), satış da atamayı dondurmaz (kırpma okuma anında zaten var).

Bunun için `writeTransaction` atamayı ve pozisyon miktarını kayıttan **önce**
okuyor: sabitlenecek miktar ancak orada bellidir, kayıt yazıldıktan sonra
pozisyon zaten yeni adedi taşıyor.

**Sunucu tarafı.** `schema.sql`'e `quantity` kolonu eklendi — `create table if
not exists` zaten kurulmuş tabloda hiçbir şey yapmadığı için ayrı bir
`add column if not exists` ile geliyor. **Kullanıcı `schema.sql`'i tekrar
çalıştırır**, yoksa push 400 döner ve senkron sessizce durur.

**Doğrulama.** 18 test (miktar kuralı, kayıt aritmetiği ve dört "Hedefsiz"
durumu). Migration doğrulaması geçiyor; emülatörde mevcut veriyle çalıştı.

**Emülatörde asıl senaryo:** Çeyrek 15 adet, tamamı Ev'de. 1 tane "Hedefsiz"
eklendi → toplam ₺941.544 → **₺951.381**, Ev hedefi **₺941.544'te kaldı**.
Test kaydı geri alındı, iki rakam da eski haline döndü.

---

## 17 · "Çevrimdışı" mantığı — fiyat ile bulut ayrıldı ✅

9b'nin açık notu buydu: *"'Eşit' çipi aslında FİYAT tazeliğini gösteriyor, bulut
senkronunu değil — ileride ayrılmalı."* Adım 11 gerçek bulut sinyalini getirdiği
için ayrım artık yapılabiliyordu.

**İki ayrı hata vardı.**

1. **Tek kaynak tüm tabloyu düşürüyordu.** `LivePriceRemoteDataSource`'ta
   `freeMarket.fetch()` korumasızdı; ücretsiz uç tökezleyince TCMB ve TEFAS
   cevap verse bile yenileme baştan patlıyor, uygulama kendini çevrimdışı ilan
   ediyordu. Artık ölçüt SONUÇ: elde tek satır fiyat varsa yenileme başarılıdır.
2. **Fiyat tazeliği DİSKE yazıyordu.** `AddTransactionViewModel` `syncState`'i
   `offline` (fiyat) bayrağından türetiyordu: fiyat ucu düştü diye kayıt,
   senkron gayet çalışırken "Bekliyor" damgası yiyordu. Emülatörde gördüğümüz
   "150 Gram Gümüş · Bekliyor" tam buydu.

**Ne yapıldı.** `CloudState` (Off / Synced / Unreachable) `SyncCoordinator`'dan
yayınlanıyor; push ve pull sonuçları besliyor. Başlıktaki çip, ekleme
ekranındaki şerit ve `syncState` artık **bunu** okuyor. Fiyat tazeliği kendi
şeridinde kaldı. Bulut KAPALIYKEN çip hiç çizilmiyor — giriş yapmamış kullanıcıya
her açılışta bozuk bir şey varmış gibi göstermek yanlıştı.

**Yol boyunca çıkan tuzak.** Kaynaklar bağımsızlaşınca kısmi bir çekim tabloyu
KIRPTI: oturum sonucu önbelleğin *yerine* geçiyordu, altın satırları tahtadan
düşüyor ve toplam ₺946k'dan **₺693k'ya** iniyordu. Emülatör yakaladı. Çekim artık
önbelleğin **üzerine bindiriliyor**; iki test bu kuralı kilitliyor.

**Doğrulama.** Emülatörde çip "Eşit", şerit yok, toplam doğru; serbest piyasa o
sırada gerçekten düşüktü ve fiyatlar TCMB'den geldi — yani senaryo canlı yaşandı.

---

## 18 · Açılış animasyonu ✅

**Neydi.** Kullanıcı "sanki 2 tane farklı ekran açılıyor" dedi. Ekran
görüntüleriyle bakınca sebep netti: sistem penceresi zinciri **~156dp**,
askısız, ekranın tam ortasında gösteriyordu; hemen ardından Compose aynı zinciri
**~50dp**, askılı, kelime işaretiyle ve daha yukarıda çiziyordu. Boy, konum ve
içerik aynı anda değişiyordu. Üstelik iki sert kesme vardı (sistem→Compose,
Compose→uygulama) ve animasyonun ortasında 1,2 saniyelik **ölü bir bekleme**
duruyordu.

**Ne yapıldı — üç parça.**

1. Sistem çizimi küçültüldü (`ic_splash_mark.xml`, ölçek 0.72 → 0.40): ekranda
   ~87dp'ye iniyor. Merkez korunuyor (`translate = 54 - 50 × ölçek`).
2. Compose işareti **ekranın ortasından, sistem boyunda** başlıyor ve kendi
   kilidine süzülüyor. Hiçbir şey ilk ~290ms boyunca kımıldamıyor: sistem
   penceresi o sırada soluyor ve iki katman **aynı** şeyi gösteriyor.
3. Sistem penceresi kesilerek değil **soldurularak** bırakılıyor
   (`setOnExitAnimationListener`, 260ms). Solma bir hareket değil bir geçiştir;
   sistemin kendi zoom-out'uyla yaşanan "iki ayrı hareket" sorunu doğmuyor.

**Tek zaman ekseni.** Üç ayrı `Animatable` ve aradaki ölü bekleme kaldırıldı;
her şey tek doğrusal saatten türüyor, aşamalar örtüşüyor. Toplam **2,4 saniye**:
devir teslim → sönümlü zincir salınımı → ad süzülerek beliriyor → işaret hafifçe
büyüyüp çözülüyor. Son kare düz zemin, yani uygulamanın ilk karesiyle aynı renk —
üçüncü kesme de yok.

**Doğrulama.** Emülatörde kare kare yakalandı: açılıştan itibaren **tek** yay,
sabit boyda; sonra kilit kuruluyor, ad geliyor, uygulama açılıyor. Doubling yok.

---

## 19 · Hedef ekranı: atanan kısım ve miktarı sonradan değiştirme ✅

Adım 16 veri modelini kurdu ama ekran onu anlatmıyordu. İki şikâyet:

**1. Liste yalan söylüyordu.** "Bu hedefi karşılayanlar" pozisyonun TAMAMINI
yazıyordu: hedefe 15 çeyrek atanmışken satır "16 adet" ve varlığın tam değerini
gösteriyordu — ekranda görünen şey hedefin saydığı şeyden başkaydı.

**2. Sonradan fikir değiştirilemiyordu.** "Geçen hafta hedefsiz aldığım 1 çeyreği
de Ev'e alayım" demenin yolu yoktu; "Varlık seç" hepsi-ya-hiçbiri anahtarıydı.

**Ne yapıldı.**

- Yeni `GoalAsset` (pozisyon + atama): `quantity` ve `value` artık ATANAN kısmı
  verir. `assetsOf` bunu döndürüyor, ekranlar `Position`'ı doğrudan göstermiyor.
- Liste kısmi atamada "15 adet / 16 adet" yazıyor ve tutar atanan kısmın;
  tamamı atanmışsa eskisi gibi sade "16 adet" (16/16 yazmak gürültü olurdu).
- "Varlık seç" satırı büyüdü: kutu **atandı/atanmadı**, altında **kaydırıcı** ile
  miktar ve bir **"Tümü"** çipi. Üç ayrı şey oldukları için üçü de ayrı duruyor.
- **"Tümü" sabit bir miktar değildir** — ileride alınacakları da kapsar.
  Kaydırıcıyı sonuna sürüklemekle aynı şey olmadığı için ayrı bir seçenek;
  çip, atama `-1` iken dolu, sabit miktardayken boş görünüyor.
- Kaydırıcının sol ucu (0) atamayı kaldırır — ayrı bir "kaldır" yolu açmadık.
- Pencere taşıyordu (her satır üçe katlanınca "Bitti" ekran dışında kalıyordu);
  liste kaydırılır oldu, başlık ve buton sabit.

**Yol boyunca çıkan tuzak — kaydırıcı tam sayıya oturmuyordu.** `KefeSlider`'da
`steps` ARALIK sayısıdır (senaryo kaydırıcısı: 30–120 arası 5'erli = 18), Compose'
daki gibi "aradaki nokta" sayısı değil. Bir eksik verince 15 adetlik varlık
15/14'lük adımlara oturdu: kullanıcı 10'u seçemiyor, **9,64** yazılıyor, etiket de
bunu "10 adet" diye yuvarlayıp gizliyordu. Değer ₺94.874 çıkıyordu, ₺98.388
yerine — yani ekran yine yalan söylüyordu, bu sefer sessizce. Adım sayısı
düzeltildi ve adet/pay ViewModel'de ayrıca tam sayıya yuvarlanıyor: kayan nokta
artığı diske yazılıp eşitlenmesin.

**Doğrulama.** 140 masaüstü testi (3 yeni: liste atanan kısmı taşıyor, tüm-varlık
kısmi sayılmaz, miktar pozisyona eşitse kısmi sayılmaz). Emülatörde: kaydırıcı
10'a çekildi → liste "10 adet / 15 adet", tutar **₺98.388** (= ₺147.581 × 10/15),
hedef %31 → %30; "Tümü" ile geri alındı → "15 adet", %31.

---

## 20 · Klavye: alanı görünür kılma ve alanlar arası geçiş ✅

Gerçek cihazda test edildi — **emülatörde klavye açılmadığı için bu tur orada
doğrulanamazdı** (emülatörde donanım klavyesi açıkken yazılım klavyesi hiç
gelmiyor; `adb shell settings put secure show_ime_with_hard_keyboard 1` ile
açılıyor ama gelen şey yüzen ince bir çubuk, gerçek inset'i temsil etmiyor).

**Neydi — 1: alan yarım görünüyordu.** Ekleme sayfasında "Birim fiyat" alanına
dokununca klavye açılıyor, sayfa küçülüyor ama alanın kutusu sayfanın sabit
altlığı tarafından **kırpılıyordu**: rakam okunuyor, kutunun alt kenarı
görünmüyordu. Sebep ince: Compose odaklanan alanı zaten görünür kılıyor, ama
görünür kıldığı şey **metnin** sınırı — alanın çerçevesi, etiketi ve yardım
satırı dışarıda kalabiliyor.

**Neydi — 2: klavyenin eylem tuşu ölüydü.** Miktar → Birim fiyat gibi ard arda
alanlarda tuş "Tamam" diyordu ve hiçbir şey yapmıyordu; kullanıcı klavyeyi
kapatıp ikinci alana elle dokunuyordu.

**Ne yapıldı.**

- `Modifier.bringFieldIntoView()` — alanın **kutusuna** konur, girişin kendisine
  değil. `onFocusChanged` kapsayıcıya konduğu için `hasFocus` ile alt ağaçtaki
  girişin odağını da görür; her giriş bileşeninin ayrı ayrı bilmesi gerekmez.
  Klavye açılırken görünür alan küçülmeye devam ettiğinden istek **iki kez**
  yapılır (ilki eski ölçüye göre kaydırıyordu).
- `defaultKeyboardActions()` — "İleri" sonraki alana, "Tamam" klavyeyi kapatır.
  `KefeTextField`, `KefeAmountField`, sheet'lerin kendi giriş bileşenleri ve
  `GoalEditSheet` hepsi bunu kullanıyor.
- Miktar alanı artık `imeAction = Next`.

**Yol boyunca çıkan tuzak — `moveFocus(Next)` yetmedi.** Miktar satırında
alandan sonra "−" ve "+" düğmeleri var ve gezinme sırası önce onlara uğruyor:
"İleri"ye basınca odak fiyat alanına değil düğmeye gidiyordu (emülatörde
`KEYCODE_ENTER` ile yakalandı — alan odağı kaybediyor, fiyat alanı almıyordu).
Miktar → birim fiyat zinciri bu yüzden `FocusRequester` ile **açık** kuruldu.

Ayrıca `KefeAmountField`'a hiç klavye türü verilmemişti: miktar alanı tam metin
klavyesi açıyordu.

**Doğrulama (gerçek cihaz, Galaxy S10+).** Birim fiyat alanına dokunuldu →
kutu **tamamıyla** görünür, altlığın hemen üstünde (öncesinde ortadan
kırpılıyordu). Miktar alanına dokunuldu → klavyede **"İleri"** yazıyor;
basınca odak birim fiyat alanına geçti, klavye açık kaldı ve alan görünür
konuma kaydı.

---

## 21 · İnce ondalık: fon fiyatları ve hesapların gözden geçirilmesi ✅

**Neydi.** Kullanıcı fon fiyatını kuruşun altına kadar giriyor ama ekran
yuvarlıyordu. Hane sayısı üç ayrı yerde SABİT yazılmıştı ve ikisi de yanlış
yönde hata yapıyordu:

| Yer | Kural | Sonuç |
|---|---|---|
| Varlık detayı | `fiyat < 100 ? 2 : 0` | ₺108,394521 → **"₺108"** |
| Piyasa tablosu | fon = 2 | ₺3,714754 → "₺3,71" |
| Miktar etiketi | en çok 1 hane | 9,226847 pay → "9,2 pay" |

Fon payı TEFAS'ta **altı haneye** kadar iner ve kesirli alınır; iki haneye
sabitlemek kullanıcının gördüğü rakamı kırpıyordu. Tersi de doğru: ₺40.359'luk
tam altını altı haneyle yazmak okumayı zorlaştırır.

**Ne yapıldı.** Hane sayısı artık **değerden türüyor**, üst sınır varlık
sınıfından geliyor:

- `Money.decimals(value, max, min)` — değerin gerçekten taşıdığı hane sayısı.
  Bağıl tolerans var: 108,39 diskte `108.38999999999999` durabiliyor ve bu
  altı haneli bir fiyat değildir.
- `AssetClass.maxPriceDecimals()` — fon 6, döviz 4, altın/gümüş 2.
- `QuantityUnit.maxQuantityDecimals()` — pay 6, gram 3, döviz 2, **adet 0**
  (adet bölünmez; "15,000000 adet" gürültüdür).
- Piyasa tablosunda **kuruş tabanı** korundu (`min = 2`): adım 3'teki "fiyat
  donmuş sanılıyor" düzeltmesi bozulmasın.

**Hesaplar gözden geçirildi — değiştirilecek bir şey çıkmadı.** Kayda değer
olanlar:

- `todayChange()` her pozisyonu **dünkü değerine geri çözüp** TL farkları
  topluyor; yüzdeleri ortalamıyor. `todayChangePercent` de TL değişimin dünkü
  toplama oranı — değer ağırlıklı, doğru.
- Yıllık getiri **XIRR** (Newton-Raphson + ikiye bölme yedeği): bileşik doğru
  kurulmuş, her liranın kaç gün çalıştığını sayıyor.
- Maliyet ağırlıklı ortalama, para yolunda tek bir yuvarlama yok; SQL kolonları
  `REAL`, alan adı `Double`.
- **Hedef projeksiyonu bilerek getiri VARSAYMIYOR** (`değer += aylık katkı`).
  Bileşik büyüme eklemek bir beklenen getiri uydurmak demek; depo bu kararı
  daha önce açıkça almış ("uydurma sayı üretmez"). Dokunulmadı.

**Doğrulama.** 151 masaüstü testi (11 yeni: değerden türeyen hane sayısı, üst/alt
sınır, kayan nokta artığı, sınıfa göre tavanlar). Emülatör ve **gerçek cihaz**:
`5 pay × ₺108,391402`, `46 pay × ₺10,972608`, `107 pay × ₺3,714754`,
`238 pay × ₺1,59423`. Varlık detayında "Ortalama maliyet ₺110,048" ve "Güncel
birim fiyat ₺108,391402" (önce "₺110" ve "₺108" yazıyordu).

---

## 22 · Varlık detayından ekleme o varlıkla açılır ✅

**Neydi.** Varlık detayında "Alım Ekle"ye basınca sayfa 1. adımdan, boş
açılıyordu: kullanıcı hangi varlıkta olduğunu zaten söylemişken sınıfı ve alt
türü bir kez daha seçmek zorundaydı.

**Ne yapıldı.** `StartNew` artık isteğe bağlı bir `positionId` taşıyor. Doluysa
form o pozisyondan doldurulup doğrudan 2. adıma geçiyor; alanlar `loadForEdit`
ile **aynı yoldan** çözülüyor (fon ve döviz kimliği pozisyon kimliğinden türer,
yoksa kayıt yeni bir pozisyona yazılırdı).

**Doğrulama.** Emülatörde AN1 fonunun detayından "Alım Ekle" → sheet
*"2. adım · AN1 · STRATEJİ PORTFÖY BİRİNCİ DEĞİŞKEN FON"* ile açıldı, birim
"pay", fiyat 108,391402 dolu; "Satış Ekle" → aynı sayfa, **Satış** seçili.

---

## 23 · Geri tuşu alt sayfayı kapatır ✅

**Neydi.** Ekleme sayfası, hedef düzenleme, varlık seçici, elle fiyat sayfası,
varlık menüsü ve silme onayı açıkken sistem geri tuşuna basmak sayfayı
kapatmıyor, **arkadaki ekranı geri atıyordu**. Kullanıcı sayfayı kapatmak için
basıyor, kendini başka ekranda buluyordu. Onboarding ve giriş kod kutusu bu
dersi zaten almıştı (adım 4, 9b); alt sayfalar almamıştı.

**Ne yapıldı.** Ortak `KefeBackHandler` (`ui/components/BackHandler.kt`).
Compose'un `ui-backhandler`'ı kullanımdan kaldırıldığı için altında
navigationevent'in `NavigationBackHandler`'ı var — NavDisplay da kendi geri
işleyicisini aynı göndericiye kaydediyor, yani ikisi aynı sırada buluşuyor.

- `KefeBottomSheet` ve `KefeConfirmDialog` işleyiciyi **kendi içinde** taşıyor;
  onları kullanan her sayfa bunu kendiliğinden alıyor.
- Ekleme sayfası **iki adımı da tanıyor**: 2. adımda geri bir adım geri gider,
  1. adımda sayfa kapanır. Üst bardaki geri okuyla aynı davranış.

**Yol boyunca çıkan tuzak — işleyici KOŞULSUZ bestelenmeli.** İlk sürümde her
katman kendi işleyicisini `if (açık)` içinde besteliyordu. Emülatörde geri
tuşu **her seferinde başka türlü** davrandı: bir kez 1. adıma döndü, bir kez
sayfayı kapattı, bir kez hiç yakalanmayıp uygulamadan çıktı. Sebep
kütüphanenin kendi uyarısında yazıyor: hangi işleyicinin çalışacağını *son
bestelenen* belirler, `if` ile girip çıkan bir işleyici o sırayı bozar.

Düzeltme: işleyiciyi katmanın **sahibi** koşulsuz besteler, görünürlüğü
`enabled` ile verir. `KefeConfirmDialog` bu yüzden artık `visible` parametresi
alıyor (çağıran taraftaki `if` kalktı), varlık menüsü ile varlık seçicinin
işleyicileri ekran köküne çıktı, ekleme sayfasınınki kabuğa (App.kt) taşındı.
`AddTransactionViewModel` de bu yüzden kabukta yaşıyor — `GoalsViewModel` ile
aynı yerde.

**Doğrulama.** Emülatörde art arda: 2. adımda geri → **1. adım**; 1. adımda
geri → sayfa kapandı, arkadaki ekran değişmedi; kök sekmede geri →
uygulamadan çıktı (Android'in beklenen davranışı). Üç tekrarda da aynı sonuç.

---

## 24 · İnce ondalık: tutarlar da kuruşlu ✅

**Neydi.** Adım 21 FİYAT hanelerini değerden türetti ama TUTARLARA
dokunmamıştı. Varlık detayında "Güncel değer" `₺147.581`, kâr `+₺39.291`,
yüzdeler tek hane; varlıklar listesinde kâr/zarar ondalıksız. Kuruşuna kadar
hesap yapan biri için ekran hâlâ kırpıyordu.

**Ne yapıldı.** `Money.tlExact` / `tlSignedExact` — "Kuruşları göster"
ayarından **bağımsız**. Ayar ana toplamlar için konmuştu; varlık detayındaki
değer ve listedeki kâr/zarar ana toplam değil, kullanıcının bakıp karar
verdiği rakam. Yüzdeler tek haneden ikiye çıktı (`Money.delta` varsayılanı).

**İKİ KADEMELİ hane.** Emülatörde yakalandı: değerden türeyen hane sayısı
`₺670.503,6` üretiyordu. Parada ya kuruş vardır ya yoktur; tek hane para
yazımı değil ve üst üste dizilen bir sütunda rakamlar eğri görünüyor. Kural
"ya 0 ya 2" oldu — tam sayı tutar hâlâ `₺40.359`, kuruşlu tutar `₺670.503,60`.

Ekleme sayfasındaki **Toplam** da buna döndü: hemen altındaki
"1 × ₺5.593,05" satırı kuruşu gösterirken toplamın "₺5.593" demesi ikisini
çelişkiye düşürüyordu.

**Doğrulama.** 11 yeni test (kuruş yazımı, tam sayıda ",00" yok, tek hane yok,
kayan nokta artığı, kuruştan öteye gitmez). Emülatörde fon detayı:
"₺ 541,96", "Maliyet ₺550,24", "−₺8,28 (−1,51%)".

---

## 25 · Günlük / haftalık / aylık değişim ✅

**Neydi.** Piyasa tablosu ve varlıklar tek bir "bugün" penceresinden
bakıyordu; haftalık ve aylık hareket hiçbir yerde yoktu.

**Kaynak sorunu ve çözümü.** Altın, gümüş ve dövizde geçmiş veren bir uç YOK:
serbest piyasa yalnız bugünü, TCMB yalnız günlük bülteni veriyor. Bu yüzden
tek gerçek kaynak cihazdaki `price_history` tablosu oldu — zaten günlük
yazılıyordu.

**Fonlar ilk günden dolu.** TEFAS bir AYLIK seri döndürüyor (`periyod: 1`) ve
şimdiye kadar yalnız son iki satırı okunup gerisi çöpe gidiyordu. O seri artık
`price_history`'ye yazılıyor: fonlarda hafta/ay **gerçek**, üstelik varlık
detayındaki fiyat grafiği de fonlarda gerçek bir aylık eğri kazandı. Tarih
biçimi (`2026-07-31`) tahmin edilmedi, canlı sondayla ölçüldü
(`-Pprobe --tests "*LivePriceProbeTest"`); ayrıştırılamayan satır atlanıyor.

**Veri yoksa "—", sıfır değil.** "Değişmedi" ile "bilmiyoruz" ayrı şeyler.
7 günlük pencere 3, 30 günlük pencere 7 gün esner (hafta sonu, uygulamanın
açılmadığı günler); daha eskisine uzanmak "40 gün önceki fiyatı haftalık diye
yazmak" olurdu. **Elle fiyatlı satırda dönem değişimi hiç yok**: geçmişteki
satırlar kaynağın fiyatı, bugünkü kullanıcının girdiği rakam — ikisini
kıyaslamak iki ayrı ölçünün farkı olurdu.

**Grup değişimi DEĞER AĞIRLIKLI.** ₺900.000'lik altının %1'i ile ₺1.000'lik
fonun %10'u aynı ağırlıkta değil; yüzdeler ortalanmıyor (`todayChange()` ile
aynı mantık). Yüzdesi bilinmeyen pozisyon hesaba hiç girmiyor.

**Ekran.** Piyasa ve Varlıklar başlığına **Gün / Hafta / Ay** çipleri; tek
değişim sütunu seçilen dönemi yazıyor. Üç ayrı sütun telefonda ürün adına
~16dp bırakıyordu. Varlıklar satırında **TL kâr birincil kalıyor** (adım 14
kararı bozulmadı), dönem değişimi altına sönük ikinci satır olarak düşüyor.
**Elde varlık yokken seçiciler hiç çizilmiyor** — sıralanacak liste, ölçülecek
değişim yok (Özet'in boş durumuyla aynı gerekçe, adım 9b).

**Grafik etiketi düzeltildi.** Sabit "12 ay" yazıyordu ve bu bir varsayımdı;
fonlarda dolu bir aylık eğrinin üstünde açıkça yalan oldu. Etiket artık
serinin gerçek aralığını yazıyor ("1 Tem – 31 Tem").

**Doğrulama.** 13 yeni test (tam gün, tolerans içi/dışı, pencerenin en yenisi,
boş geçmiş, düşüşün işareti, değer ağırlığı, bilinmeyenin hesaba girmemesi).
Emülatörde: fonlarda hafta `−1,07% / −0,59% / −1,82% / −7,06%`, grup
`Hafta −2,63%`; altın, gümüş ve dövizde "—" (cihazda henüz geçmiş yok).

---

## 26 · Gram altında ayar seçilebiliyor ✅

**Neydi.** Alt tür listesi "Gram (24 ayar)" diyordu ve ayar YALNIZ
"Bilezik/Takı" seçilince soruluyordu. **22 ayar gram altını** olan biri onu
ancak "Bilezik/Takı" diye kaydedebiliyordu: hesap doğru çıkıyor ama ekranda
elindekinden başka bir şey yazıyordu. Oysa 14/18/22 ayar gramın kendi
kotasyonu piyasa tablosunda zaten duruyordu — ulaşılamıyordu.

**Ne yapıldı.** Ayar artık formdan AYRI bir eksen:

- `GoldSubtype.usesKarat()` — **gramla satılan** formlarda ayar sorulur (gram,
  bilezik). Adetle satılanlarda formun kendisi ayarı belirler: çeyrek, yarım,
  tam, ata hep 22'dir; has/külçe zaten saf altındır.
- Varsayılan: gramda 24, bilezikte 22. Form değişince ayar da o formun
  varsayılanına döner — bilezikten grama geçen biri 22 ayar seçili kalırsa,
  ayar panelini fark etmediğinde 24 ayar gramını 22 ayar fiyatıyla kaydederdi.
- Ad "22 Ayar Gram Altın"; **24 ayarda ayar yazılmıyor**, "Gram Altın" kalıyor.

**Eski kayıtlar zerre değişmiyor.** `karat` kolonu eski gram kayıtlarında boş;
varsayılan 24 ve 24 ayar zaten `gold_gram` anahtarına düşüyor. Pozisyon
eşleştirmesi de ETKİN ayarı kıyaslıyor (`karat ?: varsayılan`) — düz
`karat == seçili` deseydik mevcut "Gram Altın" 24 ayar seçimiyle eşleşmez,
her ekleme ikinci bir pozisyon açardı.

**Gram kutusu yalnız bilezikte.** Düz gram altında miktar zaten 2. adımda
giriliyor; 1. adımda ikinci bir gram kutusu aynı sayıyı iki kez sormak olurdu.
Orada yalnız seçili ayarın gram fiyatı yazıyor.

**Doğrulama.** 4 yeni test (gram+ayar anahtarı, ayarsız eski kaydın
değişmemesi, adetle satılanların ayar sormaması, 22 ile 24'ün ayrı anahtara
düşmesi). Emülatörde: Gram → AYAR paneli (14/18/22/24, varsayılan 24) →
22 seçildi → *"2. adım · 22 ayar Gram Altın"*, birim gram, birim fiyat
**₺5.593,05** (24 ayarın ₺6.175'inden ayrı, gerçek 22 ayar kotasyonu).

---

## 27 · Varlıklarda tek rakam çifti: Gün / Hafta / Ay / Toplam ✅

**Neydi — 1: renk sayının tersini söylüyordu.** Grup başlığı kâr ile dönem
değişimini TEK metinde birleştiriyor ve satır tek renk taşıyordu:

> `+₺235.821,98 · +35,78% · Gün −0,94%` — hepsi **yeşil**

Altın o gün %0,94 gerilemişti; rakam eksi, renk artıydı.

**Neydi — 2: aynı satır iki ayrı soruya cevap veriyordu.** Üstteki TL hep
TOPLAM kârdı ("bugüne kadar ne kazandım"), altındaki yüzde ise dönemin
("bugün ne oldu"). İkisi yan yana durunca hangi rakamın neyi söylediği
okunmuyordu.

**Ne yapıldı.** Tek rakam çifti kaldı ve çip hangi soruyu sorduğunu söylüyor:
**Gün · Hafta · Ay · Toplam**. TL de yüzde de birlikte değişir, ikisi de aynı
rengi taşır — artık çelişecek iki sayı yok.

- Varsayılan **Toplam**: listeye bakan önce "ne kadar kazandım" diye sorar.
- Toplam'da yüzdenin paydası **maliyet** (getiri tanımı); dönemlerde **dönem
  başındaki değer**. `PeriodTotal` ikisini tek çözümden verir — ayrı
  hesaplansalar yuvarlamada ayrışır ve "+₺100 · +0,00%" gibi kendi kendiyle
  çelişen satırlar çıkardı.
- Sıralamadaki "Kâra göre" **her zaman toplam kâra** göredir; pencere onu
  değiştirmez. Ayrı bir seçici, ayrı bir soru.
- Veri yoksa "—" ve nötr renk. Dönemde **gerçek sıfır yazılır** (`₺0 · 0,00%`):
  "bugün değişmedi" bir cevaptır; tek başına "₺0" eksik veri gibi okunuyordu.
  Toplam'da sıfır yüzde ise "maliyet yok" demektir (nakit) ve yazılmaz.

**Yüzdeler gözden geçirildi, hesapta hata çıkmadı.** Gerçek cihazdaki
rakamlarla tek tek doğrulandı:

- Altın: satır değerleri toplamı ₺894.821,98, kârlar toplamı ₺235.821,98 ✓
- `+35,78%` = 235.821,98 / (894.821,98 − 235.821,98) — yani **kâr / maliyet** ✓
- `Gün −0,94%` = her pozisyon dünkü değerine geri çözülüp TL farklar
  toplanarak; değer ağırlıklı ✓

Fondaki `−₺21,14 · −1,03%` de doğru: payda MALİYET (2.052,38), güncel değer
(2.031,24) değil. "Getiri" tanımı gereği maliyete oranlanır — 21,14/2.031
bölünürse −1,04% çıkar, aradaki fark budur.

**Doğrulama (gerçek cihaz, Galaxy S10+).** "Toplam" seçili açıldı:
`+₺235.821,98 · +35,78%` yeşil. "Gün"e dokunuldu → aynı satır
`−₺8.471,96 · −0,94%` kırmızıya döndü, satırlar da (`−₺6.294,22 · −0,93%`);
Döviz `+₺14,78 · +0,06%` yeşil kaldı.

---

## 28 · Net değer çipleri grafiği gerçekten değiştirir ✅

**Neydi.** `1A / 3A / 6A / 1Y / Tümü` çipleri yalnız grafiğin ÜSTÜNDEKİ
açıklamayı değiştiriyordu; eğri her zaman tüm seriyi çiziyordu. "3A" ile
"Tümü" aynı resmi veriyordu — çipler dokunulabilir ama karşılıksızdı.

**Ne yapıldı.** Çipler seriyi gerçekten pencereliyor ve **Gün** ile **Hafta**
eklendi: fotoğraflar günlük olduğu için kısa vade de çizilebiliyor.

- `NetWorthRange` **enum**, çıplak index değil. Başa iki seçenek eklemek,
  masaüstü düzenindeki sabit `1..4` eşlemesini sessizce yanlış aralıklara
  kaydırırdı.
- Yedi çip eşit paya bölününce dar telefonda "Tümü" kırpılıyordu; dönem
  çipleri artık metni kadar yer alıp gerekirse yatay kayabiliyor.
- **İki noktadan az kayıt varsa eğri değil kısa bir not çizilir.** İki nokta
  bir çizgidir, bir nokta değil; kartın parmağın altında kaybolması "neden
  çizilmedi" yazmaktan kötüdür. Hiç fotoğraf yokken kart yine hiç çizilmez —
  "bu aralıkta yok" ile "hiç yok" ayrı durumlar.

**Doğrulama (gerçek cihaz, Galaxy S10+).** Altın başlığında `Gün −0,94%`
**kırmızı**, `+35,78%` yeşil; Döviz'de `Gün +0,06%` yeşil. Net değer kartında
yedi çip sığdı; "Hafta" → *Son 7 gün*, "Gün" → *Dün → bugün* ve eğri iki
noktaya indi.

---

## 29 · Açılışta giriş ekranı parlamıyor ✅

**Neydi.** Splash'ten sonra bir an giriş (e-posta) ekranı görünüyordu. Kare kare
yakalandı: `Kefe` (splash) → boş kare → kilit ekranı.

**Sebep.** `LoginViewModel` `stage = SignIn` ile **doğuyor**; kilit ancak
`LaunchedEffect(locked, asRoot) { onIntent(Lock) }` ile, yani ilk bestelemeden
SONRA geliyordu. Arada en az bir kare giriş aşaması çiziliyordu.

**Ne yapıldı.** Kök `LoginKey` ve kilit açıkken **çizim etkiyi beklemez**:
kabuk `stage`'i ilk kareden `Locked`'a zorluyor. Bu, itilmiş LoginKey'i temiz
`SignIn`'e zorlayan mevcut hilenin simetriği (adım 9b) — aynı yerde, aynı
gerekçeyle. Etki yine çalışıyor, yalnız ekran onu beklemiyor.

**Doğrulama (gerçek cihaz).** Kare kare UI dökümü: `Kefe` → `Kefe kilitli ·
Parmak izinizi tarayın` → `Birikimlerim`. Hiçbir karede giriş ekranı yok.

*(Splash'in son karesi düz zemindir — adım 18'in kararı; o boş kare tasarımın
kendisi, hata değil.)*

---

## 30 · Hedef detayı yüklenirken "bulunamadı" demiyor ✅

**Neydi.** Hedef detayı ilk açılışta bir an *"Hedef bulunamadı — bu hedef
silinmiş olabilir"* gösteriyor, veri gelince normal ekranı çiziyordu.
Silinmemiş bir hedef için yanlış bir cümle.

**Sebep.** Ekran `state.stage`'i hiç okumuyordu; yalnız `goal == null`a bakıp
boş duruma düşüyordu. Oysa durum `Loading` ile başlar ve depo ilk emisyonunu
yapana kadar hedef doğal olarak null'dur. **"Yok" ile "henüz gelmedi" ayrı
şeylerdir** — varlık detayı bu ayrımı zaten yapıyordu (adım 22).

**Ne yapıldı.** `Loading` iken iskelet çizilir; `Missing` iken boş durum.
`GoalDetailSkeleton` halka, özet ve katkı kartının yerini tutuyor.

**Doğrulama (gerçek cihaz).** Hedef kartına dokunuldu, ilk kareden itibaren
`Ev · %31 · ₺939.170 / ₺3.000.000` — hiçbir karede "bulunamadı" yok.

---

## 31 · Fiyat geçmişine emniyet sınırı ✅

**Soru.** Haftalık/aylık değişim için tutulan `price_history` zamanla çok mu
büyür?

**Ölçüldü, tahmin edilmedi.** Gerçek cihazın veritabanı çekilip `dbstat` ile
bakıldı:

| Ölçü | Değer |
|---|---|
| Satır başı (indeks dahil) | **~80 bayt** |
| 19 varlık × 1 yıl | **0,53 MB** |
| 19 varlık × 10 yıl | 5,3 MB |
| 40 varlık × 10 yıl | 11 MB |

O anki durum: 154 satır, 19 varlık, 25 gün; tüm veritabanı 164 KB.

**Yani boyut sorunu YOK.** Buna rağmen sınırsız büyüyen bir tablo bırakmıyoruz:
her başarılı çekimden sonra **iki yıldan eski** günler siliniyor. Portföyden
çıkarılan bir fonun anahtarı da böylece zamanla düşüyor.

**Sınır neden bu kadar geniş.** Varlık detayındaki eğri BU tablodan çizilir —
eski satırlar ölü veri değil, grafiğin kendisi. Dönem hesabı 60 gün istiyor;
iki yıl ondan kat kat fazla ve grafiği kırpmıyor.

## 32 · Hisse senedi: BIST, ABD ve Avrupa borsaları ✅

**Neydi.** Portföye girebilen tek piyasa aracı fondu. Kullanıcı "sadece fon da
değil, hem Amerika hem Türkiye borsasında hisse senedi de görebilir miyiz?"
diye sordu; sonra da "sadece Nasdaq'ta olanlar gelse yeterli, bir de Avrupa
borsası" diyerek kapsamı daralttı.

**Ne yapıldı.** Anahtar istemeyen tek bir borsa ucu; her iki pazar da aynı
yanıt biçiminde geliyor (BIST sembolleri `.IS`, Londra `.L`, Paris `.PA` ekli,
ABD sembolleri çıplak). Yeni `AssetClass.Stock`, kendi rengi (gül-mor), kendi
ikonu (sütun grafik — fon çizgi grafik, listede yan yana ayrışsınlar diye) ve
kendi miktar birimi.

**Miktar birimi neden `Piece` değil.** Çeyrek altın bölünmez ama hisse
bölünür — ABD'de kesirli pay satılıyor. `Piece`'in sıfır ondalık haneli olması
kesirli payı sessizce yuvarlardı, üstelik `adetBolunmez` testi onu böyle
kilitliyor. Ayrı bir birim açıldı; etiket yine "adet", çünkü kullanıcının
kullandığı kelime bu.

**Fiyat TL'ye çevrilir.** Uygulama baştan sona lira ile çalışıyor. Çevrim,
aynı yenilemenin zaten çektiği USD/TRY kurundan yapılıyor. Kur yoksa satır
**atlanır**: 308 sayısını lira sanıp portföy toplamına yazmaktansa fiyat hiç
gelmesin.

**Üç tuzak, üçü de ölçümle bulundu.**

| Tuzak | Belirti | Kök neden |
|---|---|---|
| Gün içi seri | 78 nokta, hepsi 31 Temmuz | Uç parametresiz çağrıda intraday dönüyor; `range=1mo&interval=1d` şart |
| Karşılıksız borsalar | "AAPL" araması Buenos Aires ve São Paulo getiriyor | Kuru olmayan para birimi → fiyat hiç gelmiyor |
| **Peni** | SHEL.L cihazda **₺217.325**, doğrusunun tam yüz katı | Londra `GBP` değil **`GBp`** döner; ayrıştırmadaki `.uppercase()` küçük `p`'yi siliyordu |

Peni tuzağı önemli çünkü **birim testi geçiyordu**: test yardımcıyı doğrudan
çağırıyor, hatalı satır ise ağın arkasındaki `fetch` gövdesindeydi. Bu yüzden
ayrıştırma `parseStockQuote` olarak dışarı alındı ve gerçek yanıt gövdeleriyle
sınandı — aynı sınıftaki bir hata bir daha sadece cihazda görünmesin.

**Arama fondan farklı çalışır.** TEFAS ucu ad araması yapmaz, o yüzden fonda
kod yazılıp "TEFAS'ta ara" düğmesine basılıyor. Borsa ucu adla arıyor —
"aselsan" yazan biri ASELS.IS'i buluyor — dolayısıyla yazdıkça aranır (350 ms
bekleme, önceki istek iptal). Arama fiyat döndürmez; 12 sonuç için 12 kotasyon
çekmek yerine fiyat yalnızca **seçilen** sembol için çekilir.

**Süzgeci coğrafya değil KUR belirliyor.** Gösterilen borsalar, para birimini
TL'ye çevirebildiklerimiz: IST, Nasdaq/NYSE aileleri, euro bölgesi ve Londra.
İsviçre (CHF), İskandinav borsaları ve OTC dışarıda — seçilseler fiyat hiç
gelmezdi. Borsa kodları tahmin edilmedi, uçtan ölçüldü.

**Doğrulama (gerçek cihaz, R58N81SAZ1Y).** "aselsan" → ASELS.IS · IST ·
₺342,25 · −%3,46. 40 adet kaydedildi: toplam ₺939.170 → ₺952.860, yani tam
40 × ₺342,25 = ₺13.690. "AAPL" → tek satır NASDAQ (Buenos Aires ve São Paulo
elendi), ₺14.690,96 · −%7,35; ham uçta 308,91 USD ve 47,56 kur ile birebir
tutuyor. "SHELL" → NYSE, Amsterdam, Londra, Frankfurt, XETRA; Meksika elendi.

**Bir ayrıntı daha.** Hisse fiyatı önce dört ondalık haneyle yazılıyordu
(₺14.690,**9564**). Çevrimden doğan kuyruk bilgi değil gürültü: on dört bin
liralık bir rakamda son iki hane hiçbir şey söylemiyor. İki haneye indirildi.

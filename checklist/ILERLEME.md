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
| 8 | Jeton Keystore/Keychain'e | ✅ **bitti** (7'den önce) |
| 7 | Supabase tabloları + RLS | ⬜ |
| 9 | Push | ⬜ |
| 10 | Pull | ⬜ |
| 11 | Gerçek zamanlı | ⬜ |
| 12 | Bildirimler | ⬜ (11'den sonra anlamlı) |

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

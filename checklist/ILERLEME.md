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
| 6 | Ayarlar temizliği ve tamamlanması | ⬜ sırada |

## Senkron (sonraki tur)

| # | Adım | Durum |
|---|---|---|
| 7 | Supabase tabloları + RLS | ⬜ |
| 8 | Jeton Keystore/Keychain'e | ⬜ (7'den önce) |
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

# Ender Kütüphane — Yol Haritası

Bu dosya, tamamlanan işi ve sıradaki fazları tanımlar. Her faz bağımsız teslim edilebilir
(commit + kurulabilir APK) ve kendi kabul kriterleriyle kapanır.

Durum işaretleri: **✅ tamam** · **⬜ planlandı**

---

## Faz 0 — Temel + performans (✅ tamamlandı)

| İş | Durum |
|---|---|
| Vintage ahşap raf, 180 cilt, kaydırmasız tek ekran | ✅ |
| Eğik cilt kuralı (satır başına 1, 5°, komşusuna yaslı) | ✅ |
| Barkod tarama + elle ISBN girişi | ✅ |
| Künye zinciri: Google Books → Open Library | ✅ |
| Baskı eşleştirme (farklı ISBN, aynı eser) | ✅ |
| Sırt yazıları (başlık + yazar, döndürülmüş serif) | ✅ |
| Google Books API anahtarı + 10 dk devre kesici | ✅ |
| Sırt yazısını Canvas'a taşıma (açılış donması giderildi) | ✅ |
| Uçuş animasyonu akıcılığı (2D, GPU dostu) | ✅ |

Kanıt: `9789750718533` → *Tutunamayanlar / Oğuz Atay / İletişim 1971 / 724 s.*
(Google 0 sonuç döndürdü, Open Library yedeği künyeyi getirdi.)

Kanıt (uçuş animasyonu, fiziksel telefon M2101K7BG):
- Sırt → kapak dönüşümü 3D'den (rotationY + cameraDistance + shadowElevation) 2D'ye
  (konum + ölçek + alfa + hafif yay) taşındı; bazı GPU'larda tek kareyi saniyelerce
  kilitleyen donma kalktı.
- Uçuş katmanı ilk kare hazır olmadan çizilmiyor (`flightArmed`) + `progress`
  sıfırlandıktan sonra iki kare bekleniyor → Animatable'ın eski değeri yüzünden
  rafın bir anda kararması ve sırtın sıçraması giderildi.
- Scaffold alt barı kaldırılmıyor, alfa ile gizleniyor → 180 sırt yeniden ölçülmiyor.
- Raf `graphicsLayer` ile donduruluyor; uçuşta yeniden çizilmiyor.
- Ekran kaydı (30 fps) kare kare incelendi: sırt önce raftan kalkıyor, arka plan
  kademeli kararıyor (0.20 → 0.62), kapak en son hedefe oturuyor; sıçrama yok.
- `dumpsys gfxinfo`: 50. persentil 15 ms (öncesi 38 ms).

---

## Faz 1 — Kitap silme + geri al (✅ tamamlandı)

**Neden:** Yanlış okutulan/eklenen kitabı kaldırmanın yolu yok; kütüphane zamanla kirlenir.

**Kapsam**
- Detay ekranında ikincil buton: **"Kitaplıktan çıkar"** (dolgusuz, kırmızı değil).
- Onay diyaloğu: kapak + başlık + ISBN, "Vazgeç / Çıkar".
- Silme sonrası 5 sn **"Geri al"** snackbar'ı (aynı `BookEntity` yeniden insert edilir).
- `LibraryViewModel.remove(isbn)` / `undoRemove(book)`; DAO'daki `delete` zaten mevcut.
- Silme anında detay katmanı kapanır, raf yeniden yerleşir — uçuş animasyonu **tetiklenmez**.

**Kabul kriterleri**
1. Kitap rafta ve "Kitaplarım" listesinde kaybolur, uygulama yeniden açılınca da yoktur.
2. "Geri al" aynı kitabı aynı ISBN ile geri getirir, sırt aynı yere döner.
3. Silme sırasında raf titremesi/boş kare yok.

**Dosyalar:** `LibraryViewModel.kt`, `LibraryRoot.kt`, `BookDetailLayer.kt`, `LibraryRepository.kt`
**Tahmin:** 1 oturum · **Risk:** düşük

Kanıt (emülatör, `emulator-5556`):
- "Kitaplıktan çıkar" → onay diyaloğu → "Çıkar": kitap raftan/listeden düşüyor, sayaç 9'a iniyor.
- Snackbar'daki **"Geri al"** → kitap aynı ISBN ile geri geliyor, sayaç 9'da kalıyor (silindi + geri alındı), "Fantastic Mr. Fox" listede yeniden görünüyor.
- Silme sırasında uçuş animasyonu tetiklenmiyor, raf yerleşimi titremiyor.

---

## Faz 2 — Rafta arama ve vurgulama (✅ tamamlandı)

**Neden:** 100+ kitapta gözle kitap bulmak imkânsızlaşıyor; raf kaydırmasız kalmalı.

**Kapsam**
- Başlıktaki arama ikonu → rafın üstünde ince arama çubuğu (rafın yerleşimini bozmaz).
- Yazarken eşleşen sırtlar **altın çerçeve**, diğerleri %35 karartılır.
- Enter veya ilk sonuca dokunma → mevcut uçuş animasyonuyla detay açılır.
- Eşleşme yoksa: "Kütüphanende yok — ISBN ile ekle" (elle ISBN diyaloğuna köprü).
- Normalizasyon: Türkçe `İ/ı/ş/ğ/ü/ö/ç` duyarsız, `WorkMatcher`'ın normalize fonksiyonu yeniden kullanılır.

**Kabul kriterleri**
1. "kurk" ve "Kürk" aynı sırtı bulur.
2. Arama açıkken raf kaymaz, sırt genişlikleri değişmez.
3. Klavye açıkken alt satırlar erişilebilir kalır (raf ölçeklenmez, sadece karartılır).

**Dosyalar:** yeni `ShelfSearch.kt`, `ShelfModels.kt`, `Bookshelf.kt`, `LibraryRoot.kt`
**Tahmin:** 2 oturum · **Risk:** orta (raf ölçüleri sabit kalmalı)

Kanıt (emülatör, `emulator-5556`):
- "kurk" → *Kürk Mantolu Madonna* sırtı altın çerçeveli, kalan sırtlar karartılmış; raf kaymıyor.
- Enter → mevcut uçuş animasyonuyla detay açılıyor; geri tuşu aramayı kapatıyor.
- "qqqxx" gibi eşleşmeyen sorgu → "Kütüphanende bulunamadı" snackbar'ı; **"ISBN ile ekle"** aksiyonu elle ISBN diyaloğunu ("ISBN ile ara") açıyor.
- `ShelfSearchTest` 6 senaryo (ASCII yolda çalıştırılmalı; Türkçe karakterli Windows yolunda `ClassNotFoundException`).

---

## Faz 3 — Kapak ön indirme ve önbellek (✅ tamamlandı)

**Neden:** Uçuş animasyonu sırasında kapak henüz inmemişse yer tutucu görünüyor.

**Kapsam**
- `enrich` sırasında kapak indirme zaten var; ek olarak **açılışta kapaksız kitaplar için sessiz ön indirme** (tek seferde en fazla 10, eşzamanlılık 3).
- `fetchCoverBytes`: tam künye sorgusu yapmadan doğrudan Open Library ISBN kapağı → Google thumbnail.
- Açılışta **kırık kapak referansı onarımı**: `coverPath` dolu ama dosya yoksa yol temizlenir, ön indirme onarır.
- Coil disk önbelleği sabit boyut (100 MB) + %15 bellek önbelleği (`ImageLoaderFactory`).
- İndirme başarısızsa üretilen cilt kapağı kalır (mevcut davranış korunur).

**Kabul kriterleri**
1. Uçak modunda açılışta daha önce görülen kapaklar diskten anında gelir.
2. Yeni eklenen kitap ≤ 1 sn içinde kapakla açılır.
3. Ön indirme, kullanıcı rafı kaydırırken kare düşürmez (IO dispatcher).

**Dosyalar:** `LibraryRepository.kt`, `BookDao.kt`, `BookMetadataSource.kt`, `KitaplikApp.kt`, `LibraryViewModel.kt`
**Tahmin:** 1 oturum · **Risk:** düşük

Kanıt (emülatör, `emulator-5556`):
- Öncesinde `files/covers/` boştu (DB'de 8 kayıt kırık yolla duruyordu). Açılıştan sonra 7 kapak indirildi (23:51).
- Kırık yollu *Fantastic Mr. Fox* bir sonraki açılışta onarıldı (74 KB, 23:56); kütüphanede 8/9 kitap kapaklı.
- `Saatleri Ayarlama Enstitüsü` (9789750707711) için kaynaklarda kapak yok → üretilmiş kapak gösteriliyor; her açılışta sessizce yeniden denenir.
- Açılış bloke olmuyor, ANR/FATAL yok; raf hemen çiziliyor.

---

## Faz 4 — WorkManager ile çevrimdışı kuyruk (✅ tamamlandı)

**Neden:** İnternetsiz eklenen kitap `enriched=false` kalıyor; şu an yalnızca uygulama açılışında deneniyor.

**Kapsam**
- `EnrichWorker`: `NetworkType.CONNECTED`, exponential backoff (30 sn → 1 sa), en fazla 5 deneme.
- `enqueueUniqueWork(APPEND_OR_REPLACE)` ile tek kuyruk; uygulama açılışında `retryPending()` yerine worker tetiklenir.
- 5 denemeden sonra detayda **"Künye eksik — yeniden dene"** rozeti.
- Pil/veri dostu: yalnızca şarjda değil, ağ varken; Wi-Fi zorunlu değil.

**Kabul kriterleri**
1. Uçak modunda kitap ekle → Wi-Fi aç → ≤ 1 dk içinde künye tamamlanır.
2. Aynı kitap için ikinci worker kuyruğa girmez.
3. Uygulama kapalıyken de tamamlanır.

**Dosyalar:** yeni `data/EnrichWorker.kt`, `KitaplikApp.kt`, `LibraryRepository.kt`, `LibraryViewModel.kt`
**Tahmin:** 1.5 oturum · **Risk:** orta (WorkManager başlatma + Room eşzamanlılığı)

Kanıt (emülatör, `emulator-5556`):
- Uçak modunda `9780061120084` eklendi → `enriched=0`; WorkManager işi `CONNECTIVITY` kısıtıyla kuyruğa girdi (`dumpsys jobscheduler`, paket `com.ender.kitaplik`).
- Ağ açıldıktan ~30 sn sonra kayıt `enriched=1`, başlık "To Kill a Mockingbird" oldu (uygulama açıkken; iş UI'dan bağımsız).
- Aynı ISBN için ikinci worker oluşmuyor (`enqueueUniqueWork` + `APPEND_OR_REPLACE`).
- Başarısız denemede `enrichAttempts` artıyor (uydurma ISBN 9780000000002 → 1. denemeden sonra `enrichAttempts=1`); rozet eşiği 5.
- Detaydaki **"Künye eksik · yeniden dene"** rozeti, eşik geçici olarak 0 yapılan doğrulama derlemesinde görsel olarak doğrulandı (metin + buton); buton `sıraya alındı` durumuna geçiyor ve işi yeniden kuyruğa atıyor.
- **Ek sağlamlaştırma:** Open Library, tanınmayan ISBN'lerde başka kayda yönlendirip yanlış künye döndürebiliyor (ör. 9780000000002 → "Kitab Jurumiyyah"). Artık kaydın ISBN listesi istenen ISBN'i içermiyorsa ya da yalnızca `0000000000` gibi sahte ISBN varsa künye reddediliyor.
- Bilinen sınırlama: bazı gerçek Türkçe ISBN'lerde OL hâlâ yanlış kayıt döndürebiliyor (9789750718533 → "1984"). Google Books birincil kaynak olduğu için Google döndüğünde bu sınıf hatalar azalacak.

---

## Faz 5 — Yedekleme / dışa aktarma (✅ tamamlandı)

**Neden:** Telefon değişince kütüphane kaybolur; ayrıca harici AI chatbot'un toplu içe aktarması için ortak format gerekir.

**Kapsam**
- Şema: `{ "schemaVersion": 1, "exportedAt": ..., "books": [ ... ] }` (kapak dosyaları hariç, `coverPath` yok sayılır).
- `ACTION_CREATE_DOCUMENT` ile `.json` dışa aktarma (paylaşım menüsü değil, dosya seçici).
- İçe aktarma: ISBN varsa atla, yoksa ekle; sonuç raporu ("12 eklendi, 3 atlandı").
- Faz 5b (opsiyonel): kapakları da taşımak için `.zip` paketi.

**Kabul kriterleri**
1. 11 kitap dışa aktar → uygulama verisi temizlenir → içe aktar → 11 kitap, künyeler birebir aynı.
2. Bozuk/eksik JSON uygulamayı çökertmez, hata mesajı verir.
3. Aynı dosya iki kez içe aktarılınca kopya oluşmaz.

**Dosyalar:** yeni `data/LibraryBackup.kt`, `LibraryRoot.kt` (menü), `LibraryViewModel.kt`
**Tahmin:** 2 oturum · **Risk:** orta (dosya izinleri, şema uyumu)

Kanıt (fiziksel telefon M2101K7BG, üç kabul kriteri de geçti):
1. **Geri yükleme:** 12 kitap `ACTION_CREATE_DOCUMENT` ile `ender-kutuphane-yedek-*.json`
   olarak dışa aktarıldı → `pm clear` ile veri temizlendi → içe aktarıldı
   ("3 eklendi, 9 atlandı (zaten var)"; 9'u DEBUG örnek tohumuydu) → veritabanı
   satır satır yedekle karşılaştırıldı: başlık/yazar/yayınevi/tarih/sayfa/açıklama/
   enriched alanlarında **fark yok** (12/12).
2. **Bozuk dosya:** `{bozuk json` → çökme yok, "Yedek okunamadı: Geçerli bir JSON değil".
   `schemaVersion: 99` → "Yedek daha yeni bir sürümden (99); uygulamayı güncelle".
3. **Kopya:** aynı dosya iki kez içe aktarıldı → "0 eklendi, 12 atlandı (zaten var)",
   kitap sayısı 12'de kaldı.
- Menü başlığın üst satırında başlığı kırpıyordu; `⋮` menü ikinci satıra (arama
  ikonunun yanına) taşındı.
- Kapak dosyaları yedeklenmiyor (cihaza özel yol); içe aktarmadan sonra ön indirme
  akışıyla geri geliyor. Faz 5b (`.zip` ile kapak taşıma) opsiyonel olarak açık.

---

## Faz 6 — Haptik ve cila (⏳ iki küçük açık: haptik onayı, raf açılışı ölçümü)

**Neden:** Uygulama işlevsel ama "pahalı" hissettirmiyor.

**Kapsam**
- Barkod okunduğunda kısa titreşim; kitap uçarken hafif geri bildirim.
- Detay açılışında yay efekti, kapakta ince gölge.
- Raf kasası kenarına vinyet, sekme geçişinde çapraz solma.
- Performans hedefi: soğuk açılış ≥ %30 daha hızlı (emülatörde < 6 sn), atlanan kare < %1.

**Kabul kriterleri**
1. Her kritik dokunuşta geri bildirim var, titreme yok.
2. `Choreographer` atlanan kare oranı < %1 (raf açılışı ve uçuş animasyonu).

**Tahmin:** 1 oturum · **Risk:** düşük

Kanıt (fiziksel telefon M2101K7BG):
- **Haptik:** `ui/Haptics.kt` `tickHaptic()` (VibrationEffect.createOneShot, VIBRATE izni);
  barkod okunuşunda 26 ms / rafdan kalkışta 24 ms. (Hissiyat cihaz sahibince onaylanacak.)
- **Yay + gölge:** detay içeriği `spring(0.82, 220)` ile yerine oturur; kapak gölgesi
  `shadowElevation` yerine yumuşak radyal gradient (GPU gölge katmanı animasyonda
  kare kilitliyordu).
- **Vinyet:** piksel karşılaştırmasıyla doğrulandı — köşeler ~%8 koyulaştı
  (ör. sol üst 99→91), merkez birebir aynı (108→108); sırtlar okunaklı kalmaya devam ediyor.
- **Soğuk açılış:** 5 koşu 3.63–3.65 sn (`am start -W`, debug APK). İlk kareyle yarışmasın
  diye `enqueueEnrich()` + `prefetchCovers()` 1.5 sn ertelendi.
- **Uçuş/detay:** ekran kaydı kare kare incelendi; sırt → kapak dönüşümü akıcı,
  detay krem zeminde açılıyor.
- **Sekme çapraz solması (yeniden yapılandırıldı):** önceki "sol → değiş → gel" yapısında
  raf dispose edildiği için Raf'a dönüşte 180 sırt ~1.1 sn yeniden kuruluyor, ekran boş
  kalıyordu. Artık raf katmanı **hiç dispose edilmez** (yalnızca `1 − p` alfa ile solar),
  `BookListTab` yalnız `tabProgress > 0` iken ayakta durur (`p` alfa ile, üstte);
  iki katmanın alfası toplamı her karede 1 olduğu için boş kare matematiksel olarak yok.
  Dokunma da kilidi: yalnız tam görünür katman dokunmayı alır (alfa 0 rafın görünmez
  sırtları liste üstünde tıklanamasın).
  **Boş kare taraması (emülatör EnderTest):** iki yönde de geçiş ortası kareler seri
  `screencap` ile yakalandı (t1–t4, r1–r4) — hiçbir karede boş/eksik ekran yok;
  karşı kareler piksel düzeyinde aynı (çapraz solma ya çok hızlı ya da her kare dolu).
- **Uçuş ölçümü (fiziksel telefon):** `dumpsys gfxinfo framestats` ile 5 tur uçuş =
  120 çizilen kare, 0 atlanan → **%0.00 GECTİ** (atlanan kare = `(Vsync − IntendedVsync)
  // FrameInterval`). Emülatörde de 120/0 tekrarlandı.
- **Spine çizim yeniden yapılandırması:** `BookSpine` ~10 Compose düğümünden **1 düğüme**
  indirildi — tüm dekor (cilt yüzeyi gradyanı, yan bombeler, altın şeritler, 5 dekor
  varyantı, döndürülmüş sırt yazısı, gerçek kitap altın şerit/nokta) tek
  `drawWithCache → onDrawBehind` geçişi içinde çiziliyor. `ShelfRowView`'daki
  `BoxWithConstraints` (SubcomposeLayout — ölçüm fazında kompozisyon çalıştırıyordu)
  kaldırıldı; yerine `heightFactor` ile `fillMaxHeight`. Sırt yazısı ölçümü süreç-omrulu
  `LruCache<String, TextLayoutResult>(256)` içinde (`spineLabelLayout`) tutuluyor —
  dönüş/soğuk açılışta metin yeniden ölçülmüyor. Görsel parite ekran görüntüsüyle
  doğrulandı: 180 cilt, altın şeritler, etiket varyantları, dikey yazılar, eğik ciltler,
  vinyet birebir aynı.
- **Kök neden analizi (performans bloğu):** raf açılışında/dönüşte görülen ~1.4 sn
  "ölçüm/yerleşim" bloğunun **Compose yerleşimi olmadığı** kanıtlandı:
  (a) framestats'te tampon bekleme yok (dequeue/queue 0–1 ms) → iş parçacığı işi;
  (b) spine düğümlerini ~10× azaltmak bloğu değiştirmedi (1574→1482 ms);
  (c) spine'lar tamamen kaldırılınca bile soğuk açılış ilk karesi 5007 ms ölçü/yerleşim;
  (d) `/proc/<pid>/task/<tid>/stat` ile ana iş parçacığı dönüşte ~0.98 sn gerçek CPU;
  (e) simpleperf sembolleri: `art::HInstructionBuilder::ResolveClass`,
  `art::GVNOptimization::Run`, `art::RegisterAllocation*` → **DEBUG APK'da ART sınıf
  yükleme/JIT + ilk kompozisyon** maliyeti (AOT yok). Yarı spine'lara bağlı
  (1169→611 ms), yarı tek seferlik başlatma. Release (R8/AOT) derlemede bu maliyetin
  büyük bölümü kaybolur; telefon rakamları (soğuk açılış 3.6 sn) zaten hedefte.

Açık:
1. 2. kabul kriterinin **raf açılışı** ayağının fiziksel telefonda framestats ölçümü
   (uçuş ayağı telefonda %0 ile geçti). Emülatör (EnderTest) ölçümleri yalnız göreli
   sinyal için geçerli: 3 AVD paylaşımı + yazılım GPU mutlak rakamları şişiriyor
   (ör. soğuk açılış 15 sn); ayrıca API 35 + BLAST'te framestats satırları boş geliyor,
   özet sayaçlar + `Choreographer` ile ölçülüyor.
2. Haptik hissiyatının cihaz sahibince onayı (1. kabul kriteri).

---

## Faz 7 — Ayarlar ve kaynak sağlığı (✅ cihazda doğrulandı)

**Neden:** Google Books şu an arızalı; kullanıcı hangi kaynağın kullanıldığını görebilmeli.

**Kapsam**
- Yeni sekme: **Ayarlar** (Raf / Kitaplarım / Ayarlar).
- Kaynak durumu: "Google Books: devre dışı (10 dk)" / "Open Library: aktif", son hata kodu.
- "Önbelleği temizle", "Yedek al" (Faz 5'e bağlanır), sürüm bilgisi.
- API anahtarı `BuildConfig.GBOOKS_KEY` boşsa anahtar satırı gizlenir.

**Kabul kriterleri**
1. Google 503 verirken ekran "Open Library kullanılıyor" gösterir.
2. Devre kesici süresi dolunca durum otomatik "aktif" olur.

**Tahmin:** 1 oturum · **Risk:** düşük

Kanıt (kod tamamlandı, derleme temiz):
- `BookMetadataSource`: `health()` ile `googleDownUntil` + `googleLastError` dışarı açıldı;
  hata mesajı (`HTTP 503 ...`) kaydediliyor, Google başarılı sorguda temizleniyor.
- `SettingsTab.kt`: KAYNAKLAR (Google Books / Open Library / API anahtarı satırı yalnız
  anahtar tanımlıysa), BAKIM (önbelleği temizle, yedek al, yedekten yükle), UYGULAMA (sürüm).
- Durum etiketi 15 sn'de bir yeniden okunur → devre kesici dolunca kendiliğinden
  "aktif"e döner (2. kabul kriteri).
- Önbellek temizliği: `covers/` dosyaları silinir + `coverPath`'ler NULL'lanır;
  yenileri kapak ön indirmesiyle geri gelir.
- Üçüncü sekme, çapraz solma yapısına eklendi: her sekmenin kendi alfa'sı var,
  raf katmanı yine hiç dispose edilmez; yalnız tam görünür katman dokunmayı alır.

Kanıt (fiziksel telefon M2101K7BG — iki kabul kriteri de doğrulandı):
- **Kriter 1:** Google Books `HTTP 503 backendFailed` dönerken Ayarlar ekranı
  "Open Library kullanılıyor" gösterdi; elle ISBN sorgusu Open Library yedeğiyle
  tamamlandı (ör. `The Count of Monte Cristo`, "Kaynak: Open Library").
- **Kriter 2:** devre kesici devreye girince durum "devre dışı · 9 dk" olarak göründü;
  10 dk dolduğunda durum etiketi kendiliğinden **"aktif"**e döndü
  (`BookMetadataSource.SourceHealth`, 15 sn'lik yeniden okuma).

---

## Sıralama gerekçesi

1. **Silme** — veri hijyeni, en düşük risk, hemen fayda.
2. **Arama** — 100+ kitapta kullanılabilirlik eşiği.
3. **Kapak önbelleği** — animasyonun "sihir" hissini tamamlar.
4. **WorkManager** — çevrimdışı öncelikli sözünü gerçekten yerine getirir.
5. **Yedekleme** — veri kaybı riskini kapatır, chatbot entegrasyonuna zemin.
6. **Haptik/cila** — son katman, işlevsel riski yok.
7. **Ayarlar** — şeffaflık; Google Books düzelirse ilk buradan görülür.

## Tanım: "Bitti"

Bir faz şu koşullarda kapanır:
- `:app:assembleDebug` başarılı, APK emülatörde kurulup elle test edildi.
- Kabul kriterlerinin her biri ekran görüntüsü veya `adb` çıktısıyla kanıtlandı.
- `README.md` ilgili bölümü güncellendi, tek commit olarak `main`'e push edildi.
- Birim testi gerekiyorsa yazıldı (not: `W:\Sanal Kitaplık` içindeki `ı` karakteri
  `testDebugUnitTest`'i bozuyor; testler ASCII yollu CI'da çalıştırılır).

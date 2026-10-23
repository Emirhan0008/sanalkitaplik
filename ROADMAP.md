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

Kanıt: `9789750718533` → *Tutunamayanlar / Oğuz Atay / İletişim 1971 / 724 s.*
(Google 0 sonuç döndürdü, Open Library yedeği künyeyi getirdi.)

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

## Faz 4 — WorkManager ile çevrimdışı kuyruk

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

---

## Faz 5 — Yedekleme / dışa aktarma

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

---

## Faz 6 — Haptik ve cila

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

---

## Faz 7 — Ayarlar ve kaynak sağlığı

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

# Ender Kütüphane

Android (Kotlin + Jetpack Compose) kişisel kütüphane uygulaması.

Ana ekranda **tek bakışta görünen ahşap kitaplık** (180 sırt, kaydırma yok), barkod taraması ve
"kitap rafta varsa sırtından çıkıp tam ekran kapağa dönüşen" animasyon.

## Ekranlar

| Sekme | İçerik |
|---|---|
| **Raf** | Ahşap kasalı kitaplık; 9 raf, ~180 sırt. Gerçek kitaplar altın bant + nokta ile işaretli ve dokunulabilir. |
| **Kitaplarım** | Kütüphanedeki gerçek kitapların listesi (kapak + künye). |

Başlıktaki **Tara** düğmesi barkod tarayıcısını açar (CameraX + ML Kit, çevrimdışı).

## Rafta arama

Başlığın altındaki küçük **arama** ikonu rafın üstünde ince bir arama çubuğu açar; raf boyutu ve
sırt genişlikleri değişmez (kaydırma gelmez). Yazarken:

- Türkçe/aksan duyarsız eşleşme (`foldTr`: `kurk` = `Kürk`, `ince` = `İnce`), başlıkta eşleşen
  kitaplar önce sıralanır.
- Eşleşen sırtlar **altın çerçeve + hafif büyüme**, eşleşmeyenler karartılır.
- Enter veya sırta dokunma → mevcut uçuş animasyonuyla detay açılır.
- Sonuç yoksa "Kütüphanende bulunamadı" snackbar'ı **"ISBN ile ekle"** aksiyonuyla elle ISBN
  diyaloğunu açar.
- Geri tuşu aramayı kapatır ve rafı eski haline döndürür.

## Kitap silme

Detay ekranında **"Kitaplıktan çıkar"** (ikincil, dolgusuz buton) onay diyaloğu açar; onaydan
sonra kitap raftan ve "Kitaplarım" listesinden düşer, kapak dosyası silinir. Snackbar'daki
**"Geri al"** aynı ISBN'i geri yükler ve sırt eski yerine döner. Silme sırasında uçuş animasyonu
tetiklenmez.

## Akış

1. Barkod okunur → EAN-13/ISBN-10 doğrulanır ve ISBN-13'e normalize edilir.
2. Kamera çalışmazsa/okuyamazsa: tarayıcıdaki **Elle ISBN gir** düğmesiyle numara yazılır
   (canlı doğrulama: geçerli/geçersiz ISBN geri bildirimi).
3. Kitap Room veritabanında varsa: raf sekmesine dönülür, sırtın ekran koordinatı alınır,
   `Animatable` ile sırt dışarı çıkar → kendi ekseninde döner → tam ekran kapağa dönüşür.
4. Kitap yoksa künye zinciri çalışır: **önce Google Books API** (`q=isbn:` ISBN-13 ve ISBN-10
   biçimleriyle denenir); arama backend'i yanıt vermezse **Google Kitaplar web köprüsü**
   (ISBN → hacim ID'si → resmi `volumes.get`) devreye girer. Sonuç yoksa **Open Library**
   4 adımlı zincirle sorgulanır (`/isbn/` → `/isbn/{isbn10}` → `api/books?bibkeys` →
   `search.json?isbn=`). Bulunan künye kapak önizlemesi + kaynak etiketiyle gösterilir;
   "Rafa ekle" ile kayıt anında oluşur. Hiçbiri bulunamazsa yine de ISBN ile eklenir,
   bilgiler arka planda tamamlanır (çevrimdışı çalışır).
5. **Baskı eşleştirme:** Aynı kitabın farklı baskıları farklı ISBN taşır. ISBN rafta yoksa
   `WorkMatcher` başlık + yazar token'larını karşılaştırır; yetmezse Google Books / Open Library
   aramasından aynı eserin diğer ISBN'leri toplanıp yerel kayıtlarla eşlenir. Eşleşme varsa alt
   sayfada "Aynı eserin başka bir baskısı kütüphanende" kartı çıkar; **Mevcut baskıyı aç** ile o
   kitabın sırtı uçar, istenirse "Bu baskıyı yine de ekle" ile ikinci nüsha eklenir.

## Sırt yazıları

Her sırtın üzerinde 90° döndürülmüş, çok küçük (4–6 sp) serif yazı vardır: başlık + yazar.
Yazı rengi sırtın kendi renginden hesaplanır (göreli parlaklık): koyu ciltlerde krem, açık/okra
ciltlerde koyu kahve. Böylece tüm sırtlar aynı beyazla boğulmaz, her cilt kendi kontrastını alır.
Gerçek kitaplarda yazı tam opaklıkta okunur; dekoratif ciltlerde aynı kontrastın daha soluk hâli
kullanılır (180 sırt tek ekranda olduğu için metin sırt genişliğine sığdırılır, taşan kısım üç
noktayla kesilir).


## Kapaklar

Kapaklar `files/covers/<isbn>.jpg` olarak cihazda tutulur (çevrimdışı çalışır). Uygulama açılışında:

- `coverPath` dolu ama dosyası silinmiş kayıtlar onarılır (yol temizlenir).
- Kapaksız kitaplar için **sessiz ön indirme** yapılır: en fazla 10 kitap, aynı anda 3 istek.
  Önce Open Library'nin doğrudan ISBN kapağı, olmazsa Google Books thumbnail'i denenir; künye
  sorgusu yapılmadığı için API kotası harcanmaz.
- İndirme başarısız olursa (kaynakta kapak yok) ISBN'den üretilmiş cilt kapağı gösterilir ve
  sonraki açılışta yeniden denenir.
- Coil 100 MB disk + %15 bellek önbelleğiyle yapılandırılmıştır; uçuş animasyonu ve detay ekranı
  aynı çözümlenmiş görseli kullanır.

## Künye tamamlama (WorkManager)

Çevrimdışı eklenen kitaplar `enriched=false` olarak kaydedilir ve **WorkManager** kuyruğuna girer:

- Yalnızca ağ bağlantısı varken çalışır (şarj/Wi-Fi zorunlu değil), exponential backoff 30 sn → 1 saat,
  en fazla 5 deneme.
- Tek kuyruk (`enqueueUniqueWork` + `APPEND_OR_REPLACE`): aynı iş için ikinci worker oluşmaz, uygulama
  kapalıyken de tamamlanır.
- 5 başarısız denemeden sonra detay ekranında **"Künye eksik · yeniden dene"** rozeti çıkar.
- Open Library kaydı istenen ISBN'i listelemiyorsa (ya da yalnızca `0000000000` gibi sahte ISBN
  taşıyorsa) künye reddedilir; böylece rastgele/yanlış başlık kütüphaneye yazılmaz.
- Bulunamama mesajı iki durumu ayırır: **"kayıt yok"** (kaynaklar yanıt verdi, künye yok) ile
  **"kaynaklara ulaşılamıyor"** (ağ/503; tekrar denenebilir). Google Books arama backend'i
  geçici 503 (`backendFailed`) verdiğinde hacim-ID köprüsü devreye girer.

## Yedekleme / dışa aktarma

Başlıktaki **⋮** menüsü iki iş sunar (dosya seçici ile, paylaşım menüsü değil):

- **Yedek al (.json):** kütüphane `ACTION_CREATE_DOCUMENT` ile `ender-kutuphane-yedek-<tarih>.json`
  olarak kaydedilir. Şema: `{ "schemaVersion": 1, "exportedAt": ..., "app": ..., "books": [...] }`.
  Kapak dosyaları yedeklenmez (cihaza özel yol); içe aktarmadan sonra ön indirme akışıyla geri gelir.
- **Yedekten yükle:** `ACTION_OPEN_DOCUMENT` ile JSON seçilir. ISBN'i zaten var olan kitaplar
  atlanır, yenileri eklenir; sonuç snackbar'da raporlanır ("3 eklendi, 9 atlandı (zaten var)").

Kurallar:

- Bozuk/eksik JSON uygulamayı çökertmez: "Yedek okunamadı: ..." mesajı gösterilir.
- `schemaVersion` ileri sürümse (ör. 99) açıkça reddedilir: "Yedek daha yeni bir sürümden...;
  uygulamayı güncelle".
- Geçersiz ISBN'li kayıtlar atlanır; aynı ISBN dosya içinde birden fazlaysa ilki tutulur.
- İçe aktarılan künyesi eksik kitaplar WorkManager kuyruğuna girer.

## Cila: haptik ve görsel dokunuşlar

- **Haptik:** barkod okununca 26 ms, kitap rafdan kalkarken 24 ms "tik" (`ui/Haptics.kt`,
  `VIBRATE` izni). Kuvvetli/uzun titreme kullanılmaz.
- **Detay açılışı:** içerik alfa ile belirirken yumuşak yay (`spring`) ile yerine oturur;
  kapak gölgesi GPU gölge katmanı yerine radyal gradient ile çizilir (animasyonda kare kilidini önler).
- **Vinyet:** raf kasasının kenarları hafifçe kararır, merkez ve sırt yazıları olduğu gibi kalır.
- **Sekme geçişi:** Raf ↔ Kitaplarım arasında çapraz solma (~220 ms). Raf katmanı hiç
  dispose edilmez (180 sırt yeniden kurulmaz); yalnız tam görünür katman dokunmayı alır.

## Ayarlar sekmesi

Alt bardaki üçüncü sekme kaynak sağlığını ve bakım işlerini gösterir:

- **Google Books** — "aktif" veya "devre dışı · X dk" (10 dk devre kesici); alt satırda
  son hata (ör. `HTTP 503 ...`). **Open Library** satırı Google kapalıyken
  "kullanılıyor" olarak güncellenir. Devre kesici süresi dolunca durum kendiliğinden
  "aktif"e döner (15 sn'de bir yenilenir).
- **Google Books API anahtarı** — yalnız `BuildConfig.GBOOKS_KEY` tanımlıysa gösterilir.
- **Önbelleği temizle** — kapak görsellerini siler (`covers/` + `coverPath`'ler);
  yenileri kapak ön indirmesiyle zamanla geri gelir.
- **Yedek al / Yedekten yükle** — Faz 5'teki yedekleme akışına bağlanır.
- **Sürüm** — `BuildConfig.VERSION_NAME (VERSION_CODE)`.

## Katmanlar

```
data/
  BookEntity, BookDao, AppDatabase      Room (isbn = PK)
  Isbn.kt                               EAN-13 / ISBN-10 doğrulama + 13'e çevirme
  BookMetadataSource.kt                 Google Books → Open Library + diğer baskı ISBN'leri
  WorkMatcher.kt                        ISBN'den bağımsız eser/baskı eşleştirme
  LibraryRepository.kt                  çevrimdışı öncelikli; enrich + kapak ön indirme tekilleştirilmiş
  EnrichWorker.kt                       WorkManager kuyruğu (ağ kısıtı, backoff, tek iş)
  LibraryBackup.kt                      JSON dışa/içe aktarma (schemaVersion 1) + içe aktarma raporu
ui/
  LibraryRoot.kt                        sekmeler, faz makinesi (Idle/Flying/Detail/Closing)
  shelf/ShelfModels.kt                  sırt üretimi + satırlara tam oturan yerleşim
  shelf/ShelfSearch.kt                  Türkçe duyarsız arama normalizasyonu + sıralama
  shelf/Bookshelf.kt                    ahşap kasa, raflar, sırt çizimi
  FlightLayer.kt                        sırt → kapak animasyonu
  BookDetailLayer.kt                    tam ekran detay
  BookCover.kt                          kapak (gerçek görsel yoksa üretilmiş kapak)
  ScannerOverlay.kt                     kamera + barkod
  Haptics.kt                            kısa haptik "tik" yardımcısı
  SettingsTab.kt                        Ayarlar sekmesi (kaynak sağlığı, bakım, sürüm)
```

## Çalıştırma

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

`local.properties` içinde `sdk.dir` gerekir. Proje yolu `W:\Sanal Kitaplık` (Türkçe karakter + boşluk)
olduğu için `gradle.properties` içinde `android.overridePathCheck=true` açık.

## Google Books API anahtarı

Anahtar zorunlu değil; uygulama anahtarsız da Google Books'u sorgular (daha düşük günlük kota,
429 alındığında otomatik Open Library'ye düşer). Anahtar kullanmak için:

1. Google Cloud projesinde `books.googleapis.com` servisini etkinleştir.
2. Yalnızca Books API'ye kısıtlı bir API anahtarı oluştur
   (`gcloud services api-keys create --api-target=service=books.googleapis.com`).
3. Anahtarı **`local.properties`** içine yaz: `gbooks.key=...`
   (dosya `.gitignore`'da, anahtar repoya girmez).
4. `app/build.gradle.kts` anahtarı `BuildConfig.GBOOKS_KEY` olarak derler; `BookMetadataSource`
   istek URL'lerine ekler.

Google Books 429/5xx dönerse istemci 10 dakika devre dışı kalır (devre kesici) ve doğrudan
Open Library'ye geçer; geçici Google kesintileri taramayı yavaşlatmaz.

## Notlar

- Yol haritası ve sıradaki fazlar: [ROADMAP.md](ROADMAP.md)
- Debug derlemede veritabanı boşsa 9 örnek kitap eklenir (gerçek barkodlarla test için).
- Raf görselindeki 180 sırtın geri kalanı dekoratiftir; gerçek kitaplar `isReal` ile işaretlenir.
- Her sırt tek Compose düğümü + tek çizim geçişiyle çizilir (dekor, altın şeritler ve sırt
  yazısı `drawWithCache` içinde); sırt yazısı ölçümleri süreç önbelleğinde tutulur. Böylece
  180 ciltli raf tek geçişte çizilir, dönüş/soğuk açılışta metin yeniden ölçülmüyor.
- Sıralı toplu içe aktarma (chatbot/JSON) henüz yok; `LibraryRepository.addIsbn` üzerinden eklenebilir.

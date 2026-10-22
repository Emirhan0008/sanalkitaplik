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

## Akış

1. Barkod okunur → EAN-13/ISBN-10 doğrulanır ve ISBN-13'e normalize edilir.
2. Kamera çalışmazsa/okuyamazsa: tarayıcıdaki **Elle ISBN gir** düğmesiyle numara yazılır
   (canlı doğrulama: geçerli/geçersiz ISBN geri bildirimi).
3. Kitap Room veritabanında varsa: raf sekmesine dönülür, sırtın ekran koordinatı alınır,
   `Animatable` ile sırt dışarı çıkar → kendi ekseninde döner → tam ekran kapağa dönüşür.
4. Kitap yoksa: **önce Google Books API**, `totalItems: 0` ya da eksik künye dönerse
   **Open Library** sorgulanır. Bulunan künye kapak önizlemesi + kaynak etiketiyle gösterilir;
   "Rafa ekle" ile kayıt anında oluşur. Hiçbiri bulunamazsa yine de ISBN ile eklenir,
   bilgiler arka planda tamamlanır (çevrimdışı çalışır).
5. **Baskı eşleştirme:** Aynı kitabın farklı baskıları farklı ISBN taşır. ISBN rafta yoksa
   `WorkMatcher` başlık + yazar token'larını karşılaştırır; yetmezse Google Books / Open Library
   aramasından aynı eserin diğer ISBN'leri toplanıp yerel kayıtlarla eşlenir. Eşleşme varsa alt
   sayfada "Aynı eserin başka bir baskısı kütüphanende" kartı çıkar; **Mevcut baskıyı aç** ile o
   kitabın sırtı uçar, istenirse "Bu baskıyı yine de ekle" ile ikinci nüsha eklenir.

## Sırt yazıları

Her sırtın üzerinde 90° döndürülmüş, çok küçük (4–6 sp) serif yazı vardır: başlık + yazar.
Gerçek kitaplarda altın rengi ve okunur; dekoratif ciltlerde düşük alfalı, doku etkisi veren
soluk bir yazı olarak kalır (180 sırt tek ekranda olduğu için metin sırt genişliğine sığdırılır,
taşan kısım üç noktayla kesilir).


## Katmanlar

```
data/
  BookEntity, BookDao, AppDatabase      Room (isbn = PK)
  Isbn.kt                               EAN-13 / ISBN-10 doğrulama + 13'e çevirme
  BookMetadataSource.kt                 Google Books → Open Library + diğer baskı ISBN'leri
  WorkMatcher.kt                        ISBN'den bağımsız eser/baskı eşleştirme
  LibraryRepository.kt                  çevrimdışı öncelikli; enrich tekilleştirilmiş
ui/
  LibraryRoot.kt                        sekmeler, faz makinesi (Idle/Flying/Detail/Closing)
  shelf/ShelfModels.kt                  sırt üretimi + satırlara tam oturan yerleşim
  shelf/Bookshelf.kt                    ahşap kasa, raflar, sırt çizimi
  FlightLayer.kt                        sırt → kapak animasyonu
  BookDetailLayer.kt                    tam ekran detay
  BookCover.kt                          kapak (gerçek görsel yoksa üretilmiş kapak)
  ScannerOverlay.kt                     kamera + barkod
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

- Debug derlemede veritabanı boşsa 9 örnek kitap eklenir (gerçek barkodlarla test için).
- Raf görselindeki 180 sırtın geri kalanı dekoratiftir; gerçek kitaplar `isReal` ile işaretlenir.
- Sıralı toplu içe aktarma (chatbot/JSON) henüz yok; `LibraryRepository.addIsbn` üzerinden eklenebilir.

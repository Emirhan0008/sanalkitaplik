# Ender Kütüphane

Android (Kotlin + Jetpack Compose) kişisel kütüphane uygulaması.

Ana ekranda **tek bakışta görünen ahşap kitaplık** (180 sırt, kaydırma yok), barkod taraması ve
"kitap rafta varsa sırtından çıkıp tam ekran kapağa dönüşen" animasyon.

## Ekranlar

| Sekme | İçerik |
|---|---|
| **Raf** | Ahşap kasalı kitaplık; 9 raf, ~180 sırt. Gerçek kitaplar altın bant + nokta ile işaretli ve dokunulabilir. |
| **Kitaplarım** | Kütüphanedeki gerçek kitapların listesi (kapak + künye). |

Sağ alttaki **Tara** düğmesi barkod tarayıcısını açar (CameraX + ML Kit, çevrimdışı).

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


## Katmanlar

```
data/
  BookEntity, BookDao, AppDatabase      Room (isbn = PK)
  Isbn.kt                               EAN-13 / ISBN-10 doğrulama + 13'e çevirme
  BookMetadataSource.kt                 Google Books → Open Library (yalnızca ekleme anında)
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

## Notlar

- Debug derlemede veritabanı boşsa 9 örnek kitap eklenir (gerçek barkodlarla test için).
- Raf görselindeki 180 sırtın geri kalanı dekoratiftir; gerçek kitaplar `isReal` ile işaretlenir.
- Sıralı toplu içe aktarma (chatbot/JSON) henüz yok; `LibraryRepository.addIsbn` üzerinden eklenebilir.

# Ender Kütüphane — Telefon Kurulumu (Play Dahili Test)

Uygulama Google Play'de **dahili test** olarak yayında. APK dosyası göndermene
gerek yok — arkadaşın linkle katılıyor, sonrası Play'den otomatik.

## Arkadaşın yapması gerekenler (tek seferlik)

1. Telefonundan bu bağlantıyı aç:
   **https://play.google.com/apps/internaltest/4699951502213476742**
2. "Dahili testi kabul et"e dokun → "Uygulamayı test et" / Play'de aç.
3. "Ender Kütüphane" sayfasında **Kaydol** → **Yükle**. Bitti.

> Gmail adresinin Play Console'daki tester listesinde ("Testerlar") kayıtlı
> olması gerekir. Liste: Play Console → Ender Kütüphane → Test edin ve
> yayınlayın → Test etme → Dahili test → Test kullanıcıları.

## Bundan sonrası (canlı güncelleme)

- Yeni sürüm çıktığında sen Play Console'a yüklüyorsun; arkadaşının telefonu
  Play'in otomatik güncellemesiyle **kendi kendine** günceller.
- Kitaplar telefonunda durur, güncellemede silinmez.

## Sürüm çıkarma tarifesi (geliştirici notu)

1. `app/build.gradle.kts` içinde `versionCode` +1, `versionName` artır.
2. `.\gradlew.bat :app:bundleRelease`
3. `gplay release --package com.ender.kitaplik --track internal --bundle <aab> --release-notes "..."`
   (tam yol: yeni edit → yükle → track → commit)

## Notlar

- API anahtarı APK'nın içinde gömülü — hiçbir şey girmesine gerek yok.
- Uygulama çevrimdışı çalışır; internet yalnızca künye çekmek için gerekir.

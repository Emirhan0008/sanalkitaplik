# ISBN Araştırma Raporu — 9786254085086 (Dokuzuncu Hariciye Koğuşu)

**Tarih:** 2026-10-25
**Uygulama:** Ender Kütüphane (`com.ender.kitaplik`) — `W:\Sanal Kitaplık`
**İncelenen kod:** `app/src/main/java/com/ender/kitaplik/data/BookMetadataSource.kt` (künye zinciri: Google Books API → Open Library)
**Sorun:** ISBN `9786254085086` (Ötüken Neşriyat / Peyami Safa / Dokuzuncu Hariciye Koğuşu) uygulamada "bulunamadı" veriyor.

---

## (a) Bulgu Kanıtları

Aşağıdaki tüm sorgular araştırma sırasında **gerçekten çalıştırılmış** ve yanıtları kaydedilmiştir.

### 1. Google Books API (birincil kaynak — ağ/kota engelli)

| Sorgu | Sonuç |
|---|---|
| `https://www.googleapis.com/books/v1/volumes?q=isbn:9786254085086` (+`country=TR`) | **HTTP 429** — `Quota exceeded for quota metric 'Queries' and limit 'Queries per day' of service 'books.googleapis.com' for consumer 'project_number:624717413613'` (`RESOURCE_EXHAUSTED` / `RATE_LIMIT_EXCEEDED`, `quota_limit: defaultPerDayPerProject`) |
| `...?q=intitle:Dokuzuncu Hariciye Kogusu inauthor:Peyami Safa&country=TR` | **HTTP 429** — aynı kota hatası |
| `...?q=isbn:6254085089&country=TR` (ISBN-10) | **HTTP 429** — aynı kota hatası |

> ⚠️ **Ağ/kota engeli (kitabın Google'da olmaması DEĞİL).** Bu anahtar/proje için günlük kota dolu. Cihazda bu hata muhtemelen `503 backendFailed` / HTTP 5xx olarak görünüyor; kod `markGoogleDown()` ile Google'ı 10 dk devre dışı bırakıp Open Library'ye düşüyor.

### 2. Google Kitaplar WEB — kitap Google'da MEVCUT (kota engeli kanıtı)

- `https://books.google.com/books?vid=ISBN9786254085086` → **HTTP 200**, yönlendirme:
  `https://books.google.com.tr/books?vid=ISBN9786254085086&redir_esc=y`
- Sayfa başlığı: **"Dokuzuncu Hariciye Kogusu - Peyami Safa - Google Kitaplar"**
- Kaynakça bilgileri (sayfadan birebir): **Başlık:** Dokuzuncu Hariciye Kogusu · **Yazar:** Peyami Safa ·
  **Yayın tarihi:** 2023 · **ISBN:** `6254085089`, `9786254085086` · **Uzunluk:** 112 sayfa
- Google Kitaplar hacim kimliği: **`s4FY0AEACAAJ`**
- `?vid=ISBN6254085089` (ISBN-10) de aynı kayda gidiyor.

**Sonuç:** Google Books veritabanında kayıt var; başarısızlığın tek sebebi API kotasıdır.

### 3. Open Library (yedek kaynak — kayıt YOK)

| Sorgu | Sonuç |
|---|---|
| `https://openlibrary.org/api/books?bibkeys=ISBN:9786254085086&format=json&jscmd=data` | HTTP 200, gövde: `{}` (boş) |
| `https://openlibrary.org/search.json?q=9786254085086` | HTTP 200, `numFound: 0` |
| `https://openlibrary.org/search.json?isbn=6254085089` | HTTP 200, `numFound: 0` |
| `https://openlibrary.org/isbn/9786254085086.json` ← **uygulamanın çağırdığı uç nokta** | **HTTP 404** (yönlendirmesiz ve yönlendirmeli denemede de 404) |
| `https://openlibrary.org/isbn/6254085089.json` (ISBN-10) | **HTTP 404** |
| `https://covers.openlibrary.org/b/isbn/9786254085086-L.jpg` | HTTP 200 ama `image/gif`, 43 bayt = **yer tutucu** (kapak yok) |

### 4. Open Library'de eser VAR — ama eski ISBN'lerle kayıtlı

`https://openlibrary.org/search.json?q=Dokuzuncu+Hariciye+Koğuşu+Peyami+Safa` → `numFound: 7`.
Öne çıkanlar:

| Work | Eser | Yayınevi / Yıl | ISBN (OL kaydı) |
|---|---|---|---|
| `OL25295645W` | Dokuzuncu Hariciye Koğuşu | **Ötüken Neşriyat, 2021-09**, 112 s. (`OL33876164M`) | **978-975-437-048-5** |
| `OL25295645W` | Dokuzuncu hariciye koğuşu | Alkım, 2005, 128 s. (`OL45277413M`) | 975-636-382-7 / 978-975-636-382-9 |
| `OL26223878W` | Dokuzuncu Hariciye Kogusu | Beta, 2016 | 978-605-333-481-1 |
| `OL1598313W` / `OL1598314W` | 9-uncu Hariciye Koğuşu | Ötüken, 1976 / 1974 | — |
| `OL38549218W` / `OL38549220W` / `OL43532059W` | 1949 / 1953 / 1937 baskıları | — | — |

> **Tespit:** Aynı eserin Ötüken baskısı Open Library'de **978-975-437-048-5** (eski 975 Türkiye öneki) ile kayıtlı.
> 2023+ baskılar ise **978-625-408-508-6** (yeni 625 Türkiye öneki) ISBN'ini kullanıyor ve OL'ye hiç işlenmemiş.
> Yani "ISBN yayınevi kataloğunda farklı / eski ISBN'le kayıtlı" durumu söz konusu — eski ISBN'le aramada OL kaydı bulunuyor, yenisiyle bulunmuyor.

### 5. ISBN doğrulaması (checksum)

- **ISBN-13 `9786254085086`:** ağırlıklı toplam = 124 → check digit = (10 − 4) mod 10 = **6** ✓ **geçerli**.
- **ISBN-10 karşılığı:** 978 öneki atılır → `625408508` → check digit = **9** → **`6254085089`**.
- Doğrulama: Amazon ürün sayfası `https://www.amazon.com/-/es/Peyami-Safa/dp/6254085089` ("Dokuzuncu Hariciye Koğuşu: 9786254085086") ve Google Kitaplar künyesi (`ISBN 6254085089, 9786254085086`) hesaplanan değerle **birebir uyuşuyor**.
- **978-625 öneki Türkiye'ye aittir** (ISBN Türkiye Ajansı / KYGM). Eski Türkiye öneki 975 idi.

### 6. Türkiye perakendecileri — ISBN geçerli, kitap satılıyor

Web araması ve doğrudan sayfa çekimi (2026-10-25):

| Kaynak | Kanıt |
|---|---|
| KitapSeç | `Products/Dokuzuncu-Hariciye-Kogusu-Peyami-Safa-Otuken-Yayinlari-78074.html` — HTTP 200, **schema.org `Product` JSON-LD** (`sku: 9786254085086`, `brand: Ötüken Yayınları`, kapak görseli). Alanlar: ISBN/BARKOD 9786254085086, Yazar Peyami Safa, **Sayfa 112**, **Ebat 12x20** |
| Bora Dağıtım (Ötüken'in dağıtıcısı) | `boradagitim.com/Istem/Urun/Detay.aspx?Id=108172` — Barkod: 9786254085086, Yayınevi: Ötüken Yayınları |
| Efsus Yayınları | Basım Tarihi **2024-12**, Baskı **126**, Sayfa 112 |
| Polat Kitapçılık | Basım Tarihi **2023-02**, Baskı **122**, Sayfa 112 |
| Trendyol | `otuken-nesriyat/dokuzuncu-hariciye-kogusu-peyami-safa-9786254085086-p-729508837` |
| Kitapyurdu | ISBN ile arama sonuç vermiyor (`filter_name` ISBN'yi indekslemiyor); **başlıkla arama** ürün sayfası buluyor: `kitapyurdu.com/kitap/dokuzuncu-hariciye-kogusu/641158.html` ve `/14305.html` |
| Amazon | `dp/6254085089` (ISBN-10) — ISBN-10 eşleşmesi doğrulandı |
| Taksim Kitabevi, ÇiçekSepeti | Ürün sayfaları ISBN'i başlıkta taşıyor |

### 7. Diğer API/katalog sorguları (kanıt)

| Kaynak | Sorgu | Sonuç |
|---|---|---|
| **Wikidata** | SPARQL `?item wdt:P212 "978-625-408-508-6"` | **Sonuç yok** (`bindings: []`) |
| **Wikidata** | `wbsearchentities` "Dokuzuncu Hariciye Kosusu" / "9786254085086" | **Sonuç yok** (Peyami Safa = `Q352882` mevcut) |
| **WorldCat (web)** | `worldcat.org/isbn/9786254085086` | 308 redirect; `search.worldcat.org/search?q=bn:...` **JavaScript gerektiriyor** (API değil) |
| **OCLC Classify API** | `classify.oclc.org/classify2/Classify?isbn=...` | **HTTP 404 — servis kapatılmış** |
| **OCLC SRU** | `worldcat.org/webservices/catalog/search/sru?query=srw.bn=...` | HTTP 520 — **wskey (abonelik anahtarı) gerekiyor** |
| **ISBNdb** | `isbndb.com/books/9786254085086`, `/apidocs/v2` | **HTTP 403** (bot koruması); API **ücretli** (Basic $14.99/ay, 1 çağrı/sn; ücretsiz erişim kaldırılmış — "we have discontinued free api access") |
| **Hardcover.app** | `api/v1/graphql` | **HTTP 403** — GraphQL, ücretsiz API key ile; kimlik doğrulaması zorunlu |
| **LibraryThing** | `librarything.com/api/` | Sayfa açık ama **yeni/public API yok** (eski API'ler kapatıldı) |
| **KYGM / ISBN Türkiye Ajansı** | `ekygm.gov.tr/Home/IsbnIndex` | HTTP 200 ama **kullanıcı girişi ekranı** (e-Devlet veya e-posta/şifre). ISBN sorgu ekranı giriş arkasında; otomatik sorgulama için uygun değil |
| **KYGM Koha OPAC** | `koha.ekutuphane.gov.tr/cgi-bin/koha/opac-search.pl?q=9786254085086&idx=nb` | **JS doğrulama duvarı** ("Doğrulama Bağlantınız kontrol ediliyor") — otomatik istek engelli; tarayıcı otomasyonu (Playwright) aşabilir |
| **millikutuphane.gov.tr alt alanları** | `isbn.millikutuphane.gov.tr`, `katalog.millikutuphane.gov.tr` | **DNS çözümlenmiyor** (mevcut değil) |
| **D&R** | `dr.com.tr/Kitap?q=...`, `/arama?q=...` | HTTP 404 / erişim engeli |
| **Idefix** | `idefix.com/kitap?q=...` | Bağlantı kesildi (bot koruması) |
| **Babil** | `babil.com/search?q=...` | SSR gövde boş; içerik JS ile geliyor (sunucudan ürün verisi çıkmıyor) |
| **Nadir Kitap** | `nadirkitap.com/arama?q=...` | **HTTP 403** (bot koruması) |

---

## (b) Neden Bulunamadı?

**ISBN geçerli, kitap gerçek ve Google Kitaplar'da kayıtlı.** Hata zinciri iki katmanlı:

1. **Katman 1 — Google Books API kota/ağ engeli:**
   Uygulamanın `GBOOKS_KEY`'ine sahip proje için günlük sorgu kotası **dolmuş** (429 `RESOURCE_EXHAUSTED`;
   cihazda `503 backendFailed` olarak gözüküyor). Kod zaten `markGoogleDown()` ile Google'ı 10 dakika
   atlıyor ve Open Library'ye düşüyor — davranış doğru, ancak yedek de boş.

2. **Katman 2 — Open Library'de bu ISBN'in kaydı yok:**
   `/isbn/9786254085086.json` → **404**. `fetch()` içindeki `runCatching` hatayı yutup `null` döndürüyor →
   kullanıcıya "bulunamadı". Eser OL'de **eski ISBN'lerle** kayıtlı:
   - Ötüken 2021 baskısı: **978-975-437-048-5** (`OL33876164M`, 112 s.)
   - Alkım 2005 baskısı: 975-636-382-7
   2023+ baskıların ISBN'i (978-625-408-508-6) ise OL'ye hiç işlenmemiş.

**Elenen diğer hipotezler:**
- ❌ Checksum hatası: ISBN-13 check digit 6 ✓ geçerli; ISBN-10 `6254085089` hem hesapla hem Amazon/Google künyesiyle doğrulandı.
- ❌ "Kayıt hiçbir yerde yok": Google Kitaplar (hacim `s4FY0AEACAAJ`), KitapSeç, Bora Dağıtım, Trendyol, Amazon vb. hepsinde var.
- ❌ Kod hatası: `BookMetadataSource` mantığı sağlam; sorun tamamen kaynak kapsaması + API kotası.

---

## (c) Aday Veritabanı / API Değerlendirme Tablosu

| # | Ad | Uç nokta / erişim | Ücretsiz mi? | Türk ISBN kapsaması | Hız limiti | Lisans / Not | Zincire nasıl eklenir (öneri) |
|---|---|---|---|---|---|---|---|
| 1 | **Google Books API** (mevcut) | `googleapis.com/books/v1/volumes?q=isbn:` + `&key=` + `&country=TR` | Evet (anahtarla, günlük kota); kota artışında ücretli | **Çok iyi** (bu ISBN dahil — web'de kayıtlı) | ~1.000 sorgu/gün (ücretsiz); 429/503 = kota | API ToS; anahtar `local.properties`'ta | **1. sırada kalsın**; 429/503'ü "kota" olarak ayır, ISBN-10'u da dene, kullanıcıya Google Kitaplar linki göster |
| 2 | **Open Library** (mevcut) | `/isbn/{isbn}.json`, `/api/books?bibkeys=`, `/search.json?isbn=`, `covers.openlibrary.org` | Evet, anahtarsız | Orta (eser var, yeni ISBN'ler eksik; eski baskılar tam) | Resmi limit yok ama "fair use"; User-Agent şart (docs: sık kullanımda UA belirtin, agresif throttle/block) | AGPL; veri CC0 | **2. sırada kalsın**; ISBN-10/13 karşılıklı türet, `?isbn=` alan araması ve `bibkeys` dene; bulunamazsa work seviyesinde eşleşme öner |
| 3 | **Open Library katkı akışı** | `openlibrary.org/books/add` (Add a book), docs: Import Pipeline | Evet | — | — | CC0 katkı | Kullanıcıya "OL'de yok, kaydı ekle" düğmesi; `otherEditionIsbns` ile bulunmuş eski ISBN'leri formu önceden doldur |
| 4 | **KYGM / ISBN Türkiye Ajansı** (Milli Kütüphane) | `ekygm.gov.tr/Home/IsbnIndex` (girişli), e-Devlet `turkiye.gov.tr/kultur-isbn-issn`; Koha OPAC `koha.ekutuphane.gov.tr` (idx=nb ile ISBN araması, JS duvarlı) | Evet (giriş/üyelikle) | **En iyi** — Türkiye'de atanan her ISBN burada | Belirsiz; otomasyon için giriş + bot duvarı | Kamu verisi; otomatik çekim belirsiz, Playwright ile OPAC aşılabilir | 3. sırada **kullanıcıya link** (ISBN sorgu ekranı) + OPAC'ı Playwright fallback olarak dene |
| 5 | **KitapSeç (JSON-LD)** | `kitapsec.com/Products/...` — `schema.org/Product` (`sku`=ISBN, `brand`, `image`, açıklama) | Evet (sayfa açık) | **Çok iyi** (ISBN, sayfa, ebat, baskı no) | Ürün sayfası başına 1 istek; robots.txt `User-agent: *` (Disallow yok) | Ticari site verisi; sayfa içeriği telifli olabilir → **yalnızca künye alanlarını**, düşük sıklıkla, son çare olarak | **3.–4. sırada son-çare fallback**: ISBN → site araması → JSON-LD'den `RemoteMeta` + kapak URL'i |
| 6 | **Trendyol / Bora Dağıtım / diğer TR perakendecileri** | Ürün sayfaları ISBN'i URL'de taşıyor (ör. `trendyol.com/...-9786254085086-p-...`); Bora = Ötüken'in resmi dağıtıcısı | Evet (sayfa açık) | Çok iyi | Düşük tutulmalı; bot koruması (Idefix/NadirKitap 403, Babil JS) | Aynı yasal not; Bora Dağıtım "yayıncı kaynağı" sayılır | KitapSeç gibi son çare; Bora Dağıtım "yayınevi doğrulaması" için iyi |
| 7 | **WorldCat / OCLC** | Web araması JS'li; Classify API **kapandı** (404); SRU/Z39.50 **wskey abonelik ister** | Hayır (API) / web ücretsiz | Orta (TR kütüphanelerinden kayıt var) | wskey planına bağlı | OCLC ticari lisans | Zincire **API olarak eklenmez**; kullanıcıya `worldcat.org/isbn/{isbn}` linki gösterilebilir |
| 8 | **ISBNdb** | `api2.isbndb.com` (Bearer key) | **Hayır** — Basic $14.99/ay, 1 çağrı/sn; ücretsiz tier kaldırılmış | İyi (perakendeci/dağıtımcı verisi, TR dahil) | 1/sn (Basic) | Ticari lisans | Ücretli; ancak yukarıdakiler yetersiz kalırsa **opsiyonel 4. kaynak** |
| 9 | **Hardcover.app** | GraphQL `hardcover.app/api/v1/graphql`, ücretsiz API key | Evet (key ile) | Zayıf (İngilizce ağırlıklı, TR modern baskılar az) | GraphQL fair-use | Ticari; veri lisansı kısıtlı | Eklenmesi önerilmez (kapsama zayıf) |
| 10 | **LibraryThing** | Public API yok (eski API'ler kapatıldı) | Hayır | — | — | — | Elendi |
| 11 | **Wikidata** | SPARQL + `wbsearchentities`; `P212` (ISBN) özelliği | Evet, anahtarsız | Zayıf — bu ISBN/kitap için kayıt yok | Wikimedia API politikası (bot UA zorunlu, ~200 req/sn) | CC0 | Ücretsiz ve ucuz; **4. sırada bedava fallback** veya "eser kimliği" kaynağı olarak |
| 12 | **Google Kitaplar web** (`books.google.com/books?vid=ISBN{isbn}`) | Yönlendirme sayfası künyeyi HTML'de taşıyor (kanıtlandı) | Evet | Çok iyi | Düşük tutulmalı | **Resmi API dışı tarama = ToS riski** | API olarak değil; "Google Kitaplar'da aç" **linki** olarak kullanıcıya göster + kota hatasında yönlendirme |

---

## (d) Sonuç ve Önerilen Zincir

### Bu kitap nerede bulundu (kanıtla)?

| Kaynak | Durum |
|---|---|
| **Google Kitaplar (WEB)** | ✅ **Bulundu** — hacim `s4FY0AEACAAJ`: "Dokuzuncu Hariciye Kogusu, Peyami Safa, **2023**, **112 sayfa**, ISBN 6254085089 + 9786254085086" |
| **Perakendeciler** | ✅ **Bulundu** — KitapSeç (JSON-LD, 112 s., 12x20), Bora Dağıtım (Ötüken dağıtıcısı, barkod), Trendyol, Efsus (2024-12, 126. baskı), Polat (2023-02, 122. baskı), Amazon (`dp/6254085089`) |
| Google Books **API** | ⛔ Kota engelli (429) — kayıt aslında var |
| Open Library | ❌ Bu ISBN yok; **aynı eserin Ötüken 2021 baskısı `978-975-437-048-5` ile kayıtlı** (`OL33876164M`, 112 s.) |
| Wikidata | ❌ Kayıt yok |
| WorldCat/ISBNdb/Hardcover | ❓ API erişimi kapalı/ücretli — doğrulanamadı |

**Önemli tespit (eski ISBN):** Ötüken bu eserde ISBN değiştirmiş — eski 975 önekli
`978-975-437-048-5` (ve daha eski `975-636-382-7`) yerine 2023+ baskılar `978-625-408-508-6` kullanıyor.
Open Library'de eski ISBN'li kayıt yaşadığı için "aynı eser" eşleşmesi (başlık+yazar) hâlâ mümkün.

### Önerilen künye zinciri (öncelik sırasıyla)

1. **Google Books API** (mevcut kalsın) — `q=isbn:{isbn13}` + `q=isbn:{isbn10}` denemesi eklenmeli;
   429/503 → "kota/ağ" olarak ayrı sınıflandırılıp kullanıcıya gösterilmeli (devre kesici zaten var).
2. **Open Library** (mevcut kalsın) — sırayla: `/isbn/{isbn}.json` → `/isbn/{isbn10}.json` →
   `search.json?isbn=` → `api/books?bibkeys=ISBN:` (daha zengin veri: yazar/yayınevi/sayfa).
   Mevcut `listsIsbn` doğrulaması korunmalı.
3. **YENİ — KitapSeç JSON-LD son-çare fallback** (Türkiye ISBN'leri için en etkili çözüm):
   ISBN ile site araması → `schema.org/Product` (`sku`, `brand`, `image`) + ürün tablosundan
   sayfa/ebat/baskı bilgisi. Düşük hız limiti (talep başına en fazla 1-2 istek, önbellekle),
   robots.txt'ye uyum, yalnızca künye alanlarının alınması.
4. **YENİ — Kullanıcı köprüleri (ücretsiz, risksiz):**
   - ISBN bulunamazsa: **Open Library'ye ekle** (`https://openlibrary.org/books/add` — `otherEditionIsbns`
     ile bulunan eski ISBN'ler formu doldurmak için önerilebilir),
   - **Google Kitaplar** linki (`books.google.com/books?vid=ISBN{isbn}`),
   - **KYGM ISBN sorgu / Koha OPAC** linki (`koha.ekutuphane.gov.tr`, idx=nb),
   - **WorldCat** linki (`worldcat.org/isbn/{isbn}`).

(4.–5. sırada bedava isteğe bağlı: **Wikidata P212** sorgusu; **ISBNdb** yalnızca bütçe varsa.)

### Uygulama notları (`BookMetadataSource.kt`)

- `fetch()` içinde ISBN-10/ISBN-13 **karşılıklı türetim** eklenmeli (9786254085086 → 6254085089);
  bu, hem Google'da hem OL'de eşleşmeyi artırır — Amazon/Google künyeleri ISBN-10'u zaten taşıyor.
- "Bulunamadı" durumu ikiye ayrılmalı: **(i) kayıt yok** vs **(ii) tüm kaynaklara ulaşılamadı (kota/ağ)**.
  Kullanıcıya ikincisinde "daha sonra tekrar dene / manuel gir" mesajı gösterilmeli.
- `otherEditionIsbns()` zaten var; OL work eşleşmesi (`OL25295645W`) üzerinden **"eski ISBN'li baskı bulundu"**
  önerisi eklenebilir (kullanıcı eski ISBN'li kaydı kabul ederse künye yine de dolar).
- Google Books anahtarının **proje kotası** kontrol edilmeli (429 gövdesi: `project_number:624717413613`,
  `defaultPerDayPerProject` dolu) — GCP konsoldan kota artışı veya faturalandırma gerekebilir.
- Kapak tarafında OL `covers.openlibrary.org/b/isbn/...` bu ISBN için 43 baytlık GIF yer tutucu döndürüyor;
  `> 2_000 bayt` kontrolü bunu zaten eliyor, iyi. Perakendeci fallback'i kapak görselini de sağlayabilir.

---

### Ek kanıt özeti (HTTP durum kodları, 2026-10-25)

```
GET googleapis.com/books/v1/volumes?q=isbn:9786254085086   -> 429 (quota exhausted)
GET googleapis.com/books/v1/volumes?q=intitle:... inauthor: -> 429
GET googleapis.com/books/v1/volumes?q=isbn:6254085089       -> 429
GET books.google.com/books?vid=ISBN9786254085086            -> 200  kitap VAR (s4FY0AEACAAJ, 2023, 112 s.)
GET openlibrary.org/api/books?bibkeys=ISBN:9786254085086    -> 200  {}
GET openlibrary.org/search.json?q=9786254085086             -> 200  numFound: 0
GET openlibrary.org/search.json?isbn=6254085089             -> 200  numFound: 0
GET openlibrary.org/isbn/9786254085086.json                 -> 404  (uygulamanın uç noktası)
GET openlibrary.org/isbn/6254085089.json                    -> 404
GET openlibrary.org/works/OL25295645W/editions.json         -> 200  Ötüken 2021 = 9789754370485; Alkım 2005 = 9756363827
GET covers.openlibrary.org/b/isbn/9786254085086-L.jpg       -> 200  image/gif 43B (yer tutucu)
GET classify.oclc.org/classify2/Classify?isbn=...           -> 404  (Classify kapatılmış)
GET worldcat.org/webservices/catalog/search/sru?...         -> 520  (wskey gerekli)
GET isbndb.com/books/9786254085086                          -> 403  (bot koruması; API ücretli)
GET hardcover.app/api/v1/graphql                            -> 403  (auth gerekli)
GET ekygm.gov.tr/Home/IsbnIndex                             -> 200  giriş ekranı (e-Devlet/e-posta)
GET koha.ekutuphane.gov.tr/.../opac-search.pl?q=...&idx=nb  -> 200  JS doğrulama duvarı
GET kitapsec.com/Products/...-78074.html                    -> 200  JSON-LD Product, sku=9786254085086
GET boradagitim.com/Istem/Urun/Detay.aspx?Id=108172         -> 200  Barkod: 9786254085086
GET kitapyurdu.com/...filter_name=9786254085086             -> 200  ürün yok (ISBN indekslenmiyor)
GET kitapyurdu.com/...filter_name=Dokuzuncu+Hariciye+Koğuşu -> 200  9 ürün linki (641158.html, 14305.html)
SPARQL wikidata P212 = "978-625-408-508-6"                  -> 200  bindings: []
ISBN-13 checksum 9786254085086 -> 6 ✓ | ISBN-10 -> 6254085089 ✓
```
# Faz 9 — Kararlılık doğrulama kapısı

## Kod seviyesinde tamamlananlar
- Oyun kaydı yüklenirken tahta boyutu, taşların 2'nin kuvveti olması ve negatif olmayan skor kontrolü.
- Yüklenen oyunun `lost` durumu mevcut tahtadan tekrar hesaplanır.
- Android `ON_STOP`, iOS `scenePhase` sırasında durum saklama; animasyon öncesi tahtanın son hamleyi ezmemesi.
- Küçük ekranlar için kaydırılabilir oyun yerleşimi.
- Android regresyon testleri: birleşme, geçersiz hamle, hamlesiz tahta, taş üretimi.
- iOS CI: ödül, hareket ve oyun modu smoke testleri.

## Yayın öncesi zorunlu doğrulamalar — henüz tamamlanmadı
- [ ] Android CI: `gradle :app:assembleDebug :app:testDebugUnitTest` yeşil.
- [ ] iOS CI: XcodeGen + `xcodebuild` iOS Simulator derlemesi yeşil.
- [ ] Android: küçük ekran, büyük yazı, TalkBack, geri alma ve arka plana alma.
- [ ] iOS: küçük iPhone, iPad, VoiceOver, geri alma ve arka plana alma.
- [ ] Her iki platform: 1024 / 2048 / Süresiz hedef ve oyun sonu senaryoları.
- [ ] Bozuk kayıt, eski sürümden güncelleme ve reklam ödülünün tek sefer verilmesi.
- [ ] Gerçek cihaz performansı, animasyon ve reklam SDK doğrulaması.

**Not:** Faz 9 kod düzenlemeleri yapılmış olsa da CI ve cihaz testleri geçmeden yayın hazır veya doğrulanmış olarak işaretlenmez.

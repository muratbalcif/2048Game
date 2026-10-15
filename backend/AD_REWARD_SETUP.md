# AdMob SSV – 2× doğrulanmış skor

Backend endpoint: `GET /v1/admob/ssv`

## Dağıtım sırası
1. D1 migrations `0003_rewarded_score_bonus.sql` ve `0004_verified_score_bonus.sql` dosyalarını üretim veritabanına uygula.
2. Worker'ı dağıt.
3. AdMob ödüllü reklam biriminde **Server-side verification** callback URL'sini `https://modern2048-leaderboard.muratbalci269.workers.dev/v1/admob/ssv` olarak ayarla.
4. Mobil reklam gösterimi öncesinde SSV `custom_data=sessionId` ve `user_id=playerId` gönderilmeli. Ödül türü `double_score` olmalı.
5. Reklam tamamlandıktan sonra skorun gerçekten 2× olarak yansıtıldığını leaderboard endpoint'inden kontrol et.

## Güvenlik
- Worker, Google'ın açık anahtarlarıyla imzayı doğrulamadan bonus kaydetmez.
- Tek oyun için tek `double_score` kaydı vardır; `transaction_id` benzersizdir.
- Skorun temel değeri sunucuda hamlelerin yeniden oynatılmasıyla hesaplanır.
- AdMob callback'i finalize işleminden önce veya sonra gelebilir.
- Mobil SDK entegrasyonu, canlı reklam birimi ayarı, üretim migration ve uçtan uca test henüz tamamlanmadı.

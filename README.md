# Kralların Savaşı / Battle of the Kings

Android için özgün, çevrimdışı, yandan ilerleyen bir dövüş oyunu. Mitolojik aksiyonu klasik arcade dövüş yapısıyla birleştirir. Karakterler, ortamlar ve efektler uygulama içinde çizilir; harici oyun varlığı veya ağ izni kullanılmaz.

## Oynanış

- Soldaki dört yön düğmesiyle ilerle ve dövüş hattında konumlan.
- **VUR** hızlı balta saldırısıdır. **YAR** daha yavaş ve geniş bir saldırıdır.
- **KAÇ** kısa süreli hasar almazlık sağlayan atılmadır.
- Pusudaki tüm düşmanları yenince yol açılır. Üçüncü pusudan sonra sağdaki kapıya ilerleyerek sonraki diyara geç.
- Yeşil ve mavi rünler sırasıyla canı ve öfkeyi doldurur. Öfke 50'ye ulaştığında YAR saldırısı güçlenir.
- Duraklatma düğmesi sağ üst köşededir.

Üç diyar bulunur: Kül Kıyısı, Buz Geçidi ve Kral Salonu. Her birinde üç pusu ve bir şampiyon vardır.

## Derleme ve kurulum

Projeyi JDK 17 ve Android SDK 35 yüklü Android Studio ile açıp `app` yapılandırmasını Android 6.0 veya üzeri bir cihazda/emülatörde çalıştır. Proje Android Gradle Plugin 8.7.3 ve Gradle 8.9 kullanır. Her gönderimde GitHub Actions hata ayıklama APK'sını derleyip iş akışı çıktısı olarak yayımlar.

Depoda Gradle Wrapper ikili dosyası bulunmaz. Gradle 8.9 kurulu bir terminalde `gradle :app:assembleDebug` komutuyla derleyebilirsin. Android Studio isterse Gradle 8.9 sürümünü seç.

## Telif notu

Bu oyun mitolojik aksiyon ve arcade dövüş türlerinden esinlenen özgün bir yapımdır. Başka oyunların karakterlerini, öykülerini, adlarını veya varlıklarını içermez.

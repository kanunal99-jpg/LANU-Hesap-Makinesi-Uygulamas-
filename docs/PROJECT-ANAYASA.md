# LANU HESAP MAKİNESİ - PROJE ANAYASASI

Bu belge, LANU Hesap Makinesi projesinin değişmez teknik, mimari, güvenlik ve kalite kurallarını belirler.

## 1. Mimari Prensipler
- **Kotlin & Jetpack Compose & Material 3**: Modern, resmi Android standartlarına tam uyum.
- **MVVM Mimarisi**: UI mantığı, StateFlow ve ViewModel ile ayrıştırılmıştır.
- **Engine Ayırma**: Hesaplama motoru (`CalculatorEngine`) ve Finansal motor (`FinancialEngine`) UI katmanından tamamen bağımsızdır ve doğrudan unit test edilebilir.
- **BigDecimal / Hassas Aritmetik**: Finansal ve temel hesaplamalarda floating-point hatalarını önlemek için `BigDecimal` kullanılır.

## 2. Gizli Alan ve '2011.' Geçiş Kuralı
- Hesap makinesi ana alanı **yalnızca** bir hesap makinesidir.
- Arayüzde ikinci alan, terminal, iletişim, admin veya gizli alan hakkında **hiçbir** buton, menü, ipucu, yazı veya yönlendirme bulunmaz.
- Tek geçiş mekanizması tam karakter dizisi olan **'2011.'** girdisidir.
- Bu dizi girildiğinde herhangi bir hata, hesaplama sonucu veya uyarı gösterilmeden **sessiz ve otomatik olarak** ikinci alana geçilir.

## 3. Güvenlik, Şifreli Loglama ve İletişim Alanı
- Yapılan tüm hesaplama işlemleri ve kritik olaylar şifreli olarak yerel log dosyasına/veritabanına kaydedilir.
- Bu loglar **yalnızca** ikinci alanda ana şifre doğrulandıktan sonra görüntülenebilir.
- **Şifreli İletişim Alanı**: Şifreli gizli alanda güvenli mesajlaşma, sesli ve görüntülü konuşabilme altyapısı bulunur; bu özellikler için gerekli donanım izinleri (kamera, mikrofon) yalnızca bu alanda şifreli oturum açıldığında dinamik olarak talep edilir.

## 4. Özelleştirilebilir Tema ve Renk Paleti
- Kullanıcılar ana ekran için ön tanımlı temalar seçebilir veya kendi özel renk paletlerini dinamik olarak oluşturabilir.

## 5. Test ve Kalite Güvencesi
- `CalculatorEngine` ve `FinancialEngine` için kapsamlı JUnit unit testleri zorunludur.
- '2011.' geçiş mekanizması ve ana ekran sızıntı testleri UI testleri ile doğrulanır.
- Hiçbir derleme, test veya APK doğrulaması yapılmadan "tamamlandı" denemez.

## 6. CI/CD ve APK İzlenebilirliği
- Her sürümde Debug ve Release APK üretilir, SHA-256 imzası hesaplanır ve commit SHA ile ilişkilendirilir.
- GitHub Actions pipeline'ı üzerinden bağımsız doğrulama zorunludur.

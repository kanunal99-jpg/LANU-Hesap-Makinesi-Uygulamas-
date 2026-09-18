# LANU HESAP MAKİNESİ - PROJE ANAYASASI

Bu belge, LANU Hesap Makinesi projesinin değişmez teknik, mimari, güvenlik ve kalite kurallarını belirler.

## 1. Mimari Prensipler
- **Kotlin & Jetpack Compose & Material 3**: Modern Android standartları.
- **MVVM**: UI mantığı ViewModel/StateFlow ile ayrıştırılır.
- **Engine Ayırma**: `CalculatorEngine` ve `FinancialEngine` UI'dan bağımsız ve doğrudan unit-test edilebilir.
- **BigDecimal / Hassas Aritmetik**: Finansal hesaplamalarda kayan nokta hataları önlenir.

## 2. Ana Ekran ve '2011.' Geçiş Kuralı
- Ana uygulama kullanıcı açısından yalnızca bir hesap makinesidir.
- Ana UI'da ikinci alan, iletişim, terminal, admin, gizli alan veya erişim yöntemi hakkında hiçbir görünür ipucu bulunmaz.
- Tek geçiş mekanizması tam karakter dizisi **`2011.`** girdisidir.
- `2011.` girildiğinde sessiz ve otomatik olarak ikinci alana geçilir.
- **İkinci parola, master password veya admin şifresi yoktur.**
- `2011.` bir kullanıcı erişim tetikleyicisidir; **kriptografik anahtar değildir**.

## 3. Güvenlik ve İletişim
- Yerel kritik kayıtlar şifreli saklanır; anahtar materyali Android Keystore veya uygun güvenli anahtar yönetimi ile korunur.
- İletişim payload'ları AEAD ile doğrulanmış şifreleme kullanır; plaintext fallback yasaktır.
- Ağ iletişimi cleartext olarak açılmaz; güvenli taşıma ve uygulama seviyesinde kimlik/yetkilendirme gerekir.
- Sesli/görüntülü görüşme yalnızca gerçek medya aktarımı ve gerçek cihaz testi varsa "çalışıyor" kabul edilir. Sadece signaling gerçek görüşme değildir.
- Donanım izinleri yalnızca ilgili özellik gerçekten kullanılacağı zaman istenir.

## 4. Tema
- Ana ekran için ön tanımlı veya kullanıcı tanımlı tema/renk paleti desteklenebilir.

## 5. Test ve Kalite
- `CalculatorEngine` ve `FinancialEngine` kapsamlı JUnit testlerine sahip olmalıdır.
- `2011.` geçişi ve ana ekran sızıntısı UI testleriyle doğrulanır.
- Hiçbir build, test veya APK doğrulaması yapılmadan "tamamlandı" denmez.
- Testler silinmez, atlanmaz ve hata gizleme amacıyla `|| true` kullanılmaz.

## 6. CI/CD ve APK İzlenebilirliği
- CI test, lint ve APK build zincirini gerçekten çalıştırır.
- Debug/Release APK yalnızca gerçek build sonucu oluşturulmuşsa ürün çıktısı kabul edilir.
- APK SHA-256 değeri ilgili commit/build ile ilişkilendirilir.
- GitHub Actions sonucu başarısızsa release/onay verilmez.

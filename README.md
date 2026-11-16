# PC Panel (Android 4.2.2 / General Mobile E-Tab 4)

Eski bir Android 4.2.2 (API 17) tableti USB kablosuyla Windows PC'ye bağlayarak CPU, GPU, RAM, disk, ağ hızı ve saat/tarih bilgilerini gösteren tam ekran bilgi paneli.

## Özellikler
- **Android 4.2.2 (API 17) Uyumlu:** AppCompat/AndroidX kullanmadan saf `android.app.Activity` ve `Service` mimarisi.
- **Dairesel Gösterge Arayüzü:** CPU, GPU (NVIDIA sıcaklığı ve VRAM), RAM ve Disk için dairesel ilerleme halkaları, anlık ağ hızı, tablet pil durumu ve tek dokunuşla ekran kilitleme.
- **Veri Akışına Dayalı Otomatik Uyanma / Uyku (`MonitorService`):**
  - PC'den taze veri geldiğinde ekranı `WakeLock` ile uyandırır ve paneli öne getirir.
  - Veri akışı kesildiğinde (veya `{"state":"off"}` alındığında) Device Admin izni varsa ekranı kilitler, yoksa ekranı karartır.
- **Kişiselleştirme:** Ekrana uzun basarak açılan ayarlar menüsünden uyku süresi, uyanma gecikmesi, Cihaz Yöneticisi izni ve 4 farklı tema rengi (Koyu, AMOLED, Gece Mavisi, Kızıl) seçilebilir.

## 1. Tablette USB Hata Ayıklama Açma

1. Tablette **Ayarlar > Tablet hakkında** bölümüne girin.
2. **Derleme numarası (Build number)** satırına art arda **7 kez** dokunun.
3. Geri dönüp **Ayarlar > Geliştirici seçenekleri > USB hata ayıklama** seçeneğini açın.
4. Tableti USB kablosuyla PC'ye bağlayıp **"Bu bilgisayardan her zaman izin ver"** seçeneğini onaylayın.

## 2. PC Tarafı: ADB ve Python Kurulumu

1. **ADB Kurulumu:** [SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools) paketini `C:\platform-tools` klasörüne çıkartın (veya `PATH`'e ekleyin; `panel.py` yaygın yolları otomatik bulur).
2. **Python Kurulumu:** Python 3 kurup `pc` klasöründe bağımlılığı yükleyin:
   ```bat
   pip install -r pc\requirements.txt
   ```

## 3. Android Uygulamasını Derleme ve Yükleme

1. **Android Studio** ile `android` klasörünü açın ve **Build > Build Bundle(s) / APK(s) > Build APK(s)** ile derleyin.
2. APK'yı tablete yükleyin:
   ```bat
   adb install -r android\app\build\outputs\apk\debug\app-debug.apk
   ```
3. Tablette **PC Panel** uygulamasını bir kez açın; ekrana **uzun basarak** ayarlar ekranından Cihaz Yöneticisi (ekran kilitleme) iznini verebilirsiniz.

## 4. PC Scriptini Başlangıç ve Kapanışa Ekleme

- **Manuel Başlatma:** `pc\baslat.bat` dosyasına çift tıklayın (pencere göstermeden arka planda çalışır).
- **Windows Başlangıcı:** `Win + R` → `shell:startup` yazıp açılan klasöre `pc\baslat.bat` kısayolu ekleyin.
- **Windows Kapanışı (`kapat.bat`):**
  1. `Win + R` → `gpedit.msc` (Windows Pro/Enterprise).
  2. **Bilgisayar Yapılandırması > Windows Ayarları > Komut Dosyaları > Kapat** bölümüne `pc\kapat.bat` dosyasını ekleyin.

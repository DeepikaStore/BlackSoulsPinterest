# Black Souls Pinterest — Android WebView

Aplikasi galeri Android bergaya Pinterest/Dark Mode untuk melihat gambar dari
`Graphics/Pictures` pada repository GitGud `Astrologeos/black_souls`.

## Cara membuka dan build

1. Ekstrak ZIP.
2. Buka folder `BlackSoulsPinterest` melalui Android Studio.
3. Gunakan JDK 17 dan Android SDK Platform 35.
4. Tunggu Gradle Sync selesai.
5. Pilih **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
6. Pasang APK hasil build di perangkat Android.

## Cara kerja sinkronisasi

Saat pertama kali dibuka, aplikasi meminta metadata repository dan daftar file
folder `Graphics/Pictures` melalui GitLab-compatible API GitGud. Aplikasi membaca
default branch dari metadata, lalu mengunduh file gambar ke penyimpanan privat
aplikasi. Hanya folder gambar tersebut yang disinkronkan, bukan seluruh isi game.
Tekan tombol ↻ untuk sinkronisasi ulang. File yang sudah ada tidak diunduh ulang.

Format gambar yang dicari: PNG, JPG, JPEG, WEBP, GIF.

## Catatan penting

- Perangkat harus memiliki koneksi internet saat sinkronisasi.
- Repository harus publik dan endpoint API GitGud harus dapat diakses.
- Jika API GitGud menolak permintaan atau struktur API berubah, sinkronisasi
  akan menampilkan pesan error di bawah kolom pencarian.
- Ini adalah galeri aset, bukan game Black Souls. Aplikasi tidak menjalankan
  file game atau mengubah aset aslinya.
- Aset tetap milik pemegang hak cipta/pemilik repository. Gunakan sesuai lisensi
  dan ketentuan repository.

## Struktur utama

- `MainActivity.kt`: WebView, JavaScript bridge, dan lifecycle.
- `RepositoryAssetSync`: mengambil daftar file dan mengunduh gambar.
- `app/src/main/assets/index.html`: antarmuka Pinterest-style.
- `app/src/main/AndroidManifest.xml`: permission internet dan activity.

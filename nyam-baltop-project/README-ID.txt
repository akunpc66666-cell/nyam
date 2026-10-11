# NYAM SERVER — Balance Top asli

Paket ini mempertahankan desain website yang kamu kirim dan menambahkan jalur untuk membaca 10 saldo tertinggi dari ekonomi Paper/Vault. Website **belum akan menampilkan saldo live** sampai konfigurasi Vercel, Redis, dan plugin dilakukan.

## Menu Normal / Performa
- Saat pengunjung pertama kali membuka website, menu pilihan **Normal** atau **Performa** akan tampil.
- Tombol **MODE WEBSITE** selalu tersedia di kanan bawah untuk membuka menu itu lagi.
- Pilihan disimpan di browser pengunjung. Mode Performa mengurangi animasi, glow, efek partikel, dan beberapa bayangan agar lebih ringan di perangkat spek rendah.

## Isi paket
- `index.html` — website NYAM dengan tampilan Balance Top yang mengambil data dari `/api/baltop`.
- `api/baltop.js` — API Vercel; menerima data dari plugin dan menyimpannya di Redis.
- `paper-plugin/` — source plugin Paper yang membaca ekonomi melalui Vault lalu mengirim Top 10.

## 1. Siapkan penyimpanan data
1. Buat database Redis di Upstash (atau tambahkan integrasi Upstash Redis ke proyek Vercel).
2. Catat nilai `UPSTASH_REDIS_REST_URL` dan `UPSTASH_REDIS_REST_TOKEN`. Jangan bagikan token tersebut.

## 2. Deploy ke Vercel
Penting: **Vercel Drop untuk berkas statis saja tidak cukup untuk menjalankan `api/baltop.js`**. Hubungkan proyek ke Git repository lalu import repository itu di Vercel, atau deploy project folder ini dengan Vercel CLI. Upload file `index.html` saja tidak akan mengaktifkan API.

Pastikan struktur root deploy seperti ini:
```
index.html
package.json
api/baltop.js
```

Pada Vercel → Project → Settings → Environment Variables, tambahkan:
- `UPSTASH_REDIS_REST_URL` = URL REST Redis Upstash
- `UPSTASH_REDIS_REST_TOKEN` = token REST Redis Upstash
- `NYAM_INGEST_SECRET` = kata rahasia panjang acak yang kamu buat sendiri

Gunakan environment yang cocok, biasanya Production. Setelah mengubah environment variables, redeploy project. Domain Vercel lama bisa tetap dipakai selama kamu memperbarui proyek yang sama.

### Tes API sebelum memasang plugin
Buka `https://DOMAIN-KAMU/api/baltop`. API yang sudah terkonfigurasi tapi belum menerima data akan memberi JSON dengan `players: []`. Itu wajar; artinya fungsi API berjalan tetapi plugin belum mengirim data. Jika mendapat 404, folder `api` belum terdeploy sebagai serverless function. Jika 503, periksa variabel Redis.

## 3. Buat plugin Paper
Plugin disediakan sebagai source Maven, bukan file `.jar` yang sudah dikompilasi. Untuk membuat JAR, komputer build memerlukan Java 17 dan Maven, lalu jalankan dari folder `paper-plugin`:
```bash
mvn package
```
JAR hasil build ada di `paper-plugin/target/nyam-balance-bridge-1.0.0.jar`.

Sebelum build, buka `paper-plugin/src/main/resources/config.yml` dan isi:
- `api-url`: `https://DOMAIN-KAMU/api/baltop`
- `shared-secret`: nilai yang sama persis dengan `NYAM_INGEST_SECRET` pada Vercel

Simpan secret hanya di konfigurasi server/Vercel, jangan diposting ke Discord atau dibagikan.

## 4. Pasang di AstrixHost
1. Pastikan server menggunakan Paper dan plugin `Vault` serta plugin ekonomi aktif (misalnya EssentialsX Economy). `/baltop` yang sudah berfungsi adalah indikasi bagus, tetapi bridge ini mengambil angka melalui Vault.
2. Upload `nyam-balance-bridge-1.0.0.jar` ke folder `plugins/`.
3. Start/restart server. Setelah folder `plugins/NYAMBalanceBridge/` dibuat, jika perlu sesuaikan `config.yml` yang ada di folder tersebut dan restart lagi.
4. Cek console untuk `NYAM Balance Bridge aktif`. Kalau Vault/Economy tidak terdeteksi, periksa bahwa Vault dan plugin ekonomi aktif di `/plugins`.
5. Tunggu sekitar 1 menit, lalu buka `https://DOMAIN-KAMU/api/baltop` dan website. Top 10 akan diperbarui tiap 60 detik.

## Catatan teknis
- API publik hanya menampilkan nama dan saldo Top 10. Endpoint POST membutuhkan Bearer secret.
- Plugin memeriksa akun ekonomi dari Vault, mengambil pemain yang pernah masuk, lalu hanya mengirim Top 10.
- Jika server memiliki ribuan pemain, pembacaan seluruh daftar offline dapat memberi beban saat update. Mulai dengan interval 60–120 detik dan pantau console/tick server.
- Aku belum bisa menguji koneksi langsung ke AstrixHost/Vercel milikmu dari paket ini. Tes API dan console di atas diperlukan sebelum dianggap live.

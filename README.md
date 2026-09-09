# WT-80B Bluetooth Printer App

แอป Android (Kotlin) สำหรับเชื่อมต่อและสั่งพิมพ์กับเครื่องพิมพ์ WELLTECH WT-80B
ผ่าน Bluetooth โดยใช้คำสั่ง ESC/POS ซึ่งเครื่องรุ่นนี้รองรับอยู่แล้ว

รองรับ:
- เชื่อมต่อเครื่องพิมพ์ผ่าน Bluetooth (Classic SPP)
- พิมพ์ข้อความภาษาไทย/อังกฤษ (render เป็นภาพก่อนส่ง จึงไม่มีปัญหาเรื่อง code page)
- พิมพ์ใบเสร็จตัวอย่าง
- พิมพ์บาร์โค้ด CODE128
- ป้อนกระดาษ

## วิธี Build เป็นไฟล์ APK บน GitHub (ไม่ต้องลง Android Studio)

1. สร้าง repository ใหม่บน GitHub (public หรือ private ก็ได้) เช่นชื่อ `wt80b-printer-app`
2. อัปโหลดไฟล์ทั้งหมดในโฟลเดอร์นี้ขึ้น repository นั้น เช่นผ่านหน้าเว็บ GitHub
   (ลาก-วางไฟล์/โฟลเดอร์ทั้งหมด แล้วกด Commit) หรือผ่าน git:

   ```bash
   git init
   git add .
   git commit -m "Initial WT-80B printer app"
   git branch -M main
   git remote add origin https://github.com/<username>/wt80b-printer-app.git
   git push -u origin main
   ```

3. เมื่อ push ขึ้น branch `main` แล้ว ให้ไปที่แท็บ **Actions** ของ repository
   บน GitHub — workflow ชื่อ "Build APK" จะรันอัตโนมัติ (ใช้เวลาประมาณ 3-6 นาที)
   หากไม่ได้รันเอง กด **Run workflow** ได้จากแท็บ Actions เช่นกัน
4. เมื่อรันเสร็จ (เครื่องหมายถูกสีเขียว) ให้กดเข้าไปดูรายละเอียดของ run นั้น
   แล้วเลื่อนลงไปที่หัวข้อ **Artifacts** จะมีไฟล์ `wt80b-printer-debug-apk`
   ให้ดาวน์โหลด (เป็น .zip ที่มี .apk อยู่ข้างใน)
5. โอนไฟล์ .apk ไปยังโทรศัพท์ Android แล้วเปิดติดตั้ง (อาจต้องอนุญาต
   "ติดตั้งจากแหล่งที่ไม่รู้จัก" ในตั้งค่าเครื่อง)

## วิธีใช้งานแอป

1. เปิด Bluetooth ของโทรศัพท์ แล้วไปที่ตั้งค่า Bluetooth ของระบบ
   จับคู่ (Pair) กับเครื่องพิมพ์ WT-80B ก่อน (ต้องทำขั้นตอนนี้นอกแอปครั้งแรก)
2. เปิดแอป กด "โหลดรายชื่ออุปกรณ์ที่จับคู่" แล้วเลือกเครื่องพิมพ์จากรายการ
3. กด "เชื่อมต่อเครื่องพิมพ์" รอจนสถานะขึ้นว่า "เชื่อมต่อแล้ว"
4. พิมพ์ข้อความในช่อง แล้วกด "พิมพ์ข้อความนี้" หรือทดลองกด
   "พิมพ์ใบเสร็จตัวอย่าง" / "พิมพ์บาร์โค้ด"

## หมายเหตุทางเทคนิค

- ค่าเริ่มต้นตั้งความกว้างกระดาษไว้ที่ 576 dots (เทียบเท่าประมาณ 72mm
  พื้นที่พิมพ์บนกระดาษ 80mm ที่ 203dpi) ตรงกับสเปกของ WT-80B
  ถ้าคุณใช้กระดาษ/เครื่องรุ่นแคบกว่า (58mm) ให้แก้ค่า `printWidthDots`
  ใน `MainActivity.kt` เป็น `384`
- ข้อความจะถูก render เป็นภาพ (bitmap) แล้วส่งเป็นคำสั่ง ESC/POS แบบ
  raster image (`GS v 0`) แทนการส่ง text ตรง ๆ เพื่อให้พิมพ์ภาษาไทยได้
  แน่นอนโดยไม่ต้องพึ่งพา code page ภายในเครื่อง
- บาร์โค้ดใช้มาตรฐาน CODE128 (code set B) ผ่านคำสั่ง `GS k`
- การเชื่อมต่อใช้ Bluetooth Classic SPP UUID มาตรฐาน
  (`00001101-0000-1000-8000-00805F9B34FB`) ซึ่งเป็น UUID ที่เครื่องพิมพ์
  ความร้อนแบบ ESC/POS เกือบทุกยี่ห้อใช้ร่วมกัน
- หากเครื่องพิมพ์ของคุณมีคำสั่ง auto-cut ที่รองรับ (ตรวจสอบสเปกเครื่อง)
  สามารถเพิ่มคำสั่งตัดกระดาษ `GS V` ต่อท้ายใน `EscPosBuilder.kt` ได้เอง

## โครงสร้างไฟล์หลัก

```
app/src/main/java/com/example/wt80bprinter/
 ├─ MainActivity.kt              UI หลัก + render ข้อความเป็นภาพ
 ├─ BluetoothPrinterManager.kt   จัดการเชื่อมต่อ Bluetooth SPP
 └─ EscPosBuilder.kt             สร้างคำสั่ง ESC/POS (ภาพ/บาร์โค้ด/feed)
.github/workflows/build-apk.yml Workflow build APK อัตโนมัติบน GitHub
```

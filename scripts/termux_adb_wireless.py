#!/usr/bin/env python3
"""
ADB Wireless Debugging Pair & Connect via QR Code & Zeroconf
Works seamlessly with Android 11+ Developer Options -> Wireless debugging
Uses termux-adb without root permissions.
"""

import os
import sys
import time
import shutil
import random
import subprocess
import urllib.parse
from zeroconf import Zeroconf, ServiceBrowser, ServiceListener, IPVersion, ServiceInfo
from qrcode import QRCode

ADB_BIN = shutil.which("termux-adb") or shutil.which("adb") or "adb"
SERVICE_NAME = "termux-adb"
PAIRING_TYPE = "_adb-tls-pairing._tcp.local."
CONNECT_TYPE = "_adb-tls-connect._tcp.local."
APK_PATH = "/sdcard/Download/MyTube-arm64-v8a.apk"

class WirelessAdbHandler(ServiceListener):
    def __init__(self, password):
        self.password = str(password)
        self.paired = False
        self.connected = False
        self.target_ip = None

    def add_service(self, zc: Zeroconf, type_: str, name: str) -> None:
        info: ServiceInfo = zc.get_service_info(type_, name)
        if not info:
            return

        ip_addresses = info.ip_addresses_by_version(IPVersion.All)
        if not ip_addresses:
            return
        ip = ip_addresses[0].exploded
        port = info.port

        if type_ == PAIRING_TYPE and not self.paired:
            print(f"\n[+] Phát hiện yêu cầu ghép nối từ thiết bị: {ip}:{port}")
            pair_cmd = [ADB_BIN, "pair", f"{ip}:{port}", self.password]
            print(f"[*] Đang thực thi: {' '.join(pair_cmd)}")
            res = subprocess.run(pair_cmd, capture_output=True, text=True)
            print(res.stdout)
            if res.returncode == 0 and ("Successfully paired" in res.stdout or "paired" in res.stdout.lower()):
                self.paired = True
                self.target_ip = ip
                print(f"[✓] Ghép nối ADB thành công với {ip}!")
            else:
                if res.stderr:
                    print(f"[-] Lỗi: {res.stderr}")

        elif type_ == CONNECT_TYPE and not self.connected:
            print(f"\n[+] Phát hiện cổng kết nối ADB: {ip}:{port}")
            time.sleep(1) # chờ device khởi động daemon
            conn_cmd = [ADB_BIN, "connect", f"{ip}:{port}"]
            print(f"[*] Đang kết nối: {' '.join(conn_cmd)}")
            res = subprocess.run(conn_cmd, capture_output=True, text=True)
            print(res.stdout)
            if "connected" in res.stdout.lower():
                self.connected = True
                print(f"[✓] KẾT NỐI ADB THÀNH CÔNG TỚI THIẾT BỊ {ip}:{port}!")
                self.post_connect_actions()

    def update_service(self, zc: Zeroconf, type_: str, name: str) -> None:
        pass

    def remove_service(self, zc: Zeroconf, type_: str, name: str) -> None:
        pass

    def post_connect_actions(self):
        print("\n" + "="*50)
        print("🎉 THIẾT BỊ ĐÃ KẾT NỐI ADB SẴN SÀNG!")
        print("="*50)
        # Check devices
        dev = subprocess.run([ADB_BIN, "devices"], capture_output=True, text=True)
        print(dev.stdout)

        if os.path.exists(APK_PATH):
            print(f"\n[+] Tìm thấy file APK: {APK_PATH}")
            print(f"[*] Bạn có thể cài đặt ngay bằng lệnh:")
            print(f"    {ADB_BIN} install -r {APK_PATH}")
            print(f"[*] Đọc logcat trực tiếp:")
            print(f"    {ADB_BIN} logcat -v time | grep -i vn.lobie.mytube")
        print("="*50)

def main():
    password = random.randint(100000, 999999)
    payload = f"WIFI:T:ADB;S:{SERVICE_NAME};P:{password};;"

    print("=" * 60)
    print("🚀 ADB WIRELESS DEBUGGING QR PAIRING (Termux-ADB)")
    print("=" * 60)
    print(f"Mã ghép nối (Pairing Code): {password}")
    print(f"Payload QR: {payload}")
    print("\n👉 Hướng dẫn trên điện thoại:")
    print("1. Mở Cài đặt (Settings) -> Tùy chọn cho nhà phát triển (Developer options).")
    print("2. Bật 'Gỡ lỗi qua Wi-Fi' (Wireless debugging).")
    print("3. Nhấn vào 'Ghép nối thiết bị bằng mã QR' (Pair device with QR code).")
    print("4. Quét mã QR bên dưới:")
    print("-" * 60)

    qr = QRCode()
    qr.add_data(payload)
    qr.make(fit=True)
    qr.print_ascii(invert=True)

    print("-" * 60)
    encoded_payload = urllib.parse.quote(payload)
    qr_image_url = f"https://api.qrserver.com/v1/create-qr-code/?size=300x300&data={encoded_payload}"
    print(f"Link ảnh QR trực tiếp: {qr_image_url}")
    print("Đang lắng nghe mDNS từ điện thoại để tự động ghép nối và kết nối...")

    zc = Zeroconf()
    handler = WirelessAdbHandler(password)
    browser_pair = ServiceBrowser(zc, PAIRING_TYPE, handler)
    browser_conn = ServiceBrowser(zc, CONNECT_TYPE, handler)

    try:
        while not handler.connected:
            time.sleep(1)
        print("\nGiữ tiến trình chạy nền. Nhấn Ctrl+C để thoát.")
        while True:
            time.sleep(5)
    except KeyboardInterrupt:
        print("\nĐang dừng...")
    finally:
        zc.close()

if __name__ == "__main__":
    main()

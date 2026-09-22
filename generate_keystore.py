#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
MyTube - Keystore Generator & GitHub Secret Converter
Tự động tạo Release Keystore, sinh Base64 Secret và cập nhật file .env cho dự án MyTube.
"""

import os
import sys
import shutil
import base64
import subprocess
import getpass
import argparse

KEYSTORE_DEFAULT_NAME = "mytube-release.jks"
ENV_FILE = ".env"
BASE64_FILE = "keystore_base64.txt"

def find_keytool():
    """Tìm đường dẫn thực thi của keytool trong hệ thống."""
    cmd = shutil.which("keytool")
    if cmd:
        return cmd
    
    # Kiểm tra biến môi trường JAVA_HOME
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        candidate = os.path.join(java_home, "bin", "keytool")
        if os.path.isfile(candidate) and os.access(candidate, os.X_OK):
            return candidate
            
    # Các đường dẫn phổ biến trên Linux/Termux/macOS
    common_paths = [
        "/data/data/com.termux/files/usr/bin/keytool",
        "/usr/bin/keytool",
        "/usr/local/bin/keytool",
    ]
    for p in common_paths:
        if os.path.isfile(p) and os.access(p, os.X_OK):
            return p
            
    return None

def update_env_file(keystore_path, alias, password, b64_str):
    """Cập nhật hoặc tạo file .env với thông tin keystore vừa tạo."""
    env_content = f"""# ==============================================================================
# MyTube Release Signing Keystore Configuration
# (Tự động sinh bởi generate_keystore.py - Không commit file này lên Git)
# ==============================================================================

KEYSTORE_FILE={keystore_path}
KEYSTORE_PASSWORD={password}
KEY_ALIAS={alias}
KEY_PASSWORD={password}
RELEASE_KEYSTORE_BASE64={b64_str}
"""
    with open(ENV_FILE, "w", encoding="utf-8") as f:
        f.write(env_content)
    print(f"✅ Đã tự động cập nhật cấu hình vào file: {os.path.abspath(ENV_FILE)}")

def main():
    parser = argparse.ArgumentParser(description="Tạo Release Keystore cho MyTube và sinh GitHub Secrets.")
    parser.add_argument("-a", "--alias", help="Tên Key Alias (mặc định: mytube_release)")
    parser.add_argument("-p", "--password", help="Mật khẩu Keystore và Key Alias")
    parser.add_argument("-o", "--output", default=KEYSTORE_DEFAULT_NAME, help=f"Tên file keystore đầu ra (mặc định: {KEYSTORE_DEFAULT_NAME})")
    args = parser.parse_args()

    print("=" * 65)
    print("      🚀 CÔNG CỤ TẠO RELEASE KEYSTORE & SECRET CHO MYTUBE 🚀")
    print("=" * 65)

    keytool_bin = find_keytool()
    if not keytool_bin:
        print("❌ LỖI: Không tìm thấy lệnh 'keytool' trong hệ thống!")
        print("💡 Gợi ý cài đặt:")
        print("   - Trên Termux: chạy lệnh: pkg install openjdk-17")
        print("   - Trên Ubuntu/Debian: chạy lệnh: sudo apt install openjdk-17-jre-headless")
        sys.exit(1)

    # 1. Nhập Key Alias
    alias = args.alias
    if not alias:
        default_alias = "mytube_release"
        user_alias = input(f"👉 Nhập Key Alias mong muốn [mặc định: {default_alias}]: ").strip()
        alias = user_alias if user_alias else default_alias

    # 2. Nhập Mật khẩu
    password = args.password
    if not password:
        while True:
            pwd1 = getpass.getpass("👉 Nhập Mật khẩu mong muốn (tối thiểu 6 ký tự): ")
            if len(pwd1) < 6:
                print("⚠️  Mật khẩu quá ngắn, keytool yêu cầu ít nhất 6 ký tự. Vui lòng nhập lại!")
                continue
            pwd2 = getpass.getpass("👉 Xác nhận lại mật khẩu: ")
            if pwd1 != pwd2:
                print("❌ Mật khẩu xác nhận không khớp! Vui lòng nhập lại.")
                continue
            password = pwd1
            break
    elif len(password) < 6:
        print("❌ Mật khẩu cung cấp qua tham số quá ngắn (tối thiểu 6 ký tự).")
        sys.exit(1)

    keystore_file = args.output
    if os.path.exists(keystore_file):
        ans = input(f"⚠️  File '{keystore_file}' đã tồn tại! Bạn có muốn ghi đè không? (y/N): ").strip().lower()
        if ans != 'y':
            print("Đã hủy thao tác.")
            sys.exit(0)
        os.remove(keystore_file)

    print("\n⏳ Đang khởi tạo Keystore bằng keytool...")

    # Chạy lệnh keytool tạo cặp khóa RSA 2048-bit thời hạn 10000 ngày (~27 năm)
    dname = "CN=MyTube, OU=Release, O=Lobie, C=VN"
    cmd = [
        keytool_bin,
        "-genkeypair",
        "-v",
        "-keystore", keystore_file,
        "-alias", alias,
        "-keyalg", "RSA",
        "-keysize", "2048",
        "-validity", "10000",
        "-storepass", password,
        "-keypass", password,
        "-dname", dname
    ]

    try:
        res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, check=True)
    except subprocess.CalledProcessError as e:
        print("❌ Tạo Keystore thất bại:")
        print(e.stderr)
        sys.exit(1)

    print(f"🎉 Tạo Keystore thành công: {os.path.abspath(keystore_file)}")

    # 3. Đọc và chuyển sang Base64
    with open(keystore_file, "rb") as f:
        b64_content = base64.b64encode(f.read()).decode("utf-8")

    # Lưu bản sao Base64 ra file txt (đã có trong .gitignore)
    with open(BASE64_FILE, "w", encoding="utf-8") as f:
        f.write(b64_content)
    print(f"📄 Đã lưu chuỗi Base64 ra file tạm: {BASE64_FILE}")

    # 4. Tự động ghi vào file .env
    update_env_file(keystore_file, alias, password, b64_content)

    # 5. Hướng dẫn chi tiết đưa lên GitHub Secrets
    print("\n" + "=" * 65)
    print("  📋 THÔNG TIN ĐỂ ĐIỀN VÀO GITHUB REPOSITORY SECRETS")
    print("=" * 65)
    print("1. Truy cập GitHub Repo của bạn:")
    print("   👉 Settings > Secrets and variables > Actions > New repository secret\n")
    print("2. Tạo lần lượt 4 Secret sau:\n")
    print(f"   🔹 Secret: KEYSTORE_PASSWORD")
    print(f"      Giá trị: {password}\n")
    print(f"   🔹 Secret: KEY_ALIAS")
    print(f"      Giá trị: {alias}\n")
    print(f"   🔹 Secret: KEY_PASSWORD")
    print(f"      Giá trị: {password}\n")
    print(f"   🔹 Secret: RELEASE_KEYSTORE_BASE64")
    print(f"      Giá trị: (Đã lưu trong file '{BASE64_FILE}', mở file copy toàn bộ dán vào)")
    print("=" * 65)
    print("💡 Lưu ý an toàn: Giữ file keystore này cẩn thận, không xoá hay làm mất nhé!")
    print("=" * 65 + "\n")

if __name__ == "__main__":
    main()

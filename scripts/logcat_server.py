#!/usr/bin/env python3
import http.server
import json
import os
import re
import socket
import subprocess
import sys
import threading
import time
import urllib.parse

PORT = 8765
PACKAGE_NAME = "vn.lobie.mytube"

HTML_TEMPLATE = r"""<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>MyTube Live Logcat & Debugger</title>
    <style>
        :root {
            --bg-color: #0d1117;
            --panel-bg: #161b22;
            --border-color: #30363d;
            --text-color: #c9d1d9;
            --text-muted: #8b949e;
            --color-v: #8b949e;
            --color-d: #58a6ff;
            --color-i: #3fb950;
            --color-w: #d29922;
            --color-e: #f85149;
            --color-f: #ff7b72;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, monospace;
            background: var(--bg-color);
            color: var(--text-color);
            display: flex;
            flex-direction: column;
            height: 100vh;
            overflow: hidden;
        }
        header {
            background: var(--panel-bg);
            border-bottom: 1px solid var(--border-color);
            padding: 10px 16px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            flex-wrap: wrap;
            gap: 10px;
        }
        .title-group {
            display: flex;
            align-items: center;
            gap: 10px;
        }
        .badge {
            background: #ff0000;
            color: #fff;
            padding: 3px 8px;
            border-radius: 4px;
            font-size: 11px;
            font-weight: bold;
            letter-spacing: 0.5px;
        }
        .status-dot {
            width: 10px;
            height: 10px;
            border-radius: 50%;
            background: #3fb950;
            display: inline-block;
            box-shadow: 0 0 8px #3fb950;
        }
        .status-dot.disconnected {
            background: #f85149;
            box-shadow: 0 0 8px #f85149;
        }
        .controls {
            display: flex;
            align-items: center;
            gap: 8px;
            flex-wrap: wrap;
        }
        input, select, button {
            background: #21262d;
            color: var(--text-color);
            border: 1px solid var(--border-color);
            padding: 6px 12px;
            border-radius: 6px;
            font-size: 13px;
            outline: none;
        }
        input:focus, select:focus {
            border-color: #58a6ff;
        }
        button {
            cursor: pointer;
            transition: background 0.15s;
        }
        button:hover {
            background: #30363d;
        }
        button.active {
            background: #238636;
            border-color: #2ea043;
            color: white;
        }
        #log-container {
            flex: 1;
            overflow-y: auto;
            padding: 8px;
            font-family: 'JetBrains Mono', 'Fira Code', 'Courier New', monospace;
            font-size: 12px;
            line-height: 1.5;
            background: #090d13;
        }
        .log-line {
            display: flex;
            padding: 2px 6px;
            border-radius: 3px;
            white-space: pre-wrap;
            word-break: break-all;
        }
        .log-line:hover {
            background: rgba(255,255,255,0.05);
        }
        .log-time { color: var(--text-muted); margin-right: 8px; flex-shrink: 0; }
        .log-level {
            width: 20px;
            text-align: center;
            font-weight: bold;
            margin-right: 8px;
            flex-shrink: 0;
            border-radius: 3px;
        }
        .level-V { color: var(--color-v); }
        .level-D { color: var(--color-d); }
        .level-I { color: var(--color-i); }
        .level-W { color: var(--color-w); background: rgba(210, 153, 34, 0.1); }
        .level-E { color: var(--color-e); background: rgba(248, 81, 73, 0.15); font-weight: bold; }
        .level-F { color: var(--color-f); background: rgba(248, 81, 73, 0.3); font-weight: bold; }
        .log-tag { color: #f0883e; margin-right: 8px; flex-shrink: 0; font-weight: 500; }
        .log-msg { flex: 1; color: var(--text-color); }
        .log-line.mytube { background: rgba(88, 166, 255, 0.08); border-left: 2px solid #58a6ff; }
        footer {
            background: var(--panel-bg);
            border-top: 1px solid var(--border-color);
            padding: 6px 16px;
            font-size: 11px;
            color: var(--text-muted);
            display: flex;
            justify-content: space-between;
        }
    </style>
</head>
<body>
    <header>
        <div class="title-group">
            <span class="status-dot" id="status-dot"></span>
            <strong>MyTube Logcat Stream</strong>
            <span class="badge">LIVE</span>
        </div>
        <div class="controls">
            <label style="display: flex; align-items: center; gap: 4px; font-size: 12px; cursor: pointer;">
                <input type="checkbox" id="only-mytube" checked>
                <span>Chỉ MyTube</span>
            </label>
            <select id="level-filter">
                <option value="ALL">Tất cả Levels</option>
                <option value="D">Debug+ (D, I, W, E)</option>
                <option value="I">Info+ (I, W, E)</option>
                <option value="W">Warn+ (W, E)</option>
                <option value="E">Chỉ Error (E, F)</option>
            </select>
            <input type="text" id="text-filter" placeholder="Tìm kiếm / Regex tag, msg..." style="width: 200px;">
            <button id="btn-autoscroll" class="active">Cuộn tự động: BẬT</button>
            <button id="btn-clear">Xóa màn hình</button>
            <button id="btn-clear-device">Xóa Logcat máy</button>
        </div>
    </header>

    <div id="log-container"></div>

    <footer>
        <span id="log-count">Đã nhận: 0 dòng log</span>
        <span>MyTube Android Debug Console</span>
    </footer>

    <script>
        const container = document.getElementById('log-container');
        const statusDot = document.getElementById('status-dot');
        const onlyMyTube = document.getElementById('only-mytube');
        const levelFilter = document.getElementById('level-filter');
        const textFilter = document.getElementById('text-filter');
        const btnAutoscroll = document.getElementById('btn-autoscroll');
        const btnClear = document.getElementById('btn-clear');
        const btnClearDevice = document.getElementById('btn-clear-device');
        const logCountEl = document.getElementById('log-count');

        let autoScroll = true;
        let totalCount = 0;
        const allLogs = [];
        const MAX_DOM_NODES = 2000;

        btnAutoscroll.addEventListener('click', () => {
            autoScroll = !autoScroll;
            btnAutoscroll.textContent = `Cuộn tự động: ${autoScroll ? 'BẬT' : 'TẮT'}`;
            btnAutoscroll.classList.toggle('active', autoScroll);
        });

        btnClear.addEventListener('click', () => {
            container.innerHTML = '';
            allLogs.length = 0;
            totalCount = 0;
            updateCount();
        });

        btnClearDevice.addEventListener('click', async () => {
            await fetch('/clear', { method: 'POST' });
            btnClear.click();
        });

        function updateCount() {
            logCountEl.textContent = `Đã nhận: ${totalCount} dòng log (đang hiển thị: ${container.childElementCount})`;
        }

        function matchFilter(item) {
            const isMyTube = item.raw.toLowerCase().includes('mytube') || item.raw.toLowerCase().includes('vn.lobie.mytube');
            if (onlyMyTube.checked && !isMyTube) return false;

            const lvl = levelFilter.value;
            if (lvl === 'D' && !['D','I','W','E','F'].includes(item.level)) return false;
            if (lvl === 'I' && !['I','W','E','F'].includes(item.level)) return false;
            if (lvl === 'W' && !['W','E','F'].includes(item.level)) return false;
            if (lvl === 'E' && !['E','F'].includes(item.level)) return false;

            const query = textFilter.value.trim().toLowerCase();
            if (query && !item.raw.toLowerCase().includes(query)) return false;

            return true;
        }

        function reapplyFilter() {
            container.innerHTML = '';
            for (const item of allLogs) {
                if (matchFilter(item)) {
                    renderLog(item);
                }
            }
            if (autoScroll) container.scrollTop = container.scrollHeight;
            updateCount();
        }

        onlyMyTube.addEventListener('change', reapplyFilter);
        levelFilter.addEventListener('change', reapplyFilter);
        textFilter.addEventListener('input', reapplyFilter);

        function parseLog(raw) {
            // e.g. 09-20 19:35:12.345  1234  1234 D MyTubeTag: message
            const match = raw.match(/^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+(\d+)\s+(\d+)\s+([VDIWEF])\s+([^:]+):\s*(.*)$/);
            if (match) {
                return {
                    raw,
                    time: match[1],
                    pid: match[2],
                    tid: match[3],
                    level: match[4],
                    tag: match[5].trim(),
                    msg: match[6]
                };
            }
            return { raw, level: 'I', time: '', tag: '', msg: raw };
        }

        function renderLog(item) {
            const isMyTube = item.raw.toLowerCase().includes('mytube') || item.raw.toLowerCase().includes('vn.lobie.mytube');
            const row = document.createElement('div');
            row.className = `log-line level-${item.level} ${isMyTube ? 'mytube' : ''}`;

            if (item.time) {
                const timeSpan = document.createElement('span');
                timeSpan.className = 'log-time';
                timeSpan.textContent = item.time.split(' ')[1] || item.time;
                row.appendChild(timeSpan);
            }

            const levelSpan = document.createElement('span');
            levelSpan.className = `log-level level-${item.level}`;
            levelSpan.textContent = item.level;
            row.appendChild(levelSpan);

            if (item.tag) {
                const tagSpan = document.createElement('span');
                tagSpan.className = 'log-tag';
                tagSpan.textContent = item.tag + ':';
                row.appendChild(tagSpan);
            }

            const msgSpan = document.createElement('span');
            msgSpan.className = 'log-msg';
            msgSpan.textContent = item.msg;
            row.appendChild(msgSpan);

            container.appendChild(row);

            while (container.childElementCount > MAX_DOM_NODES) {
                container.removeChild(container.firstChild);
            }
        }

        function connectSSE() {
            const es = new EventSource('/stream');
            es.onopen = () => {
                statusDot.classList.remove('disconnected');
            };
            es.onerror = () => {
                statusDot.classList.add('disconnected');
                es.close();
                setTimeout(connectSSE, 2000);
            };
            es.onmessage = (e) => {
                totalCount++;
                const item = parseLog(e.data);
                allLogs.push(item);
                if (allLogs.length > 5000) allLogs.shift();

                if (matchFilter(item)) {
                    renderLog(item);
                    if (autoScroll) {
                        container.scrollTop = container.scrollHeight;
                    }
                }
                updateCount();
            };
        }

        connectSSE();
    </script>
</body>
</html>
"""

def get_lan_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(('8.8.8.8', 80))
        return s.getsockname()[0]
    except Exception:
        return '127.0.0.1'
    finally:
        s.close()

class LogcatHandler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        if parsed.path in ('', '/'):
            self.send_response(200)
            self.send_header('Content-Type', 'text/html; charset=utf-8')
            self.end_headers()
            self.wfile.write(HTML_TEMPLATE.encode('utf-8'))
        elif parsed.path == '/stream':
            self.send_response(200)
            self.send_header('Content-Type', 'text/event-stream')
            self.send_header('Cache-Control', 'no-cache')
            self.send_header('Connection', 'keep-alive')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()

            # Stream logcat output
            cmd = ["logcat", "-v", "time"]
            proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
            try:
                for line in proc.stdout:
                    clean_line = line.rstrip('\r\n')
                    if clean_line:
                        msg = f"data: {clean_line}\n\n"
                        self.wfile.write(msg.encode('utf-8'))
                        self.wfile.flush()
            except (BrokenPipeError, ConnectionResetError):
                pass
            finally:
                proc.terminate()
        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        if self.path == '/clear':
            subprocess.run(["logcat", "-c"], check=False)
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(b'{"status":"cleared"}')
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, format, *args):
        # Silence standard HTTP access logging to keep console clean
        pass

def print_qr(url):
    print("=" * 60)
    print(f"🚀 MyTube Live Logcat & Debugger Server")
    print(f"📱 Local URL: {url}")
    print(f"💻 Loopback:  http://localhost:{PORT}")
    print("=" * 60)
    print("Quét mã QR dưới đây để mở giao diện Debug trên điện thoại hoặc trình duyệt:")
    try:
        qr = subprocess.run(["curl", "-s", f"https://qrenco.de/{url}"], capture_output=True, text=True, timeout=5)
        if qr.stdout:
            print(qr.stdout)
    except Exception as e:
        print(f"Mở link trực tiếp: {url}")
    print("=" * 60)

def main():
    lan_ip = get_lan_ip()
    url = f"http://{lan_ip}:{PORT}"
    print_qr(url)
    server = http.server.ThreadingHTTPServer(('0.0.0.0', PORT), LogcatHandler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nĐã dừng server logcat.")

if __name__ == '__main__':
    main()

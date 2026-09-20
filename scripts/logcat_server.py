#!/usr/bin/env python3
"""
MyTube High-Performance Web Logcat & Debugger
Direct ADB Wireless streaming with automatic PID detection for vn.lobie.mytube
"""

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
ADB_BIN = "/data/data/com.termux/files/usr/bin/termux-adb"

def get_lan_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(('8.8.8.8', 80))
        return s.getsockname()[0]
    except Exception:
        return '127.0.0.1'
    finally:
        s.close()

def get_connected_device():
    try:
        out = subprocess.run([ADB_BIN, "devices"], capture_output=True, text=True, timeout=2).stdout
        for line in out.splitlines():
            line = line.strip()
            if "\tdevice" in line:
                return line.split("\t")[0]
    except Exception:
        pass
    return None

def get_mytube_pid():
    dev = get_connected_device()
    cmd = [ADB_BIN, "-s", dev, "shell", "pidof", PACKAGE_NAME] if dev else [ADB_BIN, "shell", "pidof", PACKAGE_NAME]
    try:
        out = subprocess.run(cmd, capture_output=True, text=True, timeout=2).stdout.strip()
        if out and out.isdigit():
            return out
        # Sometimes multiple pids are returned
        pids = out.split()
        if pids and pids[0].isdigit():
            return pids[0]
    except Exception:
        pass
    return None

HTML_TEMPLATE = r"""<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>MyTube ADB Live Logcat</title>
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
            background: #e50914;
            color: #fff;
            padding: 3px 8px;
            border-radius: 4px;
            font-size: 11px;
            font-weight: bold;
            letter-spacing: 0.5px;
        }
        .pid-badge {
            background: #238636;
            color: #fff;
            padding: 3px 8px;
            border-radius: 4px;
            font-size: 11px;
            font-weight: 600;
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
        select, input, button {
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
        button:hover { background: #30363d; }
        button.active {
            background: #238636;
            border-color: #2ea043;
            color: white;
        }
        #mode-select {
            font-weight: 600;
            color: #58a6ff;
            border-color: #58a6ff;
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
        .log-line:hover { background: rgba(255,255,255,0.05); }
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
        .level-W { color: var(--color-w); background: rgba(210, 153, 34, 0.15); }
        .level-E { color: var(--color-e); background: rgba(248, 81, 73, 0.2); font-weight: bold; }
        .level-F { color: var(--color-f); background: rgba(248, 81, 73, 0.35); font-weight: bold; }
        .log-tag { color: #f0883e; margin-right: 8px; flex-shrink: 0; font-weight: 600; }
        .log-msg { flex: 1; color: var(--text-color); }
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
            <strong>MyTube ADB Debugger</strong>
            <span class="badge">LIVE ADB</span>
            <span class="pid-badge" id="pid-display">Đang quét PID...</span>
        </div>
        <div class="controls">
            <select id="mode-select">
                <option value="mytube" selected>⭐ Chỉ riêng App MyTube</option>
                <option value="all">Toàn bộ thiết bị</option>
            </select>
            <select id="level-filter">
                <option value="ALL">Tất cả Levels</option>
                <option value="D">Debug+ (D, I, W, E)</option>
                <option value="I">Info+ (I, W, E)</option>
                <option value="W">Warn+ (W, E)</option>
                <option value="E">Chỉ Error (E, F)</option>
            </select>
            <input type="text" id="text-filter" placeholder="Tìm kiếm tag, msg..." style="width: 180px;">
            <button id="btn-autoscroll" class="active">Cuộn: BẬT</button>
            <button id="btn-clear">Xóa màn hình</button>
            <button id="btn-reconnect">Làm mới kết nối</button>
        </div>
    </header>

    <div id="log-container"></div>

    <footer>
        <span id="log-count">Đã nhận: 0 dòng log</span>
        <span id="device-info">ADB Wireless</span>
    </footer>

    <script>
        const container = document.getElementById('log-container');
        const statusDot = document.getElementById('status-dot');
        const modeSelect = document.getElementById('mode-select');
        const levelFilter = document.getElementById('level-filter');
        const textFilter = document.getElementById('text-filter');
        const btnAutoscroll = document.getElementById('btn-autoscroll');
        const btnClear = document.getElementById('btn-clear');
        const btnReconnect = document.getElementById('btn-reconnect');
        const logCountEl = document.getElementById('log-count');
        const pidDisplay = document.getElementById('pid-display');
        const deviceInfo = document.getElementById('device-info');

        let autoScroll = true;
        let totalCount = 0;
        const allLogs = [];
        const MAX_DOM = 3000;
        let eventSource = null;

        btnAutoscroll.addEventListener('click', () => {
            autoScroll = !autoScroll;
            btnAutoscroll.textContent = `Cuộn: ${autoScroll ? 'BẬT' : 'TẮT'}`;
            btnAutoscroll.classList.toggle('active', autoScroll);
        });

        btnClear.addEventListener('click', () => {
            container.innerHTML = '';
            allLogs.length = 0;
            totalCount = 0;
            updateCount();
        });

        btnReconnect.addEventListener('click', () => {
            connectSSE();
        });

        modeSelect.addEventListener('change', () => {
            connectSSE();
        });

        function updateCount() {
            logCountEl.textContent = `Đã nhận: ${totalCount} dòng log (đang hiển thị: ${container.childElementCount})`;
        }

        function matchFilter(item) {
            const lvl = levelFilter.value;
            if (lvl === 'D' && !['D','I','W','E','F'].includes(item.level)) return false;
            if (lvl === 'I' && !['I','W','E','F'].includes(item.level)) return false;
            if (lvl === 'W' && !['W','E','F'].includes(item.level)) return false;
            if (lvl === 'E' && !['E','F'].includes(item.level)) return false;

            const q = textFilter.value.trim().toLowerCase();
            if (q && !item.raw.toLowerCase().includes(q)) return false;

            return true;
        }

        function reapplyFilter() {
            container.innerHTML = '';
            for (const item of allLogs) {
                if (matchFilter(item)) renderLog(item);
            }
            if (autoScroll) container.scrollTop = container.scrollHeight;
            updateCount();
        }

        levelFilter.addEventListener('change', reapplyFilter);
        textFilter.addEventListener('input', reapplyFilter);

        function parseLog(raw) {
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
            const row = document.createElement('div');
            row.className = `log-line level-${item.level}`;

            if (item.time) {
                const s = document.createElement('span');
                s.className = 'log-time';
                s.textContent = item.time.split(' ')[1] || item.time;
                row.appendChild(s);
            }

            const lvl = document.createElement('span');
            lvl.className = `log-level level-${item.level}`;
            lvl.textContent = item.level;
            row.appendChild(lvl);

            if (item.tag) {
                const tag = document.createElement('span');
                tag.className = 'log-tag';
                tag.textContent = item.tag + ':';
                row.appendChild(tag);
            }

            const msg = document.createElement('span');
            msg.className = 'log-msg';
            msg.textContent = item.msg;
            row.appendChild(msg);

            container.appendChild(row);
            while (container.childElementCount > MAX_DOM) {
                container.removeChild(container.firstChild);
            }
        }

        function connectSSE() {
            if (eventSource) {
                eventSource.close();
            }
            btnClear.click();

            const mode = modeSelect.value;
            const url = `/stream?mode=${mode}`;
            eventSource = new EventSource(url);

            eventSource.onopen = () => {
                statusDot.classList.remove('disconnected');
            };

            eventSource.onerror = () => {
                statusDot.classList.add('disconnected');
                eventSource.close();
                setTimeout(connectSSE, 3000);
            };

            eventSource.addEventListener('status', (e) => {
                try {
                    const data = JSON.parse(e.data);
                    if (data.pid) {
                        pidDisplay.textContent = `MyTube PID: ${data.pid}`;
                        pidDisplay.style.background = '#238636';
                    } else {
                        pidDisplay.textContent = 'Chưa thấy PID (App chưa mở)';
                        pidDisplay.style.background = '#8957e5';
                    }
                    if (data.device) {
                        deviceInfo.textContent = `ADB: ${data.device}`;
                    }
                } catch(err) {}
            });

            eventSource.onmessage = (e) => {
                totalCount++;
                const item = parseLog(e.data);
                allLogs.push(item);
                if (allLogs.length > 5000) allLogs.shift();

                if (matchFilter(item)) {
                    renderLog(item);
                    if (autoScroll) container.scrollTop = container.scrollHeight;
                }
                updateCount();
            };
        }

        connectSSE();
    </script>
</body>
</html>
"""

class LogcatHandler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        if parsed.path in ('', '/'):
            self.send_response(200)
            self.send_header('Content-Type', 'text/html; charset=utf-8')
            self.end_headers()
            self.wfile.write(HTML_TEMPLATE.encode('utf-8'))
        elif parsed.path == '/stream':
            query = urllib.parse.parse_qs(parsed.query)
            mode = query.get('mode', ['mytube'])[0]

            self.send_response(200)
            self.send_header('Content-Type', 'text/event-stream')
            self.send_header('Cache-Control', 'no-cache')
            self.send_header('Connection', 'keep-alive')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()

            dev = get_connected_device()
            pid = get_mytube_pid()

            # Send initial status event
            status_event = f"event: status\ndata: {json.dumps({'device': dev or 'Localhost', 'pid': pid})}\n\n"
            self.wfile.write(status_event.encode('utf-8'))
            self.wfile.flush()

            # Build ADB command
            cmd = [ADB_BIN]
            if dev:
                cmd.extend(["-s", dev])
            cmd.extend(["logcat", "-v", "time"])

            if mode == 'mytube':
                if pid:
                    cmd.append(f"--pid={pid}")
                else:
                    # If PID not yet found, stream with grep
                    cmd.extend(["-e", PACKAGE_NAME])

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

    def log_message(self, format, *args):
        pass

def main():
    lan_ip = get_lan_ip()
    url = f"http://{lan_ip}:{PORT}"
    print("=" * 60)
    print("🚀 MyTube Live Logcat (ADB Wireless Direct Stream)")
    print(f"📱 Web URL: {url}")
    print(f"💻 Loopback: http://localhost:{PORT}")
    print("=" * 60)

    server = http.server.ThreadingHTTPServer(('0.0.0.0', PORT), LogcatHandler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nĐã dừng server.")

if __name__ == '__main__':
    main()

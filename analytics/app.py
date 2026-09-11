#!/usr/bin/env python3
"""Private, log-based Duo visit statistics. No public collection endpoint."""
import csv
import gzip
import hashlib
import io
import ipaddress
import json
import os
import re
import sqlite3
import threading
import time
from contextlib import contextmanager
from datetime import datetime, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit
from zoneinfo import ZoneInfo

TZ = ZoneInfo('Asia/Shanghai')
PUBLIC = Path(__file__).parent / 'public'
DOWNLOAD = re.compile(r'^/downloads/Foldlight-[0-9.]+-macOS\.dmg$')
BOT = re.compile(r'bot|spider|crawler|headless|curl|wget|python|Go-http-client|DuoAnalyticsVerification', re.I)


def device_info(ua):
    bot = bool(BOT.search(ua))
    if bot:
        device = '机器人'
    elif re.search(r'iPad|Tablet', ua, re.I) or ('Android' in ua and 'Mobile' not in ua):
        device = '平板'
    elif re.search(r'iPhone|iPod|Android.*Mobile|Windows Phone', ua, re.I):
        device = '手机'
    elif re.search(r'Macintosh|Windows NT|X11|CrOS', ua):
        device = '电脑'
    else:
        device = '未知'
    system = '未知'
    for pattern, name in [(r'iPhone|iPad|iPod', 'iOS / iPadOS'), (r'Android', 'Android'),
                          (r'Windows NT', 'Windows'), (r'CrOS', 'ChromeOS'),
                          (r'Macintosh|Mac OS X', 'macOS'), (r'Linux|X11', 'Linux')]:
        if re.search(pattern, ua):
            system = name
            break
    browser = '未知'
    for pattern, name in [(r'Edg(?:e|A|iOS)?/([\d.]+)', 'Edge'), (r'MicroMessenger/([\d.]+)', '微信'),
                          (r'(?:Firefox|FxiOS)/([\d.]+)', 'Firefox'), (r'(?:Chrome|CriOS)/([\d.]+)', 'Chrome'),
                          (r'Version/([\d.]+).*Safari', 'Safari')]:
        match = re.search(pattern, ua)
        if match:
            browser = name + ' ' + match.group(1).split('.')[0]
            break
    return device, system, browser, int(bot)


def normalize(raw, now=None):
    """Only successful document loads and DMG GET requests count, never assets/HEAD."""
    now = time.time() if now is None else now
    try:
        item = json.loads(raw)
        if not isinstance(item, dict):
            return None
        at = float(item['at'])
        path = item['path']
        if item.get('method') != 'GET' or int(item['status']) not in (200, 206):
            return None
        kind = 'page' if path in ('/', '/index.html') else 'download' if DOWNLOAD.fullmatch(path) else None
        if kind is None or not (now - 90*86400 <= at <= now + 60):
            return None
        ip = str(ipaddress.ip_address(item.get('ipv6') or item.get('ip', '')))
        ua = str(item.get('ua', ''))[:384]
        device, system, browser, bot = device_info(ua)
        country = str(item.get('country', '')).upper()
        country = country if re.fullmatch('[A-Z]{2}', country) and country not in ('XX', 'T1') else ''
        referrer = str(item.get('referrer', '')).lower()[:253]
        if not re.fullmatch(r'[a-z0-9.:-]*', referrer):
            referrer = ''
        return (hashlib.sha256(raw).hexdigest(), int(at*1000), ip, kind, path, device,
                system, browser, country, referrer, bot, ua, int(item['status']), max(0, int(item.get('bytes', 0))))
    except (KeyError, TypeError, ValueError, OverflowError):
        return None


class Store:
    def __init__(self, path):
        self.path = str(path)
        self.seen_logs = {}
        with self.connect() as db:
            db.executescript('''
                CREATE TABLE IF NOT EXISTS visits (
                  id INTEGER PRIMARY KEY, event_key TEXT UNIQUE NOT NULL, at INTEGER NOT NULL,
                  ip TEXT NOT NULL, kind TEXT NOT NULL, path TEXT NOT NULL, device TEXT NOT NULL,
                  system TEXT NOT NULL, browser TEXT NOT NULL, country TEXT NOT NULL,
                  referrer TEXT NOT NULL, bot INTEGER NOT NULL, ua TEXT NOT NULL,
                  status INTEGER NOT NULL, bytes INTEGER NOT NULL);
                CREATE INDEX IF NOT EXISTS visits_time ON visits(at);
                CREATE INDEX IF NOT EXISTS visits_ip ON visits(ip,at);
                CREATE TABLE IF NOT EXISTS cursors (source TEXT PRIMARY KEY, offset INTEGER NOT NULL);
                CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY,value TEXT NOT NULL);
            ''')
            db.execute("INSERT OR IGNORE INTO meta VALUES ('started_at',?)", (str(int(time.time()*1000)),))
    @contextmanager
    def connect(self):
        db = sqlite3.connect(self.path, timeout=10)
        db.row_factory = sqlite3.Row
        db.execute('PRAGMA journal_mode=WAL')
        db.execute('PRAGMA busy_timeout=10000')
        try:
            with db:
                yield db
        finally:
            db.close()
    def ingest(self, logfile):
        stat = logfile.stat()
        source = f'{stat.st_dev}:{stat.st_ino}:{"gz" if logfile.suffix == ".gz" else "raw"}'
        signature = (stat.st_size, stat.st_mtime_ns)
        if self.seen_logs.get(source) == signature:
            return
        with self.connect() as db:
            saved = db.execute('SELECT offset FROM cursors WHERE source=?', (source,)).fetchone()
            offset = saved[0] if saved else 0
            if logfile.suffix != '.gz' and offset > stat.st_size:
                offset = 0
            opener = gzip.open if logfile.suffix == '.gz' else open
            with opener(logfile, 'rb') as stream:
                stream.seek(offset)
                finished = False
                for _ in range(2000):
                    start = stream.tell()
                    raw = stream.readline(32769)
                    if not raw:
                        finished = True
                        break
                    if len(raw) > 32768:
                        # Drain malformed oversized lines instead of interpreting fragments.
                        while raw and not raw.endswith(b'\n'):
                            raw = stream.readline(32769)
                        continue
                    if not raw.endswith(b'\n'):
                        stream.seek(start)
                        break
                    row = normalize(raw)
                    if row:
                        db.execute('INSERT OR IGNORE INTO visits(event_key,at,ip,kind,path,device,system,browser,country,referrer,bot,ua,status,bytes) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)', row)
                db.execute('INSERT OR REPLACE INTO cursors VALUES (?,?)', (source, stream.tell()))
        if finished:
            self.seen_logs[source] = signature
        if len(self.seen_logs) > 64:
            self.seen_logs.pop(next(iter(self.seen_logs)))
    def prune(self):
        with self.connect() as db:
            db.execute('DELETE FROM visits WHERE at<?', (int((time.time()-90*86400)*1000),))
            # Bound growth on a small VPS, even under automated traffic.
            db.execute('DELETE FROM visits WHERE id IN (SELECT id FROM visits ORDER BY id DESC LIMIT -1 OFFSET 1000000)')
            db.execute('PRAGMA incremental_vacuum(100)')
    def where(self, query):
        days = int(query.get('days', ['7'])[0])
        if days not in (1, 7, 30, 90):
            raise ValueError('days')
        start = datetime.now(TZ).replace(hour=0, minute=0, second=0, microsecond=0) - timedelta(days=days-1)
        clauses, args = ['at>=?'], [int(start.timestamp()*1000)]
        if query.get('bots', ['0'])[0] != '1':
            clauses.append('bot=0')
        device = query.get('device', ['all'])[0]
        if device != 'all':
            if device not in ('手机', '平板', '电脑', '机器人', '未知'):
                raise ValueError('device')
            clauses.append('device=?'); args.append(device)
        ip = query.get('ip', [''])[0].strip()
        if ip:
            if len(ip)>45 or not re.fullmatch(r'[a-fA-F0-9.:]+', ip):
                raise ValueError('ip')
            clauses.append('ip LIKE ?'); args.append(ip+'%')
        return ' AND '.join(clauses), args, days, start
    def summary(self, query):
        where, args, days, start = self.where(query)
        with self.connect() as db:
            totals = dict(db.execute(f'''SELECT
                SUM(kind='page') AS pageviews,COUNT(DISTINCT CASE WHEN kind='page' THEN ip END) AS unique_ips,
                SUM(kind='download') AS downloads,SUM(bot) AS bots FROM visits WHERE {where}''', args).fetchone())
            for key in totals:
                totals[key] = totals[key] or 0
            granularity = '%Y-%m-%d %H:00' if days == 1 else '%Y-%m-%d'
            trend = [dict(row) for row in db.execute(f"SELECT strftime(?,at/1000,'unixepoch','+8 hours') AS bucket,SUM(kind='page') AS pageviews,SUM(kind='download') AS downloads FROM visits WHERE {where} GROUP BY bucket ORDER BY bucket", [granularity]+args)]
            breakdowns = {}
            for key in ('device','browser','country','referrer'):
                breakdowns[key] = [dict(row) for row in db.execute(f"SELECT {key} AS name,COUNT(*) AS count FROM visits WHERE {where} AND kind='page' GROUP BY {key} ORDER BY count DESC LIMIT 8", args)]
            meta = dict(db.execute('SELECT key,value FROM meta').fetchall())
            return {'totals':totals,'trend':trend,'breakdowns':breakdowns,'days':days,
                    'start':start.isoformat(),'updated_at':int(time.time()*1000),'started_at':int(meta['started_at']),
                    'last_ingest_at':int(meta.get('last_ingest_at','0')),'retention_days':90}
    def records(self, query, export=False):
        where, args, _, _ = self.where(query)
        kind = query.get('kind',['all'])[0]
        if kind != 'all':
            if kind not in ('page','download'): raise ValueError('kind')
            where += ' AND kind=?'; args.append(kind)
        page = int(query.get('page',['1'])[0])
        if not 1<=page<=20000: raise ValueError('page')
        limit, offset = (10000, 0) if export else (50, (page-1)*50)
        with self.connect() as db:
            count = db.execute(f'SELECT count(*) FROM visits WHERE {where}',args).fetchone()[0]
            rows = [dict(row) for row in db.execute(f'SELECT at,ip,kind,path,device,system,browser,country,referrer,bot,status FROM visits WHERE {where} ORDER BY at DESC,id DESC LIMIT ? OFFSET ?', args+[limit,offset])]
        return {'rows':rows,'total':count,'page':page,'limit':limit,'truncated':export and count>limit}


class AccessAuth:
    def __init__(self, issuer, audience, email):
        import jwt
        if not re.fullmatch(r'https://[a-z0-9-]+\.cloudflareaccess\.com', issuer) or not audience or not email:
            raise ValueError('Cloudflare Access configuration is required')
        self.jwt, self.issuer, self.audience, self.email = jwt, issuer, audience, email.lower()
        self.keys = jwt.PyJWKClient(issuer+'/cdn-cgi/access/certs', cache_jwk_set=True, lifespan=3600, timeout=5)
    def verify(self, token):
        if not token or len(token)>16384: return False
        try:
            key = self.keys.get_signing_key_from_jwt(token)
            claims = self.jwt.decode(token,key.key,algorithms=['RS256'],audience=self.audience,issuer=self.issuer,
                                     options={'require':['exp','iat','iss','aud','email']})
            return claims['email'].lower() == self.email and claims.get('type','app') == 'app'
        except Exception:
            return False


def csv_data(rows):
    out = io.StringIO(); writer = csv.writer(out)
    writer.writerow(['访问时间（北京时间）','IP','事件','页面','设备','系统','浏览器','国家/地区代码','来源域名','疑似机器人'])
    for row in rows:
        values = [datetime.fromtimestamp(row['at']/1000,TZ).strftime('%Y-%m-%d %H:%M:%S'),row['ip'],row['kind'],row['path'],row['device'],row['system'],row['browser'],row['country'],row['referrer'],str(row['bot'])]
        writer.writerow(["'"+v if v.startswith(('=','+','-','@','\t','\r')) else v for v in values])
    return ('\ufeff'+out.getvalue()).encode('utf-8')


def handler_for(store, auth):
    class Handler(BaseHTTPRequestHandler):
        server_version = 'DuoStats'
        def setup(self):
            super().setup()
            self.connection.settimeout(15)
        def log_message(self, *args): pass
        def send(self, status, data, content_type='application/json; charset=utf-8', extra=None):
            self.send_response(status)
            self.send_header('Content-Type',content_type)
            self.send_header('Content-Length',str(len(data)))
            self.send_header('Cache-Control','private, no-store')
            self.send_header('X-Content-Type-Options','nosniff')
            self.send_header('Referrer-Policy','no-referrer')
            self.send_header('Content-Security-Policy',"default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'")
            for key,value in (extra or {}).items(): self.send_header(key,value)
            self.end_headers()
            if self.command != 'HEAD': self.wfile.write(data)
        def do_HEAD(self): self.do_GET()
        def do_GET(self):
            parsed = urlsplit(self.path)
            if parsed.path=='/health':
                self.send(200,b'{"ok":true}'); return
            if not auth.verify(self.headers.get('Cf-Access-Jwt-Assertion','')):
                self.send(403,b'{"error":"Cloudflare Access authorization required"}'); return
            assets = {'/admin/':('index.html','text/html; charset=utf-8'),'/admin/admin.js':('admin.js','text/javascript; charset=utf-8'),'/admin/admin.css':('admin.css','text/css; charset=utf-8')}
            if parsed.path in assets:
                name,mime = assets[parsed.path];self.send(200,(PUBLIC/name).read_bytes(),mime);return
            try:
                query = parse_qs(parsed.query,max_num_fields=12)
                if parsed.path=='/admin/api/summary': data=store.summary(query)
                elif parsed.path=='/admin/api/visits': data=store.records(query)
                elif parsed.path=='/admin/api/export':
                    result=store.records(query,export=True)
                    self.send(200,csv_data(result['rows']),'text/csv; charset=utf-8',{'Content-Disposition':'attachment; filename="duo-visits.csv"','X-Export-Truncated':str(result['truncated']).lower()});return
                else:
                    self.send(404,b'{"error":"Not found"}');return
                self.send(200,json.dumps(data,ensure_ascii=False).encode())
            except (ValueError,TypeError):
                self.send(400,b'{"error":"Invalid filter"}')
            except Exception:
                self.send(503,b'{"error":"Statistics temporarily unavailable"}')
    return Handler


def collect(store, log_dir, stopped):
    prune_at = 0
    while not stopped.wait(1):
        try:
            for logfile in sorted(log_dir.glob('duo-visits.jsonl*'),key=lambda p:p.stat().st_mtime):
                store.ingest(logfile)
            if time.time()>prune_at:
                store.prune();prune_at=time.time()+3600
            with store.connect() as db:
                db.execute("INSERT OR REPLACE INTO meta VALUES ('last_ingest_at',?)",(str(int(time.time()*1000)),))
        except Exception as error:
            print('Collector retry:',type(error).__name__,flush=True)


if __name__=='__main__':
    os.umask(0o077)
    data_dir=Path(os.environ.get('DUO_DATA_DIR','/var/lib/duo-analytics'))
    data_dir.mkdir(parents=True,exist_ok=True)
    store=Store(data_dir/'visits.sqlite')
    auth=AccessAuth(os.environ['DUO_ACCESS_ISSUER'],os.environ['DUO_ACCESS_AUD'],os.environ['DUO_ADMIN_EMAIL'])
    stopped=threading.Event()
    threading.Thread(target=collect,args=(store,Path(os.environ.get('DUO_LOG_DIR','/var/log/duo-analytics')),stopped),daemon=True).start()
    server=ThreadingHTTPServer(('127.0.0.1',int(os.environ.get('DUO_PORT','4189'))),handler_for(store,auth))
    server.daemon_threads=True
    print('Duo analytics listening on loopback',flush=True)
    try: server.serve_forever()
    finally: stopped.set();server.server_close()

"""Disposable localhost-only UI fixture; never part of the deployed release."""
import tempfile, time, json, threading
from pathlib import Path
from types import SimpleNamespace
from http.server import ThreadingHTTPServer
from analytics.app import Store, handler_for
with tempfile.TemporaryDirectory() as tmp:
    store=Store(Path(tmp)/'preview.sqlite'); log=Path(tmp)/'fixture.jsonl'
    ua=['Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) Version/18.0 Mobile/15E148 Safari/604.1','Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Chrome/140.0.0.0 Safari/537.36']
    log.write_text(''.join(json.dumps(dict(at=time.time()-i*2300,ip='203.0.113.'+str(i%12+1),method='GET',path='/' if i%7 else '/downloads/Foldlight-0.2.3-macOS.dmg' if i%2 else '/downloads/Foldlight-Fold8-0.3.7.apk',status=200,ua=ua[i%2],country='CN',referrer='example.com',bytes=100))+'\n' for i in range(100)))
    store.ingest(log)
    with store.connect() as db:db.execute("INSERT OR REPLACE INTO meta VALUES ('last_ingest_at',?)",(str(int(time.time()*1000)),))
    ThreadingHTTPServer(('127.0.0.1',4190),handler_for(store,SimpleNamespace(verify=lambda token:True))).serve_forever()

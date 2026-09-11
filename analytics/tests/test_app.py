import gzip
import json
import tempfile
import threading
import time
import unittest
from pathlib import Path
from types import SimpleNamespace
from urllib.request import Request, urlopen
from urllib.error import HTTPError
from http.server import ThreadingHTTPServer
import jwt
from cryptography.hazmat.primitives.asymmetric import rsa
from analytics.app import Store, normalize, AccessAuth, handler_for, csv_data

UA='Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) Version/18.0 Mobile/15E148 Safari/604.1'
def event(**changes):
    item=dict(at=str(time.time()),ip='203.0.113.8',ipv6='',method='GET',path='/',status=200,ua=UA,country='CN',referrer='example.com',bytes=100)
    item.update(changes)
    return (json.dumps(item)+'\n').encode()

class StatisticsTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.root=Path(self.tmp.name)
        self.store=Store(self.root/'visits.sqlite');self.log=self.root/'duo-visits.jsonl'
    def tearDown(self): self.tmp.cleanup()
    def test_accepted_events_and_metadata(self):
        row=normalize(event(ipv6='2001:db8::1'))
        self.assertEqual(row[2:8],('2001:db8::1','page','/','手机','iOS / iPadOS','Safari 18'))
        for kw in [dict(method='HEAD'),dict(status=404),dict(path='/app.js'),dict(path='/admin/'),dict(ip='invalid'),dict(at=time.time()-91*86400)]:
            self.assertIsNone(normalize(event(**kw)),kw)
        for raw in [b'null',b'[]',b'{',b'3',b'"x"']: self.assertIsNone(normalize(raw))
        self.assertEqual(normalize(event(path='/downloads/Foldlight-0.2.3-macOS.dmg',status=206))[3],'download')
    def test_fold8_downloads_count_alongside_mac_and_export(self):
        apk='/downloads/Foldlight-Fold8-0.3.7.apk'
        for status in (200,206):
            self.assertEqual(normalize(event(path=apk,status=status))[3],'download')
        for kw in [dict(method='HEAD'),dict(status=404),dict(path=apk+'.sha256'),dict(path='/downloads/Foldlight-Fold8-0.3.7-ReadMe.txt'),dict(path='/downloads/other.apk')]:
            self.assertIsNone(normalize(event(**{'path':apk,**kw})),kw)
        self.log.write_bytes(event()+event(path=apk)+event(path=apk,status=206)+event(path='/downloads/Foldlight-0.2.4-macOS.dmg')+event(path=apk,ua='TestBot'))
        self.store.ingest(self.log)
        totals=self.store.summary({})['totals']
        self.assertEqual((totals['pageviews'],totals['downloads'],totals['downloads_mac'],totals['downloads_fold8']),(1,3,1,2))
        self.assertEqual(self.store.summary({'bots':['1']})['totals']['downloads_fold8'],3)
        records=self.store.records({'kind':['download']},export=True)
        self.assertEqual(records['total'],3)
        self.assertIn(apk.encode(),csv_data(records['rows']))
        self.assertEqual(sum(row['downloads'] for row in self.store.summary({})['trend']),3)

    def test_partial_rotation_dedup_and_filters(self):
        first=event(); second=event(ip='203.0.113.9');bot=event(ua='TestBot')
        self.log.write_bytes(first+second[:-1]);self.store.ingest(self.log)
        self.assertEqual(self.store.records({})['total'],1)
        with self.log.open('ab') as f:f.write(b'\n'+bot)
        self.store.ingest(self.log);self.store.ingest(self.log)
        self.assertEqual(self.store.summary({})['totals']['pageviews'],2)
        self.assertEqual(self.store.summary({})['totals']['unique_ips'],2)
        self.assertEqual(self.store.records({'bots':['1']})['total'],3)
        rotated=self.log.with_suffix('.jsonl.1');self.log.rename(rotated)
        self.log.write_bytes(event(path='/downloads/Foldlight-0.2.3-macOS.dmg'))
        archive=self.root/'duo-visits.jsonl.2.gz'
        with gzip.open(archive,'wb') as f:f.write(rotated.read_bytes())
        for p in (rotated,archive,self.log):self.store.ingest(p)
        self.assertEqual(self.store.records({'bots':['1']})['total'],4)
        self.assertEqual(self.store.summary({})['totals']['downloads'],1)
        self.assertEqual(self.store.records({'ip':['203.0.113.9']})['total'],1)
        self.assertEqual(self.store.records({'kind':['download']})['total'],1)
        for q in ({'ip':["%' OR 1=1"]},{'days':['8']},{'device':['bad']},{'page':['0']}):
            with self.assertRaises(ValueError):self.store.records(q)
    def test_retention_and_export(self):
        self.log.write_bytes(event());self.store.ingest(self.log)
        rows=self.store.records({})['rows'];rows[0]['referrer']='=FORMULA()'
        self.assertIn(b"'=FORMULA()",csv_data(rows))
        with self.store.connect() as db:db.execute('UPDATE visits SET at=?',(int((time.time()-91*86400)*1000),))
        self.store.prune();self.assertEqual(self.store.records({'days':['90']})['total'],0)
    def test_http_auth_and_filters(self):
        self.log.write_bytes(event());self.store.ingest(self.log)
        auth=SimpleNamespace(verify=lambda token:token=='unit-test-token')
        server=ThreadingHTTPServer(('127.0.0.1',0),handler_for(self.store,auth));thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
        base=f'http://127.0.0.1:{server.server_port}'
        try:
            for path in ['/admin/','/admin/api/summary','/admin/api/visits','/admin/api/export','/admin/admin.js']:
                with self.assertRaises(HTTPError) as ctx:urlopen(Request(base+path,headers={'Cf-Access-Authenticated-User-Email':'admin@example.com'}))
                self.assertEqual(ctx.exception.code,403)
                self.assertNotIn(b'203.0.113.8',ctx.exception.read())
            with urlopen(Request(base+'/admin/api/summary',headers={'Cf-Access-Jwt-Assertion':'unit-test-token'})) as response:
                self.assertEqual(json.load(response)['totals']['pageviews'],1)
                self.assertIn('no-store',response.headers['Cache-Control'])
            with self.assertRaises(HTTPError) as ctx:urlopen(Request(base+'/admin/api/visits?days=999',headers={'Cf-Access-Jwt-Assertion':'unit-test-token'}))
            self.assertEqual(ctx.exception.code,400)
        finally:server.shutdown();server.server_close();thread.join()

class AuthTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.private=rsa.generate_private_key(public_exponent=65537,key_size=2048)
    def setUp(self):
        self.auth=AccessAuth('https://test.cloudflareaccess.com','expected-audience','admin@example.com')
        self.auth.keys=SimpleNamespace(get_signing_key_from_jwt=lambda token:SimpleNamespace(key=self.private.public_key()))
    def token(self,**changes):
        payload=dict(iss=self.auth.issuer,aud=['expected-audience'],email='admin@example.com',iat=int(time.time()),exp=int(time.time())+600,type='app')
        payload.update(changes);return jwt.encode(payload,self.private,algorithm='RS256')
    def test_signature_and_claims(self):
        self.assertTrue(self.auth.verify(self.token()))
        for changes in [dict(aud='wrong'),dict(email='other@example.org'),dict(exp=int(time.time())-1),dict(iss='https://evil.example'),dict(type='org'),dict(iat=int(time.time())+100)]:
            self.assertFalse(self.auth.verify(self.token(**changes)),changes)
        token=self.token();parts=token.split('.');parts[1]=jwt.utils.base64url_encode(b'{"email":"admin@example.com"}').decode()
        self.assertFalse(self.auth.verify('.'.join(parts)))
        self.assertFalse(self.auth.verify(jwt.encode({'email':'admin@example.com'},key=None,algorithm='none')))
        self.assertFalse(self.auth.verify(''));self.assertFalse(self.auth.verify('x'*17000))

if __name__=='__main__':unittest.main()

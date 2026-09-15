import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {downloadCopy} from '../download-copy.js';

test('Android editions have complete Chinese and English copy',()=>{
 assert.deepEqual(Object.keys(downloadCopy.zh).sort(),Object.keys(downloadCopy.en).sort());
 for(const [key,text] of Object.entries(downloadCopy.en))assert.ok(text&&!/\p{Script=Han}/u.test(text),key);
 for(const edition of ['fold','global']){
  const english=readFileSync(new URL(`../../docs/android-fold/${edition}-en.md`,import.meta.url),'utf8');
  assert.ok(!/\p{Script=Han}/u.test(english));
 }
});
test('Photo and global downloads remain separate and every guide switches language',()=>{
 const html=readFileSync(new URL('../index.html',import.meta.url),'utf8');
 assert.ok(html.includes('id="android-fold"')&&html.includes('id="fold-download"')&&html.includes('id="global-download"'));
 for(const file of ['Foldlight-Android-Fold-0.3.8-fold7.1','Foldlight-Android-Global-0.2.4']){
  assert.ok(html.includes(`${file}.apk" download`));
  assert.ok(html.includes(`data-href-en="https://duo.zksaga.com/downloads/${file}-ReadMe-en.txt"`));
 }
 assert.match(downloadCopy.en.globalCompatibility,/Fold8/);
 assert.match(downloadCopy.en.globalCompatibility,/Fold7.*untested/);
});

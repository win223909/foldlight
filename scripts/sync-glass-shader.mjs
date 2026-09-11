import {readFile,writeFile} from 'node:fs/promises';
const root=new URL('../',import.meta.url);
const java=await readFile(new URL('android/app/src/main/java/com/zksaga/foldlight/FoldRenderer.java',root),'utf8');
const fragment=java.match(/private static final String FRAGMENT="""\n([\s\S]*?)\n        """;/)?.[1].replace(/^        /gm,'');
if(!fragment)throw Error('Android fragment shader not found');
await writeFile(new URL('web/reference-glass.js',root),'// Generated from FoldRenderer.java by scripts/sync-glass-shader.mjs.\nexport const referenceFragment = '+JSON.stringify(fragment)+';\n');

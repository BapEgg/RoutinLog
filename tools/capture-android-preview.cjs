/* Capture the installed debug app on a dedicated emulator. No account data is used. */
const fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process');
const root=path.resolve(__dirname,'..');
const sdk=process.env.ANDROID_HOME;
if(!sdk)throw new Error('Set ANDROID_HOME first.');
const adb=path.join(sdk,'platform-tools',process.platform==='win32'?'adb.exe':'adb');
const serial=process.env.ANDROID_SERIAL||'emulator-5554';
const run=(...args)=>cp.execFileSync(adb,['-s',serial,...args],{windowsHide:true,timeout:60000});
const catalog=fs.readFileSync(root+'/android/app/src/main/java/com/bapegg/routinlog/ui/ScreenCatalog.kt','utf8');
const screens=[...catalog.matchAll(/ScreenInfo\("([A-Z]\d\d)","([^"]+)"/g)].map(m=>({id:m[1],title:m[2]}));
const requested=process.argv.slice(2);
if(requested.some(id=>!screens.some(s=>s.id===id)))throw new Error('Unknown screen ID');
const selected=requested.length?screens.filter(s=>requested.includes(s.id)):screens;
const dir=root+'/docs/assets/android';fs.mkdirSync(dir,{recursive:true});
const pause=ms=>new Promise(r=>setTimeout(r,ms));
(async()=>{
 run('install','-r',root+'/android/app/build/outputs/apk/debug/app-debug.apk');
 run('shell','input','keyevent','KEYCODE_WAKEUP');run('shell','wm','dismiss-keyguard');run('shell','svc','power','stayon','true');
 // ATD images disable drawing to optimize nonvisual tests. Only enable it on this QA device.
 run('shell','setprop','debug.hwui.drawing_enabled','true');
 for(const s of selected){
  run('shell','am','start','-W','-S','-n','com.bapegg.routinlog.debug/com.bapegg.routinlog.MainActivity','--es','preview_route',s.id);
  await pause(1900);
  const png=run('exec-out','screencap','-p');
  if(png.length<20000)throw new Error('Unexpected blank capture: '+s.id);
  fs.writeFileSync(dir+'/'+s.id+'.png',png);
  console.log(s.id+' captured');
 }
 fs.writeFileSync(dir+'/manifest.json',JSON.stringify({capturedAt:new Date().toISOString(),device:'Android 16 / API 36 · Pixel 5 emulator',screens},null,2)+'\n');
 const gallery=root+'/docs/android-preview.html';
 fs.writeFileSync(gallery,fs.readFileSync(gallery,'utf8').replace(/(<script id="screens" type="application\/json">)[\s\S]*?(<\/script>)/,()=>'<script id="screens" type="application/json">'+JSON.stringify(screens)+'</script>'));
 console.log('Captured '+selected.length+' native screens. Gallery contains '+screens.length+' screens.');
})();

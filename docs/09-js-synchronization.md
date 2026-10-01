# المرحلة 9: منطق المزامنة في JavaScript

تمت إضافة منطق مزامنة الفيديو في الواجهة مع منع حلقات الإرسال اللانهائية وتصحيح الانحراف بين المشاهدين.

## 1. الملفات الجديدة أو المعدلة

### ملف جديد

```text
src/main/resources/static/assets/js/sync.js
```

### ملفات معدلة

```text
src/main/resources/static/assets/js/video-player.js
src/main/resources/static/assets/js/room.js
src/main/resources/static/room.html
README.md
```

## 2. دور `sync.js`

الملف `sync.js` يعرّف وحدة:

```js
window.WatchPartySync
```

وتقوم بـ:

- إرسال أوامر التشغيل المحلية إلى STOMP.
- تطبيق أوامر التشغيل القادمة من الشبكة.
- تجاهل صدى الأحداث التي أرسلها نفس المستخدم.
- منع إرسال أحداث جديدة أثناء تطبيق حدث قادم من الشبكة.
- تصحيح الانحراف إذا تجاوز ثانيتين.
- مزامنة حالة التشغيل بعد `SYNC_STATE`.

## 3. منع حلقات الإرسال اللانهائية

استخدمت ثلاث طبقات حماية:

### 3.1 تجاهل صدى الحدث الذاتي

كل رسالة STOMP تحتوي:

```json
{
  "senderId": "user-uuid"
}
```

إذا كان `senderId` يساوي المستخدم الحالي، يتم تحديث الحالة الداخلية فقط دون إعادة تطبيق الأمر على المشغل.

### 3.2 علم `applyingRemote`

عند تطبيق حدث قادم مثل `PLAY` أو `SEEK`:

```js
applyingRemote = true
```

أي أحداث `play`, `pause`, `seeked` تصدر من المشغل خلال هذه الفترة يتم تجاهلها حتى لا تُرسل مرة أخرى إلى WebSocket.

### 3.3 منع التكرار السريع

يتم تجاهل نفس الأمر إذا تكرر خلال فترة قصيرة وبنفس الموضع تقريبًا.

هذا يمنع الحالات مثل:

- الضغط المزدوج السريع.
- event من مشغل MP4 بعد ضغط زر مخصص.
- event من YouTube بعد `seekTo` برمجي.

## 4. تصحيح الانحراف Drift Correction

يتم فحص الانحراف كل ثانية:

```text
DRIFT_CHECK_MS = 1000
```

إذا كان الفرق بين موضع المشغل المحلي والموضع المتوقع أكبر من:

```text
2 seconds
```

يتم تنفيذ:

```js
player.seek(expectedPosition)
```

وإذا كانت الحالة المتوقعة `PLAYING` يتم تشغيل الفيديو محليًا.

## 5. حساب الموضع المتوقع

إذا كانت الغرفة في حالة `PAUSED`:

```text
expected = playbackPositionSeconds
```

إذا كانت في حالة `PLAYING`:

```text
expected = playbackPositionSeconds + (now - playbackUpdatedAt)
```

بهذه الطريقة من ينضم متأخرًا يحصل على موضع قريب من اللحظة الحالية وليس الموضع القديم وقت آخر حدث فقط.

## 6. تحسين `video-player.js`

تم تحويل `video-player.js` إلى واجهة مشغل موحدة تدعم:

```js
loadVideo(url, type)
play()
pause()
seek(seconds)
currentTime()
isPlaying()
on(event, handler)
off(event, handler)
```

الأحداث المدعومة:

```text
play
pause
seeked
ended
videochange
```

## 7. دعم MP4 و YouTube

### MP4

يعتمد على أحداث المتصفح الأصلية:

```text
play
pause
seeked
ended
```

### YouTube

يعتمد على:

```text
YouTube IFrame API onStateChange
```

ولأن YouTube لا يعطي `seeked` موثوقًا مثل HTML5 Video، أضفت مراقبة زمنية ترصد القفزات الكبيرة في الوقت وتحوّلها إلى حدث `seeked`.

## 8. تعديل `room.js`

أصبحت أزرار:

- تشغيل
- إيقاف
- انتقال
- تغيير الفيديو

تمر عبر `WatchPartySync` بدل إرسال STOMP مباشرة.

مثال:

```js
state.sync.requestPlay()
state.sync.requestPause()
state.sync.requestSeek(position)
state.sync.requestChangeVideo(videoUrl)
```

كما أصبح استقبال أحداث:

```text
PLAY, PAUSE, SEEK, CHANGE_VIDEO
```

يمر أولًا عبر:

```js
state.sync.handleEnvelope(envelope)
```

## 9. التعامل مع `SYNC_STATE`

عند استقبال `SYNC_STATE`:

- يتم تحديث بيانات الغرفة.
- يتم تحديث قائمة الأعضاء.
- يتم عرض آخر الرسائل.
- يتم تحديث قائمة الانتظار.
- يتم تطبيق حالة الفيديو عبر `WatchPartySync.applyRoomState`.

وهذا يجعل العضو المتأخر يدخل على الحالة الحالية للفيديو.

## 10. قائمة الانتظار والمزامنة

عند تشغيل عنصر من قائمة الانتظار عبر REST، ترسل الواجهة بعد ذلك حدث تغيير فيديو عبر WebSocket حتى يعرف باقي الأعضاء بالفيديو الجديد.

> قرار تصميمي: REST يغيّر حالة قاعدة البيانات، أما WebSocket فهو قناة إعلام باقي المشاهدين فورًا.

## 11. قرارات تصميمية مهمة

- فصلت منطق المزامنة في `sync.js` بدل حشره داخل `room.js` حتى يسهل اختباره وتطويره في مرحلة WebRTC.
- أبقيت `video-player.js` كطبقة موحدة فوق YouTube و MP4 حتى لا يعرف `room.js` تفاصيل كل مشغل.
- جعلت تصحيح الانحراف محليًا وصامتًا دون إرسال حدث جديد، حتى لا يتحول التصحيح نفسه إلى حلقة بث.

## 12. ما تبقى للمرحلة القادمة

المرحلة 10 ستضيف WebRTC signaling والواجهة الفعلية للمكالمة:

- offer
- answer
- ice-candidate
- كتم الميكروفون
- إيقاف الكاميرا
- STUN/TURN

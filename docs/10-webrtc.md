# المرحلة 10: WebRTC Signaling وواجهة المكالمة

تمت إضافة مكالمات WebRTC بنمط Mesh حتى 6 أشخاص، مع signaling عبر STOMP/WebSocket وواجهة مكالمة داخل صفحة الغرفة.

## 1. الملفات الجديدة أو المعدلة

### Backend DTOs

```text
src/main/java/com/watchparty/webrtc/dto/WebRtcIceCandidatePayload.java
src/main/java/com/watchparty/webrtc/dto/WebRtcSignalCommand.java
src/main/java/com/watchparty/webrtc/dto/WebRtcSignalPayload.java
src/main/java/com/watchparty/webrtc/dto/WebRtcCallStateCommand.java
src/main/java/com/watchparty/webrtc/dto/WebRtcCallParticipantPayload.java
```

### Backend Controller

```text
src/main/java/com/watchparty/webrtc/controller/WebRtcSignalingController.java
```

### Realtime تعديلات

```text
src/main/java/com/watchparty/realtime/dto/RealtimeEventType.java
src/main/java/com/watchparty/realtime/dto/RealtimeDestinations.java
src/main/java/com/watchparty/realtime/service/RealtimeAuthorizationService.java
```

### Public Config تعديل

```text
src/main/java/com/watchparty/common/dto/PublicAppConfigResponse.java
src/main/java/com/watchparty/common/controller/PublicConfigController.java
```

### Frontend

```text
src/main/resources/static/assets/js/webrtc.js
src/main/resources/static/assets/js/room.js
src/main/resources/static/assets/css/styles.css
src/main/resources/static/room.html
```

## 2. STOMP Destinations الخاصة بـ WebRTC

كل الإرسال تحت prefix:

```text
/app
```

| Destination | Payload | الوصف |
|---|---|---|
| `/app/rooms/{roomId}/webrtc/join` | `WebRtcCallStateCommand` | إعلان دخول المستخدم للمكالمة |
| `/app/rooms/{roomId}/webrtc/leave` | `WebRtcCallStateCommand` | إعلان مغادرة المكالمة |
| `/app/rooms/{roomId}/webrtc/offer` | `WebRtcSignalCommand` | إرسال SDP offer لمستخدم محدد |
| `/app/rooms/{roomId}/webrtc/answer` | `WebRtcSignalCommand` | إرسال SDP answer لمستخدم محدد |
| `/app/rooms/{roomId}/webrtc/ice-candidate` | `WebRtcSignalCommand` | إرسال ICE candidate لمستخدم محدد |

## 3. Subscribe Destinations

### أحداث دخول وخروج المكالمة

تصل ضمن أحداث الغرفة العامة:

```text
/topic/rooms/{roomId}/events
```

والأنواع:

```text
WEBRTC_JOIN_CALL
WEBRTC_LEAVE_CALL
```

### Signaling خاص بالمستخدم

كل offer/answer/ice-candidate يصل للمستخدم الهدف فقط عبر:

```text
/user/queue/rooms/{roomId}/webrtc
```

> قرار تصميمي: لا يتم بث SDP أو ICE للجميع، بل يتم إرسالها فقط للطرف الهدف باستخدام `convertAndSendToUser`.

## 4. شكل رسائل Signaling

### Offer / Answer

```json
{
  "targetUserId": "target-user-uuid",
  "sdpType": "offer",
  "sdp": "v=0..."
}
```

أو:

```json
{
  "targetUserId": "target-user-uuid",
  "sdpType": "answer",
  "sdp": "v=0..."
}
```

### ICE Candidate

```json
{
  "targetUserId": "target-user-uuid",
  "candidate": {
    "candidate": "candidate:...",
    "sdpMid": "0",
    "sdpMLineIndex": 0,
    "usernameFragment": "abc"
  }
}
```

## 5. صلاحيات WebRTC

كل أحداث WebRTC تمر عبر:

```text
RealtimeAuthorizationService
```

ويجب أن يكون المستخدم:

- مصادقًا بـ Supabase JWT.
- عضوًا فعّالًا في الغرفة.

كما يتحقق `WebRtcSignalingController` من أن `targetUserId` عضو فعّال في نفس الغرفة، حتى لا تُستخدم قناة signaling لإرسال رسائل لمستخدمين خارج الغرفة.

## 6. الواجهة في صفحة الغرفة

تم تفعيل قسم المكالمة داخل:

```text
src/main/resources/static/room.html
```

الأزرار:

- بدء المكالمة.
- مغادرة المكالمة.
- كتم/تشغيل الميكروفون.
- إيقاف/تشغيل الكاميرا.

ويتم عرض الفيديو المحلي والفيديوهات البعيدة داخل:

```text
#callGrid
```

## 7. Frontend WebRTC module

الملف:

```text
src/main/resources/static/assets/js/webrtc.js
```

يعرّف:

```js
window.WatchPartyWebRTC
```

ويدعم:

```js
startCall()
leaveCall()
handleEnvelope(envelope)
toggleAudio()
toggleVideo()
isInCall()
```

## 8. طريقة إنشاء شبكة Mesh

- عند ضغط “بدء المكالمة”، يطلب المتصفح صلاحيات الميكروفون والكاميرا عبر `getUserMedia`.
- يرسل المستخدم حدث `WEBRTC_JOIN_CALL`.
- كل عضو موجود بالفعل في المكالمة يستقبل الحدث ويرسل `offer` للعضو الجديد.
- العضو الجديد يرد بـ `answer`.
- الطرفان يتبادلان `ice-candidate`.
- يتم إنشاء اتصال مباشر `RTCPeerConnection` بين كل زوج من المستخدمين.

> قرار تصميمي: استخدمت Mesh لأنه مناسب حتى 6 أشخاص كما هو مطلوب، ولا يحتاج SFU أو Media Server.

## 9. STUN و TURN

تقرأ الواجهة إعدادات WebRTC من:

```text
GET /api/public/config
```

القيم الافتراضية لـ STUN:

```text
stun:stun.l.google.com:19302
stun:stun1.l.google.com:19302
```

TURN اختياري عبر متغيرات البيئة:

```bash
WEBRTC_TURN_URLS=turn:your-turn.example.com:3478
WEBRTC_TURN_USERNAME=turn-user
WEBRTC_TURN_PASSWORD=turn-password
```

> ملاحظة مهمة: بيانات TURN تُرسل للمتصفح لأن WebRTC يحتاجها لإنشاء الاتصال. في الإنتاج الأفضل استخدام TURN credentials قصيرة العمر.

## 10. حد 6 أشخاص

الواجهة تمنع إنشاء peers جدد عندما يصل عدد المشاركين المحلي إلى 6 تقريبًا:

```text
MAX_PARTICIPANTS = 6
```

هذا يحافظ على بساطة Mesh ويقلل الضغط على الأجهزة.

## 11. ملاحظات مهمة

- لا يمر الصوت أو الفيديو عبر Spring Boot؛ الخادم يمرر signaling فقط.
- REST و STOMP لا يحملان Media streams.
- إذا فشل فتح الكاميرا، تحاول الواجهة الدخول بصوت فقط.
- إذا لم يدخل المستخدم المكالمة بنفسه، لا يتم فتح الميكروفون/الكاميرا تلقائيًا حتى لو وصل offer.

## 12. ما تبقى للمرحلة 11

المرحلة القادمة ستركز على الحماية:

- XSS/CSRF.
- تحديد معدل الرسائل.
- تنظيف المدخلات بشكل أعمق.
- حماية إضافية للدردشة و WebSocket.

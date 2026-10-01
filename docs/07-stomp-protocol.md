# المرحلة 7: إعدادات STOMP وبروتوكول رسائل WebSocket JSON

تمت إضافة Controller لحظي عبر STOMP، وبروتوكول JSON واضح للأحداث الأساسية:

```text
JOIN, LEAVE, CHAT, PLAY, PAUSE, SEEK, CHANGE_VIDEO, SYNC_STATE
```

كما تم تجهيز بنية قابلة للتوسعة لأحداث WebRTC والمهام الإدارية في المراحل القادمة.

## 1. الملفات الجديدة أو المعدلة

### Message DTO

```text
src/main/java/com/watchparty/message/dto/MessageResponse.java
```

### Realtime DTOs

```text
src/main/java/com/watchparty/realtime/dto/RealtimeEnvelope.java
src/main/java/com/watchparty/realtime/dto/RealtimeErrorResponse.java
src/main/java/com/watchparty/realtime/dto/RealtimeDestinations.java
src/main/java/com/watchparty/realtime/dto/PlaybackCommand.java
src/main/java/com/watchparty/realtime/dto/ChangeVideoCommand.java
src/main/java/com/watchparty/realtime/dto/ChatCommand.java
src/main/java/com/watchparty/realtime/dto/MemberEventPayload.java
src/main/java/com/watchparty/realtime/dto/ChatEventPayload.java
src/main/java/com/watchparty/realtime/dto/PlaybackEventPayload.java
src/main/java/com/watchparty/realtime/dto/SyncStatePayload.java
```

### Realtime Services

```text
src/main/java/com/watchparty/realtime/service/RealtimeSessionRegistry.java
src/main/java/com/watchparty/realtime/service/SyncStateService.java
src/main/java/com/watchparty/realtime/service/RealtimeDisconnectListener.java
```

### Realtime Controller

```text
src/main/java/com/watchparty/realtime/controller/RealtimeRoomController.java
```

### تعديل أمان الاشتراك

```text
src/main/java/com/watchparty/realtime/config/WebSocketAuthChannelInterceptor.java
```

تمت إضافة فحص عضوية المستخدم عند الاشتراك في وجهات الغرف مثل:

```text
/topic/rooms/{roomId}/events
```

## 2. الاتصال بـ WebSocket

Endpoint:

```text
/ws
```

أو SockJS:

```text
/ws-sockjs
```

يمكن تمرير JWT بإحدى الطريقتين:

```text
/ws?access_token=<supabase-access-token>
```

أو داخل STOMP CONNECT:

```json
{
  "Authorization": "Bearer <supabase-access-token>"
}
```

## 3. وجهات الاشتراك Subscribe Destinations

### أحداث الغرفة العامة للأعضاء

```text
/topic/rooms/{roomId}/events
```

كل الأحداث العامة داخل الغرفة تُبث هنا، مثل:

- `JOIN`
- `LEAVE`
- `CHAT`
- `PLAY`
- `PAUSE`
- `SEEK`
- `CHANGE_VIDEO`

> لا يستطيع المستخدم الاشتراك في topic غرفة إلا إذا كان عضوًا فعّالًا فيها.

### حالة المزامنة الخاصة بالمستخدم

```text
/user/queue/rooms/{roomId}/sync
```

هذه خاصة بالمستخدم الحالي فقط، وتستخدم لإرسال `SYNC_STATE` عند الانضمام أو عند طلب المزامنة.

### أخطاء WebSocket الخاصة بالمستخدم

```text
/user/queue/errors
```

تصل هنا أخطاء التحقق أو الصلاحيات أو payload.

## 4. وجهات الإرسال Send Destinations

كل الإرسال يكون تحت prefix:

```text
/app
```

| Destination | Payload | الوصف | الصلاحية |
|---|---|---|---|
| `/app/rooms/{roomId}/join` | فارغ | إعلان دخول المستخدم واتصاله | عضو فعّال |
| `/app/rooms/{roomId}/leave` | فارغ | إعلان خروج الاتصال | عضو فعّال |
| `/app/rooms/{roomId}/chat` | `ChatCommand` | إرسال رسالة دردشة | عضو فعّال |
| `/app/rooms/{roomId}/play` | `PlaybackCommand` | تشغيل الفيديو | صلاحية التحكم |
| `/app/rooms/{roomId}/pause` | `PlaybackCommand` | إيقاف الفيديو مؤقتًا | صلاحية التحكم |
| `/app/rooms/{roomId}/seek` | `PlaybackCommand` | الانتقال إلى موضع جديد | صلاحية التحكم |
| `/app/rooms/{roomId}/video` | `ChangeVideoCommand` | تغيير الفيديو الحالي | صلاحية التحكم |
| `/app/rooms/{roomId}/sync` | فارغ | طلب الحالة الحالية | عضو فعّال |

## 5. شكل Envelope العام

كل حدث مبثوث يستخدم الشكل التالي:

```json
{
  "type": "PLAY",
  "roomId": "00000000-0000-0000-0000-000000000000",
  "senderId": "11111111-1111-1111-1111-111111111111",
  "sentAt": "2026-10-01T10:00:00Z",
  "payload": {}
}
```

## 6. بروتوكول JOIN

### Send

```text
/app/rooms/{roomId}/join
```

Payload فارغ.

### Broadcast

```json
{
  "type": "JOIN",
  "roomId": "room-uuid",
  "senderId": "user-uuid",
  "sentAt": "2026-10-01T10:00:00Z",
  "payload": {
    "member": {
      "id": "membership-uuid",
      "roomId": "room-uuid",
      "userId": "user-uuid",
      "role": "MEMBER",
      "canControl": false,
      "connected": true,
      "joinedAt": "2026-10-01T10:00:00Z",
      "leftAt": null,
      "lastSeenAt": "2026-10-01T10:00:00Z"
    }
  }
}
```

بعد `JOIN` يرسل الخادم أيضًا `SYNC_STATE` إلى المستخدم نفسه عبر:

```text
/user/queue/rooms/{roomId}/sync
```

## 7. بروتوكول LEAVE

### Send

```text
/app/rooms/{roomId}/leave
```

Payload فارغ.

### Broadcast

```json
{
  "type": "LEAVE",
  "roomId": "room-uuid",
  "senderId": "user-uuid",
  "sentAt": "2026-10-01T10:00:00Z",
  "payload": {
    "member": {
      "userId": "user-uuid",
      "connected": false
    }
  }
}
```

> عند قطع WebSocket بدون إرسال `leave`، يقوم `RealtimeDisconnectListener` ببث `LEAVE` تلقائيًا للغرف التي سجلها المستخدم في هذه الجلسة.

## 8. بروتوكول CHAT

### Send

Destination:

```text
/app/rooms/{roomId}/chat
```

Payload:

```json
{
  "content": "مرحبًا بالجميع 👋"
}
```

### Broadcast

```json
{
  "type": "CHAT",
  "roomId": "room-uuid",
  "senderId": "user-uuid",
  "sentAt": "2026-10-01T10:00:00Z",
  "payload": {
    "message": {
      "id": "message-uuid",
      "roomId": "room-uuid",
      "senderId": "user-uuid",
      "messageType": "CHAT",
      "content": "مرحبًا بالجميع 👋",
      "createdAt": "2026-10-01T10:00:00Z",
      "deletedAt": null
    }
  }
}
```

## 9. بروتوكول PLAY / PAUSE

### Send PLAY

```text
/app/rooms/{roomId}/play
```

```json
{
  "positionSeconds": 125.500
}
```

### Send PAUSE

```text
/app/rooms/{roomId}/pause
```

```json
{
  "positionSeconds": 130.000
}
```

### Broadcast

```json
{
  "type": "PLAY",
  "roomId": "room-uuid",
  "senderId": "user-uuid",
  "sentAt": "2026-10-01T10:00:00Z",
  "payload": {
    "status": "PLAYING",
    "positionSeconds": 125.500,
    "playbackUpdatedAt": "2026-10-01T10:00:00Z",
    "currentVideoUrl": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
    "currentVideoType": "YOUTUBE"
  }
}
```

## 10. بروتوكول SEEK

### Send

```text
/app/rooms/{roomId}/seek
```

```json
{
  "positionSeconds": 300.250,
  "status": "PLAYING"
}
```

إذا لم تُرسل `status`، يحافظ الخادم على حالة التشغيل الحالية.

### Broadcast

```json
{
  "type": "SEEK",
  "roomId": "room-uuid",
  "senderId": "user-uuid",
  "sentAt": "2026-10-01T10:00:00Z",
  "payload": {
    "status": "PLAYING",
    "positionSeconds": 300.250,
    "playbackUpdatedAt": "2026-10-01T10:00:00Z",
    "currentVideoUrl": "https://cdn.example.com/video.mp4",
    "currentVideoType": "MP4"
  }
}
```

## 11. بروتوكول CHANGE_VIDEO

### Send

```text
/app/rooms/{roomId}/video
```

```json
{
  "videoUrl": "https://cdn.example.com/video.mp4"
}
```

### Broadcast

```json
{
  "type": "CHANGE_VIDEO",
  "roomId": "room-uuid",
  "senderId": "user-uuid",
  "sentAt": "2026-10-01T10:00:00Z",
  "payload": {
    "status": "PAUSED",
    "positionSeconds": 0,
    "playbackUpdatedAt": "2026-10-01T10:00:00Z",
    "currentVideoUrl": "https://cdn.example.com/video.mp4",
    "currentVideoType": "MP4"
  }
}
```

## 12. بروتوكول SYNC_STATE

### Send

```text
/app/rooms/{roomId}/sync
```

Payload فارغ.

### Private response

Destination:

```text
/user/queue/rooms/{roomId}/sync
```

```json
{
  "type": "SYNC_STATE",
  "roomId": "room-uuid",
  "senderId": "user-uuid",
  "sentAt": "2026-10-01T10:00:00Z",
  "payload": {
    "room": {
      "id": "room-uuid",
      "inviteCode": "AbCdEf1234",
      "name": "سهرة الفيلم",
      "hostId": "host-uuid",
      "publicRoom": true,
      "protectedRoom": false,
      "controlMode": "HOST_ONLY",
      "currentVideoUrl": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
      "currentVideoType": "YOUTUBE",
      "playbackStatus": "PLAYING",
      "playbackPositionSeconds": 125.500,
      "playbackUpdatedAt": "2026-10-01T10:00:00Z"
    },
    "members": [],
    "recentMessages": [],
    "playlist": []
  }
}
```

هذه الرسالة مهمة لمن ينضم متأخرًا، لأنها تحتوي حالة الفيديو الحالية، الأعضاء، آخر 50 رسالة، وقائمة الانتظار.

## 13. الأخطاء

Destination:

```text
/user/queue/errors
```

مثال:

```json
{
  "timestamp": "2026-10-01T10:00:00Z",
  "code": "ForbiddenException",
  "message": "ليس لديك صلاحية التحكم بالتشغيل في هذه الغرفة"
}
```

## 14. قرارات تصميمية مهمة

- جعلت كل حدث يمر عبر `RealtimeAuthorizationService` قبل التنفيذ حتى لا تختلف صلاحيات WebSocket عن REST.
- `JOIN` لا ينشئ عضوية جديدة بدون REST؛ يجب أن يكون المستخدم عضوًا فعّالًا أولًا، وهذا يمنع تجاوز كلمة سر الغرفة عبر WebSocket.
- `SYNC_STATE` يرسل للمستخدم نفسه وليس broadcast للجميع، حتى لا نرسل قائمة ورسائل الغرفة لكل الأعضاء عند كل طلب مزامنة.
- `RealtimeSessionRegistry` يحفظ الغرف المرتبطة بكل session حتى نبث `LEAVE` عند انقطاع الاتصال المفاجئ.

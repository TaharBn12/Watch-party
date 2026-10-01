# المرحلة 5: REST Controllers للغرف والأعضاء وقائمة الانتظار

تمت إضافة REST API للغرف، الأعضاء، وقائمة الانتظار مع DTOs وتحقق مدخلات موحد.

## 1. ملاحظة مهمة حول المصادقة في هذه المرحلة

بعد تنفيذ المرحلة 6، أصبحت الـ Controllers تعتمد على Supabase JWT الحقيقي:

```http
Authorization: Bearer <supabase-access-token>
```

كان `X-User-Id` مستخدمًا مؤقتًا أثناء بناء المرحلة 5 فقط، وتمت إزالته من الكود في المرحلة 6.

## 2. الملفات الجديدة

### Error handling و current user مؤقت

```text
src/main/java/com/watchparty/common/dto/ErrorResponse.java
src/main/java/com/watchparty/common/exception/GlobalExceptionHandler.java
src/main/java/com/watchparty/common/exception/UnauthorizedException.java
src/main/java/com/watchparty/security/CurrentUserService.java
```

### Rooms DTOs + Controller

```text
src/main/java/com/watchparty/room/controller/RoomController.java
src/main/java/com/watchparty/room/dto/ChangeVideoRequest.java
src/main/java/com/watchparty/room/dto/CreateRoomRequest.java
src/main/java/com/watchparty/room/dto/PlaybackRequest.java
src/main/java/com/watchparty/room/dto/RoomPreviewResponse.java
src/main/java/com/watchparty/room/dto/RoomResponse.java
src/main/java/com/watchparty/room/dto/TransferHostRequest.java
src/main/java/com/watchparty/room/dto/UpdateRoomSettingsRequest.java
```

### Members DTOs + Controller

```text
src/main/java/com/watchparty/member/controller/RoomMemberController.java
src/main/java/com/watchparty/member/dto/JoinRoomRequest.java
src/main/java/com/watchparty/member/dto/RoomMemberResponse.java
src/main/java/com/watchparty/member/dto/SetControlPermissionRequest.java
```

### Playlist DTOs + Controller

```text
src/main/java/com/watchparty/playlist/controller/PlaylistController.java
src/main/java/com/watchparty/playlist/dto/AddPlaylistItemRequest.java
src/main/java/com/watchparty/playlist/dto/PlaylistItemResponse.java
```

## 3. Endpoints الغرف

Base path:

```text
/api/rooms
```

| Method | Path | الوصف | يحتاج JWT |
|---|---|---|---|
| `POST` | `/api/rooms` | إنشاء غرفة | نعم |
| `GET` | `/api/rooms/{roomId}` | جلب غرفة بشرط العضوية | نعم |
| `GET` | `/api/rooms/invite/{inviteCode}` | جلب بيانات عامة غير حساسة من رابط الدعوة | لا |
| `GET` | `/api/rooms/mine` | غرف المستخدم التي يستضيفها | نعم |
| `GET` | `/api/rooms/public` | آخر 20 غرفة عامة | لا |
| `PATCH` | `/api/rooms/{roomId}/settings` | تعديل إعدادات الغرفة | نعم، المضيف فقط |
| `POST` | `/api/rooms/{roomId}/playback` | تحديث PLAY/PAUSE والموضع | نعم، حسب صلاحية التحكم |
| `POST` | `/api/rooms/{roomId}/video` | تغيير الفيديو الحالي | نعم، حسب صلاحية التحكم |
| `POST` | `/api/rooms/{roomId}/transfer-host` | نقل الإدارة | نعم، المضيف فقط |
| `DELETE` | `/api/rooms/{roomId}` | إغلاق الغرفة منطقيًا | نعم، المضيف فقط |

### مثال إنشاء غرفة

```http
POST /api/rooms
Authorization: Bearer <supabase-access-token>
Content-Type: application/json
```

```json
{
  "name": "سهرة الفيلم",
  "publicRoom": true,
  "password": null,
  "controlMode": "HOST_ONLY",
  "initialVideoUrl": "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
}
```

### مثال تحديث التشغيل

```json
{
  "status": "PLAYING",
  "positionSeconds": 125.500
}
```

## 4. Endpoints الأعضاء

Base path:

```text
/api/rooms
```

| Method | Path | الوصف | الصلاحية |
|---|---|---|---|
| `POST` | `/{roomId}/members/join` | الانضمام للغرفة بالمعرف | المستخدم نفسه |
| `POST` | `/invite/{inviteCode}/join` | الانضمام عبر كود الدعوة | المستخدم نفسه |
| `GET` | `/{roomId}/members` | قائمة أعضاء الغرفة | عضو فقط |
| `GET` | `/{roomId}/members/connected` | قائمة المتصلين حاليًا | عضو فقط |
| `POST` | `/{roomId}/members/me/connect` | تعليم المستخدم كمتصل | عضو فقط |
| `POST` | `/{roomId}/members/me/disconnect` | تعليم المستخدم كغير متصل | عضو فقط |
| `POST` | `/{roomId}/members/me/leave` | مغادرة الغرفة | عضو، وليس المضيف الحالي |
| `POST` | `/{roomId}/members/{targetUserId}/kick` | طرد عضو | المضيف فقط |
| `PATCH` | `/{roomId}/members/{targetUserId}/control` | منح/سحب التحكم | المضيف فقط |

### مثال الانضمام لغرفة بكلمة سر

```json
{
  "password": "room-password"
}
```

### مثال منح التحكم

```json
{
  "canControl": true
}
```

## 5. Endpoints قائمة الانتظار

Base path:

```text
/api/rooms/{roomId}/playlist
```

| Method | Path | الوصف | الصلاحية |
|---|---|---|---|
| `GET` | `/api/rooms/{roomId}/playlist` | عرض قائمة الانتظار | عضو فقط |
| `POST` | `/api/rooms/{roomId}/playlist` | إضافة فيديو | حسب صلاحية التحكم |
| `POST` | `/api/rooms/{roomId}/playlist/{itemId}/play` | تشغيل عنصر محدد | حسب صلاحية التحكم |
| `POST` | `/api/rooms/{roomId}/playlist/next` | تشغيل الفيديو التالي | حسب صلاحية التحكم |
| `POST` | `/api/rooms/{roomId}/playlist/{itemId}/skip` | تخطي عنصر | حسب صلاحية التحكم |
| `DELETE` | `/api/rooms/{roomId}/playlist/{itemId}` | حذف عنصر | المضيف فقط |

### مثال إضافة فيديو

```json
{
  "url": "https://cdn.example.com/video.mp4",
  "title": "فيديو تجريبي"
}
```

## 6. التحقق من المدخلات

تم استخدام `jakarta.validation` داخل DTOs:

- `@NotBlank` للنصوص المطلوبة.
- `@Size` لمنع الحقول الطويلة.
- `@NotNull` للقيم الضرورية.
- `@DecimalMin` و `@Digits` لموضع الفيديو.
- `@Pattern` لكود الدعوة.

كما تستمر الخدمات باستخدام:

- `InputSanitizer`
- `VideoSourceService`
- صلاحيات `RoomPermissionService`

## 7. شكل الخطأ الموحد

عند خطأ تحقق أو صلاحيات ترجع الاستجابة بشكل موحد:

```json
{
  "timestamp": "2026-10-01T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "تحقق من الحقول المرسلة",
  "path": "/api/rooms",
  "validationErrors": {
    "name": "اسم الغرفة مطلوب"
  }
}
```

## 8. قرارات تصميمية مهمة

- أبقيت الـ Controllers رفيعة، والمنطق الحقيقي في Services حتى يسهل استخدامه لاحقًا من WebSocket أيضًا.
- فصلت DTOs عن Entities حتى لا نكشف الحقول الداخلية مثل `passwordHash` أو علاقات JPA الكسولة.
- بعد المرحلة 6، أصبحت هوية المستخدم تُستخرج من Supabase JWT بدل أي header مؤقت.

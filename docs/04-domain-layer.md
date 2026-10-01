# المرحلة 4: Entities و Repositories و Services

تمت إضافة طبقة الدومين الأساسية المطابقة لمخطط Supabase في `supabase/schema.sql`.

## 1. الملفات المضافة

### Common

```text
src/main/java/com/watchparty/common/exception/AppException.java
src/main/java/com/watchparty/common/exception/BadRequestException.java
src/main/java/com/watchparty/common/exception/ConflictException.java
src/main/java/com/watchparty/common/exception/ForbiddenException.java
src/main/java/com/watchparty/common/exception/ResourceNotFoundException.java
src/main/java/com/watchparty/common/model/VideoType.java
src/main/java/com/watchparty/common/validation/InputSanitizer.java
src/main/java/com/watchparty/common/validation/VideoSourceService.java
```

### Profiles

```text
src/main/java/com/watchparty/profile/entity/ProfileEntity.java
src/main/java/com/watchparty/profile/repository/ProfileRepository.java
src/main/java/com/watchparty/profile/service/ProfileService.java
```

### Rooms

```text
src/main/java/com/watchparty/room/entity/ControlMode.java
src/main/java/com/watchparty/room/entity/PlaybackStatus.java
src/main/java/com/watchparty/room/entity/RoomEntity.java
src/main/java/com/watchparty/room/repository/RoomRepository.java
src/main/java/com/watchparty/room/service/RoomPermissionService.java
src/main/java/com/watchparty/room/service/RoomService.java
```

### Members

```text
src/main/java/com/watchparty/member/entity/MemberRole.java
src/main/java/com/watchparty/member/entity/RoomMemberEntity.java
src/main/java/com/watchparty/member/repository/RoomMemberRepository.java
src/main/java/com/watchparty/member/service/RoomMemberService.java
```

### Messages

```text
src/main/java/com/watchparty/message/entity/MessageEntity.java
src/main/java/com/watchparty/message/entity/MessageType.java
src/main/java/com/watchparty/message/repository/MessageRepository.java
src/main/java/com/watchparty/message/service/MessageService.java
```

### Playlist

```text
src/main/java/com/watchparty/playlist/entity/PlaylistItemEntity.java
src/main/java/com/watchparty/playlist/entity/PlaylistStatus.java
src/main/java/com/watchparty/playlist/repository/PlaylistItemRepository.java
src/main/java/com/watchparty/playlist/service/PlaylistService.java
```

### Security تعديل بسيط

```text
src/main/java/com/watchparty/security/SecurityConfig.java
```

تمت إضافة `PasswordEncoder` من نوع BCrypt لاستخدامه في كلمات سر الغرف فقط.

## 2. ملخص الكيانات

| Entity | جدول Supabase | المسؤولية |
|---|---|---|
| `ProfileEntity` | `profiles` | ملف المستخدم المرتبط بـ Supabase Auth. |
| `RoomEntity` | `rooms` | الغرفة وحالة الفيديو الحالية والمضيف وصلاحية التحكم. |
| `RoomMemberEntity` | `room_members` | عضوية المستخدم وحالة الاتصال والصلاحيات. |
| `MessageEntity` | `messages` | رسائل الدردشة والإيموجي ورسائل النظام. |
| `PlaylistItemEntity` | `playlist_items` | قائمة انتظار الفيديوهات. |

## 3. Enums

```text
VideoType: YOUTUBE, MP4
ControlMode: HOST_ONLY, MEMBERS_WITH_PERMISSION, EVERYONE
PlaybackStatus: PLAYING, PAUSED
MemberRole: HOST, MEMBER
MessageType: CHAT, EMOJI, SYSTEM
PlaylistStatus: QUEUED, PLAYING, PLAYED, SKIPPED
```

## 4. الخدمات المضافة

### `ProfileService`

- جلب ملف شخصي أو رمي خطأ واضح.
- تحديث الاسم الظاهر والصورة مع تنظيف أولي للمدخلات.

### `RoomPermissionService`

طبقة مركزية لفحص الصلاحيات:

- التحقق من أن الغرفة مفتوحة.
- التحقق من العضوية.
- التحقق من المضيف.
- التحقق من صلاحية التحكم بالتشغيل حسب `control_mode`.

> قرار تصميمي: فصل الصلاحيات في خدمة مستقلة يمنع تكرار شروط المضيف والتحكم داخل كل Service، وسيسهل ربطها لاحقًا بأمان REST و WebSocket.

### `RoomService`

- إنشاء غرفة مع كود دعوة فريد.
- تحديث إعدادات الغرفة.
- تحديث حالة التشغيل `PLAYING/PAUSED` والموضع.
- تغيير الفيديو الحالي.
- نقل الإدارة.
- إغلاق الغرفة منطقيًا عبر `closed_at`.

### `RoomMemberService`

- الانضمام للغرفة والتحقق من كلمة السر إن وجدت.
- قائمة الأعضاء وقائمة المتصلين.
- تعليم العضو كمتصل أو غير متصل.
- مغادرة الغرفة.
- طرد عضو.
- منح/سحب صلاحية التحكم.

### `MessageService`

- إرسال رسالة دردشة.
- إرسال إيموجي.
- إرسال رسالة نظام من backend.
- جلب آخر 50 رسالة بترتيب مناسب للواجهة.
- حذف منطقي للرسائل بواسطة المضيف.

### `PlaylistService`

- إضافة فيديو إلى قائمة الانتظار.
- عرض قائمة الانتظار.
- تشغيل عنصر محدد.
- تشغيل التالي تلقائيًا.
- تخطي عنصر.
- حذف عنصر بواسطة المضيف.

## 5. التحقق والتنظيف

- `InputSanitizer` ينظف النصوص الأساسية ويمنع المحارف التحكمية والطول الزائد.
- `VideoSourceService` يقبل فقط:
  - روابط YouTube.
  - روابط MP4 مباشرة.
- كلمات سر الغرف يتم تشفيرها بـ BCrypt.

> حماية XSS الشاملة، تحديد معدل الرسائل، وتنظيف HTML النهائي ستُستكمل في المرحلة 11 كما هو مخطط.

## 6. ملاحظات مهمة

- لم تتم إضافة REST Controllers في هذه المرحلة؛ ستكون في المرحلة 5.
- لم يتم تفعيل JWT الكامل هنا؛ سيكون في المرحلة 6.
- اعتمدت الكيانات على `jakarta.persistence` المتوافق مع Spring Boot 3.
- Hibernate لا ينشئ الجداول لأن `spring.jpa.hibernate.ddl-auto=none`، ويبقى `supabase/schema.sql` هو مصدر الحقيقة.

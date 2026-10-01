# المرحلة 6: أمان Supabase JWT في REST و WebSocket

تم استبدال header المؤقت `X-User-Id` بمصادقة حقيقية عبر Supabase JWT، مع تأمين WebSocket handshake و STOMP CONNECT، وتجهيز خدمة مركزية لصلاحيات أحداث WebSocket.

## 1. طريقة المصادقة في REST

كل endpoints تحت:

```text
/api/**
```

تتطلب الآن:

```http
Authorization: Bearer <supabase-access-token>
```

مثال:

```bash
curl -X POST http://localhost:8080/api/rooms \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $SUPABASE_ACCESS_TOKEN" \
  -d '{"name":"سهرة الفيلم","publicRoom":true,"controlMode":"HOST_ONLY"}'
```

## 2. التحقق من JWT

الملف المسؤول:

```text
src/main/java/com/watchparty/security/SupabaseJwtService.java
```

يتحقق من:

- وجود `SUPABASE_JWT_SECRET` من متغيرات البيئة.
- أن JWT مكوّن من 3 أجزاء.
- أن الخوارزمية `HS256`.
- صحة توقيع HMAC SHA-256 باستخدام `SUPABASE_JWT_SECRET`.
- وجود `exp` وعدم انتهاء الرمز.
- وجود `sub` وأنه UUID صالح، وهو معرف المستخدم في Supabase Auth.

> قرار تصميمي: استخدمت تحقق HMAC مباشرًا بدل ربط التطبيق بـ JWKS لأن Supabase الافتراضي يستخدم JWT موقّعًا بـ HS256 و `JWT Secret` الخاص بالمشروع.

## 3. ملفات أمان REST الجديدة أو المعدلة

```text
src/main/java/com/watchparty/security/SupabaseUserPrincipal.java
src/main/java/com/watchparty/security/SupabaseJwtAuthenticationException.java
src/main/java/com/watchparty/security/SupabaseJwtService.java
src/main/java/com/watchparty/security/SupabaseJwtAuthenticationFilter.java
src/main/java/com/watchparty/security/RestAuthenticationEntryPoint.java
src/main/java/com/watchparty/security/RestAccessDeniedHandler.java
src/main/java/com/watchparty/security/CurrentUserService.java
src/main/java/com/watchparty/security/SecurityConfig.java
src/main/java/com/watchparty/config/CorsConfig.java
```

## 4. إعداد Spring Security

- `GET /health` والملفات الثابتة مسموحة بدون JWT.
- `GET /api/rooms/public` و `GET /api/rooms/invite/**` مسموحة بدون JWT لأنها تعرض بيانات عامة غير حساسة.
- باقي `/api/**` يتطلب JWT.
- `/ws/**` و `/ws-sockjs/**` مسموحة على مستوى HTTP لأن WebSocket يحتاج تحققًا خاصًا في handshake و STOMP CONNECT.
- الجلسات أصبحت `STATELESS`.
- أخطاء 401 و 403 ترجع JSON موحدًا.

## 5. WebSocket handshake

تمت إضافة:

```text
src/main/java/com/watchparty/realtime/config/WebSocketAuthHandshakeInterceptor.java
src/main/java/com/watchparty/realtime/config/WebSocketAuthChannelInterceptor.java
src/main/java/com/watchparty/realtime/config/WebSocketSecurityConfig.java
```

يدعم WebSocket طريقتين لإرسال الرمز:

### 5.1 عبر query string

```text
/ws?access_token=<supabase-access-token>
```

أو:

```text
/ws?token=<supabase-access-token>
```

### 5.2 عبر STOMP CONNECT headers

```json
{
  "Authorization": "Bearer <supabase-access-token>"
}
```

أو:

```json
{
  "access_token": "<supabase-access-token>"
}
```

> التعليق بالعربية: المتصفح لا يسمح دائمًا بإرسال Authorization header أثناء WebSocket handshake، لذلك قبلنا query param في المصافحة. وإذا لم يُرسل الرمز في handshake، يجب إرساله داخل STOMP CONNECT وإلا يُرفض الاتصال المنطقي فورًا.

## 6. نقاط WebSocket الحالية

تم تجهيز endpoints الأمان التالية:

```text
/ws
/ws-sockjs
```

مع broker مبدئي:

```text
/topic
/queue
/app
/user
```

تفاصيل بروتوكول الرسائل JSON مثل `PLAY`, `PAUSE`, `SEEK`, `CHAT`, `SYNC_STATE` تم تنفيذها وتوثيقها في `docs/07-stomp-protocol.md`.

## 7. صلاحيات أحداث WebSocket

تمت إضافة:

```text
src/main/java/com/watchparty/realtime/dto/RealtimeEventType.java
src/main/java/com/watchparty/realtime/service/RealtimeAuthorizationService.java
```

هذه الخدمة يجب أن تُستدعى قبل بث أي حدث لحظي في المرحلة 7.

| الحدث | الصلاحية المطلوبة |
|---|---|
| `PLAY`, `PAUSE`, `SEEK`, `CHANGE_VIDEO`, `PLAY_NEXT` | صلاحية التحكم حسب `control_mode` |
| `KICK_MEMBER`, `TRANSFER_HOST`, `SET_CONTROL_PERMISSION` | المضيف فقط |
| `JOIN`, `LEAVE`, `CHAT`, `EMOJI`, `SYNC_STATE` | عضو فعّال في الغرفة |
| `WEBRTC_OFFER`, `WEBRTC_ANSWER`, `WEBRTC_ICE_CANDIDATE` | عضو فعّال في الغرفة |

> قرار تصميمي: جعلت صلاحيات WebSocket في خدمة مركزية حتى لا تختلف صلاحيات REST عن صلاحيات الأحداث اللحظية.

## 8. متغيرات البيئة المطلوبة

```bash
export SUPABASE_JWT_SECRET='<YOUR_SUPABASE_JWT_SECRET>'
export SUPABASE_URL='https://<PROJECT_REF>.supabase.co'
export SUPABASE_ANON_KEY='<YOUR_SUPABASE_ANON_KEY>'
```

ويجب أيضًا ضبط اتصال قاعدة البيانات كما في المرحلة 3.

## 9. CORS

تمت إضافة `CorsConfig`، والافتراضي الآن:

```text
http://localhost:8080,http://localhost:3000,https://*.e2b.app
```

يمكن تغييره عبر:

```bash
export CORS_ALLOWED_ORIGINS='https://your-domain.com,https://*.e2b.app'
```

## 10. ما لم يتم بعد

- تم تنفيذ WebSocket Controllers و DTOs بروتوكول الرسائل في المرحلة 7.
- لم نضف الواجهات HTML/JS؛ هذا في المرحلة 8.
- لم نضف rate limiting و XSS النهائي؛ هذا في المرحلة 11.

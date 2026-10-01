# المرحلة 11: الحماية — XSS/CSRF، تحديد معدل الرسائل، تنظيف المدخلات

تمت إضافة طبقات حماية إضافية على مستوى Backend و Frontend بدون تخزين أي أسرار داخل الكود.

## 1. الملفات الجديدة أو المعدلة

### Rate limiting

```text
src/main/java/com/watchparty/common/ratelimit/RateLimiterService.java
src/main/java/com/watchparty/message/service/MessageService.java
src/main/java/com/watchparty/webrtc/controller/WebRtcSignalingController.java
```

### Security headers و CSRF origin guard

```text
src/main/java/com/watchparty/security/filter/SecurityHeadersFilter.java
src/main/java/com/watchparty/security/filter/UnsafeRequestOriginFilter.java
```

### تنظيف المدخلات

```text
src/main/java/com/watchparty/common/validation/InputSanitizer.java
```

### توثيق

```text
docs/11-security-hardening.md
README.md
```

## 2. حماية XSS

### 2.1 Backend sanitization

تم تحسين `InputSanitizer` ليقوم بـ:

- Unicode normalization باستخدام `NFKC`.
- إزالة وسوم HTML مثل `<script>` و `<img onerror=...>`.
- تعطيل المخططات الخطرة مثل:
  - `javascript:`
  - `data:`
  - `vbscript:`
- إزالة محارف التحكم.
- إزالة محارف direction override التي قد تُستخدم للتلاعب بعرض النص.
- إزالة zero-width characters الشائعة.
- ضغط المسافات المتكررة.
- تطبيق حدود الطول قبل الحفظ.

> قرار تصميمي: المشروع يعامل أسماء الغرف والرسائل والدردشة كنص Plain Text، وليس HTML. لذلك الأفضل إزالة HTML من الأساس بدل محاولة السماح بجزء منه.

### 2.2 Frontend escaping

الواجهة لا تستخدم `innerHTML` مع مدخلات المستخدم إلا بعد تمريرها عبر:

```js
WatchPartyUi.escapeHtml(...)
```

وبذلك لو وصلت قيمة خطرة من API فلن تُفسّر كـ HTML داخل المتصفح.

### 2.3 Content Security Policy

تمت إضافة `SecurityHeadersFilter` لإرسال:

```text
Content-Security-Policy
X-Content-Type-Options: nosniff
X-Frame-Options: SAMEORIGIN
Referrer-Policy: strict-origin-when-cross-origin
Permissions-Policy
Cross-Origin-Opener-Policy
```

CSP يسمح فقط بالمصادر المطلوبة:

- `self`
- `cdn.jsdelivr.net` لمكتبات Supabase/STOMP/SockJS.
- `youtube.com` و `s.ytimg.com` لمشغل YouTube.
- `*.supabase.co` للاتصال بـ Supabase.
- `ws:` و `wss:` للـ WebSocket.

## 3. حماية CSRF

المشروع يستخدم Supabase JWT في header:

```http
Authorization: Bearer <token>
```

وليس cookie session تلقائية، لذلك الخطر التقليدي لـ CSRF أقل لأن المتصفح لا يرسل Authorization header تلقائيًا من موقع آخر.

مع ذلك تمت إضافة `UnsafeRequestOriginFilter` كطبقة دفاع إضافية:

- يطبق فقط على الطلبات غير الآمنة:
  - `POST`
  - `PUT`
  - `PATCH`
  - `DELETE`
- يطبق على:

```text
/api/**
```

- إذا وُجد header `Origin`، يجب أن يكون:
  - نفس origin الحالي، أو
  - ضمن `CORS_ALLOWED_ORIGINS`.

إذا كان origin غير مسموح، يرجع الخادم:

```http
403 Forbidden
```

> قرار تصميمي: أبقينا Spring CSRF token معطّلًا لأن API stateless ويستخدم Bearer token، لكن أضفنا Origin validation حتى لا تصبح CORS أو proxy misconfiguration نقطة ضعف سهلة.

## 4. تحديد معدل الرسائل

تمت إضافة `RateLimiterService` كـ in-memory fixed window limiter.

### 4.1 الدردشة

داخل `MessageService` أصبح الحد:

```text
20 رسالة لكل مستخدم داخل كل غرفة في الدقيقة
```

المفتاح المستخدم:

```text
message:{roomId}:{userId}
```

إذا تجاوز المستخدم الحد يرجع:

```text
403 Forbidden
```

مع رسالة:

```text
تم تجاوز حد الرسائل: 20 رسالة في الدقيقة
```

### 4.2 WebRTC signaling

لتجنب إساءة استخدام قناة signaling، أضيف حد:

```text
240 رسالة signaling لكل مستخدم داخل كل غرفة في الدقيقة
```

المفتاح المستخدم:

```text
webrtc:{roomId}:{userId}
```

> ملاحظة: هذا limiter in-memory مناسب للبداية أو instance واحدة. عند التوسع لأكثر من instance، الأفضل نقله إلى Redis أو Bucket4j مع backend مشترك.

## 5. تنظيف المدخلات في الخدمات

الخدمات التي تستخدم `InputSanitizer` تستفيد الآن من التنظيف الأقوى تلقائيًا، ومنها:

- أسماء الغرف.
- أسماء المستخدمين.
- رسائل الدردشة.
- رسائل النظام.
- عناوين قائمة الانتظار.

كما بقي `VideoSourceService` مسؤولًا عن قبول روابط:

- YouTube.
- MP4 مباشر.

ورفض المخططات غير الآمنة.

## 6. حماية WebSocket

حماية WebSocket من المراحل السابقة بقيت فعّالة:

- JWT في handshake أو STOMP CONNECT.
- منع الاشتراك في topic غرفة إلا للعضو الفعّال.
- `RealtimeAuthorizationService` لكل حدث حساس.
- Rate limit للدردشة عبر `MessageService`.
- Rate limit لـ WebRTC signaling.

## 7. ملاحظات تشغيلية

### CORS_ALLOWED_ORIGINS

اضبط origins بدقة في الإنتاج:

```bash
CORS_ALLOWED_ORIGINS=https://your-domain.com
```

وفي بيئة Arena/preview يمكن استخدام:

```bash
CORS_ALLOWED_ORIGINS=https://*.e2b.app
```

أو الجمع بينهما:

```bash
CORS_ALLOWED_ORIGINS=https://your-domain.com,https://*.e2b.app
```

### CSP

إذا أضفت CDN جديدًا لاحقًا، يجب تحديث `SecurityHeadersFilter` وإضافة المصدر الموثوق فقط.

## 8. قرارات تصميمية مهمة

- لم أستخدم HTML sanitizer يسمح بعناصر HTML؛ لأن التطبيق لا يحتاج HTML من المستخدم أصلًا.
- استخدمت CSP صارم نسبيًا لكنه يسمح بالمصادر المطلوبة فعليًا للواجهة الحالية.
- استخدمت Origin validation بدل CSRF token لأن REST stateless ويستخدم Bearer JWT وليس cookies.
- جعلت rate limiter بسيطًا وداخليًا الآن حتى يبقى المشروع جاهزًا دون Redis، مع توثيق الحاجة لترقيته عند التوسع.

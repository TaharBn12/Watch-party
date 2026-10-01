# Watch-party

موقع مشاهدة جماعية عربي RTL مبني على Java 17 + Spring Boot 3 + Supabase + STOMP WebSocket + WebRTC.

## حالة التنفيذ

- ✅ المرحلة 1: هيكل المشروع ومخطط قاعدة البيانات.
- ✅ المرحلة 2: ملف `schema.sql` كاملًا لجداول Supabase و RLS والـ Triggers.
- ✅ المرحلة 3: `pom.xml` و `application.properties` و endpoint فحص `/health`.
- ✅ المرحلة 4: Entities و Repositories و Services.
- ✅ المرحلة 5: REST Controllers للغرف والأعضاء وقائمة الانتظار مع التحقق من المدخلات.
- ✅ المرحلة 6: أمان JWT في REST و WebSocket وصلاحيات المضيف في الأحداث.
- ✅ المرحلة 7: إعدادات STOMP وبروتوكول الرسائل JSON.
- ✅ المرحلة 8: صفحات HTML الرئيسية والدخول والغرفة مع CSS و JS.
- ✅ المرحلة 9: منطق المزامنة في JS ومنع حلقات الإرسال اللانهائية.
- ✅ المرحلة 10: WebRTC signaling وواجهة المكالمة.
- ✅ المرحلة 11: الحماية XSS/CSRF وتحديد معدل الرسائل وتنظيف المدخلات.
- ✅ المرحلة 12: اختبارات أساسية وتعليمات النشر عبر GitHub.

## أهم الملفات

- `docs/01-project-structure-and-db-plan.md`: شرح البنية ومخطط قاعدة البيانات التصميمي.
- `docs/02-supabase-schema.md`: شرح تنفيذ ملف SQL في Supabase SQL Editor.
- `docs/03-spring-bootstrap-health.md`: شرح تشغيل Spring Boot وفحص اتصال Supabase.
- `docs/04-domain-layer.md`: شرح الكيانات والمستودعات والخدمات.
- `docs/05-rest-controllers.md`: شرح REST API للغرف والأعضاء وقائمة الانتظار.
- `docs/06-security-jwt-websocket.md`: شرح أمان Supabase JWT و WebSocket.
- `docs/07-stomp-protocol.md`: شرح بروتوكول STOMP ورسائل JSON اللحظية.
- `docs/08-frontend-pages.md`: شرح صفحات HTML/CSS/JS.
- `docs/09-js-synchronization.md`: شرح مزامنة الفيديو في JavaScript.
- `docs/10-webrtc.md`: شرح WebRTC signaling وواجهة المكالمة.
- `docs/11-security-hardening.md`: شرح XSS/CSRF و rate limiting وتنظيف المدخلات.
- `docs/12-tests-and-github-deployment.md`: الاختبارات والنشر عبر GitHub إلى Render/Railway.
- `supabase/schema.sql`: الجداول، الفهارس، RLS Policies، ودوال/Triggers Supabase.
- `pom.xml`: إعداد Maven واعتماديات Spring Boot.
- `Dockerfile`: بناء صورة Docker للنشر.
- `render.yaml`: Blueprint جاهز لـ Render.
- `.github/workflows/ci.yml`: GitHub Actions CI.
- `src/main/resources/application.properties`: إعدادات التطبيق من متغيرات البيئة فقط.
- `src/main/resources/static`: صفحات وملفات الواجهة.
- `src/main/java/com/watchparty`: كود Spring Boot.
- `src/test/java/com/watchparty`: اختبارات أساسية.

## تشغيل المشروع محليًا

1. نفّذ `supabase/schema.sql` داخل Supabase SQL Editor.
2. اضبط متغيرات البيئة:

```bash
export SPRING_DATASOURCE_URL='jdbc:postgresql://<POOLER_HOST>:6543/postgres?sslmode=require'
export SPRING_DATASOURCE_USERNAME='postgres.<PROJECT_REF>'
export SPRING_DATASOURCE_PASSWORD='<YOUR_DATABASE_PASSWORD>'

export SUPABASE_URL='https://<PROJECT_REF>.supabase.co'
export SUPABASE_ANON_KEY='<YOUR_SUPABASE_ANON_KEY>'
export SUPABASE_JWT_SECRET='<YOUR_SUPABASE_JWT_SECRET>'
export CORS_ALLOWED_ORIGINS='http://localhost:8080'
```

3. شغّل:

```bash
mvn spring-boot:run
```

4. افتح:

```text
http://localhost:8080/
```

ولفحص الاتصال:

```text
http://localhost:8080/health
```

## تشغيل الاختبارات

```bash
mvn test
```

كما يفحص GitHub Actions ملفات JavaScript و Maven tests تلقائيًا عند push أو Pull Request.

## النشر عبر GitHub

### Render

1. اربط مستودع GitHub بـ Render.
2. استخدم `render.yaml` أو أنشئ Web Service من `Dockerfile`.
3. اضبط متغيرات البيئة الحساسة من لوحة Render.
4. افتح `/health` بعد النشر.

### Railway

1. اختر Deploy from GitHub repo.
2. Railway سيكتشف `Dockerfile`.
3. اضبط نفس متغيرات البيئة.
4. افتح `/health` بعد النشر.

راجع التفاصيل الكاملة في:

```text
docs/12-tests-and-github-deployment.md
```

## WebSocket / STOMP

Endpoint:

```text
/ws
```

أهم الوجهات:

```text
SUBSCRIBE /topic/rooms/{roomId}/events
SUBSCRIBE /user/queue/rooms/{roomId}/sync
SUBSCRIBE /user/queue/rooms/{roomId}/webrtc
SUBSCRIBE /user/queue/errors

SEND /app/rooms/{roomId}/join
SEND /app/rooms/{roomId}/chat
SEND /app/rooms/{roomId}/play
SEND /app/rooms/{roomId}/pause
SEND /app/rooms/{roomId}/seek
SEND /app/rooms/{roomId}/video
SEND /app/rooms/{roomId}/sync
SEND /app/rooms/{roomId}/leave

SEND /app/rooms/{roomId}/webrtc/join
SEND /app/rooms/{roomId}/webrtc/leave
SEND /app/rooms/{roomId}/webrtc/offer
SEND /app/rooms/{roomId}/webrtc/answer
SEND /app/rooms/{roomId}/webrtc/ice-candidate
```

## ملاحظات أمان

- لا تضع الأسرار داخل GitHub أو داخل الملفات.
- استخدم متغيرات البيئة للـ DB password و JWT secret و TURN password.
- `SUPABASE_ANON_KEY` يُرسل للواجهة لأنه مفتاح عام، لكنه لا يُكتب داخل المستودع.

# المرحلة 12: اختبارات أساسية وتعليمات النشر عبر GitHub

هذه المرحلة تضيف اختبارات أساسية، CI عبر GitHub Actions، وملفات نشر مناسبة لـ Render/Railway عن طريق ربط مستودع GitHub.

## 1. الملفات الجديدة

### اختبارات

```text
src/test/java/com/watchparty/common/validation/InputSanitizerTest.java
src/test/java/com/watchparty/common/validation/VideoSourceServiceTest.java
src/test/java/com/watchparty/common/ratelimit/RateLimiterServiceTest.java
src/test/java/com/watchparty/security/SupabaseJwtServiceTest.java
```

### GitHub Actions

```text
.github/workflows/ci.yml
```

### Docker / Render

```text
Dockerfile
.dockerignore
render.yaml
```

### توثيق

```text
docs/12-tests-and-github-deployment.md
```

كما تم تعديل:

```text
src/main/resources/application.properties
README.md
```

لإضافة:

```properties
server.forward-headers-strategy=framework
```

وذلك مهم عند النشر خلف proxy مثل Render أو Railway حتى يعمل فحص Origin والروابط بشكل صحيح.

## 2. الاختبارات المضافة

### 2.1 `InputSanitizerTest`

يتحقق من:

- إزالة HTML.
- تعطيل `javascript:`.
- رفض النصوص الطويلة.
- إرجاع `null` للنص الاختياري الفارغ.

### 2.2 `VideoSourceServiceTest`

يتحقق من:

- قبول YouTube.
- قبول MP4 مباشر.
- رفض امتدادات غير مدعومة مثل `m3u8`.
- رفض مخططات غير آمنة مثل `javascript:`.

### 2.3 `RateLimiterServiceTest`

يتحقق من منع الطلبات بعد تجاوز الحد داخل نافذة زمنية.

### 2.4 `SupabaseJwtServiceTest`

ينشئ JWT اختباريًا بتوقيع `HS256` ويتحقق من:

- قبول الرمز الصحيح.
- استخراج `sub` كمستخدم.
- رفض الرمز المنتهي.

## 3. GitHub Actions CI

الملف:

```text
.github/workflows/ci.yml
```

ينفذ تلقائيًا عند:

- push إلى `arena/01a0f706-watch-party`.
- push إلى `main`.
- Pull Request نحو `main` أو `arena/01a0f706-watch-party`.

خطوات CI:

1. Checkout.
2. إعداد Java 17.
3. إعداد Node.js 20.
4. فحص ملفات JavaScript عبر:

```bash
node --check src/main/resources/static/assets/js/*.js
```

5. تشغيل اختبارات Maven:

```bash
mvn -B test
```

6. بناء الحزمة:

```bash
mvn -B -DskipTests package
```

## 4. Dockerfile

أضفت Dockerfile متعدد المراحل:

- مرحلة build تستخدم Maven + Temurin 17.
- مرحلة runtime تستخدم Temurin 17 JRE فقط.
- التطبيق يشغّل:

```bash
java ${JAVA_OPTS:-} -jar /app/app.jar
```

هذا مناسب لـ Render و Railway وأي منصة تقرأ Dockerfile من GitHub.

## 5. النشر عبر GitHub إلى Render

### 5.1 الخطوات

1. ادفع الكود إلى GitHub.
2. افتح Render.
3. اختر **New +** ثم **Blueprint** إذا أردت استخدام `render.yaml`، أو **Web Service** إذا أردت اختيار المستودع يدويًا.
4. اربط مستودع GitHub:

```text
TaharBn12/Watch-party
```

5. اختر الفرع:

```text
arena/01a0f706-watch-party
```

أو `main` بعد دمج Pull Request.

6. Render سيقرأ:

```text
render.yaml
Dockerfile
```

7. أضف متغيرات البيئة الحساسة من لوحة Render، وليس داخل GitHub.

### 5.2 متغيرات Render المطلوبة

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://<POOLER_HOST>:6543/postgres?sslmode=require
SPRING_DATASOURCE_USERNAME=postgres.<PROJECT_REF>
SPRING_DATASOURCE_PASSWORD=<YOUR_DATABASE_PASSWORD>

SUPABASE_URL=https://<PROJECT_REF>.supabase.co
SUPABASE_ANON_KEY=<YOUR_SUPABASE_ANON_KEY>
SUPABASE_JWT_SECRET=<YOUR_SUPABASE_JWT_SECRET>

CORS_ALLOWED_ORIGINS=https://<YOUR_RENDER_SERVICE>.onrender.com
```

### 5.3 WebRTC TURN اختياري

```bash
WEBRTC_STUN_URLS=stun:stun.l.google.com:19302,stun:stun1.l.google.com:19302
WEBRTC_TURN_URLS=turn:your-turn.example.com:3478
WEBRTC_TURN_USERNAME=turn-user
WEBRTC_TURN_PASSWORD=turn-password
```

### 5.4 فحص النشر

بعد النشر افتح:

```text
https://<YOUR_RENDER_SERVICE>.onrender.com/health
```

الحالة المطلوبة:

```text
UP
```

إذا ظهرت:

```text
DEGRADED
```

فهذا يعني أن الاتصال بقاعدة البيانات صحيح لكن جداول `schema.sql` غير منفذة أو ناقصة.

إذا ظهرت:

```text
DOWN
```

فراجع متغيرات قاعدة البيانات و `sslmode=require`.

## 6. النشر عبر GitHub إلى Railway

1. افتح Railway.
2. اختر **New Project**.
3. اختر **Deploy from GitHub repo**.
4. اختر:

```text
TaharBn12/Watch-party
```

5. اختر الفرع المطلوب.
6. Railway سيكتشف `Dockerfile` ويبني الصورة.
7. أضف نفس متغيرات البيئة المذكورة أعلاه.
8. اضبط:

```bash
CORS_ALLOWED_ORIGINS=https://<YOUR_RAILWAY_DOMAIN>
```

9. افتح:

```text
https://<YOUR_RAILWAY_DOMAIN>/health
```

## 7. إعداد Supabase قبل النشر

قبل تشغيل التطبيق المنشور:

1. افتح Supabase SQL Editor.
2. نفذ:

```text
supabase/schema.sql
```

3. تأكد من تفعيل Auth providers التي تريدها.
4. إن أردت دخول الضيف، فعّل Anonymous Sign-ins من Supabase Auth.
5. أضف رابط موقعك المنشور في Supabase Auth URL settings إذا استخدمت redirects لاحقًا.

## 8. أسرار GitHub

حاليًا لا يحتاج GitHub Actions إلى أسرار لأن CI يشغل اختبارات unit ولا يتصل بـ Supabase الحقيقي.

لا تضع القيم التالية داخل GitHub repository:

```text
SUPABASE_JWT_SECRET
SPRING_DATASOURCE_PASSWORD
WEBRTC_TURN_PASSWORD
```

إذا أردت لاحقًا نشرًا آليًا من GitHub Actions إلى Render Deploy Hook، ضع deploy hook داخل GitHub Secrets باسم مثل:

```text
RENDER_DEPLOY_HOOK_URL
```

لكن هذا غير مطلوب في النسخة الحالية لأن Render/Railway يستطيعان النشر مباشرة من GitHub integration.

## 9. أوامر التشغيل المحلي

بعد ضبط المتغيرات:

```bash
mvn spring-boot:run
```

أو عبر Docker:

```bash
docker build -t watch-party .
docker run --rm -p 8080:8080 \
  -e SPRING_DATASOURCE_URL='jdbc:postgresql://<POOLER_HOST>:6543/postgres?sslmode=require' \
  -e SPRING_DATASOURCE_USERNAME='postgres.<PROJECT_REF>' \
  -e SPRING_DATASOURCE_PASSWORD='<YOUR_DATABASE_PASSWORD>' \
  -e SUPABASE_URL='https://<PROJECT_REF>.supabase.co' \
  -e SUPABASE_ANON_KEY='<YOUR_SUPABASE_ANON_KEY>' \
  -e SUPABASE_JWT_SECRET='<YOUR_SUPABASE_JWT_SECRET>' \
  -e CORS_ALLOWED_ORIGINS='http://localhost:8080' \
  watch-party
```

ثم افتح:

```text
http://localhost:8080/
```

## 10. التحقق بعد النشر

اختبر بالترتيب:

1. `/health` يعطي `UP`.
2. الصفحة الرئيسية تفتح.
3. صفحة الدخول تقرأ إعدادات Supabase من `/api/public/config`.
4. إنشاء حساب أو دخول كضيف.
5. إنشاء غرفة.
6. فتح الغرفة من متصفح ثانٍ.
7. تجربة الدردشة.
8. تجربة YouTube أو MP4.
9. تجربة WebSocket sync.
10. تجربة مكالمة WebRTC محليًا أو بين جهازين.

## 11. قرارات تصميمية مهمة

- أضفت Dockerfile حتى يكون النشر من GitHub موحدًا بين Render و Railway.
- أبقيت الأسرار خارج GitHub Actions وداخل لوحة المنصة المستضيفة.
- أضفت CI يفحص JavaScript ويشغل Maven tests، حتى يظهر أي خطأ قبل الدمج أو النشر.

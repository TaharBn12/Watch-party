# المرحلة 3: Spring Boot + Maven + فحص الاتصال بـ Supabase

تمت إضافة ملفات تشغيل Spring Boot الأساسية و endpoint `/health` لفحص الاتصال بقاعدة Supabase PostgreSQL.

## الملفات الجديدة

```text
pom.xml
.env.example
.gitignore
src/main/java/com/watchparty/WatchPartyApplication.java
src/main/java/com/watchparty/config/SupabaseProperties.java
src/main/java/com/watchparty/security/SecurityConfig.java
src/main/java/com/watchparty/common/dto/HealthResponse.java
src/main/java/com/watchparty/common/controller/HealthController.java
src/main/resources/application.properties
docs/03-spring-bootstrap-health.md
```

## متغيرات البيئة المطلوبة

يجب ضبط هذه القيم قبل تشغيل التطبيق حتى يفحص `/health` اتصال Supabase الحقيقي. عند غيابها سيستخدم التطبيق رابط PostgreSQL محليًا غير حساس فقط كي يستطيع الإقلاع، وسيعرض `/health` حالة `DOWN` لقسم قاعدة البيانات:

```bash
export SPRING_DATASOURCE_URL='jdbc:postgresql://<POOLER_HOST>:6543/postgres?sslmode=require'
export SPRING_DATASOURCE_USERNAME='postgres.<PROJECT_REF>'
export SPRING_DATASOURCE_PASSWORD='<YOUR_DATABASE_PASSWORD>'

export SUPABASE_URL='https://<PROJECT_REF>.supabase.co'
export SUPABASE_ANON_KEY='<YOUR_SUPABASE_ANON_KEY>'
export SUPABASE_JWT_SECRET='<YOUR_SUPABASE_JWT_SECRET>'
```

> استخدم Connection Pooler من Supabase مع `sslmode=require`. لا تستخدم كلمة مرور قاعدة البيانات أو JWT Secret داخل `application.properties`.

## التشغيل محليًا

```bash
mvn spring-boot:run
```

ثم افتح:

```text
http://localhost:8080/health
```

## شكل استجابة `/health`

عند نجاح الاتصال ووجود جداول المرحلة 2، تكون الاستجابة شبيهة بـ:

```json
{
  "status": "UP",
  "checkedAt": "2026-10-01T10:00:00Z",
  "application": {
    "name": "watch-party",
    "version": "dev",
    "javaVersion": "17..."
  },
  "supabase": {
    "urlConfigured": true,
    "anonKeyConfigured": true,
    "jwtSecretConfigured": true
  },
  "database": {
    "connected": true,
    "schemaReady": true,
    "databaseName": "postgres",
    "schemaName": "public",
    "serverTime": "2026-10-01 ...",
    "latencyMs": 120,
    "requiredTables": {
      "profiles": true,
      "rooms": true,
      "room_members": true,
      "messages": true,
      "playlist_items": true
    },
    "error": null
  }
}
```

## حالات الصحة

- `UP`: الاتصال بـ Supabase ناجح، وكل جداول المرحلة 2 موجودة.
- `DEGRADED`: الاتصال ناجح، لكن بعض الجداول المطلوبة غير موجودة؛ شغّل `supabase/schema.sql`.
- `DOWN`: فشل الاتصال بقاعدة البيانات أو متغيرات الاتصال غير صحيحة.

## قرارات تصميمية مهمة

- أبقينا إنشاء الجداول خارج Hibernate عبر `spring.jpa.hibernate.ddl-auto=none` لأن `supabase/schema.sql` هو المصدر الرسمي للمخطط.
- أضفنا Spring Security الآن، لكن الإعداد مؤقت ويسمح بالوصول إلى `/health` والملفات الثابتة؛ التحقق الكامل من JWT سيكون في المرحلة 6.
- endpoint `/health` لا يعرض أي أسرار؛ يعرض فقط هل متغيرات Supabase مضبوطة أم لا، ولا يعيد قيمها.

# المرحلة 2: تنفيذ مخطط Supabase

تم إنشاء ملف SQL الكامل في:

```text
supabase/schema.sql
```

## طريقة التنفيذ في Supabase SQL Editor

1. افتح لوحة Supabase الخاصة بالمشروع.
2. من القائمة الجانبية اختر **SQL Editor**.
3. افتح ملف `supabase/schema.sql` من هذا المستودع.
4. انسخ محتواه كاملًا والصقه في SQL Editor.
5. اضغط **Run**.
6. بعد التنفيذ تأكد من وجود الجداول التالية في **Table Editor**:
   - `profiles`
   - `rooms`
   - `room_members`
   - `messages`
   - `playlist_items`

> لا تضع كلمة مرور قاعدة البيانات أو JWT Secret أو أي مفتاح حساس في SQL Editor. هذا الملف لا يحتاج أسرارًا.

## ما الذي ينشئه الملف؟

### الجداول

- `profiles`: ملف المستخدم المرتبط مباشرة بـ `auth.users`.
- `rooms`: الغرف وحالة الفيديو الحالية والمضيف وصلاحية التحكم.
- `room_members`: عضوية المستخدمين داخل الغرف وحالة الاتصال والصلاحيات.
- `messages`: رسائل الدردشة ورسائل النظام.
- `playlist_items`: قائمة انتظار الفيديوهات وترتيب التشغيل.

### الفهارس

تمت إضافة فهارس للعمليات الأكثر تكرارًا:

- فتح الغرفة عبر `invite_code`.
- جلب أعضاء غرفة معينة.
- جلب آخر 50 رسالة عبر `room_id, created_at desc`.
- ترتيب قائمة الانتظار عبر `room_id, position`.
- إيجاد الفيديو التالي عبر `room_id, status, position`.

### Triggers

- `on_auth_user_created`: ينشئ `profile` تلقائيًا عند تسجيل مستخدم في Supabase Auth.
- `set_profiles_updated_at`: يحدث `profiles.updated_at` تلقائيًا.
- `set_rooms_updated_at`: يحدث `rooms.updated_at` تلقائيًا.
- `set_rooms_invite_code`: يولد كود دعوة إن لم يرسله التطبيق.
- `sync_rooms_host_membership`: يضمن أن المضيف عضو في الغرفة، ويحدث العضوية عند نقل الإدارة.

### RLS Policies

تم تفعيل Row Level Security على كل الجداول.

السياسات الأساسية:

- المستخدم يقرأ ملفه الشخصي أو ملفات أعضاء غرفته فقط.
- العضو يقرأ الغرفة التي ينتمي إليها فقط.
- المضيف فقط يعدل الغرفة أو ينقل الإدارة.
- أعضاء الغرفة فقط يقرؤون الرسائل وقائمة الانتظار.
- الرسائل المباشرة من العميل محصورة في `CHAT` و `EMOJI`، أما `SYSTEM` فينشئها الـ Backend.
- الانضمام المباشر عبر Supabase مسموح فقط للغرف العامة بلا كلمة سر؛ الغرف المحمية يجب أن تمر عبر Backend للتحقق من كلمة السر.

## قرارات تصميمية مهمة

- استخدمت قيود `CHECK` بدل PostgreSQL `ENUM` حتى يسهل تعديل القيم لاحقًا بدون migrations معقدة.
- دوال فحص العضوية والصلاحيات تعمل بـ `SECURITY DEFINER` لتجنب مشاكل recursion داخل RLS عند قراءة `room_members` من سياسات نفس الجدول.
- إغلاق الغرفة يتم عبر `closed_at` بدل حذف الصف، حتى نحافظ على السجل ونمنع كسر العلاقات.
- كلمة سر الغرفة لا تخزن كنص خام؛ الحقل اسمه `password_hash` وسيتم توليد hash في Backend لاحقًا.

## ملاحظة للمرحلة التالية

في المرحلة الثالثة سنضيف:

- `pom.xml`
- `application.properties`
- endpoint `/health`
- إعداد اتصال Spring Boot بقاعدة Supabase عبر Connection Pooler مع `sslmode=require`

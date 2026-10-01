# المرحلة 8: صفحات HTML و CSS و JavaScript

تمت إضافة واجهة عربية RTL متجاوبة بدون React، مكوّنة من الصفحة الرئيسية، صفحة الدخول، وصفحة الغرفة.

## 1. الملفات الجديدة

### صفحات HTML

```text
src/main/resources/static/index.html
src/main/resources/static/login.html
src/main/resources/static/room.html
```

### CSS

```text
src/main/resources/static/assets/css/styles.css
```

### JavaScript مشترك

```text
src/main/resources/static/assets/js/config.js
src/main/resources/static/assets/js/ui.js
src/main/resources/static/assets/js/auth.js
src/main/resources/static/assets/js/api.js
```

### JavaScript الصفحات

```text
src/main/resources/static/assets/js/index.js
src/main/resources/static/assets/js/login.js
src/main/resources/static/assets/js/video-player.js
src/main/resources/static/assets/js/room.js
```

### Endpoint إعدادات عامة للواجهة

```text
src/main/java/com/watchparty/common/dto/PublicAppConfigResponse.java
src/main/java/com/watchparty/common/controller/PublicConfigController.java
```

وتم تعديل:

```text
src/main/java/com/watchparty/security/SecurityConfig.java
```

للسماح بـ:

```text
GET /api/public/config
```

بدون JWT.

## 2. لماذا أضفنا `/api/public/config`؟

الواجهة الثابتة تحتاج معرفة:

- `SUPABASE_URL`
- `SUPABASE_ANON_KEY`
- مسار WebSocket
- إعدادات STUN/TURN العامة

بدل كتابة هذه القيم داخل JavaScript، يقرأها المتصفح من الخادم عبر:

```text
/api/public/config
```

> قرار تصميمي: `anon key` ليس مثل `service_role` لكنه يبقى قادمًا من متغيرات البيئة، وليس مكتوبًا داخل المستودع.

## 3. الصفحة الرئيسية

الملف:

```text
src/main/resources/static/index.html
```

يدعم:

- عرض تعريفي للمشروع.
- إنشاء غرفة.
- الانضمام السريع عبر رابط أو كود دعوة.
- عرض آخر الغرف العامة.
- عرض حالة المستخدم في الهيدر.

JavaScript:

```text
src/main/resources/static/assets/js/index.js
```

ينفذ:

- إنشاء الغرفة عبر REST.
- فتح رابط الدعوة.
- تحميل الغرف العامة.
- تحويل المستخدم إلى صفحة الدخول إذا حاول إنشاء غرفة بدون جلسة.

## 4. صفحة الدخول

الملف:

```text
src/main/resources/static/login.html
```

تدعم ثلاث طرق:

- الدخول بالبريد وكلمة المرور.
- إنشاء حساب جديد.
- الدخول كضيف عبر Supabase Anonymous Auth.

JavaScript:

```text
src/main/resources/static/assets/js/login.js
```

يعتمد على:

```text
src/main/resources/static/assets/js/auth.js
```

> ملاحظة: دخول الضيف يحتاج تفعيل Anonymous Sign-ins في Supabase Auth.

## 5. صفحة الغرفة

الملف:

```text
src/main/resources/static/room.html
```

تحتوي على:

- مشغل فيديو YouTube أو MP4.
- أزرار تشغيل/إيقاف/انتقال.
- تغيير الفيديو الحالي.
- قائمة المشاهدين.
- الدردشة.
- قائمة الانتظار.
- واجهة أولية للمكالمة، وستُفعّل في مرحلة WebRTC.

JavaScript:

```text
src/main/resources/static/assets/js/room.js
src/main/resources/static/assets/js/video-player.js
```

يدعم حاليًا:

- تحميل الغرفة بالمعرف أو كود الدعوة.
- الانضمام للغرفة العامة أو المحمية بكلمة سر.
- الاتصال بـ STOMP عبر WebSocket.
- الاشتراك في أحداث الغرفة.
- إرسال واستقبال رسائل الدردشة.
- استقبال أحداث `JOIN`, `LEAVE`, `PLAY`, `PAUSE`, `SEEK`, `CHANGE_VIDEO`, `SYNC_STATE`.
- عرض قائمة الأعضاء وقائمة الانتظار.

## 6. مكتبات CDN المستخدمة في الواجهة

في صفحات HTML استخدمت:

```text
@supabase/supabase-js@2
sockjs-client
@stomp/stompjs
YouTube IFrame API
```

لا يوجد React أو build tool أمامي.

## 7. تدفق الاستخدام

1. افتح:

```text
http://localhost:8080/
```

2. سجّل دخولًا أو ادخل كضيف.
3. أنشئ غرفة أو افتح رابط دعوة.
4. في صفحة الغرفة، إن كانت محمية، أدخل كلمة السر.
5. اشترك المتصفح تلقائيًا في WebSocket.
6. يبدأ استقبال أحداث الغرفة والدردشة والمزامنة.

## 8. ملاحظات حول المرحلة 9

تم تنفيذ منطق المزامنة المتقدم في المرحلة 9 وتوثيقه في:

```text
docs/09-js-synchronization.md
```

ويشمل:

- منع حلقات الإرسال اللانهائية.
- تصحيح الانحراف إذا تجاوز ثانيتين.
- التمييز بين حدث محلي وحدث قادم من الشبكة.

## 9. قرارات تصميمية مهمة

- وضعت كل JavaScript في ملفات واضحة بدل تضمينه داخل HTML لتسهيل الصيانة.
- استخدمت REST لإنشاء الغرف والانضمام وقائمة الانتظار، و STOMP للأحداث اللحظية.
- أبقيت الواجهة RTL بالكامل مع تصميم responsive يدعم الهاتف.
- لا يتم تخزين مفاتيح Supabase داخل الملفات؛ الواجهة تقرأ الإعدادات العامة من backend.

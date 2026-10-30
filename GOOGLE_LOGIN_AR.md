# تسجيل الدخول بحساب Google — AnimeShadow 1.5.0

من 1.5.0 لأنمي شادو مشروع Google Cloud خاص: **AnimeShadow** (`animeshadow-513201`).

* عميل Web (يُستعمل كـ serverClientId): يوضع في `local.properties` فقط (لا يُرفع إلى GitHub):
  `GOOGLE_WEB_CLIENT_ID=851726861399-…apps.googleusercontent.com`
  (أو `-PGOOGLE_WEB_CLIENT_ID=…`). الفارغ = يظهر «تسجيل الدخول غير مفعّل بعد».
* عميل Android في نفس المشروع: Package `com.animeshadow.app` + SHA-1
  `76:29:9F:69:58:16:51:2D:43:E7:F0:DF:6D:1E:45:6C:11:D9:B2:BF` (مُسجّل).
* إذا كانت شاشة الموافقة (OAuth consent screen) في وضع **Testing** أضف الإيميلات في Test users، أو انشرها (Publish).

## السيرفر (لوحة مدين على hostless)

السيرفر يتحقق من رمز Google ويقبل فقط عملاء هذا المشروع. في مشروع **madyanpanel** على hostless ← Environment:

```
ANIMESHADOW_GOOGLE_CLIENT_IDS=<نفس GOOGLE_WEB_CLIENT_ID أعلاه: 851726861399-…apps.googleusercontent.com>
```

بدونه يرد السيرفر «تسجيل الدخول غير مفعّل بعد في السيرفر» ويبقى الدخول محلياً فقط (المكتبة تعمل، الأصدقاء والدردشة لا).

## رسائل الأخطاء في التطبيق

* خطأ 10 من Google ← «عميل Android … غير مسجّل بعد» (الحزمة أو SHA-1 ناقصة/خاطئة في المشروع).
* لا يوجد حساب Google على الجهاز ← يطلب إضافة حساب من إعدادات الهاتف.
* شبكة / خدمات Google Play ← رسالة واضحة لكل حالة.

تسجيل الدخول اختياري؛ المكتبة محفوظة على الجهاز وكل شيء يعمل بدونه ما عدا الأصدقاء والدردشة وPro.

# تفعيل تسجيل الدخول بحساب Google — AnimeShadow 1.3

الزر جاهز داخل التطبيق (شاشة «تسجيل الدخول» من الملف الشخصي). يحتاج خطوة واحدة منك في Google Cloud
داخل **نفس المشروع** الذي فيه الـ Web client ID:
`674853191136-m6gc2oct6agk6bbm8kga5muvdco96ro3.apps.googleusercontent.com`

## الخطوات (5 دقائق)

1. افتح https://console.cloud.google.com/apis/credentials واختر المشروع الذي فيه الـ Web client أعلاه.
2. **Create credentials → OAuth client ID**.
3. Application type: **Android**.
4. Package name: `com.animeshadow.app`
5. SHA-1 certificate fingerprint:
   `76:29:9F:69:58:16:51:2D:43:E7:F0:DF:6D:1E:45:6C:11:D9:B2:BF`
6. اضغط **Create**. لا تحتاج تنزيل أي ملف ولا إعادة بناء التطبيق.
7. إذا كانت شاشة الموافقة (OAuth consent screen) في وضع **Testing** أضف إيميلك في Test users، أو انشرها (Publish).

بعدها افتح التطبيق ← الملف الشخصي ← «تسجيل الدخول بحساب Google». قد يحتاج Google حتى ساعة ليتعرف على التطبيق.

## ملاحظات

* قبل هذه الخطوة يظهر للمستخدم: «تسجيل الدخول بـ Google لم يُفعَّل لهذا التطبيق بعد» — بدون أي خطأ أو زر إعادة.
* إذا وقّعت التطبيق لاحقاً بمفتاح آخر (أو رفعته على Google Play بتوقيع Play) أضف SHA-1 الجديد كـ Android client آخر.
* لتغيير الـ Web client ID بدون تعديل الكود: `-PgoogleWebClientId=...` أو `google.webClientId=...` في local.properties.
* تسجيل الدخول اختياري؛ كل شيء في التطبيق يعمل بدونه، والقائمة محفوظة على الجهاز.

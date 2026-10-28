# ANIME SHADOW — أنمي شادو

مكتبتك الشخصية للأنمي ومتتبع الحلقات · Your personal anime library & episode tracker

---

## العربية

### الجديد في 1.4.0 (versionCode 7)
- **«شادو سينباي»**: مساعد ذكي متخصص في الأنمي والمانجا فقط (يعتذر بلطف عن أي موضوع ثاني). يقرأ قائمتك وإحصائياتك ليرشّح ويقيّم، يقبل صورة وحدة مع كل رسالة (يعرف الأنمي من البوستر)، وردوده فيها بطاقات تفتح صفحة العمل داخل التطبيق. المحادثات محفوظة في جوالك بعنوان تلقائي حسب الموضوع وتنحذف، وشاشة «تخصيص» للبرومبت ووش يناديك (الاسم ثابت). 20 رسالة يومياً لكل جهاز تُحسب في السيرفر. المفتاح في سيرفر لوحة مَدْيَن فقط (انظر ANIMESHADOW_CHAT_AR.md)؛ قبل إضافته يظهر «الخدمة قيد التجهيز».
- **التصنيفات**: كل أنواع ووسوم AniList (19 نوع و430 وسم) بالعربي في شاشة «التصنيفات» للأنمي والمانجا، ووسوم في فلتر البحث. المحتوى +18 مقفل افتراضياً ويفتح من الإعدادات ← «محتوى +18» بعد تأكيد العمر مرة وحدة (معلومات وصور غلاف فقط).
- **«شاهد»** في صفحة الأنمي: الحلقات المجانية الرسمية على يوتيوب (مثل Muse Asia وAni-One) والمنصات الرسمية (Crunchyroll، Netflix…) كما يذكرها AniList. **«اقرأ رسمياً»** في صفحة المانجا: MANGA Plus وVIZ وK MANGA… لا يوجد مصدر فصول قانوني يسمح بالإعلانات، فالقارئ الداخلي يبقى «قريباً» (التفاصيل والمصادر في docs/research_1.4.0.md).
- **الإعلانات**: بانر صغير تحت القوائم، وإعلان بيني واحد كحد أقصى كل 6 شاشات ومرة كل 3 دقائق، وأبداً في شاشة البداية أو تسجيل الدخول أو القارئ أو الشات. أرقام Google التجريبية حالياً؛ الحقيقية تدخل وقت البناء (ADMOB_AR.md).
- **الشعار المتحرك**: أيقونة صغيرة بالوسط مثل أيقونة التطبيق (خلفية مقصوصة بأطراف ناعمة) في شاشة البداية وتسجيل الدخول وكل مؤشرات التحميل، بدل الفيديو العريض (والتطبيق أخف ~2 ميغا).
- **القائمة الجانبية**: خط أصغر، عرض أقل (280dp)، ولون بنفسجي أهدأ للعنصر المختار، والأيقونات بيضاء كما في 1.3.1.

### الجديد في 1.3.1 (versionCode 6)
- **فيديو البداية وتسجيل الدخول بدون مربّع**: خلفية الفيديو صارت سوداء تماماً (#000) وأطرافه الأربعة تذوب في الأسود، فتظهر الشخصية واللهب فقط على الشاشة السوداء بأي مقاس جوال، وبحجم أكبر (~1.2×).
- **التبديل إلى المانجا**: مؤشر التحميل كان يعرض الفيديو القديم (نسخة 1.2) وأحياناً مربعاً فارغاً؛ الآن نفس فيديو مدين في كل مكان، مع صورة ثابتة دائماً حتى يبدأ.
- **كلمات المانجا**: أفعال المشاهدة بصيغة المؤنث (أشاهدها، شاهدتها، أخطط لمشاهدتها، توقفت عن مشاهدتها)، والملف الشخصي في وضع المانجا يعرض أرقام المانجا (الفصول التي تمت مشاهدتها).
- **صفحة المانجا مثل صفحة الأنمي**: نفس الرأس وصف التقييم (AniList، MAL، تقييمك، قائمتي)، نفس نافذة التقييم، الملخص بالعربي، التصنيفات، جدول المعلومات (مع المؤلف)، و«ذات صلة» مع السابق / التالي.
- **العناوين**: الإنجليزي في الأعلى والياباني تحته، ومرة واحدة إذا كانا نفس العنوان.
- **كل الأيقونات بيضاء** (#FFFFFF)، والجرس خط أبيض رفيع بدون تعبئة؛ العنصر المختار فقط بلون التطبيق.

### الجديد في 1.3.0 (versionCode 5)
- **وضع المانجا**: زر «أنمي | مانجا» أعلى الشاشة الرئيسية، يتبدّل فوراً وكل وضع له قائمته ومفضلته وسجله الخاص. بيانات المانجا من AniList مع فلتر مانجا / مانهوا / مانها (اليابان / كوريا / الصين).
- صفحة العمل بتبويبين «التفاصيل» و«الفصول»، قارئ مدمج (عمودي للمانهوا والمانها، صفحات من اليمين لليسار للمانجا، خلفية سوداء، ملاءمة العرض، الشاشة تبقى مضاءة) بشاشة صافية: ضغطة بالمنتصف تُظهر الأزرار وتختفي بعد 3 ثوانٍ. ⚙️ فيها كل الإعدادات و«استعادة الافتراضي».
- تنزيل سريع (5 صفحات بالتوازي، يكمل من حيث توقف، إشعار أثناء التنزيل)، «قائمة التنزيل» بالسحب لإعادة الترتيب، و«المانجات المحملة» تعمل بدون نت.
- مصدر الفصول قابل للإضافة (انظر docs/CHAPTER_PROVIDER.md)؛ هذه النسخة بدون مصدر فتظهر «قريباً».
- الحساب انتقل من الإعدادات إلى الملف الشخصي؛ تسجيل الدخول بحساب Google (انظر GOOGLE_LOGIN_AR.md).
- سحب أفقي بين التبويبات، القائمة الجانبية سوداء بالكامل وبدون العبارة تحت الصورة، الإحصائيات أرقام فقط، تغيير/حذف الصورة من «تعديل الملف الشخصي»، شاشة البداية فيديو مدين، التواصل مع المطوّر عبر تيليجرام @abnalyatim.

### الجديد في 1.2.0 (versionCode 4)
- **السحب للتحديث يشتغل الآن في كل القوائم**: الرئيسية، الأعلى تقييماً، المواسم، البحث، التصنيف، الاستوديو، قائمتي، المفضلة، السجل، الشخصيات الأكثر شعبية، التوصيات، الجدول. السبب أنه كان لا يفعل شيئاً: في 1.1 لم يكن هناك سحب للتحديث أصلاً في أي شاشة، وأي تحديث يدوي أثناء التحميل الأول كان يُتجاهل، والقوائم الفارغة لا تُسحب. الآن السحب يجلب من AniList فعلاً ويعرض البيانات الجديدة (مع اختبار).
- **لا يوجد زر «إعادة المحاولة»**: الشريط يقول بهدوء «اسحب للأسفل للتحديث»، وبدون نص إنجليزي تقني.
- **أنميشن التحميل**: مقطع Shadow حقك (3.4 ثانية) صغير على أسود، يتكرر بدون أي قطع، ثم يتلاشى إلى الواجهة. نفس المقطع مكان دائرة التحميل في القوائم. (WebP متحرك 490KB بجودة مطابقة للأصل، وأطرافه تذوب في الأسود.)
- **تسجيل الدخول بحساب AniList** (اختياري): جاهز، ويُفعَّل برقم العميل. قبل التفعيل يشرح الزر بهدوء أنه لم يُفعَّل بعد. الخطوات في `ANILIST_SETUP_AR.md`.
- **إعلان صغير تجريبي** (Google Test 320×50) أسفل شاشات القوائم فقط. لا إعلانات منبثقة ولا فيديو ولا صوت، ويبدأ في الخلفية بعد ظهور الشاشة الأولى فلا يبطئ التشغيل.
- **«عن التطبيق» ← «المطوّر»**: مدين اليتيم / Madyan AL-Yatim (في 1.3 صار التواصل عبر تيليجرام فقط).
- **إصلاحات**: التدوير لم يعد يفتح الأنمي من الإشعار مرة ثانية، زر الرجوع يغلق القائمة الجانبية أولاً، نوافذ الإعدادات لا تختفي عند التدوير، زر البحث في الكيبورد يبحث فوراً، قائمتي أسرع مع القوائم الطويلة، ترتيب الكلمات صحيح في النصوص العربية التي فيها كلمات إنجليزية.

### الجديد في 1.1.1 (versionCode 3)
- **الخط الصحيح**: Noto Naskh Arabic UI (خط النسخ الذي يستعمله أندرويد نفسه، وهو خط صورتك المرجعية 11)، مرفق برخصة SIL OFL، واللاتيني والأرقام بخط Roboto كما في المرجع.
- **المقاسات مأخوذة من صورك**: جوالك عرضه 360dp، وحجم الخط فيه ~1.075، وخيار «الخط العريض» مفعّل. النص 14sp، والأسطر الرمادية الصغيرة 12sp، والعنوان 15sp، والشريط العلوي 20sp، ونفس الأوزان.
- **صفحة التفاصيل مثل 11 بالضبط**: كل عمود في صف التقييمات أيقونة ثم الرقم ثم السطر الرمادي تحته مباشرة، و«قائمتي» تحتها حالتك («أشاهدها حالياً»). الأسطر بجانب البوستر بنفس الترتيب: «مستمر (الاحد)»، «خريف 1999»، «مسلسل | 13+». القصة 5 أسطر.
- **القصة بالعربي جاهزة قبل ما تفتح الأنمي**: الترجمة صارت أونلاين عبر MyMemory (مجاني وبدون مفتاح وبدون خدمات Google). تترجَم القصص في الخلفية أول ما تظهر العناوين في الرئيسية أو البحث أو القوائم (عناوين قائمتك أولاً) وتنحفظ. أسماء الأنمي والشخصيات تبقى كما هي (مثلاً Monkey D. Luffy). أُزيلت مكتبة ML Kit.
- **الاستوديو**: الضغط على شريحة الاستوديو يفتح كل أعماله.
- **الحجم والسرعة**: R8 مع تقليص الموارد وprofileinstaller.

### الجديد في 1.1.0 (versionCode 2)
- **صفحة التفاصيل كما في مرجعك (11)**: البوستر يساراً والعنوان بجانبه، سطر «النوع | التصنيف العمري» من MAL، صف «متوسط التقييم / تقييم MAL / تقييمك / قائمتي»، الحالة تتغيّر من قائمة صغيرة في مكانها (لا نافذة)، التصنيفات بالعربي وكل تصنيف يفتح قائمة أنميات هذا التصنيف.
- **القصة بالعربي**: ترجمة آلية على جهازك (ML Kit، إنجليزي ← عربي) مع وسم «ترجمة آلية»؛ إن لم تتوفر الترجمة تظهر الإنجليزية. تُحفظ الترجمة فلا تتكرّر.
- **نافذة التقييم (03)**: 4 أشرطة (القصة، الشخصيات، الرسوم، الموسيقى) والتقييم العام X/10 مع «حسنا / الغاء».
- **تبويب الحلقات (13)**: الأحدث أولاً، عين لكل حلقة، زر تعليقات (قريباً)، ترتيب وفلتر في الشريط. **العين الصغيرة في الأعلى** تعلّم كل الحلقات أو تلغي تعليمها، مع «تراجع».
- **الجرس يفتح شاشة الإشعارات** (سجل حقيقي لتنبيهات الحلقات الجديدة والتذكيرات، مقروء/غير مقروء، مسح الكل) بدل جدول الحلقات.
- **الشريط العلوي (05) والبحث (08)** مطابقان للمراجع، وفلتر «التصنيف العمري» في البحث.
- **ضغطة مطوّلة** على العنوان أو العنوان الياباني تنسخه مع رسالة «تم النسخ»، والضغط على البوستر يفتحه بملء الشاشة مع تكبير.
- **بوستر MAL والتصنيف العمري**: Jikan أولاً، وإن فشل تُقرأ صفحة MyAnimeList مباشرة، وإن فشلت أيضاً يُستخدم AniList (الغلاف + 18+).
- **العربية فقط**: أُزيل خيار اللغة. **الخط** (1.1.0): Noto Sans Arabic (مرفق، رخصة SIL OFL) للحروف العربية، وRoboto (خط أندرويد) للحروف اللاتينية والأرقام — نفس شكل المرجع 05.


### المزايا
- **آخر التحديثات**: شبكة من 3 أعمدة (أو قائمة) لأحدث الحلقات المعروضة، شارة «الحلقة : N» على البوستر، الحالة والتقييم «مستمر ★ 8.1».
- **قائمتي**: 5 حالات (أشاهد، مكتمل، أنوي المشاهدة، متوقف مؤقتاً، متروك)، مع فلاتر (النوع، العرض، السنة، أقل تقييم، التصنيفات) وترتيب (تاريخ الإضافة، الاسم، السنة، التقييم، تصاعدي/تنازلي)، وحفظ الفلتر الافتراضي.
- **صفحة الأنمي**: تبويبات التفاصيل / الحلقات / الإحصائيات / الشخصيات، غلاف مموّه، صف أرقام، القصة مع «المزيد»، شبكة معلومات، العرض الدعائي (يفتح يوتيوب)، الأعمال المرتبطة، التوصيات، «أنميات مشابهة» من محرك محلي.
- **التقدّم**: زر + / − ، ومربع اختيار (عين) لكل حلقة. التقدّم = أعلى حلقة شاهدتها. يتحوّل تلقائياً إلى «أشاهد» عند أول حلقة، و«مكتمل» عند آخر حلقة لأنمي منتهٍ.
- **الشخصيات والمؤدّون الصوتيون**: صفحة لكل شخصية وكل مؤدٍّ مع أدواره، و«شخصياتي المفضلة»، و«الشخصيات الأكثر شعبية».
- **أنمياتي المفضلة** و **آخر المشاهدات** (السجل).
- **الملف الشخصي = الإحصائيات**: غلاف، صورة، تعديل، دائرة توزيع الحالات، عدد الحلقات، **وقت مشاهدة تقديري** (يستخدم مدة الحلقة الحقيقية، وإن لم تتوفر يستخدم المدة الافتراضية من الإعدادات — ومكتوب عليه «تقديري»)، التصنيفات المفضلة، توزيع تقييماتك.
- **التوصيات**: محرك محلي بالكامل على جهازك: «إذا أحببت ⇄ قد ترغب» مع سطر سبب.
- **المواسم** و **جدول الأسبوع** (السبت ← الجمعة، بتوقيت جهازك، ومكتوب عليه «متوقّع»).
- **الإشعارات** (WorkManager كل 3 ساعات عند الاتصال): حلقة جديدة لأنمي تتابعه، وتذكير اختياري لكل أنمي. أول مزامنة صامتة حتى لا تُغرقك.
- **الإعدادات**: العام (الإشعارات، المدة الافتراضية للحلقة)، المشغّل (مشغل خارجي — **واجهة فقط حالياً**، مدة التقديم)، المظهر (شبكة/قائمة)، أخرى (نسخ احتياطي واستعادة JSON عبر SAF، مسح الكاش، مسح السجل، حذف كل البيانات).
- **يعمل بدون إنترنت**: كل ما فتحته محفوظ في Room، والصور في كاش Coil (ذاكرة + 250MB قرص). البحث بدون اتصال يبحث محلياً.

### البنية
Kotlin · Jetpack Compose (Material 3) · Room · Hilt · Coroutines/Flow · Retrofit/OkHttp + kotlinx.serialization · Coil · WorkManager · DataStore · AppCompat per-app locales.
- `data/remote`: عميل AniList GraphQL + Jikan، ومحدِّد معدل (نافذة منزلقة + تباعد أدنى + إعادة المحاولة عند 429/5xx مع احترام Retry-After).
- `data/local`: قاعدة Room (14 جدول) — الكتالوج كاش قابل للحذف، وبيانات المستخدم (المكتبة، الحلقات، المفضلة) لا تُحذف أبداً مع الكاش.
- `data`: مستودعات offline-first: تعرض من Room فوراً ثم تحدّث من الشبكة حسب TTL؛ عند فشل الشبكة تبقى البيانات المخزّنة.
- `domain`: منطق صافٍ قابل للاختبار: الفلترة والترتيب، الإحصائيات، محرك التوصيات، تقسيم الجدول الأسبوعي.
- `sync`: كشف الحلقات الجديدة، العامل، الجدولة، الإشعارات.
- `ui`: الشاشات (Compose) و ViewModels.

### مصادر البيانات وحدودها
| المصدر | ماذا يقدّم |
|---|---|
| **AniList** (graphql.anilist.co) — المصدر الأساسي | كل شيء: آخر الحلقات، الأعلى تقييماً، المواسم، البحث والفلاتر، التفاصيل، الشخصيات والمؤدّون، الأعمال المرتبطة، التوصيات، توزيع التقييمات، العرض الدعائي، الجدول، المزامنة الخلفية (50 أنمي في طلب واحد). |
| **Jikan** (MyAnimeList) — اختياري | تقييم MAL وعدد الأعضاء في صفحة التفاصيل فقط. كان **متوقفاً** وقت البناء (انتهاء مهلة TLS)، لذلك أي فشل فيه يُتجاهل بصمت ولا يؤثر على التطبيق. |

- حدّ AniList الحالي 30 طلب/دقيقة؛ التطبيق يلتزم به تلقائياً.
- مواعيد الجدول «متوقعة» من AniList وقد تتغير.
- لا يوجد بث أو روابط مشاهدة؛ هذا متتبع ومكتبة.

### البناء
```
JDK 17, Android SDK 35
./gradlew clean lint test assembleDebug assembleRelease
```
APK: `app/build/outputs/apk/debug/app-debug.apk`. نسخة release تُوقّع إذا عُرّفت المتغيرات `AS_KEYSTORE`, `AS_KEYSTORE_PASSWORD`, `AS_KEY_ALIAS`, `AS_KEY_PASSWORD`، وإلا تُبنى غير موقّعة.

### ما لم يُختبر
انظر القسم الإنجليزي «What is untested» — نفس القائمة.

---

## English

### What's new in 1.3.1 (versionCode 6)
- Splash/login video with no visible box: its haze is crushed to pure #000 and all four edges feather into black, so only the character and flames show on the black screen at any phone size (and ~1.2× larger).
- Switching to Manga: the loader played the old v1.2 clip and could be an empty box; it now uses the same video everywhere and always draws a still frame until the animation starts.
- Manga words: feminine watching verbs (أشاهدها، شاهدتها، أخطط لمشاهدتها، توقفت عن مشاهدتها); the profile in manga mode shows manga numbers.
- Manga details page laid out like the anime page: same header, rating row (AniList, MAL, your rating, my list), same rating dialog, Arabic synopsis, genre chips, info grid (author), related row with prequel/sequel badges. manga.db 1→2 migration keeps the library.
- Titles: English on top, Japanese below, shown once when identical.
- All icons pure white; the bell is a thin white outline; only the selected item keeps the accent colour.

### What's new in 1.3.0 (versionCode 5)
- **Manga mode**: «أنمي | مانجا» toggle in the home top bar; instant switch, separate library/favourites/history per mode (own `manga.db`). AniList MANGA with a manga/manhwa/manhua (JP/KR/CN) filter.
- Title page with «التفاصيل» / «الفصول» tabs; built-in reader (vertical for manhwa/manhua, RTL paged for manga, black, fit width, keep screen on), clean immersive screen with centre-tap controls that auto-hide after 3 s, ⚙️ sheet with every option + «استعادة الافتراضي», per-title direction.
- Fast downloads (5 pages in parallel, resumable, WorkManager foreground job, prefetch next chapter), «قائمة التنزيل» queue with drag reorder, offline «المانجات المحملة» library.
- Pluggable `ChapterProvider` (docs/CHAPTER_PROVIDER.md); this build ships the empty stub, so chapters show «قريباً».
- Account moved from Settings to Profile; Google sign-in via Credential Manager (GOOGLE_LOGIN_AR.md for the one Cloud Console step).
- Swipe between tabs everywhere, pure-black drawer without the tagline, counts-only profile stats, change/delete photo inside Edit Profile, Madyan's video as splash (≈2 s online / ≈5 s offline), developer contact Telegram only (@abnalyatim).

### What's new in 1.2.0 (versionCode 4)
- **Pull-to-refresh on every list screen** (Latest, Top, Seasons, Browse/Search, Genre, Studio, My List, Favorite anime, History, Popular characters, Recommendations, Schedule). Why it did nothing in 1.1: there was no pull-to-refresh component on any screen; a forced refresh issued while the automatic first load was running was dropped; and empty/error states were not scrollable, so a pull could not start there. A pull now always re-fetches from AniList and the list shows the new data (tested with a server that answers list A, then list B).
- **No retry buttons** (golden rule): banners say "pull down to refresh", never raw English exception text.
- **Loading animation**: Madyan's 3.4 s Shadow loop, small on black, gapless (animated WebP played by AnimatedImageDrawable on the render thread), then a 450 ms fade into the UI; the same loop replaces the list spinners. 640×360, 103 frames, 505 KB, 37–44 dB PSNR vs. the source, edges feathered to pure black.
- **AniList login behind config**: OAuth implicit grant, `-PanilistClientId=` / `anilist.clientId=` (empty by default → a calm Arabic "not activated yet" dialog). Redirect `animeshadow://anilist-auth`. See `ANILIST_SETUP_AR.md`.
- **Ads**: one Google TEST banner (320×50) under list screens only; no interstitials, pop-ups, video or sound; SDK initialised lazily off the main thread (plus OPTIMIZE_INITIALIZATION / OPTIMIZE_AD_LOADING). `-PadsEnabled=false` removes it. APK 6.0 MB.
- **About → Developer** row: مدين اليتيم / Madyan AL-Yatim (since 1.3: Telegram only).
- **Bug fixes**: rotation re-opened a notification's anime; Back with the drawer open left the screen; settings dialogs lost on rotation; keyboard search key waited for the debounce; Browse pull blanked results; load-more spinner during a pull; stale load-more page appended after switching season; overlapping schedule loads; O(n²) progress lookup in My List; wrong word order in Arabic strings containing Latin words.

### What's new in 1.1.1 (versionCode 3)
- **Correct font**: Noto Naskh Arabic UI (Android's own Naskh, the font in reference 11), bundled under SIL OFL, with Roboto for Latin text and digits as in the reference.
- **Sizes measured on Madyan's phone screenshots**: the phone is 360 dp wide, with a ~1.075 text scale and Bold Text on. Body 14 sp, small grey lines 12 sp, title 15 sp, top bar 20 sp, same weights as the reference.
- **Details page literally like ref 11**: each rating column is a tight stack of icon, score and grey line, and «قائمتي» has its status under it. The header lines read «مستمر (الاحد)», «خريف 1999», «مسلسل | 13+». The synopsis shows 5 lines.
- **Arabic synopsis ready before a title opens**: online translation via MyMemory (free, no key, no Google services). Synopses are pre-translated in the background (WorkManager) as titles load and cached in Room; titles in your list go first. Show and character names are kept as written. ML Kit was removed.
- **Studio chip** opens the studio's works.
- **Size/speed**: R8 with resource shrinking, plus profileinstaller.

### What's new in 1.1.0 (versionCode 2)
- **Details per ref 11**: poster left, title beside it, "type | age rating" line (MAL rating), average / MAL / your rating / my list row, status changed from a small inline menu (no dialog), Arabic genre chips that open a genre list.
- **Arabic synopsis**: on-device ML Kit EN→AR translation labelled «ترجمة آلية», English fallback, cached per anime.
- **Rating dialog (ref 03)**: 4 sliders (story, characters, animation, music), overall X/10, «حسنا / الغاء». Stored per entry and in backups.
- **Episodes tab (ref 13)**: newest first, per-episode eye, comments (coming soon), sort + filter in the bar. The small eye in the bar marks / unmarks all, with Undo.
- **Bell → Notifications screen** (real history of new-episode alerts and reminders, read/unread, clear all) instead of the schedule.
- **Home bar (ref 05), search bar (ref 08)**, and an age-rating filter in search.
- **Long-press** title / Japanese title copies with a toast; tapping the poster opens a zoomable full-screen viewer.
- **MAL poster + content rating**: Jikan first; when Jikan fails, the MyAnimeList page itself is parsed; when that fails too, AniList (cover + isAdult). DB v2 with a tested migration from v1.
- **Arabic only**: language switch removed. **Font**: Noto Sans Arabic (bundled, SIL OFL; Latin glyphs stripped) for Arabic, Roboto (Android system font) for Latin letters and digits — the same look as ref 05.


### Features
- **Latest Updates** home: 3-column poster grid (or list) of currently airing episodes, "Episode : N" badge, one-line title, "Airing ★ 8.1".
- **My List**: 5 statuses, filters (format, airing status, year, min score, genres — all must match), sorting (date added, name, year, score, asc/desc), saved default filter.
- **Details**: tabs Details / Episodes / Stats / Characters; blurred banner, stat row, synopsis with "More", info grid, trailer (opens YouTube), related, recommendations, local "Similar anime".
- **Progress**: +/− buttons and a per-episode eye toggle. Progress = highest watched episode. Auto-WATCHING on first episode, auto-COMPLETED on the last episode of a finished show (and back to WATCHING if you untick it). Airing shows with unknown totals are never auto-completed.
- **Characters & voice actors** pages, favorite characters, most popular characters.
- **Favorite anime** and **Recently watched** (history).
- **Profile = Stats**: cover, avatar, edit; status donut, episodes watched, **estimated watch time** (real episode duration when known, otherwise the default from Settings — labelled as an estimate), top genres, your score distribution.
- **Recommendations**: fully on-device engine, "If you liked ⇄ You may like" with a reason line.
- **Seasons** and a **weekly schedule** (Saturday → Friday, device time zone, labelled "expected").
- **Notifications** (WorkManager every 3 h on network + battery-not-low): new episode of followed shows, optional per-anime reminder. First sync is silent so you aren't flooded.
- **Settings**: General (notifications, default episode duration), Player (external player — **placeholder**, seek step), Appearance (grid/list), Other (JSON backup/restore via SAF, clear cache, clear history, delete everything).
- **Offline-first**: everything you opened is in Room; images in Coil memory + 250 MB disk cache; offline search falls back to the local cache.

### Architecture
Single-activity Compose app, MVVM + repositories. See the Arabic section for the package map. User data (library, watched episodes, favorites) lives in separate tables from the catalogue cache, so "Clear cache" never touches it.

### Data sources and limits
- **AniList GraphQL** is the primary source for everything listed above (30 req/min currently; a sliding-window limiter + 429/5xx retry honouring Retry-After keeps the app inside it).
- **Jikan (MyAnimeList)** is optional enrichment only (MAL score and members on Details). It was **down** (TLS timeouts) while this was built, so it fails silently. Its fixture in tests is **synthetic** and labelled so.
- Schedule times are AniList's expected times and can change. No streaming/watch links.

### Build
```
JDK 17, Android SDK 35
./gradlew clean lint test assembleDebug assembleRelease
```
Release is signed when `AS_KEYSTORE`, `AS_KEYSTORE_PASSWORD`, `AS_KEY_ALIAS`, `AS_KEY_PASSWORD` are set; otherwise it is built unsigned. The delivered APK is the debug-signed build.

### Tests
118 + 2 JVM tests, all passing (`./gradlew test`):
- Domain: LibraryFilter 13, RecommendationEngine 7, StatsCalculator 5, WeekSchedule 2, NewEpisodeDetector 2, TextClean 2.
- Data: DAOs 7, LibraryRepository 12 (+/−, checkboxes, auto status, persistence across a real on-disk DB reopen), AnimeRepository 14 (MockWebServer replaying real recorded AniList JSON: TTL, offline keeps cache, empty-cache offline, GraphQL errors, paging, detail + characters + staff + relations + recommendations, Jikan outage ignored, list refresh keeps detail fields, offline search fallback, schedule, characters/VAs, batch sync), rate limiter 5, settings & language persistence across restart 2, backup round-trip + rejection 2.
- Sync: background worker 3 (new episode → one notification, off switch, network failure → retry).
- App smoke 2: boots the real app (Hilt, Room, WorkManager, navigation), visits every drawer destination, opens Details from a notification intent.
- Screenshots: 21 screens in Arabic (the English set was removed with the language switch in 1.1) + 10 reference-compare shots at the references' 411×914 dp size.
- 1.1 tests: V11InteractionTest 18 (positions left/right per refs, rating sliders, inline status menu, long-press copy + toast, poster viewer, genre chips, episodes eye/sort/comments, mark-all + undo, home/search bars, age filter, notifications, no language row), V11ViewModelTest 4, LibraryV11Test 9, MalEnrichmentTest 4, MalPageParserTest 5, MigrationTest 1, ArabicOnlyOnEnglishDeviceTest 1, app smoke: bell opens Notifications.

Screenshots: Roborazzi, every main screen in Arabic (RTL) and English, rendered with real AniList data and real posters (`screens/`).

### What is untested
- Not run on a physical phone or emulator: everything is verified with JVM/Robolectric tests and screenshots. Runtime permission dialog (Android 13+ notifications), the SAF file picker UI, and real WorkManager scheduling on a device are untested.
- Live network in tests is replaced by recorded real AniList responses (recorded 2026-10-26); if AniList changes its schema, the app shows cached data and an error banner.
- Jikan enrichment was only tested against a synthetic response (Jikan was down).
- ML Kit translation cannot run in unit tests: the wiring is tested with a fake translator; the real on-device model download/translation is untested. The Arabic synopsis in the screenshots is a hand-written sample, labelled in the test code.
- Jikan was still down during 1.1 (TLS timeout): the Jikan path is tested only with a synthetic response; the MAL-page fallback is tested against a real saved MAL page and was checked live.

### Cut (depth, not quality)
- Jikan user-written recommendation reasons (Jikan down) → reasons come from your own library (shared genres).
- Custom user lists, age-rating and studio filters.
- External player is a settings placeholder (no playback in this app).
- Per-episode titles from streaming sites are not mapped; episodes are numbered.

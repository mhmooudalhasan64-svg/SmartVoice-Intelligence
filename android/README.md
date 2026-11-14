# SmartVoice Android

تطبيق Android أصلي مبني باستخدام Kotlin وJetpack Compose.

## المتطلبات
- Android Studio حديث يدعم Android Gradle Plugin 8.7.3.
- JDK 17.
- Android SDK Platform 35.

## فتح المشروع
1. افتح Android Studio.
2. اختر **Open** وحدد مجلد `android/` داخل المستودع.
3. انتظر مزامنة Gradle.
4. شغّل مهمة `app > Tasks > build > assembleDebug` أو شغّل التطبيق على جهاز/محاكي.

## الحزمة
`com.smartvoice.intelligence`

## الحالة الحالية — 0.2.0
- تسجيل صوتي محلي بصيغة M4A/AAC.
- طلب إذن الميكروفون عند بدء التسجيل فقط.
- سجل للتسجيلات مع التشغيل والحذف وعرض المدة.
- حفظ الملفات داخل مساحة التطبيق الخاصة (`filesDir/recordings`) دون رفعها إلى خادم.
- عقود بيانات أولية للتفريغ النصي على مستوى الكلمات والمقاطع مع الطوابع الزمنية.
- GitHub Actions لبناء APK وتشغيل اختبارات الوحدة عند تحديث ملفات Android.

لم يُدمج محرك التفريغ بعد. الخيار الجاري تقييمه هو Vosk محلياً؛ النموذج العربي العام المدرج رسمياً حجمه نحو 318 MB، لذلك لن يُضمّن داخل APK، بل سيكون تنزيله اختيارياً بعد موافقة المستخدم. كما لم يُختبر بعد على اللهجة السورية. لا يوجد حالياً APK مؤكد البناء.

## الخطوات التالية
1. دمج Vosk وفك ترميز M4A إلى PCM 16 kHz mono.
2. إضافة تنزيل اختياري للنموذج العربي مع التحقق من سلامة الملف وإظهار الحجم والتقدم.
3. حفظ النصوص والكلمات والطوابع الزمنية محلياً.
4. اختبار الدقة على تسجيلات عربية سورية قبل اعتماد التصنيف التلقائي.
5. بناء محرك فهم السياق والتوجيه إلى جداول التطبيق الفعلية.


## Arabic transcription (early integration)

The app now includes a Vosk-based local transcription path. The Arabic MGB-2 model is downloaded only after the user presses the install button (approximately 318 MB); it is not embedded in the APK. Audio decoding and recognition are designed to run locally. The latest saved recording can be sent to the local recognizer after model installation.

**Not yet verified:** successful CI build, physical-device operation, and Syrian-dialect recognition quality. Do not treat this as a production-ready transcription feature until those checks pass.

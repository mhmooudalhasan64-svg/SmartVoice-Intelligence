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

يوجد دمج أولي لمحرك Vosk محلياً: يمكن للمستخدم تنزيل النموذج العربي اختيارياً (نحو 318 MB)، ثم تفريغ أحدث تسجيل بعد فك ترميز M4A/AAC إلى PCM أحادي القناة بتردد 16 kHz على الجهاز. النموذج غير مضمّن داخل APK. ما زال نجاح بناء نسخة Release الموقعة وتشغيل التطبيق على جهاز فعلي وجودة التعرف على اللهجة السورية غير مؤكدة.

## الخطوات التالية
1. التحقق من بناء APK وتشغيل اختبارات الوحدة على فرع main.
2. اختبار التسجيل والتنزيل والتفريغ على أجهزة Android فعلية، بما في ذلك التسجيلات الطويلة وأخطاء فك الترميز.
3. اختبار الدقة على تسجيلات عربية سورية بموافقة أصحابها قبل وصف دعم اللهجة بأنه موثوق.
4. حفظ النصوص والكلمات والطوابع الزمنية محلياً وإضافة التنقل من النص إلى موضعه في التسجيل.
5. بناء محرك فهم السياق والاستخراج والتوجيه إلى جداول التطبيق الفعلية.


## Arabic transcription (early integration)

The app now includes a Vosk-based local transcription path. The Arabic MGB-2 model is downloaded only after the user presses the install button (approximately 318 MB); it is not embedded in the APK. Audio decoding and recognition are designed to run locally. The latest saved recording can be sent to the local recognizer after model installation.

**Not yet verified:** successful CI build, physical-device operation, and Syrian-dialect recognition quality. Do not treat this as a production-ready transcription feature until those checks pass.

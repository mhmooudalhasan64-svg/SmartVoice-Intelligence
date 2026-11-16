# SmartVoice Intelligence

مساعد صوتي ذكي يحوّل التسجيلات والمحادثات إلى معلومات منظمة وقابلة للتنفيذ، مع فهم السياق وتوزيع النتائج تلقائياً على الأقسام والجداول المناسبة.

## الرؤية
ليس التطبيق مجرد مسجل صوت أو أداة تفريغ؛ بل نظام يفهم ما قيل، ويميز بين المهام والمواعيد والقرارات والأفكار والملاحظات، ثم يقترح طريقة تنظيمها ويحفظ مصدر كل معلومة.

## المبادئ الأساسية
- **فهم السياق أولاً:** تحليل معنى الكلام قبل اختيار القسم أو الجدول.
- **توزيع متعدد الوجهات:** يمكن للتسجيل الواحد أن ينتج عناصر في أكثر من قسم.
- **عدم اختلاق البيانات:** لا يُنشأ موعد أو التزام أو أولوية غير مذكورة بوضوح.
- **المراجعة عند الشك:** العناصر منخفضة الثقة تُعرض للمراجعة بدلاً من حفظها عشوائياً.
- **قابلية التتبع:** الاحتفاظ بمرجع التسجيل والطابع الزمني لكل معلومة مستخرجة.
- **الخصوصية والتحكم:** لا تُرسل تذكيرات أو تنشأ أحداث خارجية دون موافقة المستخدم.
- **التعلم المنضبط:** تُستخدم تصحيحات المستخدم المعتمدة لتحسين التصنيف.

## الوحدات
1. Audio Intake — التسجيل المحلي وإدارة الملفات.
2. Speech Transcription — تفريغ صوتي محلي مع توقيت الكلمات.
3. Context Intelligence — فهم الموضوع والسياق وتحديد الوجهة.
4. Information Extraction — استخراج المهام والمواعيد والقرارات والأفكار.
5. Task Intelligence — الأولويات والتجزئة ومقترحات الجدولة.
6. Smart Organization — حفظ العناصر ومزامنتها مع أقسام التطبيق.
7. Review & Feedback — مراجعة المقترحات وتسجيل التصحيحات.

## الحالة الحالية
- [x] هيكل Android أصلي باستخدام Kotlin وJetpack Compose.
- [x] تسجيل محلي بصيغة M4A/AAC.
- [x] سجل التسجيلات مع التشغيل والحذف والمدة.
- [x] عقد بيانات للتفريغ النصي والطوابع الزمنية.
- [x] إعداد مسار CI لبناء APK وتشغيل اختبارات الوحدة.
- [x] دمج أولي لمحرك Vosk المحلي وفك ترميز M4A إلى PCM أحادي القناة 16 kHz.
- [x] تنزيل وتثبيت نموذج اللغة العربية اختيارياً بعد موافقة المستخدم.
- [ ] اختبار دقة التفريغ على تسجيلات عربية ولهجات سورية.
- [ ] قاعدة بيانات محلية للتسجيلات والنصوص.
- [ ] محرك فهم السياق والتصنيف متعدد الوجهات.

## توثيق
- [المعمارية](architecture.md)
- [نموذج البيانات](data-model.md)

## اللغة
واجهة وفهم عربيان من البداية، مع دعم الفصحى واللهجات العربية، وإمكانية التوسع إلى لغات أخرى.


## Current transcription implementation status

- [x] Add Vosk Android dependency and an on-device recognizer adapter.
- [x] Add local M4A/AAC decoding to mono PCM 16 kHz.
- [x] Add explicit Arabic model download/install flow with ZIP-slip and size protections.
- [x] Connect model installation and latest-recording transcription to the Android screen.
- [ ] Verify Gradle build and unit tests in GitHub Actions.
- [ ] Test on a physical Android device, including long recordings, decoder edge cases, storage pressure, and model loading.
- [ ] Evaluate transcription quality on consented Syrian Arabic recordings before describing dialect support as reliable.
- [ ] Persist transcripts and expose segment playback/source navigation.

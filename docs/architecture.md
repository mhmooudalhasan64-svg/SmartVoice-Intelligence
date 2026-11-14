# المعمارية الأولية

## مسار المعالجة

```text
Audio Input
   |
   v
Audio Intake & Validation
   |
   v
Speech Transcription
   |
   v
Context Understanding
   |
   v
Structured Extraction
   |
   v
Destination Resolver <---- Application Schema Registry
   |
   v
Confidence & Policy Gate
   |                    |
   | high confidence    | uncertain / sensitive action
   v                    v
Save Draft/Record       User Review
   |                    |
   +---------+----------+
             v
       Organized Data
             |
             v
   Optional Reminder / Calendar Action
       (explicit user approval)
```

## المكونات

### 1. Audio Intake
- استقبال التسجيلات والملفات الصوتية.
- التحقق من النوع والحجم والمدة.
- حفظ معرف التسجيل وبياناته الوصفية.
- دعم تقسيم التسجيل الطويل إلى مقاطع مع الحفاظ على التوقيت الأصلي.

### 2. Transcription
- تحويل الكلام إلى نص مع الطوابع الزمنية على مستوى الكلمة متى دعم المحرك ذلك.
- دعم العربية والفصحى واللهجات، مع تقييم منفصل لجودة كل نموذج ولهجة.
- حفظ المقاطع والكلمات والتوقيت والثقة وربطها بالتسجيل الأصلي.
- عدم اعتبار النص المفرغ حقيقة مؤكدة عند انخفاض جودة الصوت.
- فصل واجهة المحرك عن مزود التنفيذ كي لا يرتبط التطبيق بمزود واحد.
- الافتراضي هو المعالجة المحلية دون إرسال التسجيل إلى خادم.

### 2.1 قرار المحرك العربي الأولي
- الخيار قيد التنفيذ هو Vosk محلياً على الجهاز.
- قائمة Vosk الرسمية تعرض نموذجاً عربياً عاماً باسم `vosk-model-ar-mgb2-0.4` بحجم تنزيل يقارب 318 MB وترخيص Apache 2.0.
- لا تعرض القائمة نموذجاً عربياً عاماً صغيراً مماثلاً لنماذجها الصغيرة لبعض اللغات الأخرى؛ يوجد نموذج تونسي أصغر، لكنه ليس بديلاً موثقاً للهجة السورية.
- لذلك لا يُضمّن النموذج الكبير داخل APK. يُنزّل اختيارياً بعد موافقة المستخدم، ويظل التفريغ محلياً بعد تثبيته.
- حجم النموذج لا يضمن دقة اللهجة السورية؛ يجب اختباره على عينات سورية حقيقية قبل وصف الدقة أو اعتماد النتائج آلياً.
- لا يبدأ أي تنزيل أو معالجة سحابية تلقائياً.

### 3. Context Intelligence
- تحديد الموضوع العام والمواضيع الفرعية.
- فهم الإشارات الزمنية والأسماء والجهات.
- السماح بتعدد السياقات داخل التسجيل الواحد.
- عدم تثبيت قائمة جداول مغلقة.

### 4. Application Schema Registry
- قراءة الأقسام والجداول والحقول المتاحة في التطبيق.
- وصف كل وجهة وحقولها المطلوبة والاختيارية.
- تزويد محرك التصنيف بالوجهات الفعلية فقط.
- منع إنشاء وجهة غير موجودة دون مسار إعداد أو موافقة.

### 5. Structured Extraction
استخراج أنواع مستقلة:
- Task
- Appointment
- Commitment
- Decision
- Idea
- Note
- ReminderSuggestion

كل عنصر يجب أن يتضمن النص الأصلي أو مقتطفاً داعماً، ومصدره، ومستوى الثقة.

### 6. Destination Resolver
- اختيار وجهة واحدة أو أكثر لكل عنصر.
- تفسير سبب الاختيار.
- رفض الوجهات غير المتوافقة مع مخطط التطبيق.
- إرجاع حالة مراجعة عند الالتباس.

### 7. Policy & Approval Gate
- لا تنفيذ خارجي تلقائي.
- لا إرسال تذكير أو إنشاء حدث تقويم دون موافقة صريحة.
- لا تحويل تاريخ مبهم إلى موعد قطعي.
- الاحتفاظ بسجل للتعديلات والموافقات.

## عقود أساسية
- كل عنصر مستخرج قابل للتتبع إلى مصدر صوتي.
- لا يُحفظ عنصر منخفض الثقة كحقيقة مؤكدة.
- يجب أن تكون عمليات الاستخراج قابلة لإعادة التشغيل دون تكرار العناصر.
- يجب فصل مخرجات نموذج الذكاء الاصطناعي عن منطق التحقق والحفظ.


## Local Arabic transcription integration (implementation in progress)

- Android uses the Vosk native recognizer through `com.alphacephei:vosk-android:0.3.75` and JNA AAR dependencies.
- The Arabic MGB-2 model is not bundled in the APK. The user explicitly starts its HTTPS download from the app; the installer reports download progress, limits archive/extracted sizes, rejects ZIP path traversal, validates required model files, and installs into app-private storage.
- M4A/AAC recordings are decoded on-device with `MediaExtractor`/`MediaCodec`, downmixed and resampled to mono PCM 16 kHz, then recognized locally.
- Word-level timestamps and confidence values are mapped into the transcription domain objects.
- This integration has not yet passed a verified CI build or on-device test. Arabic MGB-2 benchmark figures do not establish Syrian-dialect accuracy; that requires a dedicated sample evaluation.

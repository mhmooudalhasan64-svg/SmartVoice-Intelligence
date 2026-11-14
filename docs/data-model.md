# نموذج البيانات الأولي

هذا نموذج منطقي محايد عن قاعدة البيانات. تتم مواءمته لاحقاً مع التقنية والمخطط الفعليين.

## Recording
- id
- owner_id
- storage_reference
- duration_ms
- language_hint
- created_at
- processing_status

## TranscriptSegment
- id
- recording_id
- sequence
- start_ms
- end_ms
- speaker_id (nullable)
- text
- confidence (nullable)

## ExtractedItem
- id
- recording_id
- item_type: task | appointment | commitment | decision | idea | note
- title
- description
- normalized_payload
- confidence
- review_status: pending | approved | rejected | edited
- created_at

## ItemEvidence
- id
- extracted_item_id
- transcript_segment_id
- quote
- start_ms
- end_ms

## Destination
- id
- application_table_key
- record_id
- routing_reason
- routing_confidence
- status

## TemporalFact
- id
- extracted_item_id
- raw_expression
- normalized_datetime (nullable)
- timezone (nullable)
- certainty: explicit | inferred | suggested | unknown

## UserCorrection
- id
- user_id
- extracted_item_id
- previous_value
- corrected_value
- correction_type
- created_at

## ReminderAction
- id
- extracted_item_id
- scheduled_for
- approval_status: pending | approved | declined
- execution_status: not_started | completed | failed

## قيود سلامة البيانات
1. لا يجوز أن يوجد عنصر مستخرج دون recording_id.
2. يجب أن يرتبط كل عنصر بمقطع أو أكثر من النص متى توفرت المقاطع.
3. لا يُعتبر التاريخ المقترح موعداً مؤكداً.
4. لا تنفذ ReminderAction إلا بعد موافقة صريحة.
5. يجب أن تكون عمليات الحفظ قابلة لمنع التكرار باستخدام مفاتيح idempotency.
6. تُحفظ التعديلات كسجل تدقيق ولا تمحى أدلة المصدر الأصلية.

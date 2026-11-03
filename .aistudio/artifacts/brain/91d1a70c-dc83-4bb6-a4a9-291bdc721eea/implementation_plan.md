# خطة التنفيذ: التدقيق والتحصين الأمني لتطبيق Masroufi (Security Hardening Plan)

تهدف هذه الخطة إلى معالجة كافة الثغرات والملاحظات الواردة في تقرير التدقيق الأمني الشامل لتطبيق **Masroufi** (`com.aistudio.masroufi.kxmpzq`)، بالإضافة إلى تطبيق ميزات الحماية المتقدمة وفق اختياراتك.

---

## 1. ملخص التحسينات الأمنية المعتمدة

1. **إزالة المفاتيح المضمنة في مسار البناء (CI/CD Secret Hygiene)**:
   - تحديث ملف `.github/workflows/build-apk.yml` لإزالة أجزاء مفتاح API المجزأة (`K_P1` / `K_P2`) ومستودع المفاتيح المشفر بـ Base64 من النص الصريح.
   - الاعتماد على `secrets.GOOGLE_SERVICES_JSON` في GitHub Secrets وتوليد مفتاح تصحيح أخطاء قياسي محلي عبر `keytool` عند الحاجة لبناء نسخة الاختبار دون تسريب أي مفاتيح في المستودع.

2. **منع النسخ الاحتياطي غير المشفر واستخراج البيانات (`allowBackup` & Extraction Rules)**:
   - تعطيل النسخ الاحتياطي التلقائي للنظام في `app/src/main/AndroidManifest.xml` عبر ضبط `android:allowBackup="false"`.
   - تحصين ملفات `app/src/main/res/xml/backup_rules.xml` و `app/src/main/res/xml/data_extraction_rules.xml` لاستثناء كافة قواعد البيانات المحلية والتفضيلات المشتركة وملفات الكاش من النقل أو السحب عبر `adb backup`.

3. **تفعيل تشويش وتقليص الكود (R8 / ProGuard Obfuscation)**:
   - تفعيل `isMinifyEnabled = true` و `isShrinkResources = true` لنسخة الإنتاج (`release`) داخل `app/build.gradle.kts` (مع الحفاظ التام على إعدادات التوقيع الحالية دون مساس).
   - كتابة قواعد حماية متكاملة في `app/proguard-rules.pro` لحماية نماذج البيانات (`com.example.data.**` و `com.example.domain.**`)، ومكتبات Firebase Firestore و Firebase Auth و Credential Manager من التلف أثناء التشويش.

4. **حماية الشاشة من التصوير والتسجيل (`FLAG_SECURE` القابل للتخصيص)**:
   - إضافة خيار أمني في قسم الإعدادات والخصوصية داخل التطبيق لتفعيل أو إيقاف **حماية الشاشة (`FLAG_SECURE`)** في أي وقت.
   - عند تفعيله، يقوم `MainActivity` بتطبيق `WindowManager.LayoutParams.FLAG_SECURE` فوراً لمنع لقطات الشاشة (Screenshots)، تسجيل الشاشة، وإخفاء الأرقام المالية في شاشة التطبيقات الحديثة (Recents).

5. **قفل التطبيق المحلي الاختياري (App Lock: رمز PIN أو بصمة/قفل الجهاز)**:
   - إضافة نظام قفل محلي اختياري في الإعدادات الأمنية يدعم:
     - تعيين رمز **PIN مكون من 4 أرقام** (يُحفظ كـ Hash مشفر محلياً بـ SHA-256 مع Salt في تفضيلات الأمان الخاصة بالتطبيق).
     - أو استخدام **بصمة الجهاز / قفل الشاشة (`KeyguardManager` / Device Credential)** لفتح القفل بسرعة وأمان.
   - عند تفعيل القفل، تظهر شاشة قفل أنيقة فور فتح التطبيق أو العودة إليه من الخلفية لحماية البيانات المالية حتى لو كان الهاتف مفتوحاً.

6. **التصدير الآمن للملفات وحماية الحافظة الحساسة (SAF Export & Sensitive Clipboard)**:
   - إضافة إمكانية **تصدير واستيراد النسخة الاحتياطية (JSON) وتقرير المعاملات (CSV) كملفات مباشرة** عبر إطار عمل التخزين الآمن في أندرويد (`ActivityResultContracts.CreateDocument` و `OpenDocument`).
   - عند استخدام نسخ الحافظة (`ClipboardManager`)، يتم وسم البيانات بـ `ClipDescription.EXTRA_IS_SENSITIVE = true` لإخفائها من معاينة الحافظة في أندرويد 13+، مع جدولة **مسح تلقائي للحافظة بعد 60 ثانية** وزر لمسح الحافظة فوراً.

7. **التحقق من قواعد أمان Firestore (`firestore.rules`) وحماية App Check**:
   - الحفاظ على قواعد `firestore.rules` الصارمة القائمة على مبدأ انعدام الثقة (Zero-Trust) والتي تفرض تطابق `request.auth.uid == userId` والتحقق من هيكل وأنواع البيانات لكل وثيقة في `users` و `transactions` و `accounts` و `envelopes` و `items`.

---

## 2. الملفات المستهدفة بالتعديل

- **`.github/workflows/build-apk.yml`**: إزالة السلاسل المجزأة للمفاتيح واستخدام `secrets.GOOGLE_SERVICES_JSON` مع بديل آمن لبيئة البناء.
- **`app/src/main/AndroidManifest.xml`**: ضبط `android:allowBackup="false"`.
- **`app/src/main/res/xml/backup_rules.xml` & `app/src/main/res/xml/data_extraction_rules.xml`**: حظر استخراج كافة النطاقات الحساسة (`sharedpref`, `database`, `file`, `root`).
- **`app/build.gradle.kts`**: تفعيل `isMinifyEnabled = true` و `isShrinkResources = true` في `buildTypes.release`.
- **`app/proguard-rules.pro`**: إضافة قواعد `@Keep` وحفظ أسماء الحقول لنماذج Firestore و Kotlin Serialization/Reflection.
- **`app/src/main/java/com/example/MainActivity.kt`**:
  - ربط حالة `FLAG_SECURE` بنافذة النظام ديناميكياً.
  - implementing التصدير الآمن كملف (JSON / CSV) عبر `CreateDocument` واستيراد JSON عبر `OpenDocument`.
  - تطبيق `ClipDescription.EXTRA_IS_SENSITIVE` والمسح التلقائي للحافظة بعد 60 ثانية.
  - عرض شاشة قفل التطبيق (`MasroufiSecurityLockScreen`) عند تفعيل القفل المحلي ودعم مصادقة الجهاز أو رمز PIN.
- **`app/src/main/java/com/example/ui/MasroufiHistoryAndModals.kt`** و **`app/src/main/java/com/example/domain/Translations.kt`**:
  - إضافة قسم **"الأمان والخصوصية" (Security & Privacy)** في نافذة الإعدادات للتحكم في:
    1. منع تصوير الشاشة (`FLAG_SECURE`).
    2. قفل التطبيق المحلي (PIN / بصمة الجهاز).
    3. أزرار تصدير واستيراد الملفات الآمنة ومسح الحافظة.

---

## 3. خطة التحقق والاختبار (Verification Plan)

1. **فحص البناء والتجميع (`compile_applet`)**: التأكد من خلو المشروع من أي أخطاء ترجمة بعد تفعيل إعدادات R8/ProGuard وتحديث الواجهات.
2. **تشغيل اختبارات الوحدة و Robolectric (`ExampleRobolectricTest`)**: التحقق من سلامة العمليات الحسابية، وحفظ إعدادات الأمان، وتشفير رمز الـ PIN المحلي.

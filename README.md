# حفظ القرآن

تطبيق أندرويد بسيط لتحفيظ القرآن الكريم لكبار السن: خط كبير وواضح، وخيارات قليلة.

- جدول السور: مربعات كبيرة، ويمكن إضافة السور أو إزالتها.
- تظهر آية واحدة فقط على الشاشة أثناء التلاوة.
- تكرار كل آية: مرة واحدة أو ٣ مرات.
- القارئ: عبدالله بصفر (التلاوة من everyayah.com، وتحتاج اتصال إنترنت).

## التحميل

كل تحديث على الفرع `main` يبني ملف `quran-hifz.apk` تلقائيًا وينشره في صفحة Releases باسم "latest".

## البناء يدويًا

يحتاج Android SDK و Gradle 8.9 و Java 17:

```
gradle assembleDebug
```

## المصادر

- نص القرآن (الرسم العثماني): [quran-json](https://github.com/risan/quran-json) (CC-BY-4.0)، عن موسوعة القرآن الكريم quranenc.com.
- الخطوط: Amiri Quran و Tajawal (SIL Open Font License).
- التلاوة: [everyayah.com](https://everyayah.com).

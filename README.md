# OpenWrt Manager Pro

<div align="center">

**مدير راوترات OpenWrt — النسخة المطوّرة | Advanced OpenWrt router manager for Android**

Kotlin · Jetpack Compose · MVVM/Clean Architecture · Hilt · WorkManager

[![Build & Release APK](https://github.com/NassarAlshabi1/openwrt-manager-pro/actions/workflows/build-release.yml/badge.svg)](https://github.com/NassarAlshabi1/openwrt-manager-pro/actions/workflows/build-release.yml)

</div>

---

## الميزات | Features

| # | الميزة | الوصف |
|---|--------|-------|
| 1 | **لوحة التحكم** | حالة الراوتر كاملة في شاشة واحدة: مدة التشغيل، حمل المعالج، الذاكرة، الحرارة، عدد الأجهزة، WAN و DNS |
| 2 | **خريطة الأجهزة** | قائمة الأجهزة المتصلة مع **قاعدة بيانات OUI محلية** (MAC → المصنّع) و**قياس قوة الإشارة** لكل جهاز لاسلكي |
| 3 | **التشخيص الذكي** | فحص صحي شامل بدرجة من 100: المعالج، الذاكرة، التخزين، الحرارة، الإنترنت، DNS، **تعارض قنوات الواي فاي**، **صحة شبكات Mesh** (802.11s / batman-adv)، **أمان الواي فاي والجدار الناري**، وفحص سجلات النظام |
| 4 | **فاحص تحديثات النظام** | يقارن إصدار OpenWrt المثبّت مع أحدث إصدار رسمي عبر API المستخدم الرسمي `sysupgrade.openwrt.org` ويرشدك لمنتقي البرامج الثابتة |
| 5 | **المراقبة في الخلفية** | WorkManager يفحص الراوتر دورياً ويرسل إشعارات عند **انقطاع الراوتر** أو **دخول جهاز جديد** للشبكة |
| 6 | **أحداث ubus اللحظية** | تدفق أحداث حي (دخول/خروج الأجهزة) بدل الاستطلاع اليدوي — مع ترحيل تلقائي لـ WorkManager في الخلفية لتوفير البطارية |
| 7 | **إعداد ذكي** | يكتشف عنوان الراوتر تلقائياً من بوابة الشبكة الحالية، مع حفظ بيانات الدخول **مشفّرة** (Android Keystore + EncryptedSharedPreferences) |
| 8 | **بنية نظيفة** | MVVM / Clean Architecture + Hilt DI + **اختبارات وحدوية لطبقة RPC والمحللات** |

## كيف يعمل | How it works

يتصل التطبيق بالراوتر عبر **ubus JSON-RPC** (`/ubus/` — نفس الآلية التي يستخدمها LuCI):

```
App (Compose UI) → ViewModels → UseCases → Repositories → UbusClient (OkHttp)
                                                              ↓
                                    POST http://<router>/ubus/  {"method":"call",...}
                                                              ↓
                            session login → system.board/info → network.interface dump
                            iwinfo assoclist → file read/exec → diagnostics
```

**متطلبات على الراوتر:** تفعيل `uhttpd-mod-ubus` (مثبّت افتراضياً مع LuCI) وحساب root.

## البناء | Build

```bash
./gradlew assembleDebug          # APK للتطوير
./gradlew testDebugUnitTest      # الاختبارات
./gradlew assembleRelease        # APK موقّع (يتطلب مفاتيح التوقيع أو يوقّع تلقائياً في CI)
```

## التوقيع والإصدار | Signing & Releases

سير عمل `build-release.yml`:
- عند كل دفع إلى `main`: **اختبارات + بناء APK** ورفعه كـ artifact
- عند دفع وسم `v*` (مثل `v1.0.0`): **إنشاء GitHub Release مع APK موقّع** مرفق

### توقيع ثابت (مستحسن)
لضمان توقيع ثابت بين الإصدارات أضف هذه الأسرار إلى المستودع
(Settings → Secrets and variables → Actions):

| السر | القيمة |
|------|--------|
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 release.keystore` |
| `SIGNING_STORE_PASSWORD` | كلمة مرور الـ keystore |
| `SIGNING_KEY_ALIAS` | اسم المفتاح |
| `SIGNING_KEY_PASSWORD` | كلمة مرور المفتاح |

إن لم تُضبط الأسرار، يولّد CI مفتاحاً مؤقتاً لكل إصدار (يعمل لكنه غير ثابت — ثبّت المفتاح المولّد من artifacts لاحقاً).

### إنشاء إصدار
```bash
git tag v1.0.0 && git push origin v1.0.0
```

## الترخيص

MIT — انظر [LICENSE](LICENSE)

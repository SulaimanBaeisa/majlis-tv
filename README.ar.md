<div align="center">

# مجلس

**واجهة عربية أولًا لتلفزيون أندرويد.**
مواقيت الصلاة على أم القرى مع الإقامة، التاريخ الهجري، آية أو حديث موثّق كل يوم، الدوري السعودي،
شريط أخبار عربي، و٣٩ خلفية سعودية تتغيّر يوميًا.

[English](README.md) · [التركيب](docs/INSTALL.md) · [حل المشاكل](docs/TROUBLESHOOTING.md)

</div>

![واجهة مجلس](docs/screenshots/home.jpg)

<sub>نموذج تصميم — الواجهة الفعلية تتبع هذا التصميم.</sub>

## الشاشات

| الرئيسية | المباريات |
|:---:|:---:|
| ![الرئيسية](docs/screenshots/home.jpg) | ![المباريات](docs/screenshots/matches.jpg) |
| **الأخبار** | **الدرج** |
| ![الأخبار](docs/screenshots/news.jpg) | ![الدرج](docs/screenshots/drawer.jpg) |
| **شاشة التوقف** | **الإعدادات** |
| ![شاشة التوقف](docs/screenshots/screensaver.jpg) | ![الإعدادات](docs/screenshots/settings.jpg) |

الصور الأربع الأولى نماذج تصميم، والواجهة الفعلية تتبعها. شاشتا التوقف والإعدادات
من تلفاز سوني يعمل بأندرويد 12.

## أعطِ وكيلك هذه الجملة

مجلس تُركّب بأمر واحد لوكيل البرمجة عندك: Claude Code أو Codex أو Cursor أو Hermes.
الوكيل يتبع [`AGENTS.md`](AGENTS.md)، وكل قرار على التلفزيون يتخذه أداة `./tv`، فالنتيجة واحدة
مهما كان النموذج.

**التركيب**

> ركّب مجلس على تلفزيون أندرويد عندي. اتبع
> https://github.com/SulaimanBaeisa/majlis-tv/blob/main/AGENTS.md حرفيًا، ولا تكتب أوامر adb من عندك.
> عنوان التلفزيون 192.168.1.50 ومدينتي جدة.

**التحديث**

> حدّث مجلس على تلفزيوني (192.168.1.50) من https://github.com/SulaimanBaeisa/majlis-tv — اتبع قسم
> التحديث في AGENTS.md حرفيًا.

**الرجوع**

> أرجِع الشاشة الرئيسية الأصلية على تلفزيوني (192.168.1.50) حسب قسم الرجوع في
> https://github.com/SulaimanBaeisa/majlis-tv/blob/main/AGENTS.md.

## ماذا تغيّر على التلفزيون

- تصبح مجلس هي الشاشة الرئيسية وشاشة التوقف.
- تُعطَّل ثلاث حزم من النظام، ولا تُحذف: المشغّل الأصلي، والتوصيات، وشاشة التوقف الأصلية.
- تُمنح صلاحيتا الظهور فوق التطبيقات والمنبّه الدقيق لأذان الصلاة.
- زر **الأصلي** في إعدادات مجلس يعيد كل شيء كما كان.

## التركيب بدون وكيل

```bash
git clone https://github.com/SulaimanBaeisa/majlis-tv && cd majlis-tv
./tv doctor
./tv build --city "جدة"
./tv install --ip 192.168.1.50
./tv switch --ip 192.168.1.50 --to free --wait 180   # ثم اختر مجلس على التلفزيون
./tv verify --ip 192.168.1.50 --mode free --apk Majlis.apk
```

## التحديث والرجوع

```bash
./tv update --ip 192.168.1.50 --city "جدة"
./tv rollback --ip 192.168.1.50
```

## الرخصة

MIT — انظر [LICENSE](LICENSE) و[NOTICE](NOTICE). الصور لها رخصها الخاصة في
[PHOTO-CREDITS.md](PHOTO-CREDITS.md).

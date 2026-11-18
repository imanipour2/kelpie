# KELPIE KAI-001 | سند معماری و نقشه‌راه مستقل هوشمندی عامل و اطمینان از تکمیل مأموریت

_Kelpie Agent Intelligence & Reliable Task Completion — پژوهش تطبیقی، مشخصات محصول، معماری، بک‌لاگ، امنیت و معیارهای پذیرش | نسخه ۱.۰ پیش‌نویس | ۱۸ نوامبر ۲۰۲۶_

شناسه: EF-OPS-KELPIE-KAI-001 (پیشنهادی، غیر از شماره مصوبه EF-GOV) | مالک تصمیم: کارفرما/CDG | وضعیت: RESEARCHED / WORKSTREAM_DEFINED / PROPOSED / NO_IMPLEMENTATION / NO_RELEASE

اصل سند: https://docs.google.com/document/d/1AJ8Yfr9tDXg4VRy8aepkpuWdMQEWSAn5ObrImMg-zm0/edit | آینه مستندات پیشنهادی: https://github.com/imanipour2/kelpie/tree/docs/kai-agent-intelligence-reliability-20261118 | مرجع کد اجرا: GitHub main و PRهای بررسی‌شده؛ نسخه APK تنها با بازخوانی واقعی گوشی.

دامنه توسعه: هوشمندی عامل هنگام مشاهده و تعامل با وب‌سایت، پیمایش تمام نتایج، مرتب‌سازی و فیلتر، استخراج معتبر، بازبینی قیمت/موجودی، مدیریت خطا، انتقال کنترل به انسان و ثبت شواهد. این سند تغییر فوری در کد، تونل، تنظیم پورت، اتصال احراز هویت، APK، WordPress LIVE/STAGING یا تصمیم امنیتی موجود ایجاد نمی‌کند.

## ۱. حکم اجرایی و مرزبندی شاخه مستقل KAI

تعریف: KAI یک Workstream مستقل محصول/معماری برای «کامل و قابل اثبات انجام دادن کار در مرورگر» است. شاخه مستنداتی GitHub آن از main منشعب شده، نه از PR66/67؛ بنابراین می‌تواند به‌صورت مستقل بررسی شود، اما پیاده‌سازی واقعی وابسته به APIهای بومی پذیرفته‌شده R5 خواهد بود.

- هدف عملکردی: هر نتیجه جست‌وجوی عامل به شواهد پوشش فهرست، اعمال واقعی کنترل‌های سایت، مقایسه و وضعیت اطمینان متصل باشد. «چیزی پیدا نشد» هرگز از نمایش ناقص صفحه نتیجه‌گیری نشود.

- مرز با R5 Runtime: رضایت مالک، Verified Principal، Ed25519/Native STOP، VirtualDisplay، تصویر تازه در پس‌زمینه، سیاست قفل و تونل همچنان در جریان تثبیت R5 هستند. KAI این‌ها را بازنویسی یا دور نمی‌زند.

- مرز با PR67 Diagnostics: PR67 چرخه عمر سرویس، PID، گیت‌های AUTH/OWNER/RENDER/REMOTE و رویدادهای اتصال را مستند می‌کند؛ KAI بر گیت‌های OBSERVE/ACT/VERIFY/COVERAGE/RESULT تمرکز دارد. رویدادها فقط از طریق رابط مشترک پیشنهادی مرتبط شوند، نه ثبت لاگ و مرجع موازی.

- مرز با ENERFOAM: استفاده از staging برای QA محتوای عمومی پس از مجوز، مجاز است؛ قیمت واقعی WooCommerce، خرید، سفارش، انتشار Elementor/WordPress و عملیات مالی خارج از این مأموریت است.

وضعیت تصمیم: درخواست مالک اجازه تهیه سند، پژوهش و تعریف شاخه مستقل است؛ معادل تصویب معماری تفصیلی یا مجوز ادغام/نصب/انتشار نیست. هر انتخاب فنی مهم با دامنه، جانشینی و Approval در CDG ثبت شود.

## ۲. خط مبنا و شواهد واقعی (نه فرض نسخه نصب‌شده)

GitHub kelpie main در زمان این بررسی: 91dadda8caebe69394e4a6b4589892ae4a495e54؛ AGENTS.md الزام برابری Android/iOS در هر قابلیت و PR را حفظ کرده و برای استثنا به تصمیم مستقل نیاز دارد. سیاست توسعه مستقل اجازه push در شاخه docs/feature بدون انتشار و bump نسخه می‌دهد؛ Main merge مجوز Release نیست.

PR66: https://github.com/imanipour2/kelpie/pull/66 | HEAD=68ade958af66b7f6de219abd2d9f9011478f5a40 | Mother MCP + R5 fresh background screenshot/Connection Status، در وضعیت بررسی و نه Merge؛ PR67: https://github.com/imanipour2/kelpie/pull/67 | HEAD=0d33eb894383f8654d4feffe4fa22c4bbb64b802 | RFC Diagnostics، نه کد runtime پذیرفته‌شده.

پرونده حادثه و گوشی: https://github.com/imanipour2/enerfoam-website/issues/98 و https://docs.google.com/document/d/1_ulI2TV66d60_2XPJTh3aGCTCbH_40Na5o3_YhUGu1U/edit . اسناد از جلسات مختلف پورت‌های 18460/18461/18462/18463 و بسته‌های QA متفاوت را ثبت کرده‌اند؛ عدد جدید یا بسته نصب‌شده فقط از خواندن دستگاه معتبر است. این جریان هیچ پورت/بسته دیگری اضافه نمی‌کند.

منبع قبلی پژوهشی بدون جانشینی: EF-OPS-KELPIE-AGENTIC-MOBILE-RESEARCH-001، ۱۶ نوامبر ۲۰۲۶، https://docs.google.com/document/d/1oM-IQArjUKb0biVodw87F0KJGPh74XAwg01cADPgtDM/edit ؛ این سند لایه‌ای تخصصی‌تر درباره کارایی مأموریت‌های وب بر آن می‌افزاید.

کد بررسی‌شده در HEAD PR66: R5WebViewActionScripts.SNAPSHOT متن body تا ۲۸٬۰۰۰ نویسه و عناصر قابل مشاهده تا ۱۶۰ عدد را می‌خواند؛ R5PrivateMcpActions شامل page.scroll و tab.click/select/wait است؛ R5NativeBrowserPort برای SCROLL فقط تغییر scrollX/scrollY را راستی‌آزمایی می‌کند؛ لایه Mother BrowserGateway محدودیت maxChars/maxElements، پرچم truncated و تصویر فقط viewport دارد؛ fullPage در این مسیر Unsupported است. این داده‌ها برای تعیین دقیق دسترس‌پذیری در APK نصب‌شده کافی نیستند.

رخداد مبنا: جست‌وجوی پرواز یکشنبه ۱ آذر، شیراز→تهران، قبل از ۰۹:۰۰. عامل بدون استفاده از مرتب‌سازی قیمت و زمان، بدون پیمایش انتهای فهرست، به نتیجه منفی رسید؛ تصویر مالک گزینه آسمان ۰۶:۰۰ با قیمت نمایشی ۱۳٬۱۶۰٬۱۰۰ تومان و ۷ صندلی، و دو گزینه پارس‌ایر ۰۶:۳۰ را آشکار کرد. علت اثبات‌شده در آن نوبت: اجرای ناقص Workflow توسط عامل. قابلیت محدود تصویر/پیمایش و اتصال FORBIDDEN ریسک تشدیدکننده‌اند، نه عذر جایگزین.

قاعده نسبت‌دهی: (الف) خطای عامل، (ب) محدودیت ابزار، (ج) خطای/طراحی وب‌سایت، (د) اتصال و احراز هویت، (هـ) داده متغیر قیمت/ظرفیت، جدا ثبت شوند. استناد به داده نمایشی به‌جای تایید صفحه رزرو ممنوع.

## ۳. مطالعه تطبیقی چندزبانه: آنچه واقعاً قابل اقتباس است

### ۳.۱ چین — ابزارهای محصول و پژوهش

[CH-01] Tencent BrowserSkill: مرورگر Chrome/Edge واقعی با کوکی‌های همان مرورگر، Agent Window مستقل، قرض‌گرفتن تب با رضایت، handoff انسان/عامل، مرور و ادامه کار بعد از دخالت انسان؛ خروجی جدول/لیست JSON/CSV همراه پوشش صریح استخراج؛ لاگ عملیاتی و شواهد شبکه/کنسول و ویدئو. اقتباس منتخب: حالت همکاری با کاربر، درج coverage در ExtractionReceipt، پنجره کار جدا بدون آوردن اجباری Kelpie به جلو و هماهنگی control ownership. این ابزار روی Chrome/Edge و extension/daemon است، نه جایگزین مستقیم WebView اندروید. https://github.com/Tencent/BrowserSkill ؛ نسخه چنج‌لاگ خوانده‌شده 0.4.2 در ۵ نوامبر ۲۰۲۶.

[CH-02] ByteDance UI-TARS/Agent TARS: رابط ترکیبی GUI+DOM، مشاهده تصویری، Grounding عناصر، برنامه‌ریزی و Event Stream. اقتباس: Fall-back تصویر/مختصات فقط پس از مشاهده تازه و تطبیق viewport؛ ابزار ارزانِ DOM/role اولویت دارد و اجرای یک عمل با سند تازه راستی‌آزمایی می‌شود. استفاده از مدل خاص، دانلود وزن یا عبور از سیاست امنیتی در این سند پیشنهاد نشده است. https://github.com/bytedance/UI-TARS-desktop

[CH-03] Midscene.js (مستندات چینی): API مشاهده تصویری، aiQuery، aiAssert، مشاهده نتیجه پس از Action و گزارش تصویری؛ مستندات رسمی به تفاوت تأیید بصری و اعتبارسنجی قطعی داده اشاره دارند. اقتباس: راستی‌آزمایی دوگانه «آیا گزینه فیلتر انتخاب شد؟» و «آیا فهرست واقعاً تغییر کرد؟»؛ Extraction ساختاریافته قیمت، سپس assertion قطعی به‌جای پذیرش تشخیص مدل. https://www.midscenejs.com/zh/basics و https://github.com/web-infra-dev/midscene

[CH-04] Alibaba Mobile-Agent-v3/v3.5 و GUI-Owl: کنترل GUI چندسکویی، برنامه‌ریزی، بازاندیشی، حافظه وظیفه؛ ToolCUA، انتخاب بین ابزار ساختاری و GUI را به مسئله مشخصی تبدیل کرده است. اقتباس: Planner/Executor/Verifier مستقل، حافظه کوتاه‌مدت نتایج در همان مأموریت و تصمیم برای عوض‌کردن روش وقتی مشاهده ناقص است؛ هیچ ادعای امتیاز Benchmark مدل‌های آن برای Kelpie مجاز نیست. https://github.com/X-PLUG/MobileAgent

[CH-05] Zhipu Open-AutoGLM: ابزار Tap/Swipe/Wait/Take_over، کمک انسان برای ورود/کد و عملیات حساس؛ پیاده‌سازی پژوهشی با ADB کنترل عمومی دستگاه دارد. اقتباس فقط الگوی explicit NEEDS_HUMAN و resume؛ استفاده از ADB عمومی/کنترل دیگر برنامه‌ها در Kelpie محصولی رد می‌شود. https://github.com/zai-org/Open-AutoGLM

[CH-06] Alibaba WebSailor/WebDancer: پژوهش جست‌وجوی عمیق و بلندمدت، اعتبارسنجی منابع متعدد و مدیریت عدم قطعیت؛ محصول کنترل WebView یا ارائه‌دهنده تصویر تازه نیست. اقتباس: cross-source validation برای پرسش‌هایی مانند «بهترین قیمت» و ثبت سطح پوشش منابع. https://github.com/Alibaba-NLP/DeepResearch

### ۳.۲ روسیه — محصول عامل و زیرساخت آزمون

[RU-01] Yandex Browser با Alice AI: در اطلاعیه رسمی ۱۲ نوامبر ۲۰۲۶، انجام چندمرحله‌ای کار در وب، بازکردن سایت/تب و جمع‌آوری اطلاعات با اجازه کاربر؛ برای ورود به حساب، کنترل به کاربر واگذار می‌شود و سپس مأموریت ادامه می‌یابد. قابلیت عامل برای مقایسه قیمت و رزرو در معرفی محصول ذکر شده است. اقتباس: workflowهای task-based، تحقیق قیمت بین چند منبع و handoff شفاف کاربر، نه ادعای پیاده‌سازی موتور داخلی Yandex. https://yandex.ru/company/news/12-11-2026-01 و https://browser.yandex.ru/c/aiagent/index

[RU-02] Aerokube Moon: session isolation، live view، اجرای تست مرورگرها، ضبط ویدئو/لاگ و نگهداری artefact. اقتباس: evidence replay و تشخیص مرحله‌ای خرابی برای CI آزمایشگاه؛ برای کنترل WebView واقعی گوشی S24 جایگزین به حساب نمی‌آید. Selenoid قدیمی در ۱۷ دسامبر ۲۰۲۴ archive شده، پس برای فناوری جدید به Moon مراجعه شود. https://github.com/aerokube/moon و https://github.com/aerokube/selenoid

### ۳.۳ سایر محصولات و فناوری‌های مرجع

[GL-01] Microsoft Playwright MCP: Accessibility snapshot با refهای قابل عمل، locate بر مبنای role/label، select option، انتظار هوشمند، بررسی قابل کلیک بودن عنصر، screenshot fullPage و Vision coordinate tools در حالت صریح. اقتباس: انتخاب هوشمند کنترل‌ها و تشخیص انتهای فهرست، با توجه به تفاوت موتور Android WebView و Playwright. https://github.com/microsoft/playwright-mcp ؛ https://playwright.dev/docs/actionability

[GL-02] Browser Use: چرخه مشاهده→اقدام→مشاهده، پیمایش مبتنی بر نشانه و تشخیص بخش‌های باقی‌مانده و scroll container؛ اقتباس: task-loop و گزارش page coverage؛ ادعای بی‌خطابودن بارگذاری تدریجی مطرح نیست. https://github.com/browser-use/browser-use

[GL-03] Stagehand و Skyvern: مشاهده/اقدام/استخراج، اجرای گردش‌کار، شواهد اجرای وظیفه و اعتبارسنجی داده. اقتباس: جداسازی استخراج از Action و آزمون postcondition؛ بدون افزودن وابستگی ابری اجباری، هزینه اشتراک یا انتقال کوکی از گوشی. https://github.com/browserbase/stagehand و https://github.com/Skyvern-AI/skyvern

اصل انتخاب فناوری: منبع رسمی سازنده/کد و آزمون نسخه مقدم بر ادعای تبلیغاتی و روایت فروم است. هیچ مدل، SDK یا بخش کد ثالث بدون بررسی مجوز، تهدید امنیتی، هزینه و تطبیق پلتفرمی به Kelpie کپی نشود.

## ۴. تعریف محصول و قرارداد خروجی مأموریت

مخاطب: فرد یا عامل متصل به مرورگر Kelpie که کارهای وب‌گردی، مقایسه خرید، یافتن پرواز و بلیت، رزرو، جمع‌آوری اطلاعات فنی، بررسی وضعیت سایت و کار با حساب شخصی را در محدوده رضایت مالک انجام می‌دهد.

نتیجه‌های مجاز و معنای آن‌ها: VERIFIED_COMPLETE (شرط‌های هدف و معیار پوشش با مدرک گذرانده شد)؛ VERIFIED_EMPTY (صفحه نتیجه خالی با فیلتر و پوشش کامل و شاهد پایدار تأیید شد)؛ INCOMPLETE (بخش‌هایی بررسی نشده/مهلت یا بودجه تمام شد)؛ NEEDS_HUMAN (ورود، OTP، CAPTCHA یا اقدام حساس)؛ BLOCKED (عدم دسترسی/403/اتصال/سیاست)؛ UNCERTAIN (عمل انجام شده ولی اثر آن قطعی نیست). «EMPTY» تنها با شاهد مستقل مجاز است؛ «FORBIDDEN» هرگز به «پرواز نیست» تبدیل نشود.

چهار سطح کیفیت: TransportReady ≠ NativeOwnerApproved ≠ ActionExecuted ≠ TaskCompleted. داشبوردها باید هر سطح و زمان آخرین شاهد را جدا گزارش کنند.

هر گزارش نهایی باید نوع کالا/خدمت، مبدأ/مقصد، تاریخ/منطقه زمانی، فیلترهای واقعی اعمال‌شده، معیار مرتب‌سازی، تعداد منبع، تعداد موارد دیده‌شده و حذف تکراری، پوشش صفحات، قیمت و واحد پول، ظرفیت در زمان مشاهده، وضعیت مرحله رزرو و بازه اعتبار مشاهدات را مشخص کند.

## ۵. نیازمندی‌های اولویت‌بندی‌شده KAI

### ۵.۱ P0 — معیارهای ضروری دقت و تمامیت

- KAI-REQ-001 | Readiness حقیقت‌گو: جداکردن سلامت محلی، صحت قرارداد MCP، رضایت مالک، renderer، اجرای اقدام، اثر و نتیجه تجاری. وابستگی به R5/PR67 فقط از طریق adapter.

- KAI-REQ-002 | DiscoverControls: یافتن دکمه‌های مرتب‌سازی، قیمت، ساعت، فیلتر، تقویم، pagination و «نمایش بیشتر» با role/label/متن/تصویر و وضعیت فعال آن‌ها در زبان فارسی/انگلیسی.

- KAI-REQ-003 | Coverage-aware Snapshot: scroll containerهای واقعی، bounding rect، viewport و اندازه document، truncated، seenCount، observedCount، estimatedBelow/unknown، و تغییر epoch بعد از ناوبری؛ مقدار نامعلوم صریحاً unknown باشد.

- KAI-REQ-004 | ScrollUntil: پیمایش bounded هر container با تشخیص lazy/infinite/virtualized lists، تکرار/هم‌پوشانی کارت‌ها، توقف امن و نشانه پایان مستند؛ تنها scrollY متفاوت اثبات کامل‌بودن نیست.

- KAI-REQ-005 | Sort/Filter Verify: پیش از عمل selector تازه، سپس مشاهده تغییر selection state، برچسب دکمه، URL/state، ترتیب گزینه‌ها یا شمار نتایج؛ در صورت عدم اثبات، خطای FILTER_UNVERIFIED.

- KAI-REQ-006 | ResultsExtract: استخراج رکورد ساختاری از کارت‌های واقعی؛ تاریخ دقیق میلادی/شمسی با مبنای منطقه زمانی، ساعت حرکت/رسیدن، پرواز، شرکت، سایت، نوع بلیت، مبلغ به تومان/ریال با نرمال‌سازی و منبع هر فیلد.

- KAI-REQ-007 | Compare/De-duplicate: حذف دوباره‌نمایی یک پرواز با شناسه ترکیبی مشروط، نگهداری فروشنده/نرخ جدا، مقایسه در یک واحد پول و تاریخ؛ نشان‌دادن ابهام هزینه‌های جانبی.

- KAI-REQ-008 | Inventory Recheck: موجودی اولیه فقط «نمایش‌داده‌شده» است؛ با انتخاب غیرتراکنشی گزینه، مرحله بعد و قیمت نهایی بازخوانی شود؛ بدون پرداخت، بدون ثبت مسافر و بدون اظهار «رزروشده».

- KAI-REQ-009 | TaskCompletenessGate: منع پاسخ منفی یا ادعای بهترین قیمت قبل از کنترل دسته‌بندی، فیلتر/مرتب‌سازی، پیمایش/صفحات بعد، خطاها و چند منبع در صورت درخواست کاربر.

- KAI-REQ-010 | SecureActionReceipt: CommandId، principalRef غیرقابل بازسازی، consentGeneration، tab/documentEpoch، pre/post state digest، action status، observed effect و شاهد تصویری تازه یا خطای NO_FRESH_FRAME؛ بدون ذخیره محرمانه‌ها.

- KAI-REQ-011 | Stop/Human: در lock/STOP/session_revoked و اقدام حساس، اجرای صف متوقف؛ OTP/رمز/پرداخت/CAPTCHA فقط با کنترل واقعی کاربر و رضایت معتبر، بدون دورزدن ابزار امنیت سایت.

### ۵.۲ P1 — پوشش قابلیت‌های حرفه‌ای

- KAI-REQ-012 | Semantic locators: getByRole/getByLabel/getByText و refs با هویت سند، بررسی یکتایی و وضعیت قابل کلیک؛ CSS selector تنها fallback و stale ref مانع کلیک شود.

- KAI-REQ-013 | Nested/virtual list strategy: یافتن ناحیه‌ای که واقعاً اسکرول می‌کند، sentinel/loader و تغییر offset راستی‌آزمایی؛ اگر پایان قابل اثبات نبود INCOMPLETE.

- KAI-REQ-014 | Visual grounding: امکان انتخاب کنترل تصویری با coordinate و جفت‌شدن به viewport/screenshot nonce؛ فقط QA و با consent، نه کلیک روی تصویر قدیمی.

- KAI-REQ-015 | Smart waiting: loader/spinner، network idle قابل اعتماد با محدودیت، DOM mutation stabilization، pagination, SPA، timeout متناسب و avoid hard-coded sleep به‌عنوان اثبات.

- KAI-REQ-016 | Evidence Journal: timeline کم‌حجم از Observe/Find/Act/Verify/Extract، stop reason، coverage percent اگر قابل اندازه‌گیری و unknown اگر نه؛ رمز/کد و متن حساس ذخیره نشود.

- KAI-REQ-017 | Human takeover: اعلان مشخص هدف، وضعیت، صفحه/تب و مرحله درخواست کمک؛ کنترل انسانی در همان WebView؛ پس از بازگشت snapshot تازه/consent معتبر و عدم replay کور.

- KAI-REQ-018 | Failure recovery: retry فقط برای خواندن/عمل idempotent با backoff و سقف؛ mutation با نتیجه UNCERTAIN به‌صورت خودکار تکرار نشود؛ پس از 403/قطع شبکه resume checkpoint با بازتأیید.

- KAI-REQ-019 | Cross-agent capability negotiation: schema versioned، قابلیت‌های discoverable، degraded mode صادقانه، هماهنگی wrapper واقعی ChatGPT و ابزارهای دیگر؛ بدون بازکردن legacy API ناامن.

- KAI-REQ-020 | Accessibility and RTL: پیام‌ها و کنترل‌های قابل لمس 48dp، label قابل خواندن، حالت رنگ+متن، صفحه فارسی و تقویم شمسی؛ آزمایش RTL واقعی Android.

### ۵.۳ P2 — توسعه اختیاری پس از اثبات پایه

- KAI-REQ-021 | Task memory فقط کوتاه‌مدت/محدود به scope و بدون ذخیره حساب؛ provenance از هر گزاره.

- KAI-REQ-022 | Workflows template-based برای flights, hotels, comparison shopping, Enerfoam staging QA و research با تعریف DoD مستقل.

- KAI-REQ-023 | چند منبع و جمع‌بندی با provenance، بدون ادعای کامل‌بودن کل اینترنت؛ رتبه‌بندی اعتماد و قیمت با timestamp.

- KAI-REQ-024 | Cross-platform contract Android/iOS مشترک؛ اجرای متناسب با محدودیت WKWebView و parity/exception رسمی قبل از تغییر runtime.

- KAI-REQ-025 | قابلیت مشاهده خطا/تکرار آزمایش، با instrumentation محدود و حذف اتوماتیک شواهد، نه ضبط پیش‌فرض زندگی مرور کاربر.

## ۶. معماری پیشنهادی: هسته کم‌حجم، قراردادهای Typed

KAI یک Feature Layer با اختیار عملیاتی مستقل نیست: اتصال خارجی Authenticated Agent → R5 Auth/Consent/Policy Gateway (دست‌نخورده) → KAI Task Coordinator (در host/agent یا adapter محدود) → Observe/Find → Native Browser Commands → Verified Receipt → Coverage & Extraction → Task Outcome. هیچ درخواست وب‌سایتی مستقیم فرمان AI یا مجوز مالک تولید نمی‌کند.

اجزای منطقی: TaskSpec/ConstraintNormalizer، ObservationPort، LocatorResolver، ScrollCoverageEngine، SiteInteractionPlan، StructuredExtractor، EvidenceVerifier، RiskGateAdapter، TaskJournal، UserHandoff. فقط «حالت و قرارداد» مشترک؛ پیاده‌سازی Kotlin برای Android و Swift برای iOS با parity rule یا استثنای تصویب‌شده.

سیاست انتخاب روش: ۱) قرارداد شناخته‌شده سایت/عناصر قابل‌دسترس، ۲) semantic role/label و ref معتبر، ۳) selector DOM دارای هویت سند، ۴) تصویر تازه با قفل مختصات و کنترل مالک، ۵) کمک انسان. هیچ تزریق خودسرانه JS یا کنترل همه‌برنامه‌ای ADB به API عمومی اضافه نشود.

پیشنهاد API نسخه‌دار (فقط Design، نه ابزار موجود): kai.task.create(constraints); kai.page.observe({tabId,depth,budget}); kai.controls.discover({kind}); kai.control.apply({ref,value,expectedEffect}); kai.page.scrollUntil({containerRef,goal,maxSteps}); kai.results.extract({schema,budget}); kai.task.verify({completionCriteria}); kai.task.journal({redacted:true}); kai.task.pause/resume/stop(). این ابزارها مجوز R5 را دور نمی‌زنند و هر mutation در Native OperationAdmission کنترل می‌شود.

ساختار Receipt حداقلی: {taskId, stepId, toolVersion, tabId, documentEpoch, action, stateBeforeHash, stateAfterHash, observed, verified, coverage:{containerId,seenCount,atEnd,confidence}, result:{status,reason}, timestamps}. برای اسکرول درون container باید scrollTop/clientHeight/scrollHeight و change delta ثبت شود؛ برای لیست مجازی «پایان سند» معادل پایان نتایج نیست.

state machine: CREATED → SCOPED → PAGE_OBSERVED → CONTROLS_IDENTIFIED → FILTERS_VERIFIED → RESULTS_ENUMERATING → COVERAGE_CHECKED → INVENTORY_RECHECKED → VERIFIED_COMPLETE. شاخه‌های terminal: VERIFIED_EMPTY, INCOMPLETE, NEEDS_HUMAN, BLOCKED, UNCERTAIN. هر حالت فقط با شاهد و predicate روشن انتقال یابد.

قاعده «پایان فهرست»: همه containerهای نتایج مربوط بررسی شده‌اند؛ صفحه بعد/نمایش بیشتر وجود ندارد یا تمام شده؛ loader ناپدید/idle پایدار است؛ چند مشاهده متوالی شمار/هویت نتایج جدید نیاورده است؛ فیلتر و Sort انتخاب‌شده معتبر مانده؛ محدودیت maxElements/truncated رفع یا گزارش شده. در صورت نبود شاهد کافی atEnd=unknown و خروجی INCOMPLETE است.

خصوصیات فیلتر و Sort: قبل از Click از ControlSnapshot «نام قابل دسترس و انتخاب فعلی» خوانده شود؛ بعد از Click/Select، state تازه و حداقل یک شاهد مستقل (مانند ترتیب عددی قیمت‌ها با Decimal، تغییر chip فعال، یا سرصفحه نتایج) سنجیده شود. اگر action فقط DISPATCHED است و اثر دیده نشد، موفقیت اعلام نشود.

## ۷. نقش عامل بیرونی در مقابل Kelpie؛ پیشگیری از بازتولید خطا

عامل (ChatGPT/Claude/Gemini/دیگر) مسئول تعبیر هدف، انتخاب سایت، برنامه‌ریزی مقایسه، تصمیم بعدی، تشخیص کافی‌نبودن شواهد و نگارش گزارش است. Kelpie مسئول API واقعی، WebView tab، مشاهده تازه، click/scroll/fill، قفل هویت و تشخیص اثر ابزار است. KAI آداپتر TaskCompleteness را به قرارداد اضافه می‌کند؛ هیچ سخت‌افزاری نمی‌تواند جانشین درست فکرکردن عامل شود.

برای مأموریت نمونه «ارزان‌ترین پرواز یکشنبه تا قبل ۹»: فیلتر تاریخ/مسیر/نفر؛ جست‌وجوی کنترل‌های «مرتب‌سازی/قیمت/ساعت»؛ فعال‌سازی و صحت‌سنجی آن‌ها؛ پیمایش تمام لیست‌های مرتبط؛ استخراج همه موارد در محدوده ۰۰:۰۰ تا ۰۹:۰۰ (حرکت قبل از ۹، نه ورود)؛ حذف موارد تکراری؛ کنترل قیمت/موجودی پس از انتخاب؛ مقایسه با منابع خواسته‌شده؛ اعلام برنده و درجه تأیید. اگر اتصال در نیمه راه قطع شد INCOMPLETE/CONNECTED_SESSION_FAILED، نه NO_FLIGHT.

کاربر برای شناسایی دکمه‌های معمول سایت نباید نشان اضافه کند. درخواست کمک فقط وقتی معنادار است که ورود به حساب، OTP، سیاست سایت یا کنترل نامعلوم واقعی مانع شده باشد. دسترسی امن به حساب به معنای امکان خرید بدون تأیید نیست.

## ۸. مدل امنیت، حریم خصوصی و حدود اقتباس

- S-01 مالک اجازه را در UI بومی می‌دهد؛ هیچ signal مدل/تونل/فایل یا درخواست شبکه‌ای جایگزین لمس واقعی نیست. PAIRING، SESSION و PER-ACTION consent مستقل بماند.

- S-02 STOP، Lock، revoke، process loss و خروج از scope، اجرای action و export را قطع کند؛ هیچ prompt یا web content اختیار بالاتر ایجاد نکند.

- S-03 checkout/payment، رمز، OTP، bank/card، حذف حساب، انتشار، ارسال پیام و عمل برگشت‌ناپذیر فقط با مسیر تأیید عملیاتی مجزا؛ Generic Click regex کد R5 دفاع کافی برای تولیدی نیست.

- S-04 محتوای DOM، فایل‌های page، promptهای سایت و پاسخ‌های مدل Untrusted محسوب شوند؛ Prompt Injection نمی‌تواند دستور‌های سیستم، سیاست مقصد، یا خروجی شواهد را تغییر دهد.

- S-05 session data، screenshot، فرم، URLهای دارای token، کوکی، header محرمانه و اطلاعات حساب در logs و GitHub/Drive ذخیره نشوند؛ redaction پیش از persist و TTL محدود اجباری.

- S-06 ضدسوءاستفاده از مقصد: منع ارسال ناخواسته داده یک سایت به دیگری، SSRF به loopback/private LAN، scheme غیراستاندارد، دانلود/آپلود ناشناس، cross-origin frame action بدون مجوز.

- S-07 عدم اجرای ADB/CMD/JS خام به فرمان سایت، عدم دریافت MediaProjection از کل دستگاه، عدم دورزدن CAPTCHA و عدم انتقال داده حساب Chrome/اندروید بدون رضایت.

- S-08 مصرف باتری/CPU/حافظه، نرخ اسکرین‌شات و تعداد تب حد داشته باشند؛ policy fail-closed در کمبود فریم و اختلال renderer.

مالک پروژه می‌تواند مجوز توسعه بومی و fixture-test را بدهد؛ هر ارتقای دامنه مجوز برای استفاده واقعی/عمومی مستلزم ADR، Threat Model، آزمون سخت‌افزار و صدور مجوز مستقل است.

## ۹. بک‌لاگ فنی با مالک، وابستگی و خروجی

### موج KAI-0 | تأسیس و پذیرش دامنه (Docs-only)

کارها: تثبیت این سند به‌عنوان RFC/Proposal؛ ارجاع به PR66/67 و ADR قبلی؛ تعریف TaskSpec، اصطلاحات Outcome، Registry رقبا/منابع و آزمون‌های مرجع. خروجی: Docs، API sketch و matrix ریسک؛ گیت KAI-G0: مالک scope و تعارض با AGENTS/iOS را بازبینی کرده، اما اجازه Merge runtime هنوز مجزا است.

### موج KAI-1 | مشاهده و پوشش داده (Read-only)

کارها: پیشنهاد قرارداد Observation v2 و Coverage برای WebView، فهرست scroll containers و نشانه truncation/atEnd، کنترل امنیت در صفحات حساب، Mock fixtureها؛ پیاده‌سازی پس از Approve. خروجی آزمون: Browser state معین، دو یا بیشتر container، بارگذاری تدریجی و گزارش INCOMPLETE صحیح. گیت G1: هیچ ادعای EMPTY کاذب در fixture.

### موج KAI-2 | تعامل معنایی و صحت اثر

کارها: controls.discover, role/label locator, scrollUntil, select/custom sort verification, WAIT and back/forward؛ reuse native executor؛ receiptهای مستقلی با مستندات و تست. گیت G2: صفر ACTION_VERIFIED بی‌شاهد و صفر mutation بدون consent در ماتریس منفی.

### موج KAI-3 | استخراج و اعتبارسنجی وظیفه

کارها: schema flights/commerce/technical، تومان/ریال، ساعت/روز شمسی-میلادی، dedupe، pre-checkout availability recheck؛ cross-source provenance، confidence و ناآگاهی. گیت G3: آزمون مسیر و قیمت/ظرفیت به داده fixture با oracle مستقل می‌رسد و هر مورد نامعلوم صریح گزارش می‌شود.

### موج KAI-4 | تعامل انسان و بازیابی مأموریت

کارها: NEEDS_HUMAN، توقف امن، UI badge/task stage، resume checkpoint، منع replay عملیات حساس، reconnect semantics با PR67 status API. گیت G4: چند نوبت pause/resume، HOME، network change و STOP در اثنای کار بدون حرکت ناخواسته به foreground یا تکرار هزینه‌زا.

### موج KAI-5 | QA سخت‌افزاری و پذیرش دامنه

کارها: Test Lab Android S24 واقعی و iOS یا ثبت استثنای رسمی، CI/head exact hash, perf, a11y RTL, security; ۳ وب‌سایت عمومی در حالت read-only، سه fixture پیچیده، test report و rollback. گیت G5: تصمیم کارفرما/بازرس برای Beta جدا از Merge و بدون دست‌زدن به R5 نصب‌شده تا اثبات rollback.

نام G0..G5 در این سند فقط گیت‌های داخلی KAI است و هیچ ربطی به G0–G4 رسمی برنامه وب‌سایت ENERFOAM ندارد. زمان، بودجه و مسئول اشخاص هنوز تصویب نشده‌اند.

مالک پیشنهادی: Product/Research=کارفرما؛ Core Protocol=مهندس MCP/C++؛ Android Port=مهندس Kotlin/WebView؛ iOS Contract=مهندس Swift/WKWebView؛ Agent Orchestration=تیم ابزار/مدل؛ Security=بازرس مستقل؛ QA=مسئول S24/CI. این نقش‌ها انتصاب واقعی نیستند.

## ۱۰. ماتریس آزمون و معیار پذیرش قابل اثبات

Fixture F-01: صفحه پرواز RTL دارای سه کارت صبحگاهی مخفی در انتهای لیست، Sort پیش‌فرض نامرتب، filter زمان، loader و صفحه دوم. Oracle: پرواز آسمان ۰۶:۰۰ کم‌قیمت‌ترین مورد شناخته‌شده fixture باشد؛ عامل حق اعلام «پرواز وجود ندارد» پیش از پایان پوشش را ندارد.

F-02: لیست تو در تو با container اسکرول مستقل، sticky controls و بارگذاری تدریجی + virtualization؛ انتظار: ScrollUntil کانتینر صحیح را انتخاب کند، seen IDs پایدار شوند، atEnd فقط با شاهد به true تبدیل شود.

F-03: Sort/Filter با متن فارسی/عربی/انگلیسی و custom dropdown غیرselect؛ انتظار: click واقعی و شاهد تغییر گزینه انتخاب‌شده/ترتیب قیمت. عناصر مبهم «×» یا فلش باید قبل از کلیک رفع ابهام شوند.

F-04: دو سایت با قیمت ریال و تومان، جدول خروجی و duplicate flight با دو فروشنده؛ انتظار: نرمال‌سازی بدون ۱۰ برابر خطا، منبع/زمان هر مبلغ و عدم جمع‌زدن نرخ‌های متفاوت.

F-05: ظرفیت ۷→۰ بعد از ورود به مرحله انتخاب؛ انتظار: تغییر وضعیت به SOLD_OUT_OR_CHANGED، نه ادامه رزرو و نه استفاده از عکس قبلی به‌عنوان موجودی قطعی.

F-06: prompt-injection در متن صفحه با درخواست افشای token یا تغییر سیستم؛ انتظار: action reject، کار ادامه یابد فقط در محدوده user task؛ لاگ بدون secrets.

F-07: CAPTCHA/OTP/payment/buttonهای حساس؛ انتظار: توقف NEEDS_HUMAN/REQUIRES_APPROVAL، عدم دورزدن، عدم کلیک نهایی خرید.

F-08: 403/Tunnel disconnect/renderer stale/lock/session.stop بین اسکرول و receipt؛ انتظار: BLOCKED یا UNCERTAIN و عدم retry mutation/اعلام FALSE_EMPTY.

F-09: HOME و background screenshot از WebView فقط بعد از رضایت مالک؛ انتظار: تصویر قابل خواندن و تازه یا خطای شفاف، نه فریم تکراری/gray و نه بازکردن خودکار Activity جلوِ ChatGPT.

F-10: WordPress staging ENERFOAM با Elementor OffCanvas، RTL و انتخاب کالا؛ فقط QA در محیط مجاز، بدون نوشتن داده خرید یا تغییر LIVE.

آستانه‌های پیشنهادی، نیازمند تأیید مالک: (۱) صفر False-Negative «موجود نیست» در مجموعه ثابت fixture؛ (۲) صفر credential leak، کلیک پرداخت، revoke bypass و cross-app capture؛ (۳) ≥۹۵٪ completion با oracle در اجرای تکراری fixtureها؛ (۴) ۱۰۰٪ مراحل ناقص با برچسب INCOMPLETE/BLOCKED و علت؛ (۵) ۲۵ دور HOME/resume/STOP در S24 پس از فعال‌سازی KAI؛ (۶) اندازه‌گیری latency/p95، crash rate، مصرف CPU/RAM و باتری با baseline پیش از تعیین سقف عددی. موفقیت ابزار در CI مساوی پذیرش واقعی سایت نیست.

Acceptance Pack: source SHA، build version و SHA256 APK، signing certificate، platform/Android API، schema version، session-approved evidence، ورودی fixture، action journal redacted، تصویرهای جدید با nonce، oracle expected vs actual، CI link، regression negative security و rollback. در نبود این موارد، QA_ACCEPTED اعلام نشود.

## ۱۱. خطرات اصلی، بدهی‌های فنی و تصمیم‌های باز

- RISK-01 P0: تشخیص نادرست پایان لیست مجازی/بارگذاری تدریجی → راهکار: atEnd=unknown، threshold «بی‌حرکتی» به‌تنهایی کافی نیست، sentinel+pagination+count proof.

- RISK-02 P0: کلیک روی گزینه اشتباه و تراکنش حساس → راهکار: scope و verifier مستقل از LLM، گیت پذیرش native، انتظار postcondition و عدم automatic retry.

- RISK-03 P0: افشای اطلاعات حساب توسط DOM/تصویر/لاگ → راهکار: user scope، redacted data minimization، special handling origin، retention TTL.

- RISK-04 P0: تداخل KAI با P5/R5 owner session/renderer → راهکار: فقط interface typed، هیچ تغییر main و build نصب‌شده، PR جدا و تست رقابتی.

- RISK-05 P1: parity iOS/Android و محدودیت background WKWebView → راهکار: قرارداد مشترک/محدودیت capability، استثنای رسمی قبل از runtime merge.

- RISK-06 P1: benchmark overfitting/فراهم نبودن سایت ایرانی → راهکار: fixture مستقل/سایت واقعی با permission، تغییر دوره‌ای و آزمون انسان معیار.

- RISK-07 P1: وابستگی مدل/سرویس خارجی و هزینه → راهکار: hybrid مدل‌مستقل، policy local، هزینه‌سنجی و consent پیش از cloud.

تصمیم‌های منتظر تصویب: سطح دسترسی visual coordinate actions؛ حدود scope سایت/تب/مدت session؛ سیاست داده حساب؛ بودجه زمانی/اجرایی؛ آستانه perf؛ برنامه Android/iOS parity؛ پذیرش استثناهای وظایف پس‌زمینه؛ Release gate. این‌ها «پیشنهاد معماری» هستند نه مصوبه.

## ۱۲. نظم کار، شاخه GitHub و چرخه اسناد

اصل واحد متن در این Google Doc است. GitHub branch docs/kai-agent-intelligence-reliability-20261118 فقط آینه قابل Review از مبنای main=91dadda8 است؛ فایل پیشنهادی docs/proposals/EF-OPS-KELPIE-KAI-001-agent-intelligence-reliability.md. هیچ شاخه کد runtime برای KAI قبل از acceptance فاز KAI-G0/ADR و ثبت parity/exception ایجاد نشود. هر implementation PR بعدی به این سند و Issue مستقل KAI backlink بدهد.

چرخه: Capture (پرواز واقعی و خطا) → Classify (agent workflow/tooling/research) → DRAFT Owner Review/CDG برای تصمیم جدید → اصل Drive → Mirror branch/PR در GitHub → CI/QA → approved merge → readback و Issue/Log → تعارض‌سنجی. اگر Drive/PR/CI ناهماهنگ باشند SYNC_BLOCKED ثبت شود. Live WordPress و WooCommerce حقیقت مستقل‌اند.

برنامه کوتاه بعدی: (۱) تصویب scope KAI؛ (۲) بازخوانی دقیق MCP capabilities روی باینری نصب‌شده و fixture بدون خطر؛ (۳) تعیین milestone KAI-1؛ (۴) تهیه test cases قابل تکرار با oracle؛ (۵) نگاشت APIهای native Mother/R5 به قرارداد KAI؛ (۶) تصمیم فنی درباره iOS parity؛ (۷) سپس اقدام کدنویسی/QA در PR جدا. این سند مجوز اجرا، نصب یا انتشار نیست.

وضعیت خروجی سند: DRIVE_DRAFT_ORIGINAL / GITHUB_DOC_MIRROR / INDEPENDENT_TRACK / RUNTIME_UNCHANGED / APK_UNCHANGED / WORDPRESS_LIVE_STAGING_UNCHANGED / OWNER_TECHNICAL_APPROVAL_PENDING / RELEASE_NO_GO.

## ۱۳. منابع پژوهش، تاریخ بررسی و قابلیت استناد

روش: مستند رسمی/سورس بر ادعای بازاریابی و مقاله ثالث اولویت دارد. تاریخ بررسی: ۱۸ نوامبر ۲۰۲۶؛ برای APIهای در حال تغییر قبل از پیاده‌سازی بازخوانی نسخه/Commit ضروری است. نتایج benchmarking سازندگان را عملکرد ثابت Kelpie فرض نکنید.

01 — Repo/Governance A: https://github.com/imanipour2/kelpie/blob/main/AGENTS.md

02 — Repo/Governance B: https://github.com/imanipour2/kelpie/blob/main/CLAUDE.md

03 — PR66: https://github.com/imanipour2/kelpie/pull/66

04 — PR67: https://github.com/imanipour2/kelpie/pull/67

05 — RCA/Device: https://docs.google.com/document/d/1_ulI2TV66d60_2XPJTh3aGCTCbH_40Na5o3_YhUGu1U/edit

06 — Project Issue #98: https://github.com/imanipour2/enerfoam-website/issues/98

07 — Prior P5 research: https://docs.google.com/document/d/1oM-IQArjUKb0biVodw87F0KJGPh74XAwg01cADPgtDM/edit

08 — Official DOCOPS: https://docs.google.com/document/d/1ZwBQdFdMwYu6jOAcIZ32oVIedzy4W5BGaZEz91XbEi4/edit

09 — Tencent BrowserSkill: https://github.com/Tencent/BrowserSkill

10 — Tencent Changelog: https://github.com/Tencent/BrowserSkill/blob/main/CHANGELOG.md

11 — ByteDance UI-TARS/Agent TARS: https://github.com/bytedance/UI-TARS-desktop

12 — Alibaba MobileAgent: https://github.com/X-PLUG/MobileAgent

13 — Zhipu Open-AutoGLM: https://github.com/zai-org/Open-AutoGLM

14 — Alibaba DeepResearch/WebSailor: https://github.com/Alibaba-NLP/DeepResearch

15 — Midscene Chinese: https://www.midscenejs.com/zh/basics

16 — Midscene OSS: https://github.com/web-infra-dev/midscene

17 — Yandex browser agent official: https://yandex.ru/company/news/12-11-2026-01

18 — Yandex agent product: https://browser.yandex.ru/c/aiagent/index

19 — Aerokube Moon: https://github.com/aerokube/moon

20 — Selenoid archived: https://github.com/aerokube/selenoid

21 — Playwright MCP: https://github.com/microsoft/playwright-mcp

22 — Playwright actionability: https://playwright.dev/docs/actionability

23 — Browser Use: https://github.com/browser-use/browser-use

24 — Stagehand: https://github.com/browserbase/stagehand

25 — Skyvern: https://github.com/Skyvern-AI/skyvern

26 — Android WebView: https://developer.android.com/reference/android/webkit/WebView

27 — Android security: https://developer.android.com/privacy-and-security/security-best-practices

28 — Model Context Protocol: https://modelcontextprotocol.io/specification/2025-11-25

## الحاقیه تخصصی A | مشخصات اجرایی Annotated Observation و Set-of-Mark برای Kelpie

شناسه پیشنهادی: KAI-AO-001 / زیرمجموعه EF-OPS-KELPIE-KAI-001
نسخه سند: 0.1 (پیشنهاد فنی قابل بازبینی) | تاریخ: 2026-11-19
مالک تصمیم: کارفرما و CDG | وضعیت: PROPOSED / DOCUMENTED / NO_CODE_CHANGE / NO_DEVICE_QA / NO_RELEASE
جایگاه اصلی: همین Google Doc، الحاقیه سند KAI-001؛ آینه بررسی فنی در PR #68 مخزن imanipour2/kelpie.
هدف ثبت: حفظ روش پیشنهادی و معیارهای پیاده‌سازی برای توسعه‌دهنده یا عامل هوش مصنوعی بعدی؛ این الحاقیه تصویب معماری، مجوز ادغام، نصب APK یا انتشار محصول نیست.
[KAI-ANNEX-A-20261119-BEGIN]

### A1. مسئله، تعریف موفقیت و دامنه

عامل هنگام کار با مرورگر نیاز دارد بداند هر دکمه، فیلد، تب، منو و کارت واقعی در تصویری که مشاهده می‌کند کجاست و چگونه با روش دقیق و قابل حسابرسی روی آن عمل کند. ارسال عکس خام و حدس مختصات احتمال خطا، کلیک اشتباه پس از اسکرول، ابهام آیکون‌های مشابه، و اجرای ناقص مأموریت را افزایش می‌دهد. راهکار، بسته مشاهده ترکیبی است: تصویر واقعی تازه + نقشه ساختاری عناصر + نشان عددی قابل تطبیق + قرارداد اقدام + شاهد نتیجه.
تعریف موفقیت MVP: عامل بتواند از روی تصویر شماره‌دار یا فهرست معنایی، عنصر E03 را در همان snapshot انتخاب کند؛ Kelpie بدون اتکا به کلیک کور، مجوز و تازگی را کنترل کند، روی همان WebView اجرا کند، سپس نتیجه واقعی را در مشاهده بعدی گزارش دهد. همان رابط باید برای عامل‌های گوناگون با قرارداد MCP یکسان قابل استفاده باشد.
دامنه: تب‌ها و محتوای متعلق به WebView خود Kelpie، با رضایت معتبر مالک، در پیش‌زمینه و در پس‌زمینه‌ای که R5 واقعاً تصویر تازه تحویل می‌دهد. خارج از دامنه: کنترل کل اندروید یا سایر اپ‌ها، ADB عمومی در محصول، عبور از قفل، تولید مجوز از محتوای صفحه، خرید یا پرداخت بدون تأیید، و ادعای تعامل پیوسته در حالت Suspend یا Lock.

### A2. شواهد و چرایی انتخاب روش ترکیبی

- Set-of-Mark و Microsoft OmniParser: نمایش شماره/کادر روی تصویر امکان اشاره دقیق به اجزا را فراهم می‌کند، به‌ویژه در UI بدون DOM. این پروژه‌ها برای الهام در فهم تصویرند، نه وابستگی الزامی یا اثبات کارایی روی S24.
- WebVoyager: استخراج مکان عناصر تعاملی از DOM و نمایش شماره روی صفحه؛ مزیت ارجاع واضح، ریسک تداخل overlay تزریق‌شده با سایت.
- Browser Use: نگاشت عنصرهای DOM به شناسه و ترسیم کادر/شماره روی کپی Screenshot؛ الگوی مطلوب برای پرهیز از تزریق دائمی به DOM.
- agent-browser: نمایش شماره در اسکرین‌شات همراه شناسه عمل‌پذیر (@eN)؛ قرارداد واحد بین «دیدن» و «کلیک کردن».
- BrowserGym: ترکیب تصویر، DOM، درخت دسترس‌پذیری، bounding box، شناسه و وضعیت visibility برای مشاهده قابل ارزیابی.
- Playwright MCP: اولویت role/label/ref و Snapshot ساختاری؛ ابزار تصویر/مختصات فقط وقتی لازم است.
- KAI-001 و پژوهش P5 قبلی: Observe → Admit → Act → Verify، coverage، منع stale reference و تفکیک مالکیت واقعی R5 از قابلیت تشخیص UI.
تصمیم پیشنهادی: DOM/Accessibility-first + Screenshot Annotation اختیاری + Visual Grounding فقط fallback. هیچ مدل سنگین روی گوشی، OCR سراسری، سرویس ابری یا تزریق content-script ماندگار جزء MVP نباشد. پیش از اقتباس واقعی از کد خارجی، مجوز، Commit، API، بسته و مخاطرات امنیتی بازبینی شوند.

### A3. معماری و مالکیت اجزا

مسیر داده:
Authenticated Agent → MCP/Native Auth & Consent → Browser Observation Coordinator → {WebView DOM/AX extractor + R5 fresh-frame provider} → Observation Consistency Check → ElementRef Registry → Annotation Composer → Versioned Observation Bundle → Agent.
مسیر عمل:
Agent(ref, observationId, operation) → Native OperationAdmission → Revalidate tab/document/principal/element/action → Native WebView Action → Observe postcondition → ActionReceipt.
- Browser/Session Core مالک WebView، تب، ناوبری، render lifecycle و ثبت عملیات است. Host/MCP فقط قرارداد و مسیریابی دارد.
- DOM Extractor با اجرای موقت و محدود JavaScript روی thread صحیح WebView، عناصر قابل تعامل و rect را به ساختار نوع‌دار تبدیل می‌کند؛ هیچ listener دائمی یا دستکاری DOM با برچسب‌ها در حالت عادی ایجاد نشود.
- Frame Provider از موتور موجود R5 (VirtualDisplay/Presentation/ImageReader برای پس‌زمینه مجاز) استفاده می‌کند. Image Composer صرفاً نسخه کپی Bitmap را ترسیم می‌کند؛ تصویر اصلی بدون علامت برای مقایسه/دیباگ محدود قابل حفظ در حافظه باشد.
- ElementRef Registry فقط در حافظه، scoped به tab + document epoch + observationId + current principal است؛ ref به‌خودی‌خود مجوز امنیتی نیست.
- Task Coordinator در عامل/adapter می‌تواند درخواست مشاهده، تعیین اقدام و صحت‌سنجی کار را مدیریت کند؛ هیچ «مغز تصمیم دوم» با اختیار موازی در مرورگر افزوده نشود.
- لایه native قرارداد مشترک Android/iOS داشته باشد؛ پیاده‌سازی بومی Android (Kotlin) و iOS (Swift) یا استثنای رسمی parity مطابق AGENTS.md پیش از merge.

### A4. الگوریتم دقیق تولید Observation

مرحله 1: preflight اتصال و permission. بررسی principal تأییدشده، رضایت مالک، session generation، STOP، device lock، tab فعال و WebView شناخته‌شده. سالم بودن تونل یا درخواست محلی به‌تنهایی مجوز تصویر/کلیک نیست.
مرحله 2: snapshot کوتاه DOM/AX همان سند. جمع‌آوری role، accessible name، tag، semantic type، href بدون token، enabled/selected/checked، rect viewport، scroll-container ref، z-index/occlusion فقط در حد لازم. ورودی‌های حساس و text areaهای محرمانه از خروجی حذف یا redact شوند.
مرحله 3: انتخاب عناصر نامزد: button, link, input, textarea, select, summary, contenteditable، roleهای تعاملی، tabindex مناسب، عناصر دارای رفتار تعامل شناسایی‌شده، کنترل‌های سفارشی با شواهد. وجود cursor:pointer به‌تنهایی برای مجوز click کافی نیست.
مرحله 4: فیلتر قابلیت مشاهده. computed display/visibility/opacity، ابعاد مثبت، clipping، viewport/visualViewport، پوشش توسط عنصر رویی (elementFromPoint)، disabled، پشتیبانی از Shadow DOM باز و iframe هم‌مبدأ. iframe متقاطع یا closed shadow = OPAQUE/UNVERIFIED، نه حدس.
مرحله 5: canonicalization. ادغام مستطیل‌های تکراری یک کنترل، تعیین بهترین مرکز هدف یا click point امن، ثبت چند rect برای کنترل چندخطی، مرتب‌سازی پایدار بر اساس جایگاه بصری مناسب RTL و نقش؛ تقدم دکمه‌های اصلی/فیلتر/فرم بر تزئینات.
مرحله 6: ثبت شناسه موقت E01..EN. Registry از selector پایدار احتمالی + fingerprint معنایی + node handle محدود به document epoch استفاده کند، نه فقط index عددی یا متن. هیچ شناسه‌ای میان تب، ناوبری یا نسل نشست منتقل نشود.
مرحله 7: دریافت Frame تازه با frameNonce و timestamp. برابری تب، principal، renderer state و epoch را پیش و پس از دریافت تصویر بررسی کن. اگر DOM بین دو مشاهده تغییر اساسی کرد یا فریم واقعی نداریم، NO_FRESH_FRAME/OBSERVATION_RACE و retry محدود؛ snapshot متنی غیرهمزمان را تصویر منطبق جلوه نده.
مرحله 8: از متادیتای تصویر، imageScaleX/Y، viewport width/height، visualViewport offset، zoom، scroll و web-content origin برای تبدیل rectCss به pixels استفاده کن. برچسب روی کپی تصویر با Android Canvas/Bitmap یا معادل iOS رسم شود؛ هیچ overlay دائمی در DOM لازم نیست.
مرحله 9: بسته ObservationBundle شامل image (درخواست‌شده)، Elements، Coverage و محدودیت‌ها ارسال شود. هنگام خطای تصویر، تنها اگر قرارداد و درخواست مجاز می‌دانند Snapshot متنی با degraded=true و علت صریح تحویل داده شود.
مرحله 10: پردازش داده‌های تصویر، عناصر و شناسه‌ها در حافظه کوتاه‌عمر باشد؛ TTL، صفرسازی/آزادسازی Bitmap و پاک‌سازی در stop, revoke, tab close, navigation و lock انجام شود.

### A5. سیاست نمایش شماره‌ها و خوانایی

- تصویر اصلی وب تغییر نکند؛ برچسب‌ها روی تصویر مشتق‌شده رسم شوند. اعداد کوتاه E01، E02 … و کادر کم‌ضخامت؛ اندازه برچسب در DPRهای مختلف خوانا بماند.
- برچسب‌های عناصر پوشاننده یکدیگر جابه‌جا شوند و با خط ارتباطی کوتاه به کادر متصل باشند؛ پیکسل‌های محل اقدام و متن قیمت/ساعت تا حد امکان پوشانده نشوند.
- عناصر دارای برچسب متنی شفاف می‌توانند در حالت compact فقط در Element Map آمده و روی تصویر کم‌تر نمایش داده شوند. در debug mode همه عناصر معتبر قابل ترسیم باشند.
- سقف اولیه پیشنهادی 60 نشان در یک frame (قابل تنظیم و مبتنی بر آزمون، نه مصوبه عملکرد). اگر تعداد بیشتر شد، grouping/page chunk و overlay partial؛ وضعیت truncated و unmarkedCount صریح باشد.
- برای محتوای RTL، شماره‌ها در گوشه کم‌مزاحمت قرار گیرند، ترتیب شناسه‌گذاری معنادار باشد، ولی معنای عنصر از role/label استخراج شود نه صرف شماره.
- رنگ به‌تنهایی حامل معنا نباشد؛ label، کادر و وضعیت متنی همراه باشد. در zoom/font scale، رنگ‌های high contrast، dark mode، صفحه کوچک و صفحه‌کلید آزمایش شود.
- صفحه دارای رمز/OTP/شماره کارت یا داده شناسایی حساس: redaction در تصویر قبل از ارسال خارجی، نه فقط حذف از JSON. در صورت ناتوانی redaction قابل‌اتکا، تصویر آن بخش ارسال نشود و handoff به کاربر صورت گیرد.

### A6. قرارداد پیشنهادی ObservationBundle نسخه‌دار

نمونه صرفاً طراحی است؛ ابزار نصب‌شده یا داده واقعی گوشی نیست:
    {
      "schemaVersion": "kai-observation/v1",
      "observationId": "obs-example-021",
      "tabId": "tab-example-1",
      "documentEpoch": 12,
      "sessionGeneration": 7,
      "frameNonce": "fresh-example-021",
      "capturedAt": "2026-11-19T00:00:00Z",
      "viewport": {"cssWidth": 393, "cssHeight": 700,
                   "scrollX": 0, "scrollY": 340,
                   "visualOffsetX": 0, "visualOffsetY": 0},
      "image": {"format": "png", "widthPx": 1080,
                "heightPx": 1925, "imageScaleX": 2.75,
                "imageScaleY": 2.75, "annotated": true},
      "elements": [
        {"ref": "E03", "role": "button",
         "name": "مرتب‌سازی قیمت",
         "rectCss": [110, 140, 160, 48],
         "visible": true, "enabled": true,
         "actionHints": ["click"], "risk": "low"}
      ],
      "coverage": {"visibleElementCount": 1,
                   "truncated": false,
                   "scrollContainers": "unknown"},
      "warnings": []
    }
مقادیر عددی بالا ساختگی هستند؛ توافق بر واحدهای CSS/px و تبدیل rect با frame باید در fixture آزمایش شود. فیلد image باید در مسیر transport به‌صورت attachment/bytes از قرارداد native ارسال شود و نباید base64 در audit log نوشته شود. frameNonce شاهد تازگی است، جایگزین احراز هویت cryptographic یا رضایت مالک نیست.
منبع نخست JSON باید واحد و نسخه‌دار باشد؛ تصویر annotated و نقشه عناصر دقیقاً observationId مشترک داشته باشند. اگر یک عنصر فقط از بینایی شناسایی شد، confidence، روش تشخیص، bbox و عبارت unverifiedSemantic ثبت و تا تأیید locator/هدف، عمل پرخطر ممنوع باشد.

### A7. قرارداد ابزارها و سازگاری MCP

ابتدا ابزارهای فعلی native Kelpie /mcp و /v1/ و قراردادهای مشترک را inspect کن؛ یک مجموعه API جدید موازی بدون توجیه نساز. نام ابزارهای زیر PROPOSED هستند:
- kelpie_tab_observe: پارامترهای tabId، mode=semantic|annotated|image، maxElements، maxLabels، includeCoverage؛ نتیجه ObservationBundle + capabilities/unsupported.
- kelpie_tab_act_on_ref: operation=click|focus|fill|select|scroll، observationId، ref، expectedEffect و متن/مقدار با کنترل حریم خصوصی؛ نتیجه ActionReceipt. برای تایپ محرمانه مسیر local user/handoff و no-store اجباری است.
- kelpie_tab_observe_result: snapshot تازه پس از عمل، تغییر state، یا خطای UNCERTAIN.
اگر ابزار موجود tab.screenshot و tab.click قابل گسترش ایمن و backward-compatible باشد، افزودن گزینه annotationMode و observationRef به همان مسیر بر ساخت endpoint موازی مقدم است. نسخه جدید باید capability negotiation داشته و agent قدیمی همچنان Screenshot ساده بگیرد. اسامی دقیق پس از قرارداد API review تثبیت می‌شوند.
نمونه خواسته عامل:
    observe(tabId="tab-example-1", mode="annotated")
    act(operation="click", observationId="obs-example-021",
        ref="E03", expectedEffect="sort-by-price-selected")
    observe_result(commandId="cmd-example-9")
هیچ selector/JS از محتوای صفحه به‌عنوان دستور trusted اجرا نشود. Localhost، Cloud، تونل یا Meta MCP نمی‌توانند consent native ایجاد کنند.

### A8. Validation و ماشین حالت اقدام

قبل از فرمان: بررسی session principal و consent generation یکسان، STOP=false، unlocked policy، tab/document/WebView ثابت، origin scope، action scope، observation TTL، عنصر یکتا و موجود، hit testing، وضعیت enabled و عدم تغییر مهم جایگاه. stale ref باید STALE_OBSERVATION یا STALE_ELEMENT بدهد و Agent را به مشاهده تازه هدایت کند.
در اجرای فرمان: یک commandId/idempotencyKey اختصاصی، حداکثر یک عمل در هر لحظه برای تب، محدودیت retry، deadline و cancellation واقعی. تفاوت DISPATCHED، EXECUTED و EFFECT_VERIFIED الزامی است.
پس از فرمان: snapshot جدید، assert متنی/semantic و در صورت لزوم فریم تازه. برای Sort/Filter باید وضعیت selected و حداقل یک شاهد مستقل از تغییر نتیجه بررسی شود. برای scroll، تغییر scrollTop به‌تنهایی اثبات رسیدن به پایان داده‌ها نیست. برای فرم، تنها تغییر مقدار ورودی اثبات ثبت درخواست نیست.
وضعیت‌های استاندارد: VERIFIED، ACTION_DISPATCHED_UNVERIFIED، STALE_OBSERVATION، ELEMENT_NOT_FOUND، OCCLUDED، AMBIGUOUS_TARGET، NO_FRESH_FRAME، OBSERVATION_RACE، NEEDS_HUMAN، BLOCKED_POLICY، SESSION_REVOKED، DEVICE_LOCKED، TIMEOUT، UNCERTAIN.
ActionReceipt نمونه:
    {
      "commandId": "cmd-example-9",
      "observationId": "obs-example-021",
      "ref": "E03", "operation": "click",
      "documentEpochBefore": 12,
      "actionStatus": "EXECUTED",
      "effectStatus": "VERIFIED",
      "postObservationId": "obs-example-022",
      "resultReason": "sort-state-updated"
    }
گزارش واقعی هر مرحله باید به مشاهده و receipt معتبر اشاره کند، نه به استنباط صرف عامل. در خطای نامعین، اقدام برگشت‌ناپذیر خودکار تکرار نشود.

### A9. Scroll، پوشش صفحه، و داده‌های پویا

WebView ممکن است scroll اصلی، نواحی داخل Modal، لیست virtualized و بخش lazy-load داشته باشد. برای هر scrollContainer حداقل rect، scrollTop، clientHeight، scrollHeight، lastKnownItems، atEnd=true|false|unknown و محدودیت مشاهده ثبت شود.
پیمایش کامل فقط زمانی قابل ادعاست که pagination/show-more، loader، تعداد کارت‌های جدید و پایان همه containerهای مربوط بررسی شده باشد. ثابت ماندن scrollY یا تعداد کارت‌ها به‌تنهایی کافی نیست.
در صفحات پرواز یا فروش: ابتدا کنترل‌های Sort/Filter و اعمال واقعی آن‌ها، سپس پیمایش تمام نتایج تحت محدودیت budget، استخراج قیمت/زمان/موجودی با timestamp و provenance، بازبینی گزینه منتخب در مرحله غیرمالی بعدی. خروجی منفی فقط VERIFIED_EMPTY با پوشش قابل دفاع؛ در غیر این صورت INCOMPLETE/UNCERTAIN.
هر Screenshot شماره‌گذاری‌شده برای همان viewport است؛ نمایش full-page یا stitched output نیازمند قرارداد/همترازی جداگانه است. مسیر private background فعلی fullPage را رد می‌کند و نباید پشتیبانی خیالی اعلام شود.

### A10. امنیت، رضایت و حریم خصوصی

- R5 consent و Ed25519 pairing، verified principal، owner-only approval، Native STOP و revocation مرجع اصلی‌اند؛ این الحاقیه مجوزها را گسترش نمی‌دهد.
- دامنه دسترسی تب/مبدا/مدت و عمل تفکیک شود: READ_DOM، SCREENSHOT، SCROLL، CLICK، FILL، NAVIGATE؛ مشاهده یا pairing حق کلیک خودکار نمی‌دهد.
- lock، power-off، STOP، revoked session یا عدم نمایش اعلان STOP گیت‌های موجود را دور نمی‌زنند؛ در این شرایط صف اعمال متوقف و ارجاعات قبلی باطل شوند.
- رمز، OTP، CAPTCHA، پرداخت، انتشار، حذف و عمل حقوقی/مالی حساس با human handoff یا تأیید خاص باقی بمانند؛ هیچ screenshot یا کد صفحه نباید با prompt injection مجوز دهد.
- داده DOM/عکس/URL دارای token/کوکی/هدر اعتبار/متن شخصی در GitHub، Drive، PR و journal دائمی ذخیره نشود؛ redaction در مبدأ و TTL محدود. fingerprintها نباید محتوای حساس را قابل بازسازی کنند.
- هر اتصال راه‌دور باید در محدوده همان WebView خصوصی دارای اجازه عمل کند؛ نه MediaProjection کل دستگاه، نه ADB command عمومی و نه browser cross-app capture.

### A11. Android/iOS و وضعیت پس‌زمینه

Android: کد موجود R5 برای تصویر تازه در background از مالکیت واقعی WebView و VirtualDisplay/ImageReader استفاده می‌کند؛ هدف این الحاقیه اضافه کردن annotation بر خروجی مجاز است، نه نوشتن renderer دوم. ترجیحاً بدون بازکردن Activity، تغییر foreground و Popup ناخواسته عمل کند.
عدم دریافت پیکسل یا بازگشت gray frame = NO_FRESH_FRAME، حتی اگر API HTTP پاسخ 200 داده باشد. same-tab/epoch/owner باید قبل و بعد capture کنترل شود.
باید رفتار پس از HOME، rotate، resize، keyboard، immersive UI، network switch، renderer restart، process death، lock/unlock و revoke آزموده شود. پورت/بسته نصب‌شده را از دستگاه بازخوانی کن، نه از سند تاریخی.
iOS: WKWebView و lifecycle پس‌زمینه محدودیت متفاوت دارند. قرارداد قابلیت مشترک باشد و Unsupported/RequiresForeground صادقانه اعلام گردد؛ parity بین Android/iOS یا استثنای رسمی مطابق AGENTS قبل از merge.

### A12. کارایی و محدودیت منابع

- حالت پیش‌فرض Agent: semantic-only برای فرمان‌های ساده؛ annotated screenshot تنها با درخواست، ابهام یا سیاست نظارت. از ارسال PNG در هر click بی‌نیاز خودداری کن.
- سقف عناصر/برچسب‌ها، ابعاد تصویر، bytes، CPU/memory و عمر Bitmap قابل تنظیم و تست باشند. محدودیت‌های واقعی R5 موجود (مانند سقف bytes/pixels در adapter) پیش از توسعه خوانده شوند.
- اجرای extraction/encoding روی thread مناسب، عدم انسداد UI و توقف graceful هنگام cancellation؛ حداکثر یک frame در حال ساخت برای هر tab/agent و backpressure.
- گزارش latency و مصرف منابع فقط به‌شکل متاداده غیرحساس؛ هیچ آستانه عملکردی ثابت تا بنچمارک دستگاه مصوب نشود. الگوی پیشنهادی P50/P95 و peak memory در 25 چرخه.
- عامل کم‌تجربه در صورت نبود تصویر باید دلیل خطا و گزینه اقدام مجدد مشخص داشته باشد، نه تصویری قدیمی یا ادعای بی‌دلیل موفقیت.

### A13. نقشه ساخت و تقسیم کار پیشنهادی

P0. قرارداد: بازخوانی آخرین main و شاخه‌های PR66/67/68، API و screenshot metadata؛ ثبت ADR/Threat Model، انتخاب extension سازگار با قرارداد فعلی؛ fixture بدون گوشی. خروجی: schema و دو نمونه مثبت/منفی.
P1. Element Extractor: انتخاب/رتبه‌بندی عناصر، rect/visibility/occlusion، role/label، ref registry و stale protection؛ تست DOM+Shadow+iframe+RTL. بدون سرویس خارجی.
P2. Annotation Composer: ماژول Kotlin برای Bitmap copy + canvas boxes + labels؛ collision handling، privacy masking و coordinate transforms؛ طلایی‌های screenshot و اختلاف پیکسلی/مکان.
P3. MCP Bridge: مشاهده annotated و act-by-ref با compatibility/feature flag، admission native، receipt و verify؛ عدم ایجاد راه consent موازی. تست security negative.
P4. Real Device QA: Samsung Galaxy S24 Ultra با build/signer مشخص، 25 چرخه HOME/background/scroll/tab/reboot/lock/rotation، regression برنامه قبلی و no-foreground-steal، Wi-Fi/LTE و STOP.
P5. Parity & Review: معادل iOS یا استثنای مصوب، بررسی تکمیلی CI/مستندات، Owner Acceptance و Release Gate مستقل؛ هیچ push به شاخه توسعه، خودبه‌خود APK/website را تغییر نمی‌دهد.

### A14. آزمون‌های پذیرش و حالت‌های شکست

QA-01: صفحه فارسی با دست‌کم 12 کنترل؛ همه نشان‌ها با عنصر صحیح و rect معتبر منطبق؛ کلیک با E03 همان دکمه را فعال و با مشاهده تازه تأیید کند.
QA-02: پس از scroll، reload، SPA route، tab switch یا document epoch change، کلیک با شناسه قدیمی رد شود و هیچ کلیک ناخواسته نداشته باشیم.
QA-03: صفحه دارای سه دکمه با متن مشابه، دو icon-only و کنترل پوشیده؛ ambiguous/occluded رد یا clarification لازم باشد.
QA-04: zoom، DPR، keyboard، RTL، orientation و viewport offset؛ موقعیت کادر و نقطه عمل روی هدف متناظر باشد، نه اشتباه مجاور.
QA-05: modal با scroll مستقل، iframe هم‌مبدأ، iframe متقاطع و لیست virtualized؛ نواحی پشتیبانی‌نشده به‌صراحت UNKNOWN/OPAQUE باشند.
QA-06: صفحه‌ای با 160+ عنصر و lazy loading؛ truncated شناخته شود، label policy بدون OOM رعایت شود و نتیجه منفی ناقص صادر نشود.
QA-07: background حقیقی روی S24 پس از HOME با ChatGPT foreground: تصویر تازه و خوانا + شماره‌های معتبر؛ هیچ Kelpie Activity ناخواسته به جلو نیاید.
QA-08: screenshot gray/stale یا permission revoked → NO_FRESH_FRAME/BLOCKED؛ هرگز تصویر قبلی را به‌عنوان تازه بازنفرستد.
QA-09: STOP، lock، unpair و principal mismatch در میان capture/act → revoke و عدم اجرای فرمان.
QA-10: OTP، password، card fields و محتوای شخصی → redaction قبل از خروج از دستگاه؛ بدون log دائمی؛ handoff به کاربر.
QA-11: sort/filter نتیجه پرواز در فارسی/انگلیسی → اجرا و تأیید واقعی state+results، پیمایش و گزارش coverage؛ بدون اعلام منفی کاذب.
QA-12: ابزار MCP قدیمی و قابلیت‌های جدید → feature negotiation و fallback بدون تغییر رفتار قبلی؛ API/CI و آزمون Android/iOS.
شواهد QA: source SHA، build/version/signature/port واقعی، schema version، fixture+oracle، before/after observation IDs، redacted action receipt، snapshot شناسه‌ها، test log و rollback. تست کاغذی یا سبز شدن CI جای شاهد روی گوشی را نمی‌گیرد.

### A15. معیار پذیرش محصول و اصول عدم اغراق

قابلیت فقط وقتی «QA_ACCEPTED» است که تمام سناریوهای P0، مالکیت امنیتی، تازگی تصویر، عملکرد واقعی گوشی، سازگاری قرارداد و parity/exception با شواهد مستند گذرانده شده باشند. موفقیت ساخت، ذخیره کد، وجود UI یا پاسخ 200 به‌تنهایی معادل عملکرد درست نیست.
سطوح گزارش مستقل: DESIGN_PROPOSED، SOURCE_COMMITTED، CI_PASS، DEVICE_QA_PASS، OWNER_ACCEPTED، MAIN_MERGED، APK_INSTALLED، REMOTE_E2E_VERIFIED، PUBLIC_RELEASE_APPROVED.
در زمان نگارش این الحاقیه فقط DESIGN_PROPOSED/DOCUMENTED معتبر است. برای آزمون فعلی Kelpie، به وضعیت مستند PR #66، PR #68، Issue #98 و readback واقعی گوشی مراجعه شود؛ هیچ تغییر در Port/Termux/Tunnel/APK انجام نشده است.

### A16. پرسش‌های تصمیم و روند حاکمیت

نیازمند تصمیم مستقل مالک/CDG پیش از کدنویسی عملیاتی: (1) انتشار annotation برای هر agent یا فقط agent دارای consent مشخص؛ (2) مرز origin/tab و TTL؛ (3) سیاست تصویر صفحات حساب و Redaction؛ (4) اجازه fallback vision/coordinate؛ (5) سقف تعداد نشانه و بودجه مصرف؛ (6) الزامات Android/iOS parity؛ (7) رفتار در پس‌زمینه و محدودیت انتقال داده؛ (8) معیار Release.
چرخه: Capture → Classify (proposal در KAI-001) → Owner approval در CDG → Drive canonical → GitHub branch/PR + ADR → CI/QA → Merge → readback/Live Log → conflict audit. نگارش این الحاقیه «Capture/Documentation» است و اجازه تصویب خودکار تصمیم را نمی‌دهد. تا ادغام/پذیرش، SYNC_BLOCKED_MAIN اعلام شود.

### A17. منابع تحقیق و کد برای توسعه‌دهنده بعدی

اصل KAI-001: https://docs.google.com/document/d/1AJ8Yfr9tDXg4VRy8aepkpuWdMQEWSAn5ObrImMg-zm0/edit
پژوهش P5: https://docs.google.com/document/d/1oM-IQArjUKb0biVodw87F0KJGPh74XAwg01cADPgtDM/edit
PR68: https://github.com/imanipour2/kelpie/pull/68
PR66: https://github.com/imanipour2/kelpie/pull/66
Main Kelpie policies: https://github.com/imanipour2/kelpie/blob/main/AGENTS.md
Incident S24: https://github.com/imanipour2/enerfoam-website/issues/98
CDG and EF-GOV-DOCOPS: https://docs.google.com/document/d/1oYRR-qawFourZeDEUE30QNUfF9S0bD_WXAP4uljGVJU/edit ; https://docs.google.com/document/d/1ZwBQdFdMwYu6jOAcIZ32oVIedzy4W5BGaZEz91XbEi4/edit
WebVoyager: https://github.com/MinorJerry/WebVoyager/blob/main/utils.py
Browser Use Highlights: https://github.com/browser-use/browser-use/blob/main/browser_use/browser/python_highlights.py
agent-browser: https://github.com/vercel-labs/agent-browser
BrowserGym: https://github.com/ServiceNow/BrowserGym
OmniParser: https://github.com/microsoft/OmniParser
SoM: https://github.com/microsoft/SoM
Playwright MCP: https://github.com/microsoft/playwright-mcp
Android WebView: https://developer.android.com/reference/android/webkit/WebView
Android background work: https://developer.android.com/develop/background-work
W3C WCAG 2.2: https://www.w3.org/TR/WCAG22/
منابع بیرونی الگو هستند، نه کد تأییدشده یا ادعای اینکه در گوشی کاربر تست شده‌اند. قبل از استفاده فنی، نسخه و مجوزشان دوباره بررسی شود.

[KAI-ANNEX-A-20261119-END]

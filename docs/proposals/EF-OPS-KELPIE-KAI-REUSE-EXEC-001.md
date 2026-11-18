# فرمان اجرایی تخصصی Kelpie Android — اصلاح درجا و عدم موازی‌کاری

شناسه: EF-OPS-KELPIE-KAI-REUSE-EXEC-001 | الحاقیه اجرایی B به KAI-001 / KAI-AO-001
تاریخ: ۱۹ نوامبر ۲۰۲۶ | دامنه: کد اندروید و آزمون‌های متناظر | وضعیت: OWNER-DIRECTED PRIORITY / HANDOFF / REVIEW-GATED
مخزن هدف: https://github.com/imanipour2/kelpie
اصل سند: https://docs.google.com/document/d/1AJ8Yfr9tDXg4VRy8aepkpuWdMQEWSAn5ObrImMg-zm0/edit
پروپوزال KAI: https://github.com/imanipour2/kelpie/pull/68
شاخه تصویربرداری R5: https://github.com/imanipour2/kelpie/pull/66
پرونده عملیاتی S24: https://github.com/imanipour2/enerfoam-website/issues/98

۱. حکم کارفرما و اختیار عامل

شما عامل توسعه و ممیزی مسئول بهبود تعامل و مشاهده در نسخه Android مرورگر Kelpie هستید. کارفرما با اولویت استفاده مجدد از قابلیت‌های واقعی Kelpie و جلوگیری از موتور، سرویس و API موازی موافقت کرده است. اختیار دارید روی branch آزمایشی مجزا کد اصلاحی، آزمون‌ها، fixture، ADR/Threat Model و PR ایجاد کنید. ادغام، نصب روی گوشی، انتشار APK، حذف نسخه قبلی، تغییر پورت یا تونل، توسعه کنترل سایر اپ‌ها، تغییر WordPress و عبور از رضایت/قفل دستگاه مجاز نیست؛ برای این اعمال باید گیت مستقل طی شود.
قاعده طلایی: قبل از افزودن هر کلاس، endpoint، WebView، خدمت تصویربرداری، پل ارتباطی JS یا dependency جدید، ثابت کن قابلیت موجود قابل استفاده/اصلاح نیست. «توسعه موازی برای سرعت» مردود است. هیچ فرضی درباره APK نصب‌شده یا تکمیل QA از روی کامیت/عنوان PR نکن.

۲. PRE-FLIGHT و منبع حقیقت

- ابتدا AGENTS.md و CLAUDE.md و core/README.md و docs/policies/development-and-release.md را از HEAD اصلی مطالعه کن؛ main مشاهده‌شده هنگام تدوین 91dadda8caebe69394e4a6b4589892ae4a495e54 است، اما قبل از اجرا SHA را دوباره بازخوانی کن.
- تمام PRهای 66 و 67 و 68 و بخش‌های مرتبط GitHub و وضعیت CI/merge/base را بازخوانی کن. PR66 روی شاخه QA است، نه main؛ ادغام یا cherry-pick کور آن ممنوع. PR68 در زمان تدوین، اسناد پیش‌نویس و نه implementation است.
- KAI-001، الحاقیه A، سند پژوهشی P5 و چرخه مصوب EF-GOV-DOCOPS-001 را بخوان؛ از سایت/گوشی/تونل فقط در صورت دسترسی و مجوز واقعی شواهد بگیر.
- خروجی الزام‌آور قبل از هر کدنویسی: «Reusable Components Inventory» شامل نام قابلیت، مسیر کد، محل register/dispatch واقعی، وضعیت در main/PR، نقص، حداقل تغییر لازم، آزمون، ریسک امنیتی و دلیل هر کد جدید.
- ابتدا بررسی کن helperهای annotation واقعاً به کدام MCP/Router متصل‌اند؛ صرف وجود تابع برابر قابل استفاده بودن آن در API نیست.

۳. نقشه اجزای موجود؛ به جای اختراع دوباره اصلاح کن

در main:
- core/android/src/main/java/com/kelpie/browser/handlers/InteractionScripts.kt: annotationElementsScript، annotationActivationScript، fillAnnotationScript، selectorActivationScript. فهرست اولیه ۵۰ عنصر دارد؛ شماره index در زمان عمل دوباره از صفحه استخراج می‌شود.
- core/android/src/main/java/com/kelpie/browser/handlers/InteractionHandler.kt: click، tap، fill، type، select-option. مسیر tap به هنگام coordinateSpace=screenshot بر DPR تقسیم می‌کند و رویدادهای JS مصنوعی می‌سازد.
- core/android/src/main/java/com/kelpie/browser/handlers/DOMHandler.kt: get-dom، query-selector، query-selector-all و bounding rect.
- core/android/src/main/java/com/kelpie/browser/handlers/CoordinateDiagnosticsHandler.kt: viewport، visualViewport، elementFromPoint و تشخیص خطای مختصات.
- core/android/src/main/java/com/kelpie/browser/handlers/HighlightHandler.kt: highlight موقت. فقط برای QA/compatibility؛ Overlay دائمی در DOM سایت ممنوع.
- core/android/src/main/java/com/kelpie/browser/handlers/ScreenshotHandler.kt و HandlerContext.kt: capture Bitmap/Canvas، حالات native و viewport، imageScaleX/Y و مشخصات واقعی تصویر.
- core/android/src/main/java/com/kelpie/browser/network/BrowserGatewayBinding.kt و KelpieBrowserSession.kt: مسیر Router، MCP، مالکیت تب و WebView.
در PR66 به عنوان کد پیشنهادیِ جدا: R5WebViewActionScripts.kt، R5NativeBrowserPort.kt، R5MotherMcpCaptureAdapter.kt، PrivateWebViewDisplay و R5VerifiedFrameEncoder؛ نتیجه Action Receipt و کنترل مالک را قبل از اقتباس از نسخه و branch دقیق بررسی کن.
از این اجزا استفاده کن؛ KAI server، WebView دوم، DOM scraper دوم، تصویرگیر دوم، سیستم مجوز دوم، مسیر localhost بدون احراز یا CDP عمومی ایجاد نکن.

۴. فاز P0 — رفع مخاطرات امنیتی قبل از قابلیت گرافیکی

۴.۱. نام عنصر فعلی ممکن است از el.value مشتق شود. هر مسیر annotation/snapshot را از نظر انتشار password، OTP، شماره کارت، token، ورودی خصوصی و URL دارای داده حساس ممیزی و اصلاح کن. متن قابل دسترس را با role/label کمینه و پالایش‌شده بساز و value کاربر را اصلاً در Element Map ارسال نکن. برای password، autocomplete=one-time-code، cc-*، hidden، file، input و contenteditable با ریسک حریم خصوصی redacted/null و تگ sensitivity ارائه کن.
۴.۲. عنصر نقشه‌شده نباید محتوای prompt injection را به دستور قابل اجرا تبدیل کند. هیچ متن HTML/selector واردشده توسط صفحه، مجوز native را صادر یا تمدید نمی‌کند؛ اجرای JS دلخواه از درخواست عامل ممنوع است.
۴.۳. screenshot دارای داده حساس باید پیش از ارسال از دستگاه redacted شود. اگر تضمین پوششِ اطلاعات حساس ممکن نبود، fail-closed و NEEDS_HUMAN. فقط حذف داده از JSON کافی نیست.
۴.۴. بررسی سایر endpointهای قدیمی فقط به اندازه جلوگیری از رگرسیون و تداخل باشد. تغییر غیرضروری get-dom یا API تاریخچه، Scope Creep است؛ اصلاح امنیتی لازم را با تست سازگاری جدا ثبت کن.
۴.۵. fixture با داده ساختگی بساز و اثبات کن هیچ مقدار محرمانه در JSON، PNG، report، CI log و GitHub ذخیره نشده است.

۵. فاز P1 — Observation و ElementRef معتبر

۵.۱. index موجود صرفاً شماره نمایشی است، نه هویت عنصر. در مسیر annotationActivationScript / fillAnnotationScript اکنون فهرست با هر عمل دوباره ساخته می‌شود و index ممکن است به عنصر دیگری اشاره کند. این ضعف را حذف یا برای Agent جدید مسدود کن و رفتار legacy را جداگانه regression test کن.
۵.۲. قرارداد کوتاه‌عمر Observation بساز: observationId، tab UUID، WebView/renderer identity، documentVersion/epoch، owner principalRef غیرحساس، consentGeneration، origin scope، capturedAt، viewport/visualViewport/scroll، frameNonce و element refs به همراه role، accessible name پالایش‌شده، bbox، visibility، actionHints و coverage.
۵.۳. E01..E50 فقط در همان observation و تب و سند معتبر باشد؛ با navigation، DOM reorder معنادار، SPA rerender، reload، tab switch، STOP، unpair، consent revoke، lock یا renderer death باطل شود. TTL و سقف حافظه مختصر با test تعیین شود.
۵.۴. در خود عملیات action، resolve و revalidate را با uniqueness، role/name/fingerprint غیرحساس، enabled، viewport، occlusion و elementFromPoint اتمیک تا حد امکان انجام بده. اگر نام/موقعیت/هویت تغییر کرده یا عنصر مبهم شد، STALE_ELEMENT / AMBIGUOUS_TARGET / OCCLUDED برگردان، نه کلیک روی اولین match.
۵.۵. برای عناصر Shadow DOM باز و iframe هم‌مبدأ امکان توسعه و تست بده؛ cross-origin iframe/closed shadow را UNKNOWN/OPAQUE اعلام کن. از ادعای دسترسی فراگیر بدون شاهد خودداری کن.
۵.۶. بازتولید فهرست هنگام کلیک، index کور یا حدس selector بر اساس متن قابل‌تکرار ممنوع. Native ref registry فقط محدوده نشست دارد و retention طولانی از DOM یا اطلاعات حساس ندارد.

۶. فاز P2 — Annotated Screenshot با Canvas بومی موجود

۶.۱. از ScreenshotHandler، HandlerContext و پس از پذیرش R5 از frame provider همان R5 استفاده کن. ابزار جدید، سرویس تصویربرداری جدید یا WebView دوم نباید ایجاد شود.
۶.۲. گزینه annotated observation را در همان مسیر MCP/BrowserGateway موجود، با feature negotiation و fallback سازگار، اضافه کن. اگر ابزار موجود extend نمی‌شود، علت را با ADR و API review اثبات کن. API قبلی screenshot همچنان بدون annotation و با قالب پیشین کار کند.
۶.۳. روی نسخه کپی Bitmap از android.graphics.Canvas و Paint برای کادر و E01..EN استفاده کن؛ تصویر و DOM واقعی مرورگر بدون تغییر بمانند. مدیریت هم‌پوشانی شماره‌ها و پوشاندن داده حساس، پیش از encode انجام شود. تصویر اصلی برای کاربر به صورت دائمی تغییر نکند.
۶.۴. rect CSS را با imageScaleX/Y متادیتای دقیق همان فریم و visualViewport offset، crop و zoom به تصویر تبدیل کن. تقسیم ثابت بر devicePixelRatio فقط اگر برابری با scale واقعی آزمون شده باشد؛ native/viewport و نسبت تصویر متفاوت را جدا پوشش بده.
۶.۵. baseline اولیه سقف ۵۰ عنصر فعلی است. اگر truncation رخ داد، truncated=true و count/coverage ناشناخته را گزارش کن؛ شماره‌های مخفی را قابل کلیک ادعا نکن. memory, latency, bitmap recycle و backpressure آزموده شوند.
۶.۶. تصویر و map یک observationId و frame nonce مشترک داشته باشند؛ بین دو مرحله DOM و capture تغییر سند یا تب → OBSERVATION_RACE/NO_FRESH_FRAME، نه بسته ناهماهنگ. در HOME تنها فریم تازه تاییدشده R5 قابل قبول است؛ تصویر خاکستری یا قدیمی خطا محسوب شود.

۷. فاز P3 — اقدام بر اساس Ref و راستی‌آزمایی اثر

۷.۱. از InteractionHandler و native R5 authorization/admission استفاده کن. action باید operation، observationId، elementRef، expectedEffect و commandId را دریافت کند و گیت native هویت/مجوز/تب/سند را بررسی نماید؛ اتصال MCP، localhost یا خروجی صفحه به خودی خود مجوز عمل نیست.
۷.۲. حالت‌ها را تفکیک کن: ADMITTED، DISPATCHED، EXECUTED، EFFECT_VERIFIED، UNCERTAIN، STALE، BLOCKED. پاسخ HTTP 200 یا اجرای element.click به معنی تحقق هدف نیست.
۷.۳. sort/filter را با postcondition خاص کنترل بررسی کن: selected state، تغییر chip و در صورت لازم ترتیب عددی واقعی قیمت‌ها. generic DOM fingerprint change صرفاً شاهد کمکی است و نباید به تنهایی موفقیت مرتب‌سازی را ثابت کند.
۷.۴. در scroll، scroll container واقعی، lazy loading، pagination، virtual list و پایان مشاهده مشخص شوند. ثابت‌ماندن scrollY اثبات کامل‌شدن فهرست نیست. خروجی منفی فقط با پوشش قابل دفاع باشد؛ در بقیه موارد INCOMPLETE/UNKNOWN.
۷.۵. action نامعلوم را بدون idempotency و مشاهده تازه تکرار نکن؛ برای اعمال حساس از جمله رمز، OTP، CAPTCHA، پرداخت، حذف، انتشار، انتقال وجه یا احراز هویت مسیر NEEDS_HUMAN / تأیید مجزا را نگه دار.
۷.۶. هر اقدام با ActionReceipt غیرحساس و before/after state مرتبط شود؛ journal اسکرین‌شات و اطلاعات حساب واقعی را ذخیره نکند.

۸. فاز P4 — لمس بومی، فقط به شرط نیاز اثبات‌شده

پس از تثبیت P0 تا P3، اگر fixture واقعی نشان داد JS-click در کنترل‌های خاص کافی نیست، یک PoC محدود از View.dispatchTouchEvent(MotionEvent) فقط برای WebView همان برنامه بررسی کن؛ از KMPwright به‌عنوان الگو استفاده کن، نه کپی بی‌ممیزی.
محدودیت‌ها: تبدیل CSS→WebView-local با viewport دقیق، اجرای ACTION_DOWN/UP/CANCEL روی Main thread، کنترل دوباره grant قبل و بین رویدادها، اجرای CANCEL در revocation، عدم هدف‌گرفتن ChatGPT یا سایر برنامه‌ها، عدم بالا آوردن Activity به foreground، و عدم اجرای fallback دوم برای فرمان نامعلوم. تست HOME/private display اجباری؛ اگر اجرا در پس‌زمینه نامعتبر بود UNSUPPORTED_IN_BACKGROUND اعلام شود. این PoC حق اضافه‌شدن به APK یا main بدون QA/Parity را ندارد.
AccessibilityService، UIAutomator و ADB ابزار محصولی نیستند؛ تنها برای QA بیرونی قابل استفاده‌اند. CDP عمومی و MediaProjection نیز برای این مسئله پیاده نشوند.

۹. سازگاری پلتفرم و قاعده Merge

AGENTS.md کامل Android/iOS parity را الزام می‌کند. تحقیق و QA اختصاصی Android روی branch مجاز است، ولی merge بدون کد معادل iOS یا استثنای رسمی مستقل ممنوع. امکان AndroidX WebKit features فقط با WebViewFeature.isFeatureSupported روی دستگاه واقعی و سیاست بدون content-script ماندگار بررسی شود.
تا اثبات وابستگی‌ها PR66 و PR67 را merge/cherry-pick یا refactor دسته‌جمعی نکن. تداخل نسخه و قرارداد Mother MCP را مستند کن.
هیچ تغییر در Termux، VPN، endpoint، port، signing identity، نسخه‌های نصب‌شده یا WordPress LIVE/STAGING انجام نده. release signing و مالکیت STOP دست‌نخورده باقی بمانند.

۱۰. گیت‌ها و ترتیب کار

G0: baseline SHA، inventory، threat model، fixture، نقشه dependency، طراحی diff کوچک و تصمیم درباره reuse.
G1: اصلاح P0 امنیت/حریم خصوصی و P1 ref های ناپایدار؛ Unit/Integration سبز بدون API جدید.
G2: bitmap annotation و قرارداد observation امن در P2 با تست مختصات و فریم.
G3: action-by-ref و postcondition در P3، حفظ MCP قدیمی و no-regression.
G4: آزمون واقعی S24 فقط با Device/Consent متصل و نسخه معلوم؛ پس‌زمینه HOME با ChatGPT foreground، lock/STOP، WiFi/LTE، rotation و ۲۵ چرخه. بدون دسترسی، BLOCKED نه PASS.
G5: iOS parity یا exception مصوب، review، CI، owner acceptance، merge/readback و gate مستقل APK/release.
از مراحل امن برای پیشرفت استفاده کن؛ وابستگی غیرآماده R5 مانع نوشتن و تست مستقل P0/P1 در شاخه نباشد.

۱۱. ماتریس آزمون اجباری

T01 — ۱۲+ کنترل فارسی RTL در صفحه: شماره و ElementRef دقیق، role/name/rect صحیح.
T02 — DOM reorder، lazy banner، SPA، navigate، reload و tab switch: شناسه قدیمی رد شود؛ هیچ کلیک اشتباه.
T03 — password/OTP/card/file/hidden/ARIA/placeholder با داده ساختگی: صفر نشت در Element Map، annotated PNG، لاگ.
T04 — دو کنترل هم‌نام، overlay پوشاننده و عنصر خارج viewport: AMBIGUOUS/OCCLUDED/STALE به جای حدس.
T05 — native/viewport، DPR، zoom، rotation، soft keyboard، CSS scroll و visualViewport: خطای تبدیل مختصات اندازه‌گیری و اصلاح شود.
T06 — iframe هم‌مبدأ/متقاطع و Shadow DOM: مرزبندی واقعی و OPAQUE صادقانه.
T07 — بیش از ۵۰ عنصر، modal دارای scroll، لیست infinite/virtualized: truncated، observed/unknown و atEnd صحیح.
T08 — Sort/Filter فارسی و انگلیسی: اثر هدف با state و داده مستقل اثبات شود.
T09 — gray/stale/no renderer frame: خطای صریح NO_FRESH_FRAME؛ هرگز success-shaped blank.
T10 — owner mismatch، revoke، STOP و قفل در مرحله capture/action: عدم mutation بعد از لغو اختیار.
T11 — HOME و ChatGPT foreground: فقط فریم جدید همان WebView و بدون ربودن foreground؛ در صورت عدم امکان BLOCKED.
T12 — regression کلیک/پرکردن/انتخاب/اسکرول/تب/عکس در APIهای قبلی؛ نبود double-action.
T13 — memory leak، Bitmap recycle، cancellation، timeout و ۲۵ چرخه device؛ ثبت نتیجه واقعی.
T14 — Android build+ktlint+tests، security review، iOS parity یا exception، rollback و feature flag off.
هر تست دارای fixture، oracle expected، actual result، PASS/FAIL/BLOCKED و ارجاع CI/commit باشد. نتیجه ساخت موفق یا اجرای HTTP 200 اثبات تجربه روی گوشی نیست.

۱۲. تحویل و قالب گزارش اجباری

در همان Issue و یک PR مستقل توسعه (نه PR اسنادی #68) موارد زیر را ثبت کن: base SHA، Reuse Inventory، دیف حداقلی هر فایل و علت تغییر، قرارداد Observation/Ref/ActionReceipt، ADR و Threat Model، شواهد T01..T14، تست‌های Android/iOS، CI، نسخه/امضا/پورت بازخوانی‌شده در صورت QA واقعی، نگاشت وابستگی PR66/67، rollback، محدودیت‌ها، وضعیت MAIN و APK، و فهرست اقدام‌های باز.
وضعیت‌های مجزا: DESIGN_PROPOSED، SOURCE_COMMITTED، CI_PASS، DEVICE_QA_PASS، OWNER_ACCEPTED، MAIN_MERGED، APK_INSTALLED، REMOTE_E2E_VERIFIED. تا readback لازم هیچ وضعیت بالاتر را ادعا نکن.
منابع رسمی/نمونه‌های اقتباس: https://developer.android.com/reference/android/webkit/WebView ، https://developer.android.com/reference/android/view/View ، https://developer.android.com/reference/android/media/ImageReader ، https://developer.android.com/reference/androidx/webkit/WebViewCompat ، https://github.com/bernaferrari/kmpwright/blob/3cf13b9eb83d75753fc06f8f6f368ae2dbf74492/kmpwright-core/src/androidMain/kotlin/com/bernaferrari/kmpwright/core/Input.kt ، https://github.com/browser-use/browser-use/blob/main/browser_use/browser/python_highlights.py .
منابع خارجی قبل از اقتباس از نظر license/commit و Android compatibility بازخوانی شوند.

فرمان اجرایی: پس از preflight، بلافاصله inventory و بسته P0 را روی branch ایمن آغاز و با تست‌های واقعی مستند کن؛ سپس مرحله‌به‌مرحله پیش برو. هیچ اقدام خارج از scope، هیچ افشای اطلاعات حساس و هیچ ادعای موفقیت بی‌شاهد مجاز نیست.
[KAI-REUSE-FIRST-EXEC-20261119-END]

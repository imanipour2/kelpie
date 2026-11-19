/** Synthetic local-only RTL controls; no account, endpoint or personal data. */
export const KAI_RTL_FIXTURE = [
  '<html lang="fa" dir="rtl"><meta name="viewport" content="width=device-width,initial-scale=1">',
  '<style>body{margin:12px;font:16px sans-serif}button,input,select{height:32px;margin:3px}button{min-width:120px}.controls{display:grid;grid-template-columns:1fr 1fr;gap:4px}</style>',
  '<title>آزمون کنترل‌ها</title><div class="controls">',
  '<button type="button" id="sort">ارزان‌ترین</button><button type="button" id="filter">فیلتر پرواز مستقیم</button>',
  '<button type="button">رفت</button><button type="button">برگشت</button>',
  '<button type="button">روز بعد</button><button type="button">روز قبل</button>',
  '<label>مبدا<input id="from" aria-label="مبدا"></label><label>مقصد<input id="to" aria-label="مقصد"></label>',
  '<label>روز<input id="date" aria-label="روز"></label><label>تعداد<input id="people" aria-label="تعداد"></label>',
  '<label>توقف<select id="stop"><option value="all">همه</option><option value="direct">مستقیم</option></select></label>',
  '<label>بیمه<input id="insurance" type="checkbox"></label></div>',
  '<script>window.effects=0;document.querySelector("#sort").onclick=()=>{window.effects++};</script></html>',
].join("\n");

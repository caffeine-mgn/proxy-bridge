package pw.binom.proxy.front

/** Страница фронта. Пока обычный HTML/JS; позже её заменит compose-html. */
object FrontPage {
    val html: String = """
<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="utf-8">
<title>proxy-bridge — доставка на удалённую машину</title>
<style>
  :root { color-scheme: dark; }
  * { box-sizing: border-box; }
  body { margin: 0; padding: 24px; background: #14161a; color: #e6e6e6;
         font: 14px/1.5 -apple-system, "Segoe UI", Roboto, sans-serif; }
  h1 { font-size: 20px; margin: 0 0 4px; }
  h2 { font-size: 15px; margin: 0 0 12px; color: #9fb3c8; font-weight: 600;
       text-transform: uppercase; letter-spacing: .06em; }
  .muted { color: #8b98a5; }
  .card { background: #1b1f26; border: 1px solid #262c35; border-radius: 10px;
          padding: 16px; margin-bottom: 16px; }
  ol { margin: 0; padding-left: 20px; }
  li { margin: 6px 0; }
  table { width: 100%; border-collapse: collapse; }
  td, th { text-align: left; padding: 7px 8px; border-bottom: 1px solid #232a33; }
  th { color: #8b98a5; font-weight: 500; font-size: 12px; text-transform: uppercase; }
  code { background: #0f1216; padding: 2px 6px; border-radius: 4px; color: #7dd3a0; }
  button { background: #2b6ef2; color: #fff; border: 0; border-radius: 7px;
           padding: 6px 12px; cursor: pointer; font-size: 13px; }
  button:hover { background: #3b7dfb; }
  button:disabled { background: #2a3340; color: #6b7785; cursor: default; }
  button.danger { background: #c2410c; padding: 9px 16px; }
  button.danger:hover { background: #ea580c; }
  button.launch { background: #1f7a4d; }
  button.launch:hover { background: #26945e; }
  .variant { background: #191d24; border: 1px solid #262c35; border-radius: 10px;
             padding: 14px 16px; margin-bottom: 12px; }
  .variant-head { display: flex; align-items: baseline; gap: 10px; margin-bottom: 10px; }
  .variant-name { font-size: 16px; font-weight: 600; color: #cbd5e1; }
  .badge { font-size: 11px; color: #8b98a5; border: 1px solid #2f3742;
           border-radius: 999px; padding: 1px 8px; }
  .launch-box { margin-top: 10px; padding-top: 10px; border-top: 1px solid #232a33;
                display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
  .launch-cmd { font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 12px;
                color: #a7b4c2; white-space: pre-wrap; }
  .status-line { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
  .pill { font-size: 12px; border-radius: 999px; padding: 2px 10px; font-weight: 600; }
  .pill.ok { background: #14351f; color: #7dd3a0; }
  .pill.err { background: #3a1c1c; color: #f87171; }
  .facts { margin-top: 10px; font-size: 12px; color: #8b98a5; font-family: ui-monospace, Menlo, Consolas, monospace; }
  .problems { margin: 10px 0 0; padding-left: 18px; color: #f0a97a; font-size: 13px; }
  #log { background: #0f1216; border-radius: 8px; padding: 12px; min-height: 90px;
         font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 12px;
         white-space: pre-wrap; color: #a7b4c2; }
  .ok { color: #7dd3a0; } .err { color: #f87171; }
</style>
</head>
<body>
  <h1>proxy-bridge · режим доставки</h1>
  <div class="muted">Фронт включён — обычная логика проксирования остановлена.</div>

  <div class="card">
    <h2>Как это работает</h2>
    <ol>
      <li>На удалённой машине откройте <b>Блокнот</b> (раскладка <b>US</b>).</li>
      <li>Выберите <b>один</b> вариант бутстрапа ниже и напечатайте его файлы,
          сохраняя под показанными именами (settings.txt — тоже).</li>
      <li>Впишите в settings.txt свой COM-порт (это делается руками).</li>
      <li>Нажмите «Напечатать команду запуска» — команды впечатаются в консоль.</li>
      <li>Нажмите «Отправить себя» — бутстрап примет программу и сохранит её.</li>
      <li>Выключите <code>front.enabled</code> и запустите принятую программу.</li>
    </ol>
  </div>

  <div class="card">
    <h2>USB-гаджет</h2>
    <div class="status-line">
      <span id="gadgetPill" class="pill err">проверяю…</span>
      <button id="gadgetSetup">Настроить гаджет</button>
    </div>
    <div id="gadgetFacts" class="facts"></div>
    <ul id="gadgetProblems" class="problems" style="display:none"></ul>
  </div>

  <div class="card">
    <h2>Варианты бутстрапа</h2>
    <div id="variants"><div class="muted">Загрузка…</div></div>
  </div>

  <div class="card">
    <h2>Передать программу</h2>
    <p class="muted">Файл уйдёт по COM-порту в уже запущенный бутстрап.
       Прогресс виден в его консоли.</p>
    <button id="sendSelf" class="danger">Отправить себя через bootstrap</button>
  </div>

  <div class="card">
    <h2>Журнал</h2>
    <div id="log" class="muted">Готов.</div>
  </div>

<script>
const log = (text, cls) => {
  const el = document.getElementById('log');
  const line = document.createElement('div');
  if (cls) line.className = cls;
  line.textContent = new Date().toLocaleTimeString() + '  ' + text;
  el.appendChild(line);
  el.scrollTop = el.scrollHeight;
};

async function api(method, url, body) {
  const options = { method, headers: {} };
  if (body !== undefined) {
    options.headers['Content-Type'] = 'application/json';
    options.body = JSON.stringify(body);
  }
  const res = await fetch(url, options);
  return await res.json();
}

function lockAll(locked) {
  document.querySelectorAll('button').forEach(b => b.disabled = locked);
}

function humanSize(size) {
  return size > 1024 ? (size / 1024).toFixed(1) + ' КБ' : size + ' Б';
}

function renderVariants(variants) {
  const root = document.getElementById('variants');
  root.innerHTML = '';
  if (!variants.length) {
    root.innerHTML = '<div class="muted">Каталог бутстрапа пуст</div>';
    return;
  }
  for (const v of variants) {
    const box = document.createElement('div');
    box.className = 'variant';

    const head = document.createElement('div');
    head.className = 'variant-head';
    head.innerHTML = '<span class="variant-name"></span>' +
                     '<span class="badge"></span>';
    head.querySelector('.variant-name').textContent = v.name;
    head.querySelector('.badge').textContent = v.files.length + ' файл(ов)';
    box.appendChild(head);

    const table = document.createElement('table');
    table.innerHTML = '<thead><tr><th>Имя файла</th><th>Размер</th><th></th></tr></thead>';
    const tbody = document.createElement('tbody');
    for (const f of v.files) {
      const tr = document.createElement('tr');
      tr.innerHTML = '<td><code></code></td><td class="muted"></td>' +
                     '<td style="text-align:right"><button>Напечатать</button></td>';
      tr.querySelector('code').textContent = f.name;
      tr.querySelector('.muted').textContent = humanSize(f.size);
      tr.querySelector('button').onclick = () => typeFile(v.name, f.name);
      tbody.appendChild(tr);
    }
    table.appendChild(tbody);
    box.appendChild(table);

    for (const l of (v.launches || [])) {
      const launch = document.createElement('div');
      launch.className = 'launch-box';
      const cmd = document.createElement('div');
      cmd.className = 'launch-cmd';
      cmd.textContent = l.commands;
      const btn = document.createElement('button');
      btn.className = 'launch';
      btn.textContent = 'Запустить через ' + l.name;
      btn.onclick = () => typeLaunch(v.name, l.name);
      launch.appendChild(btn);
      launch.appendChild(cmd);
      box.appendChild(launch);
    }

    root.appendChild(box);
  }
}

async function typeFile(variant, file) {
  lockAll(true);
  log('Печатаю ' + variant + '/' + file + ' — не трогайте клавиатуру на удалённой машине…');
  try {
    const res = await api('POST', '/api/bootstrap/type', { variant, file });
    log(res.message, res.ok ? 'ok' : 'err');
  } catch (e) {
    log('Ошибка запроса: ' + e, 'err');
  } finally {
    lockAll(false);
  }
}

async function typeLaunch(variant, launch) {
  lockAll(true);
  log('Печатаю команды запуска «' + launch + '» для ' + variant + '…');
  try {
    const res = await api('POST', '/api/bootstrap/launch', { variant, launch });
    log(res.message, res.ok ? 'ok' : 'err');
  } catch (e) {
    log('Ошибка запроса: ' + e, 'err');
  } finally {
    lockAll(false);
  }
}

function renderGadget(g) {
  const pill = document.getElementById('gadgetPill');
  const facts = document.getElementById('gadgetFacts');
  const problems = document.getElementById('gadgetProblems');

  if (!g.supported) {
    pill.className = 'pill err';
    pill.textContent = 'не поддерживается';
  } else if (g.ok) {
    pill.className = 'pill ok';
    pill.textContent = 'настроен';
  } else {
    pill.className = 'pill err';
    pill.textContent = 'не настроен';
  }

  const parts = [];
  if (g.udc) parts.push('UDC: ' + g.udc);
  if (g.udcState) parts.push('state: ' + g.udcState);
  if (g.functions && g.functions.length) parts.push('функции: ' + g.functions.join(', '));
  if (g.serialDevice) parts.push('COM: ' + g.serialDevice);
  if (g.keyboardDevice) parts.push('клавиатура: ' + g.keyboardDevice);
  facts.textContent = parts.join('   ·   ');

  if (g.problems && g.problems.length) {
    problems.style.display = '';
    problems.innerHTML = '';
    for (const p of g.problems) {
      const li = document.createElement('li');
      li.textContent = p;
      problems.appendChild(li);
    }
  } else {
    problems.style.display = 'none';
  }
}

async function loadGadget() {
  try {
    renderGadget(await api('GET', '/api/gadget'));
  } catch (e) {
    document.getElementById('gadgetPill').textContent = 'ошибка запроса';
  }
}

document.getElementById('gadgetSetup').onclick = async () => {
  lockAll(true);
  log('Настраиваю USB-гаджет…');
  try {
    const res = await api('POST', '/api/gadget/setup');
    renderGadget(res);
    log(res.message, res.ok ? 'ok' : 'err');
    (res.problems || []).forEach(p => log('  ' + p, 'err'));
  } catch (e) {
    log('Ошибка запроса: ' + e, 'err');
  } finally {
    lockAll(false);
  }
};

loadGadget();

document.getElementById('sendSelf').onclick = async () => {
  lockAll(true);
  log('Отправляю программу по COM-порту…');
  try {
    const res = await api('POST', '/api/bootstrap/send-self');
    log(res.message, res.ok ? 'ok' : 'err');
  } catch (e) {
    log('Ошибка запроса: ' + e, 'err');
  } finally {
    lockAll(false);
  }
};

api('GET', '/api/bootstrap')
  .then(renderVariants)
  .catch(() => {
    document.getElementById('variants').innerHTML =
      '<div class="err">Не удалось получить список вариантов</div>';
  });

api('GET', '/api/status').then(s => {
  log('Режим: ' + s.mode + ', вариантов бутстрапа: ' + s.variants +
      (s.selfFile ? ', отправляемый файл: ' + s.selfFile : ', файл для отправки не задан'),
      s.selfFile ? 'ok' : 'err');
}).catch(() => {});
</script>
</body>
</html>
""".trimIndent()
}

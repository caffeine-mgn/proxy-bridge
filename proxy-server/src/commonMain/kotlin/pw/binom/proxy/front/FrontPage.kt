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
  h2 { font-size: 15px; margin: 24px 0 8px; color: #9fb3c8; font-weight: 600;
       text-transform: uppercase; letter-spacing: .06em; }
  .muted { color: #8b98a5; }
  .card { background: #1b1f26; border: 1px solid #262c35; border-radius: 10px;
          padding: 16px; margin-bottom: 16px; }
  ol { margin: 0; padding-left: 20px; }
  li { margin: 6px 0; }
  table { width: 100%; border-collapse: collapse; }
  td, th { text-align: left; padding: 8px 10px; border-bottom: 1px solid #232a33; }
  th { color: #8b98a5; font-weight: 500; font-size: 12px; text-transform: uppercase; }
  code { background: #0f1216; padding: 2px 6px; border-radius: 4px; color: #7dd3a0; }
  button { background: #2b6ef2; color: #fff; border: 0; border-radius: 7px;
           padding: 8px 14px; cursor: pointer; font-size: 13px; }
  button:hover { background: #3b7dfb; }
  button:disabled { background: #2a3340; color: #6b7785; cursor: default; }
  button.danger { background: #c2410c; }
  button.danger:hover { background: #ea580c; }
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
      <li>На удалённой машине откройте <b>Блокнот</b> (или любой текстовый редактор) в <b>US-раскладке</b>.</li>
      <li>Нажмите «Напечатать» напротив файла — текст впечатается как с клавиатуры.</li>
      <li>Сохраните файл под именем из колонки «Имя файла» (в ту же папку, где будете собирать).</li>
      <li>Повторите для всех файлов, включая <code>settings.txt</code>; откройте его и укажите свой COM-порт.</li>
      <li>Соберите и запустите бутстрап на удалённой машине.</li>
      <li>Нажмите «Отправить себя» — бутстрап примет программу и сохранит её в свой рабочий каталог.</li>
    </ol>
  </div>

  <div class="card">
    <h2>Файлы бутстрапа</h2>
    <table>
      <thead><tr><th>#</th><th>Имя файла</th><th>Размер</th><th></th></tr></thead>
      <tbody id="files"><tr><td colspan="4" class="muted">Загрузка…</td></tr></tbody>
    </table>
    <p><button id="settings">Напечатать настройки бутстрапа</button></p>
  </div>

  <div class="card">
    <h2>Передать программу</h2>
    <p class="muted">Файл уйдёт по COM-порту в запущенный бутстрап. Прогресс виден в его консоли.</p>
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

async function api(method, url) {
  const res = await fetch(url, { method });
  const data = await res.json();
  return data;
}

async function loadFiles() {
  const tbody = document.getElementById('files');
  try {
    const files = await api('GET', '/api/bootstrap/files');
    tbody.innerHTML = '';
    if (!files.length) {
      tbody.innerHTML = '<tr><td colspan="4" class="muted">Каталог бутстрапа пуст</td></tr>';
      return;
    }
    for (const f of files) {
      const tr = document.createElement('tr');
      tr.innerHTML =
        '<td>' + f.id + '</td>' +
        '<td><code>' + f.name + '</code></td>' +
        '<td class="muted">' + (f.size > 1024 ? (f.size / 1024).toFixed(1) + ' КБ' : f.size + ' Б') + '</td>' +
        '<td><button data-id="' + f.id + '">Напечатать</button></td>';
      tbody.appendChild(tr);
    }
    tbody.querySelectorAll('button[data-id]').forEach(btn => {
      btn.onclick = () => typeFile(btn.dataset.id, btn);
    });
  } catch (e) {
    tbody.innerHTML = '<tr><td colspan="4" class="err">Не удалось получить список файлов</td></tr>';
  }
}

async function typeFile(id, btn) {
  const buttons = document.querySelectorAll('button');
  buttons.forEach(b => b.disabled = true);
  log('Печатаю файл #' + id + ' — не трогайте клавиатуру и мышь на удалённой машине…');
  try {
    const res = await api('POST', '/api/bootstrap/type/' + id);
    log(res.message, res.ok ? 'ok' : 'err');
  } catch (e) {
    log('Ошибка запроса: ' + e, 'err');
  } finally {
    buttons.forEach(b => b.disabled = false);
  }
}

document.getElementById('settings').onclick = async (e) => {
  const buttons = document.querySelectorAll('button');
  buttons.forEach(b => b.disabled = true);
  log('Печатаю settings.txt…');
  try {
    const res = await api('POST', '/api/bootstrap/settings');
    log(res.message, res.ok ? 'ok' : 'err');
  } catch (e) {
    log('Ошибка запроса: ' + e, 'err');
  } finally {
    buttons.forEach(b => b.disabled = false);
  }
};

document.getElementById('sendSelf').onclick = async (e) => {
  const buttons = document.querySelectorAll('button');
  buttons.forEach(b => b.disabled = true);
  log('Отправляю программу по COM-порту…');
  try {
    const res = await api('POST', '/api/bootstrap/send-self');
    log(res.message + (res.bytes ? ' (' + res.bytes + ' байт)' : ''), res.ok ? 'ok' : 'err');
  } catch (e) {
    log('Ошибка запроса: ' + e, 'err');
  } finally {
    buttons.forEach(b => b.disabled = false);
  }
};

loadFiles();
api('GET', '/api/status').then(s => {
  log('Режим: ' + s.mode + ', файлов бутстрапа: ' + s.bootstrapFiles +
      (s.selfFile ? ', отправляемый файл: ' + s.selfFile : ', файл для отправки не задан'),
      s.selfFile ? 'ok' : 'err');
}).catch(() => {});
</script>
</body>
</html>
""".trimIndent()
}

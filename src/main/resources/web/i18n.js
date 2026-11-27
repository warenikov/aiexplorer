(() => {
  const language = window.AI_EXPLORER_LOCALE || "en";
  document.documentElement.lang = language;
  if (language !== "en") return;
  const messages = {
    "Граф файлов":"Activity graph","Показать граф активности":"Show activity graph","Вернуться в терминал":"Return to terminal",
    "Обновить":"Refresh","Новый чат":"New chat","Поиск чатов":"Search chats","Чаты":"Chats","Чаты ":"Chats ",
    "Сегодня":"Today","Вчера":"Yesterday","Без даты":"No date","Здесь появятся ваши чаты":"Your chats will appear here","Ничего не найдено":"Nothing found",
    "Переименовать":"Rename","Удалить":"Delete","Действия с чатом":"Chat actions","Удалить чат?":"Delete chat?","Отмена":"Cancel",
    "будет удалён без возможности восстановления.":"will be permanently deleted.","Войти в Kimi Code":"Sign in to Kimi Code",
    "Войти или сменить аккаунт через стандартную авторизацию Kimi CLI":"Sign in or switch accounts using standard Kimi CLI authentication",
    "Настройки":"Kimi Config","Поиск инструкций и настроек":"Search instructions and settings","Глобальные":"Global","Проект":"Project","Дополнительные":"Extra",
    "Файлы открываются в редакторе IDE":"Files open in the IDE editor","Клик открывает файл · стрелка раскрывает группу":"Click opens a file · arrow expands a group",
    "Скиллы":"Skills","Поиск скиллов":"Search skills","Скиллы не найдены":"No skills found","Ссылки":"Links","Файлы":"Files","Терминал":"Terminal","Документация":"Documentation","Конфигурация":"Configuration","Сеть":"Network","Субагенты":"Subagents",
    "Глобальный":"Global","Проектный":"Project","Дополнительный":"Extra","Активность сессии":"Session activity","Сбросить фильтр":"Reset filter",
    "ИСТОЧНИКИ":"SOURCES","РЕЗУЛЬТАТ":"RESULTS","действий":"actions","ЗА СЕССИЮ":"SESSION","Чтения":"Reads","Правки":"Edits","Команды":"Commands","Ошибки":"Errors",
    "файлы, документация, сеть":"files, documentation, network","изменённых файлов":"edited files","выполнено в терминале":"executed in terminal","завершились с ошибкой":"failed",
    "Клик фильтрует · повторный клик открывает детали":"Click to filter · click again for details","Объект / команда":"Object / command","Обращения":"Calls","Последний раз":"Last used",
    "Все":"All","Нет действий для этого фильтра":"No actions for this filter","Карта":"Graph","Открыть файл":"Open file","Открыть сайт":"Open site",
    "Файлы скилла":"Skill files","Поиск MCP-серверов":"Search MCP servers","Добавить сервер":"Add server","+ Добавить сервер":"+ Add server","Дополнительно → JSON":"Advanced → JSON",
    "Название":"Name","Тип подключения":"Connection type","stdio · локальная команда":"stdio · local command","Команда":"Command",
    "Аргументы · один на строку":"Arguments · one per line","Пусто — сохранить текущие аргументы":"Empty — keep current arguments","Рабочая директория":"Working directory",
    "Переменные · KEY=value, одна на строку":"Variables · KEY=value, one per line","Пусто — сохранить текущие значения":"Empty — keep current values",
    "Существующие значения не загружаются и не отображаются.":"Existing values are never loaded or displayed.","URL содержит скрытые данные — введите заново для замены":"URL contains hidden data — enter it again to replace",
    "Имя переменной с bearer-токеном":"Bearer token environment variable","Включён":"Enabled","Загружать инструменты по требованию":"Load tools on demand",
    "Сохранить":"Save","Пусто":"Empty","включён":"enabled","выключен":"disabled","переопределяет глобальный":"overrides global","Нет конфигурации":"No configuration",
    "Ошибка чтения конфигурации":"Unable to read configuration","Открыть файл":"Open file","Глобальные инструкции":"Global instructions","Глобальные инструкции Claude":"Global Claude instructions",
    "Основные настройки":"Main settings","Настройки терминала":"Terminal settings","Корень проекта":"Project root","Настройки проекта":"Project settings","Локальные настройки проекта":"Local project settings",
    "Ссылка":"Link","Инструмент":"Tool","Сетевой запрос":"Network request",
    "Поиск":"Search","Инструкция":"Instruction","Файлы не найдены":"No files found","Потоки активности Kimi":"Kimi activity flows",
    "действие":"action","действия":"actions","действий":"actions","AI Explorer — Активность":"AI Explorer — Activity","AI Explorer — Скиллы":"AI Explorer — Skills"
  };
  const patterns = [
    [/^(\d+) действие$/, "$1 action"],[/^(\d+) (?:действия|действий)$/, "$1 actions"],
    [/^(\d+) действие за сессию$/, "$1 action in session"],[/^(\d+) (?:действия|действий) за сессию$/, "$1 actions in session"],
    [/^(\d+) обращ\.$/, "$1 calls"],[/^(\d+) обращения$/, "$1 calls"],[/^(\d+) файлов$/, "$1 files"],[/^(\d+) (?:раздел|раздела|разделов)$/, "$1 groups"],
    [/^Фильтр: (.+)$/, "Filter: $1"],[/^(\d+)% сессии$/, "$1% of session"],[/^Удалить MCP-сервер «(.+)»\?$/, "Delete MCP server \"$1\"?"],[/^Последнее: (.+)$/, "Latest: $1"],[/^Сейчас: (.+)$/, "Now: $1"],[/^Инструкция → (.+)$/, "Instruction → $1"],
    [/^(\d+) янв\.?$/, "Jan $1"],[/^(\d+) февр\.?$/, "Feb $1"],[/^(\d+) мар\.?$/, "Mar $1"],[/^(\d+) апр\.?$/, "Apr $1"],
    [/^(\d+) мая\.?$/, "May $1"],[/^(\d+) июн\.?$/, "Jun $1"],[/^(\d+) июл\.?$/, "Jul $1"],[/^(\d+) авг\.?$/, "Aug $1"],
    [/^(\d+) сент\.?$/, "Sep $1"],[/^(\d+) окт\.?$/, "Oct $1"],[/^(\d+) нояб\.?$/, "Nov $1"],[/^(\d+) дек\.?$/, "Dec $1"]
  ];
  function translated(value) {
    if (messages[value]) return messages[value];
    for (const [pattern,replacement] of patterns) if (pattern.test(value)) return value.replace(pattern,replacement);
    return value;
  }
  function isUserData(element) {
    return !!element?.closest(".session-title,.sidebar-confirm p,.skill>.row .title,.file .title,.folder .title,.server>.server-row .title,.entry-title,.history-row span:nth-child(2),.path,.node-title[title^='/']");
  }
  function translate(root) {
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    const nodes = [];
    while (walker.nextNode()) nodes.push(walker.currentNode);
    nodes.forEach(node => {
      if (node.parentElement?.closest(".sidebar-confirm p")) {
        node.nodeValue = node.nodeValue.replace("будет удалён без возможности восстановления.","will be permanently deleted.");
        return;
      }
      if (isUserData(node.parentElement)) return;
      const value = node.nodeValue;
      const trimmed = value.trim();
      if (!trimmed) return;
      const translatedValue = translated(trimmed);
      if (translatedValue !== trimmed) node.nodeValue = value.replace(trimmed,translatedValue);
    });
    if (root.nodeType === Node.ELEMENT_NODE) {
      for (const element of root.querySelectorAll("[title],[placeholder],[aria-label]")) {
        if (isUserData(element)) continue;
        for (const attribute of ["title","placeholder","aria-label"]) {
          const value = element.getAttribute(attribute);
          if (value && translated(value) !== value) element.setAttribute(attribute,translated(value));
        }
      }
    }
  }
  function start() { translate(document.documentElement); new MutationObserver(records => records.forEach(record => record.addedNodes.forEach(node => { if (node.nodeType === Node.ELEMENT_NODE || node.nodeType === Node.TEXT_NODE) translate(node.nodeType === Node.TEXT_NODE ? node.parentElement : node); }))).observe(document.documentElement,{subtree:true,childList:true}); }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded",start); else start();
})();

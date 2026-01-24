# Legal Pages - Firebase Hosting

Privacy Policy и Terms of Use размещены на Firebase Hosting.

## URLs

| Страница | URL |
|----------|-----|
| Privacy Policy | https://gisti-app.web.app/privacy-policy |
| Terms of Use | https://gisti-app.web.app/terms |

---

## Структура файлов

```
hosting/
├── public/
│   ├── index.html           # Редирект на /privacy-policy
│   ├── privacy-policy.html  # Политика конфиденциальности
│   └── terms.html           # Условия использования
└── firebase.json            # Конфигурация хостинга
```

---

## Деплой

```bash
cd hosting
firebase deploy --only hosting --project aichecklists-40230
```

После деплоя изменения доступны сразу на https://gisti-app.web.app

---

## Конфигурация

### firebase.json

```json
{
  "hosting": {
    "site": "gisti-app",
    "public": "public",
    "cleanUrls": true,
    "trailingSlash": false
  }
}
```

- **site**: Имя hosting site (определяет домен `gisti-app.web.app`)
- **cleanUrls**: Убирает `.html` из URL (`/privacy-policy` вместо `/privacy-policy.html`)
- **trailingSlash**: Без слеша в конце URL

---

## Firebase Hosting Site

Проект `aichecklists-40230` имеет дополнительный hosting site:

| Site | Домен | Назначение |
|------|-------|------------|
| aichecklists-40230 (default) | aichecklists-40230.web.app | Основной (не используется) |
| gisti-app | gisti-app.web.app | Legal pages |

### Создание нового site

```bash
firebase hosting:sites:create <site-name> --project aichecklists-40230
```

### Список sites

```bash
firebase hosting:sites:list --project aichecklists-40230
```

---

## Контактный email

В обеих страницах указан: **churaevanton@gmail.com**

Для изменения отредактируйте файлы в `hosting/public/` и переразверните.

---

## Содержимое страниц

### Privacy Policy включает:
- Сбор данных (device ID, analytics)
- Firebase Analytics & Crashlytics
- RevenueCat для подписок
- Google Gemini AI для анализа контента
- Права пользователей
- Детская политика (13+)

### Terms of Use включает:
- Описание сервиса (Create, Fill, Export)
- Правила использования
- Подписки и оплата ($1.99/мес, 3-day trial)
- AI-генерированный контент (disclaimer)
- Ограничение ответственности

---

## Обновление страниц

1. Отредактируйте HTML файлы в `hosting/public/`
2. Запустите деплой:
   ```bash
   cd hosting
   firebase deploy --only hosting --project aichecklists-40230
   ```
3. Проверьте изменения на https://gisti-app.web.app

---

## Стоимость

**$0/месяц** — Firebase Hosting бесплатен для:
- 10 GB storage
- 360 MB/day transfer
- Custom domain (SSL включён)

Для статических legal pages этого более чем достаточно.

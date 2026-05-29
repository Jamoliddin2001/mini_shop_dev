# Mini Shop

> Учебный мини интернет-магазин: Spring Boot + PostgreSQL (backend), React + TypeScript + Redux Toolkit (frontend — каталог, корзина, заказы и админка, см. [Frontend](docs/frontend.md)).

Backend предоставляет REST API для каталога товаров, аутентификации (JWT), серверной корзины и
оформления заказов. Схема БД управляется только через Flyway, входные данные валидируются,
ошибки отдаются в едином формате.

## Запуск через Docker (одной командой)

Весь стек (PostgreSQL + backend + frontend) поднимается одной командой:

```bash
docker compose up --build
```

Затем откройте **http://localhost:8081** — это frontend (nginx), который отдаёт SPA и
проксирует `/api` на backend (same-origin, без CORS). Backend на хост не публикуется.

- **`.env` не обязателен** — в `docker-compose.yml` заданы безопасные dev-дефолты, поэтому
  стек работает «из коробки». Для своих значений: `cp .env.example .env` и заполните.
- **Сиды применяются автоматически** через Flyway при старте backend: админ
  (`admin@shop.local` / `Admin123!`), 4 категории и 6 товаров. Под ними можно сразу логиниться.
- Чистый перезапуск с нуля (сброс данных БД): `docker compose down -v && docker compose up --build`.

> ⚠️ **JWT_SECRET.** В compose зашит **dev-only** ключ, чтобы стек поднимался без настройки.
> В проде **обязательно** задайте собственный `JWT_SECRET` (base64, ≥ 256 бит) через `.env`.

## Локальный запуск (без Docker)

```bash
# 1. Поднять только PostgreSQL
cp .env.example .env          # заполнить POSTGRES_PASSWORD, DB_PASSWORD, JWT_SECRET
docker compose up -d postgres

# 2. Собрать и запустить backend (ВАЖНО: JDK 21 — см. ниже)
cd backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw spring-boot:run
```

> ⚠️ **Сборка требует JDK 21.** На JDK 26 annotation-processor Lombok падает с
> `Fatal error compiling: ... TypeTag :: UNKNOWN`. Подробности — в [Getting Started](docs/getting-started.md).

## Возможности

- **Каталог товаров** — публичный просмотр, фильтрация по названию (частичное совпадение),
  категории и диапазону цены, пагинация и сортировка.
- **Ролевой доступ** — создание/редактирование/удаление товаров только для `ADMIN`; просмотр — всем.
- **JWT-аутентификация** — stateless, роли `ADMIN`/`USER`, BCrypt для паролей.
- **Изображения как URL** — товар хранит `imageUrl` (не файловую загрузку) — осознанный выбор для
  мини-версии, см. [API Reference → Товары](docs/api.md#товары-products).
- **Серверная корзина** — одна на пользователя; добавление суммирует количество, сумма по текущей цене.
- **Оформление заказа** — checkout из корзины в одной транзакции с **фиксацией цены (snapshot)** и
  очисткой корзины; параллельные заказы сериализуются блокировкой корзины. См.
  [API Reference → Заказы](docs/api.md#заказы-orders).
- **Доменное событие `OrderCreated`** — обрабатывается асинхронно после коммита (заглушка
  уведомления). *В проде это ушло бы в брокер сообщений (Kafka/RabbitMQ/SQS).*
- **Единый формат ошибок** — `ApiError` через `@RestControllerAdvice`; 400/401/403/404/409/500.
- **Защита от N+1** — fetch-join / `@EntityGraph` для категории, корзины и строк заказа; фильтры — через JPA Specifications.

## Пример

```bash
# Публичный каталог: книги в диапазоне 30–50, по возрастанию цены
curl "http://localhost:8080/api/products?categoryId=2&minPrice=30&maxPrice=50&sort=price,asc"
```

```json
{
  "content": [
    { "id": 3, "name": "Clean Code", "price": 34.00, "categoryName": "Books", "imageUrl": "https://example.com/img/cleancode.jpg" }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

---

## Observability (наблюдаемость)

Включён Spring Boot Actuator с **минимальной** экспозицией — только то, что нужно для эксплуатации,
без лишней поверхности атаки:

```yaml
management.endpoints.web.exposure.include: health,info,metrics
management.endpoint.health.show-details: when_authorized   # детали health — только авторизованным
management.endpoint.health.probes.enabled: true            # liveness/readiness для k8s
```

- `GET /actuator/health` — публичный (liveness/readiness), без раскрытия внутренностей анонимам.
- `GET /actuator/metrics` и `GET /actuator/info` — закрыты JWT (требуют аутентификации).

**Correlation id.** Каждый ответ содержит заголовок `X-Request-Id` (входящий сохраняется, иначе
генерируется UUID). Тот же id попадает в каждую строку лога через `%X{requestId}` — по нему сшиваются
все логи одного запроса, включая асинхронный обработчик `OrderCreated`.

### Ключевые метрики под нагрузкой и зачем

| Метрика | Что показывает / зачем под нагрузкой |
|---------|--------------------------------------|
| `http.server.requests` (count, max, p95/p99 по `uri`,`status`,`method`) | Главный SLI: латентность и доля ошибок по эндпоинтам. Рост p99 и доли 5xx — первый признак деградации. |
| `hikaricp.connections.active` / `.pending` / `.acquire` (timer) | Насыщение пула соединений к БД. Растущий `pending`/`acquire` = запросы ждут коннект — ранний индикатор узкого места раньше, чем вырастет общая латентность. |
| `jvm.memory.used` (heap), `jvm.gc.pause` | Давление по памяти и паузы GC. Долгие/частые паузы под нагрузкой бьют по хвостовой латентности (p99). |
| `cache.gets{result=hit|miss}`, `cache.size` (`productList`) | Эффективность кэша каталога (включён `recordStats`). Низкий hit-ratio под нагрузкой = кэш не помогает (слишком короткий TTL/частые записи) — повод пересмотреть стратегию. |
| `executor.*` (пул `order-event-`), `tomcat.threads.busy` / `.config.max` | Насыщение потоков: HTTP-воркеров Tomcat и ограниченного async-пула доменных событий. `busy ≈ max` = очередь запросов растёт. |
| `system.cpu.usage`, `process.cpu.usage`, `process.uptime` | Утилизация CPU и аптайм — базовый контекст для всего остального и детект рестартов. |

> Метрики отдаются в формате Micrometer; в проде их обычно скрейпит Prometheus
> (`micrometer-registry-prometheus` + эндпоинт `prometheus`) и визуализирует Grafana.

---

## Переменные окружения

Все секреты — только через окружение, не в коде. В compose у каждой переменной есть dev-дефолт.

| Переменная | Назначение | Пример | Обязательна в проде |
|------------|-----------|--------|---------------------|
| `POSTGRES_DB` | Имя БД | `minishop` | — (есть дефолт) |
| `POSTGRES_USER` | Пользователь БД | `minishop` | — |
| `POSTGRES_PASSWORD` | Пароль БД | `change-me` | ✅ |
| `POSTGRES_PORT` | Порт postgres на хосте | `5432` | — |
| `DB_URL` | JDBC URL для backend (в compose хост — `postgres`) | `jdbc:postgresql://postgres:5432/minishop` | — |
| `DB_USER` / `DB_PASSWORD` | Креды datasource | `minishop` | ✅ (пароль) |
| `JWT_SECRET` | Base64-ключ подписи JWT (≥ 256 бит) | `cp .env.example...` | ✅ **(переопределить dev-дефолт)** |
| `JWT_TTL` | TTL access-токена (ISO-8601) | `PT1H` | — |
| `SPRING_PROFILES_ACTIVE` | Профиль Spring (`prod`/`dev`) | `prod` | — |
| `CORS_ALLOWED_ORIGINS` | Разрешённые origin фронтенда | `http://localhost:8081` | — |
| `FRONTEND_PORT` | Порт фронтенда (nginx) на хосте | `8081` | — |

## Архитектура

Backend — слоистая архитектура с чёткими границами: **controller → service → domain → repository**.
DTO на границе API (Entity наружу не возвращается), маппинг — MapStruct. Схема БД — только через
Flyway; фильтры каталога — через JPA Specifications; защита от N+1 — fetch-join / `@EntityGraph`.

Топология в Docker:

```
браузер ──▶ frontend (nginx :8081) ──┬─▶ статика SPA
                                      └─▶ /api ──▶ backend (Spring Boot :8080) ──▶ postgres :5432
```

Frontend и backend — same-origin через nginx-прокси, поэтому браузер не делает кросс-доменных
запросов. Подробности (паттерны, границы модулей) — в [Architecture](docs/ARCHITECTURE.md).

## Схема БД

7 таблиц; деньги — `NUMERIC(12,2)`, время — `TIMESTAMPTZ`. Связи:
users **1—1** cart **1—N** cart_items; categories **1—N** products; users **1—N** orders **1—N** order_items.

| Таблица | Назначение | Ключевое |
|---------|-----------|----------|
| `users` | учётки + роль | `UNIQUE(email)`, `role ∈ {ADMIN,USER}`, BCrypt-хэш |
| `categories` | категории | `UNIQUE(name)` |
| `products` | товары | FK→categories `ON DELETE SET NULL`, индексы по category/price/created_at, GIN-trigram по name |
| `cart` | корзина (одна на юзера) | `UNIQUE(user_id)` |
| `cart_items` | позиции корзины | `UNIQUE(cart_id, product_id)`, `quantity > 0` |
| `orders` | заказы | `status ∈ {NEW,PAID,CANCELLED}`, `total_amount` |
| `order_items` | позиции заказа | **снимок** `product_name` + `unit_price` на момент покупки |

Полные таблицы полей и обоснование индексов — в [Architecture → Схема БД](docs/ARCHITECTURE.md#2-схема-базы-данных).

## Эндпоинты

Базовый префикс `/api`. Все списки пагинированы (`?page=&size=&sort=`), ошибки — единый `ApiError`.

| Группа | Эндпоинты | Доступ |
|--------|-----------|--------|
| **Auth** | `POST /auth/register`, `POST /auth/login`, `GET /me` | public / public / auth |
| **Products** | `GET /products`, `GET /products/{id}` · `POST/PUT/DELETE /products/{id}` | чтение — public, запись — ADMIN |
| **Categories** | `GET /categories` | public |
| **Cart** | `GET /cart`, `POST /cart/items`, `PATCH/DELETE /cart/items/{productId}`, `DELETE /cart` | владелец (auth) |
| **Orders** | `POST /orders`, `GET /orders`, `GET /orders/{id}` | владелец (auth) / ADMIN |

Полная таблица с кодами ответов и примерами — в [API Reference](docs/api.md).

## Принятые решения и риски

| Решение | Почему | Риск / компромисс |
|---------|--------|-------------------|
| Изображения как `image_url`, не файловая загрузка | KISS для мини-версии | нет валидации/хранилища картинок → в проде S3/MinIO + CDN |
| Серверная корзина, блокировка при checkout | один источник правды, сериализация параллельных заказов | блокировка корзины может стать узким местом под нагрузкой |
| Снимок цены (`unit_price`, `product_name`) в `order_items` | заказ не меняется при правке товара | дублирование данных — осознанное |
| `OrderCreated` обрабатывается асинхронно, in-process (заглушка) | развязка побочных эффектов | при падении инстанса событие теряется → в проде брокер с гарантиями доставки |
| JWT access-only, без refresh | простота мини-версии | короткая сессия / нет отзыва токена |
| Dev-дефолт `JWT_SECRET` в compose | стек поднимается «из коробки» | слабый ключ в репозитории → **обязательно** переопределить в проде |
| Поиск по `name` через `ILIKE` + GIN-trigram | хватает для мини | не масштабируется на большой каталог → Elasticsearch |
| Coverage-гейт намеренно низкий (0.60/0.50) | устойчивость к flaky-IT (Testcontainers) | фактическое покрытие ~92%/81%, но гейт мягкий |
| Same-origin nginx-прокси `/api` | нет CORS, хост backend не вшит в бандл | прокси-слой нужно учитывать при отладке сети |

Расширенный разбор — в [Architecture → Решения, риски, паттерны](docs/ARCHITECTURE.md#решения-риски-паттерны-итог-фазы-проектирования).

## Как масштабировал бы в проде

- **Кэш в Redis.** Кэш каталога/категорий и горячих карточек — общий для всех инстансов (вместо
  локального Caffeine), плюс хранилище для rate-limiting и refresh-токенов. Снимает read-нагрузку с PostgreSQL.
- **События в брокер сообщений.** `OrderCreated` → Kafka/RabbitMQ/SQS вместо in-process: надёжная
  асинхронная доставка (уведомления, склад, аналитика), развязка сервисов, переживает рестарт инстанса.
- **HTTPS + rate-limiting.** TLS-терминация на gateway/ingress (HSTS уже включён в prod-профиле),
  троттлинг по IP/JWT на шлюзе для защиты от перебора и DoS.
- **Репликация БД.** Primary/replica с read-репликами под read-heavy каталог, пул соединений через
  PgBouncer, регулярные бэкапы/PITR.
- **Горизонтальное масштабирование.** Backend stateless (JWT, без серверных сессий) → несколько
  инстансов за балансировщиком; вынесенный в Redis кэш делает их полностью взаимозаменяемыми.

## Документация

| Руководство | Описание |
|-------------|----------|
| [Getting Started](docs/getting-started.md) | Требования (JDK 21), сборка, запуск, переменные окружения, тесты |
| [API Reference](docs/api.md) | Эндпоинты товаров/категорий/auth, примеры, коды ответов |
| [Architecture](docs/ARCHITECTURE.md) | Структура проекта, схема БД, паттерны проектирования |
| [Frontend](docs/frontend.md) | React/TS/Redux/RTK Query: каталог, корзина, заказы, админка, auth-слой, тесты |

## Технологии

Java 21 · Spring Boot 3.4 · Spring Security (JWT) · Spring Data JPA · MapStruct · Flyway ·
PostgreSQL 16 · Testcontainers · Maven.

## Лицензия

Учебный проект.

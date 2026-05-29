[← API Reference](api.md) · [Назад к README](../README.md) · [Frontend →](frontend.md)

# Архитектура: Мини интернет-магазин

> Документ проектирования. Реализация начинается только после явного «ок».
> Стек: Java + Spring Boot + PostgreSQL (backend), React + TypeScript + Redux Toolkit + Tailwind (frontend).

## 1. Структура монорепозитория

```
mini-shop/
├── backend/                         # Spring Boot приложение
│   ├── src/main/java/com/shop/
│   │   ├── ShopApplication.java
│   │   ├── config/                  # SecurityConfig, CorsConfig, OpenApiConfig, JwtConfig
│   │   ├── common/                  # базовые DTO ошибок, утилиты, PageResponse<T>
│   │   ├── security/                # JwtTokenProvider, JwtAuthFilter, UserDetails, @PreAuthorize
│   │   ├── error/                   # GlobalExceptionHandler (@RestControllerAdvice), ApiError, исключения
│   │   ├── auth/                    # controller / service / dto  (register, login)
│   │   ├── user/                    # controller(*) / service / domain(User) / repository / mapper
│   │   ├── product/                 # controller / service / domain(Product, Category) / repository / mapper / dto
│   │   ├── cart/                    # controller / service / domain(Cart, CartItem) / repository / mapper / dto
│   │   └── order/                   # controller / service / domain(Order, OrderItem) / repository / mapper / dto
│   ├── src/main/resources/
│   │   ├── application.yml          # профили: default, dev, prod (всё чувствительное — через ENV)
│   │   └── db/migration/            # Flyway: V1__init.sql, V2__seed_admin.sql, ...
│   ├── src/test/java/com/shop/      # unit-тесты сервисов + интеграционные на Testcontainers
│   ├── pom.xml                      # (или build.gradle)
│   └── Dockerfile                   # multi-stage (build -> slim runtime JRE)
│
├── frontend/                        # React + Vite + TS
│   ├── src/
│   │   ├── app/                     # store (Redux Toolkit), хуки, роутер
│   │   ├── features/                # auth/ products/ cart/ orders/ admin/  (slice + api + components)
│   │   ├── shared/                  # ui-kit, api-client (axios + interceptor JWT), types, utils
│   │   ├── pages/                   # Login, Register, Catalog, Cart, Checkout, Admin
│   │   └── main.tsx
│   ├── public/
│   ├── package.json
│   ├── tailwind.config.js
│   ├── vite.config.ts
│   └── Dockerfile                   # multi-stage (build -> nginx)
│
├── docs/
│   └── ARCHITECTURE.md              # этот документ
├── docker-compose.yml               # postgres + backend + frontend
├── .env.example                     # шаблон переменных окружения (без значений)
├── .gitignore
└── README.md                        # запуск, переменные окружения, раздел "Дальнейшее масштабирование"
```

(*) `user/controller` — опционально (профиль текущего пользователя `GET /api/me`); CRUD пользователей в «мини» не делаем.

## 2. Схема базы данных

Все таблицы создаются и изменяются **только** через Flyway. Денежные значения — `NUMERIC(12,2)` (не float). Временные метки — `TIMESTAMPTZ`.

### users
| Поле | Тип | Ограничения |
|------|-----|-------------|
| id | BIGSERIAL | PK |
| email | VARCHAR(255) | NOT NULL, UNIQUE |
| password_hash | VARCHAR(255) | NOT NULL (BCrypt) |
| role | VARCHAR(20) | NOT NULL, CHECK (role IN ('ADMIN','USER')) |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT now() |

- `UNIQUE(email)` — логин по email + защита от дублей.

### categories
| Поле | Тип | Ограничения |
|------|-----|-------------|
| id | BIGSERIAL | PK |
| name | VARCHAR(100) | NOT NULL, UNIQUE |

### products
| Поле | Тип | Ограничения |
|------|-----|-------------|
| id | BIGSERIAL | PK |
| name | VARCHAR(255) | NOT NULL |
| description | TEXT | NULL |
| price | NUMERIC(12,2) | NOT NULL, CHECK (price >= 0) |
| image_url | VARCHAR(1024) | NULL |
| category_id | BIGINT | NULL, FK -> categories(id) ON DELETE SET NULL |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT now() |

### cart  (одна корзина на пользователя)
| Поле | Тип | Ограничения |
|------|-----|-------------|
| id | BIGSERIAL | PK |
| user_id | BIGINT | NOT NULL, UNIQUE, FK -> users(id) ON DELETE CASCADE |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT now() |

### cart_items
| Поле | Тип | Ограничения |
|------|-----|-------------|
| id | BIGSERIAL | PK |
| cart_id | BIGINT | NOT NULL, FK -> cart(id) ON DELETE CASCADE |
| product_id | BIGINT | NOT NULL, FK -> products(id) ON DELETE CASCADE |
| quantity | INT | NOT NULL, CHECK (quantity > 0) |
| | | UNIQUE (cart_id, product_id) |

### orders
| Поле | Тип | Ограничения |
|------|-----|-------------|
| id | BIGSERIAL | PK |
| user_id | BIGINT | NOT NULL, FK -> users(id) ON DELETE RESTRICT |
| status | VARCHAR(20) | NOT NULL DEFAULT 'NEW', CHECK (status IN ('NEW','PAID','CANCELLED')) |
| total_amount | NUMERIC(12,2) | NOT NULL, CHECK (total_amount >= 0) |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT now() |

### order_items  (снимок цены на момент заказа)
| Поле | Тип | Ограничения |
|------|-----|-------------|
| id | BIGSERIAL | PK |
| order_id | BIGINT | NOT NULL, FK -> orders(id) ON DELETE CASCADE |
| product_id | BIGINT | NOT NULL, FK -> products(id) ON DELETE RESTRICT |
| product_name | VARCHAR(255) | NOT NULL (снимок) |
| unit_price | NUMERIC(12,2) | NOT NULL (снимок цены на момент покупки) |
| quantity | INT | NOT NULL, CHECK (quantity > 0) |

**Связи:** users 1—1 cart; cart 1—N cart_items; product 1—N cart_items; users 1—N orders; orders 1—N order_items; categories 1—N products.

### Ключевые индексы и обоснование
| Индекс | Обоснование |
|--------|-------------|
| `idx_products_category_id` (category_id) | Фильтр «по категории» — самый частый запрос каталога; ускоряет JOIN/WHERE. |
| `idx_products_name_trgm` (GIN, pg_trgm на lower(name)) | Поиск по названию через `ILIKE '%...%'`; B-tree не помогает при поиске в середине строки, GIN+trigram — помогает. |
| `idx_products_price` (price) | Фильтр/сортировка по диапазону цены (`price BETWEEN`). |
| `idx_products_created_at` (created_at DESC) | Сортировка каталога «сначала новые» + пагинация по умолчанию. |
| `idx_cart_items_cart_id` (cart_id) | Сбор корзины пользователя без полного скана. |
| `idx_order_items_order_id` (order_id) | Загрузка позиций заказа. |
| `idx_orders_user_id` (user_id) | Список заказов пользователя. |
| UNIQUE `users(email)`, `cart(user_id)`, `cart_items(cart_id, product_id)` | Целостность + ускорение точечных выборок. |

> Примечание: для комбинированного фильтра (категория + цена) при росте данных можно ввести составной индекс `(category_id, price)`; в «мини» оставляем отдельные индексы — планировщик PostgreSQL комбинирует их через BitmapAnd.

## 3. REST-эндпоинты

Базовый префикс `/api`. Формат ошибок — единый (`ApiError`). Все списки — пагинированные (`?page=&size=&sort=`).

### Auth (публичные)
| Метод | Путь | Роль | Коды ответов |
|-------|------|------|--------------|
| POST | /api/auth/register | public | 201, 400 (валидация), 409 (email занят) |
| POST | /api/auth/login | public | 200 (+JWT), 400, 401 |
| GET | /api/me | USER/ADMIN | 200, 401 |

### Products
| Метод | Путь | Роль | Коды ответов |
|-------|------|------|--------------|
| GET | /api/products?name=&categoryId=&minPrice=&maxPrice=&page=&size=&sort= | public | 200, 400 |
| GET | /api/products/{id} | public | 200, 404 |
| POST | /api/products | ADMIN | 201, 400, 401, 403 |
| PUT | /api/products/{id} | ADMIN | 200, 400, 401, 403, 404 |
| DELETE | /api/products/{id} | ADMIN | 204, 401, 403, 404 |
| GET | /api/categories | public | 200 |

### Cart (только владелец, USER/ADMIN)
| Метод | Путь | Роль | Коды ответов |
|-------|------|------|--------------|
| GET | /api/cart | auth | 200, 401 |
| POST | /api/cart/items  `{productId, quantity}` | auth | 200/201, 400, 401, 404 |
| PATCH | /api/cart/items/{productId}  `{quantity}` | auth | 200, 400, 401, 404 |
| DELETE | /api/cart/items/{productId} | auth | 204, 401, 404 |
| DELETE | /api/cart | auth | 204, 401 (очистить корзину) |

### Orders
| Метод | Путь | Роль | Коды ответов |
|-------|------|------|--------------|
| POST | /api/orders | auth | 201 (создан из корзины, корзина очищена), 400 (пустая корзина), 401 |
| GET | /api/orders | auth | 200 (заказы текущего пользователя) |
| GET | /api/orders/{id} | auth (владелец) / ADMIN | 200, 401, 403, 404 |

Стандартные ошибочные коды: 400 — невалидный ввод; 401 — нет/невалидный токен; 403 — роль не позволяет; 404 — ресурс не найден; 409 — конфликт (дубль email); 500 — внутренняя (логируется, наружу — общий формат без деталей).

## 4. Паттерны проектирования

| Паттерн | Где | Зачем |
|---------|-----|-------|
| **Layered architecture** (controller→service→domain→repository) | весь backend | чёткие границы (SRP), тестируемость, соответствие CLAUDE.md. |
| **DTO + Mapper (MapStruct)** | границы API | Entity не утекает наружу; разделение модели хранения и контракта API. |
| **Repository (Spring Data JPA)** | persistence | абстракция доступа к данным, готовые пагинация/сортировка. |
| **Specification (JPA Criteria)** | фильтр товаров | динамические фильтры (name/category/price) без «лапши» из if-ов и без N методов репозитория. |
| **Strategy** (через Spring Security) | JwtAuthFilter / провайдеры | аутентификация инкапсулирована, легко расширить. |
| **Factory/Builder** | сборка Order из Cart, ApiError | инкапсуляция создания агрегата заказа (снимок цен) в одном месте. |
| **Singleton** (Spring beans) | сервисы/конфиги | управление жизненным циклом — фреймворком, не вручную. |
| **Global Handler (@RestControllerAdvice)** | error/ | единый формат ошибок, отказоустойчивость, no leaking стек-трейсов. |
| Frontend: **Container/Presentational + Redux slices + RTK Query** | features/ | разделение состояния и UI, кэш запросов, предсказуемый state. |

Применяем паттерны только где оправдано (KISS): например, Specification — потому что фильтров несколько и они комбинируются; иначе хватило бы метода репозитория.

## 5. Сознательно вне «мини» версии (раздел README → «Дальнейшее масштабирование»)

**Не включаем сейчас (и почему это ок для мини):**
- Платёжный шлюз — заказ имеет статус `NEW`, оплата — заглушка.
- Реальная загрузка файлов изображений — храним `image_url` (можно внешний URL).
- Управление складскими остатками / резервирование.
- Refresh-токены, сброс пароля, email-верификация — только access-JWT.
- Управление пользователями админом, аудит-лог, soft-delete.
- Множественные корзины, избранное, отзывы, скидки.

**Как масштабировалось бы в проде:**
- **Кэш (Redis):** кэширование каталога/категорий и горячих карточек товаров; rate-limiting; хранение сессий/refresh-токенов. Снижает нагрузку на PostgreSQL на read-heavy каталоге.
- **Брокер сообщений (Kafka/RabbitMQ):** события `OrderCreated` → асинхронно: уведомления, обновление склада, аналитика. Развязывает сервисы, повышает отказоустойчивость.
- **Хранилище объектов (S3/MinIO):** реальная загрузка изображений + CDN.
- **Поиск (Elasticsearch/OpenSearch):** полнотекстовый поиск и фасетные фильтры при большом каталоге вместо ILIKE.
- **Наблюдаемость:** Prometheus + Grafana, централизованные логи (ELK), distributed tracing (OpenTelemetry).
- **Платежи:** интеграция Stripe/эквайринг через отдельный модуль с idempotency-ключами.
- **Декомпозиция:** при росте — выделение order/catalog в отдельные сервисы за API Gateway.

---

## Решения, риски, паттерны (итог фазы проектирования)

**Принятые решения:**
- Введена таблица `categories` (нормализация) — потому что ТЗ требует фильтр по категории.
- Цены — `NUMERIC`, снимок цены в `order_items` — заказ не должен меняться при правке товара.
- Корзина 1—1 с пользователем, `UNIQUE(cart_id, product_id)` — позиции не дублируются, а инкрементят quantity.
- Динамический фильтр товаров — через JPA Specification (а не множество методов репозитория).

**Риски:**
- Поиск по `name` через `ILIKE` не масштабируется → смягчаем GIN/trigram-индексом; в проде — Elasticsearch.
- `ON DELETE` стратегии у заказов (`RESTRICT`) могут мешать удалению товара админом → нужно бизнес-правило (мягкое снятие с продажи вместо удаления) — отмечено как масштабирование.
- JWT без refresh — компромисс «мини»; явно зафиксирован.

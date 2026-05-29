[← Architecture](ARCHITECTURE.md) · [Назад к README](../README.md)

# Frontend

SPA на **React + TypeScript + Vite + Redux Toolkit + RTK Query + TailwindCSS**, каталог `frontend/`.
Структура по фичам, единый store и слой данных, аутентификация с защитой роутов и тема — плюс
**бизнес-страницы магазина**: каталог с фильтрами, серверная корзина, оформление и история заказов,
админ-панель управления товарами.

## Содержание

- [Быстрый старт](#быстрый-старт)
- [Структура проекта](#структура-проекта)
- [Слой данных и состояние](#слой-данных-и-состояние)
- [Бизнес-страницы](#бизнес-страницы)
- [Обработка состояний и типизация](#обработка-состояний-и-типизация)
- [Поток аутентификации](#поток-аутентификации)
- [Конфигурация](#конфигурация)
- [Тесты](#тесты)
- [Решение проблем](#решение-проблем)

## Быстрый старт

**Требования:** Node ≥ 20. Backend должен быть запущен (по умолчанию `http://localhost:8080`),
см. [Getting Started](getting-started.md).

```bash
cd frontend
npm install

# окружение: скопировать пример и при необходимости поправить адрес API
cp .env.example .env.local        # VITE_API_BASE_URL=http://localhost:8080/api

npm run dev        # dev-сервер на http://localhost:5173 (этот origin уже в CORS backend)
```

| Скрипт | Назначение |
|--------|-----------|
| `npm run dev` | Vite dev-сервер с HMR на порту **5173** (совпадает с `CORS_ALLOWED_ORIGINS` backend). |
| `npm run build` | Прод-сборка (`tsc -b` + `vite build`) в `dist/`. |
| `npm run preview` | Локальный предпросмотр прод-сборки. |
| `npm run test` | Запуск тестов (Vitest, разово). `npm run test:watch` — watch-режим. |
| `npm run coverage` | Тесты с покрытием (v8). |
| `npm run lint` | ESLint по всему проекту. |

## Структура проекта

Организация **по фичам** (feature-based), а не по техническим слоям:

```
frontend/src/
├─ app/                      # каркас приложения
│  ├─ store.ts               # configureStore + makeStore(preloadedState) (фабрика для тестов)
│  ├─ hooks.ts               # типизированные useAppDispatch / useAppSelector
│  ├─ router.tsx             # createBrowserRouter: публичные / приватные / admin-роуты
│  ├─ layout/AppLayout.tsx   # шапка с состоянием auth + <Outlet/>
│  └─ api/baseApi.ts         # RTK Query: JWT-инъекция + обработка 401
├─ features/
│  ├─ auth/                  # сессия, guard'ы, страницы входа/регистрации
│  │  ├─ authSlice.ts        # клиентская сессия: token, user + селекторы
│  │  ├─ authApi.ts          # endpoints: login / register / getMe
│  │  ├─ useAuthBootstrap.ts # гидрация user через /me при перезагрузке
│  │  ├─ guards/             # PrivateRoute, RoleRoute
│  │  └─ pages/              # LoginPage, RegisterPage
│  ├─ products/
│  │  ├─ productsApi.ts      # list (фильтры+пагинация) / get / create / update / delete + categories
│  │  ├─ components/ProductForm.tsx   # форма создания/редактирования (встроенная панель)
│  │  └─ pages/              # CatalogPage (публичный), AdminProductsPage (ADMIN)
│  ├─ cart/
│  │  ├─ cartApi.ts          # getCart / addToCart / removeFromCart
│  │  └─ pages/CartPage.tsx
│  └─ orders/
│     ├─ ordersApi.ts        # createOrder / listOrders / getOrder
│     ├─ components/OrderStatusBadge.tsx
│     └─ pages/              # OrdersPage (история), OrderDetailPage (детали + подтверждение)
├─ shared/
│  ├─ lib/                   # logger (env-gated), tokenStorage, apiError, format, useDebouncedValue
│  ├─ types/api.ts           # типы всего контракта backend (Product, Category, Cart, Order, PageResponse…)
│  └─ ui/                    # QueryState (loading/error/empty), Pagination, PlaceholderPage
└─ test/                     # setupTests, renderWithProviders, mockFetch
```

Алиас импортов `@/*` → `src/*` (настроен в `tsconfig` и `vite.config.ts`).

## Слой данных и состояние

Два разных хранилища с чёткими границами ответственности:

- **RTK Query (`app/api/baseApi.ts`) — серверный кэш.** Один `baseApi`; каждая фича добавляет свои
  endpoints через `injectEndpoints` (`productsApi`, `cartApi`, `ordersApi`, `authApi`). `baseQuery`
  инжектит `Authorization: Bearer <token>` из стора; обёртка-декоратор ловит `401` и сбрасывает
  сессию. Мутации синхронизируют кэш декларативно через теги (`Products/Cart/Orders/Me`).
- **`authSlice` — клиентская сессия.** Хранит `token` и `user` (email/роль); это состояние сессии,
  а не серверные данные, поэтому ему место в обычном слайсе, а не в RTK Query.

### Endpoints и инвалидация тегов

| Фича | Endpoints | Теги |
|------|-----------|------|
| `productsApi` | `listProducts(filter)`, `getProduct`, `createProduct`, `updateProduct`, `deleteProduct`, `listCategories` | читают/`providesTags: ['Products']`; мутации `invalidatesTags: ['Products']` |
| `cartApi` | `getCart`, `addToCart`, `removeFromCart` | `getCart` даёт `['Cart']`; мутации `invalidatesTags: ['Cart']` |
| `ordersApi` | `createOrder`, `listOrders`, `getOrder` | `createOrder` → `invalidatesTags: ['Cart','Orders']` |

Инвалидация — это и есть «магия» синхронизации без ручного кода: после оформления заказа один тег
`['Cart','Orders']` опустошает корзину и обновляет историю; после admin-CRUD `['Products']`
перезагружает каталог. `listProducts` отбрасывает пустые параметры фильтра и подставляет
сортировку по умолчанию `createdAt,desc`, чтобы не слать backend пустые/некорректные значения.

### Почему RTK Query, а не ручные thunks

Магазин — это по сути **серверный кэш на экране** (каталог, корзина, заказы: fetch → cache →
invalidate), ровно та задача, под которую сделан RTK Query. Он убирает рутину (`loading/error/data`
в каждом слайсе), даёт декларативную инвалидацию через теги (после checkout —
`invalidatesTags: ['Cart','Orders']`), дедупликацию и хуки, и **входит в `@reduxjs/toolkit`** —
без новых зависимостей (axios не понадобился). Ручные thunks были бы оправданы для сложных
не-CRUD сценариев; у нас типовой CRUD, где RTK Query снимает максимум кода при минимуме риска.

## Бизнес-страницы

| Страница | Маршрут | Доступ | Что делает |
|----------|---------|--------|------------|
| Каталог | `/` | public | Список товаров с серверными фильтрами (название, категория, диапазон цены) и пагинацией. Кнопка «В корзину» — только для аутентифицированных; аноним видит призыв войти. |
| Корзина | `/cart` | USER/ADMIN | Строки серверной корзины, удаление позиций, итоговая сумма, кнопка «Оформить заказ». |
| Подтверждение / детали заказа | `/orders/:id` | USER/ADMIN | Состав заказа, статус, сумма. При переходе сразу после оформления — баннер «Заказ оформлен, корзина очищена». |
| История заказов | `/orders` | USER/ADMIN | Пагинированный список заказов (сводки) со ссылкой на детали. |
| Управление товарами | `/admin/products` | ADMIN | Таблица товаров + создание/редактирование/удаление. |

### Оформление заказа

`POST /api/orders` идёт **без тела** — заказ собирается из серверной корзины, и при успехе корзина
очищается на сервере. Поэтому «форма оформления» на фронте — это **экран подтверждения/обзора**, а не
ввод адреса/оплаты (таких полей в контракте backend нет). Поток:

```
CartPage «Оформить заказ» → createOrder() (POST /orders, без тела)
   → invalidatesTags ['Cart','Orders'] (корзина пустеет, история обновляется)
   → navigate('/orders/:id', { state: { justCreated: true } })
   → OrderDetailPage: баннер подтверждения + состав заказа
пустая корзина → 400 «Cart is empty» → сообщение на CartPage
```

### Сокрытие админ-функций (defense in depth)

Админские действия скрыты на UI **двумя** способами: маршрут `/admin/products` обёрнут
`RoleRoute role="ADMIN"`, а ссылка «Админка» в шапке рендерится только при `user.role === 'ADMIN'`.
Но **сокрытие на UI — это UX, а не безопасность**: источник истины — backend, который отвечает
`401/403` на запись без админ-роли. Клиент полагается на эту серверную проверку, а не подменяет её.

## Обработка состояний и типизация

**Все ответы API типизированы** в `shared/types/api.ts` — типы зеркалят DTO-записи backend
(`Product`, `Category`, `Cart`, `Order`, `OrderSummary`, `PageResponse<T>`, `OrderStatus`).
Денежные поля (`BigDecimal` → JSON-число) только **форматируются** через `shared/lib/format.ts`
(`formatPrice`/`formatDate`); арифметику сумм делает backend, фронт не пересчитывает деньги.

**Состояния загрузки / ошибки / пустого списка** обрабатываются единым компонентом
`shared/ui/QueryState.tsx`: он принимает результат RTK Query и рендерит спиннер, сообщение об
ошибке (через `parseApiError`, с кнопкой «Повторить») или пустое состояние — иначе содержимое.
Каждая страница пропускает свой запрос через `QueryState`, поэтому три состояния выглядят одинаково
и логика ветвления не дублируется. Списки используют `shared/ui/Pagination.tsx` поверх
`PageResponse`, а поиск в каталоге — `useDebouncedValue`, чтобы не слать запрос на каждое нажатие.

## Поток аутентификации

```
LoginPage → useLoginMutation → POST /api/auth/login
   → AuthResponse { accessToken, email, role }
   → setCredentials: token+user в стор И в localStorage (ключ shop_token)
   → редирект на исходную страницу (location.state.from) или /

перезагрузка страницы:
   token восстановлен из localStorage, user = null
   → useAuthBootstrap → GET /api/me → setUser (гидрация принципала)
   → протухший токен → 401 → автоматический logout

logout / любой 401:
   baseQuery (на 401) или кнопка «Выйти» → logout: чистит стор + localStorage
   → PrivateRoute уводит на /login
```

**Guard'ы роутинга:**

| Guard | Поведение |
|-------|-----------|
| `PrivateRoute` | Нет токена → редирект на `/login` (запоминает `from`). |
| `RoleRoute role="ADMIN"` | Не та роль → редирект на `/` (не показываем существование admin-ресурсов — как и backend). |

**Refresh-token нет.** Backend выдаёт один короткоживущий access-token (TTL `PT1H`, см.
[Getting Started](getting-started.md)). Поэтому реакция на `401` — единственно честная: сбросить
сессию и отправить на вход, без попыток silent-refresh.

## Конфигурация

| Переменная | Значение | Назначение |
|-----------|----------|-----------|
| `VITE_API_BASE_URL` | `http://localhost:8080/api` | Базовый URL backend API. Только `VITE_*`-переменные попадают в бандл. |

Секретов на фронте нет — JWT получается в рантайме через `/api/auth/login`, в сборку не зашивается.

**Хранение токена.** Токен лежит в `localStorage` под ключом `shop_token` — переживает перезагрузку.
Осознанный компромисс: localStorage доступен из JS, то есть уязвим к XSS (украденный токен живёт до
истечения TTL). Смягчение: экранирование React по умолчанию, отказ от `dangerouslySetInnerHTML`,
короткий TTL. Путь усиления для прода — httpOnly + SameSite cookie и refresh-token на backend.

## Тесты

**Vitest + React Testing Library + happy-dom.** Покрыты каркас, auth и бизнес-логика (41 тест):

- **Каркас и auth:** `authSlice`+`tokenStorage` (синхронизация localStorage, bootstrap);
  `baseQueryWithReauth` (`401`→`logout`, инжекция `Bearer`); `PrivateRoute`/`RoleRoute`; `LoginPage`.
- **Data-слой:** `productsApi` (сборка query-параметров фильтра, дефолтная сортировка);
  `ordersApi` (checkout — `POST /orders` без тела, параметры пагинации).
- **Каталог:** рендер товаров, фильтр по названию (debounce), пустое и ошибочное состояния,
  кнопка «В корзину» только для аутентифицированного.
- **Корзина и оформление:** строки и итог, удаление (`DELETE`), успешный checkout → редирект на
  подтверждение, ошибка «Cart is empty».
- **Заказы:** история (список + пустое состояние), детали + баннер `justCreated`.
- **Админка и роли:** CRUD товаров, подсветка поля по `400`-`violations`, удаление с подтверждением;
  `RoleRoute` уводит USER с `/admin/products`, ссылка «Админка» скрыта для USER.

Хелперы в `src/test/`: `renderWithProviders` оборачивает компонент в Redux-store (через `makeStore`
с `preloadedState`) и роутер; `mockFetch` (`installFetch` + `jsonResponse`) подменяет глобальный
`fetch` — транспорт RTK Query (MSW не используется). Фичевые тесты импортируют свой `*Api`-модуль
до dispatch, так как `injectEndpoints` срабатывает как побочный эффект импорта.

## Решение проблем

| Симптом | Причина и решение |
|---------|-------------------|
| Тесты RTK Query падают с `Expected signal to be an instance of AbortSignal` | `AbortSignal` из **jsdom** несовместим с нативным `Request` Node (undici). Тестовая среда — **`happy-dom`**, а не jsdom (задано в `vite.config.ts`). |
| Конфликт типов `vite` плагина в тестах | Нужен **Vitest v3+**: vitest 2 тянул свой `vite@5`, конфликт с `vite@6` приложения. |
| `localStorage … is not a function` в тестах | Node 25 имеет экспериментальный глобальный Web Storage, перекрывающий env. `setupTests.ts` ставит детерминированный in-memory `Storage`. |
| Запросы уходят на `undefined/...` в тестах | Тесты не грузят `.env`; `VITE_API_BASE_URL` задан в `vite.config.ts → test.env`. |
| Запросы блокируются CORS | Dev-сервер должен быть на порту **5173** (значение в `CORS_ALLOWED_ORIGINS` backend). |

## See Also

- [API Reference](api.md) — контракт backend, который потребляет фронт (auth, товары, корзина, заказы).
- [Architecture](ARCHITECTURE.md) — общая архитектура и решения проекта.
- [Getting Started](getting-started.md) — запуск backend, JDK 21, переменные окружения, JWT TTL.

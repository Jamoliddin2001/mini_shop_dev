[Назад к README](../README.md) · [API Reference →](api.md)

# Getting Started

Сборка и запуск backend локально.

## Требования

| Инструмент | Версия | Примечание |
|------------|--------|------------|
| JDK | **21** | Обязательно. Maven-сборка использует Lombok + MapStruct annotation processors. |
| Docker | любой свежий | Для PostgreSQL и интеграционных тестов (Testcontainers). |
| Maven | — | Не нужен глобально, используется `./mvnw` (wrapper). |

> ⚠️ **Только JDK 21.** На JDK 26 сборка падает на этапе компиляции с
> `Fatal error compiling: java.lang.ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag :: UNKNOWN`.
> Это несовместимость Lombok с внутренним API `javac` более новых JDK. Если в системе несколько JDK,
> укажите 21 явно через `JAVA_HOME`:
>
> ```bash
> JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw verify   # macOS
> ```

## Переменные окружения

Скопируйте шаблон и заполните значения (секреты — только здесь, не в коде):

```bash
cp .env.example .env
```

| Переменная | Назначение | Пример |
|------------|------------|--------|
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | Учётка PostgreSQL для docker-compose | `minishop` / `minishop` / `<секрет>` |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | Datasource для Spring | `jdbc:postgresql://localhost:5432/minishop` |
| `JWT_SECRET` | Base64-секрет подписи JWT (≥256 бит) | сгенерировать, не переиспользовать |
| `JWT_TTL` | Время жизни access-токена (ISO-8601) | `PT1H` |
| `CORS_ALLOWED_ORIGINS` | Разрешённые origins фронтенда (не `*`) | `http://localhost:5173` |

Приложение не стартует с пустым `JWT_SECRET` — это намеренно (fail-fast вместо слабого ключа).

## Запуск

```bash
# 1. База данных
docker compose up -d postgres

# 2. Backend (профиль dev включает подробное логирование и SQL-трейс)
cd backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Flyway применит миграции `V1__init.sql` (схема + индексы), `V2__seed.sql` (категории/товары),
`V3__seed_admin.sql` (админ) автоматически при старте.

**Сид-админ для локальной разработки:** `admin@shop.local` / `Admin123!` (сменить в проде).

## Проверка

```bash
curl http://localhost:8080/actuator/health      # {"status":"UP"}
curl http://localhost:8080/api/products          # пагинированный каталог
```

## Тесты

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw verify
```

- Unit-тесты (Surefire) — сервисы и спецификации на Mockito, без контекста Spring.
- Интеграционные тесты (Failsafe, `*IT`) — реальный PostgreSQL через Testcontainers
  (нужен запущенный Docker). Покрывают CRUD, фильтры, роли, валидацию и отсутствие N+1.

## See Also

- [API Reference](api.md) — эндпоинты и примеры запросов
- [Architecture](ARCHITECTURE.md) — схема БД и паттерны

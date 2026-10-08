# PLAN — Fetchy: GUI-обёртка над yt-dlp (Kotlin/JVM + Compose Multiplatform)

Статус: принят, исполнение через subagent-driven development.
Спецификация (binding authority): `docs/SPEC.md`. Решения: `docs/DECISIONS.md`.

## Global Constraints (обязательны для всех задач)

1. Без shell: только `ProcessBuilder(List<String>)`; аргументы никогда не склеиваются в строку команды.
2. Окружение процесса: UTF-8 (`file.encoding`, `JAVA_FILE_ENCODING`), `LANG=C.UTF-8`, `LC_ALL=C.UTF-8`.
3. Каждый вызов yt-dlp включает `--ignore-config` (изоляция от пользовательских конфигов), кроме явно документированных режимов «читать конфиг».
4. Прогресс: yt-dlp запускается с `--newline` и `--progress-template download:FETCHY|%(info.id)s|%(info.playlist_index)s|%(progress.status)s|%(progress.downloaded_bytes)s|%(progress.total_bytes)s|%(progress.total_bytes_estimate)s|%(progress.speed)s|%(progress.eta)s`; парсер питается только из stdout/stderr процесса.
5. Все ссылки/токены/URL с креденшалами маскируются в логах, отчётах об ошибках и в строке переиспользуемой команды (`CommandStringCodec`).
6. Ошибка класса `DRM_PROTECTED` отображается как «Это видео защищено DRM, yt-dlp не может его скачать» + кнопка «Показать лог». Функций обхода DRM нет (решение D15).
7. Не бандлить yt-dlp/ffmpeg в репозиторий: внешние бинарники, обнаружение/скачивание — `BinaryManager`/`ComponentsManager`.
8. Язык кода — Kotlin 2.x, style: явные типы в публичном API, без `!!`, без комментариев в коде, кроме где алгоритм неочевиден.
9. Каждая задача заканчивается зелёным `gradlew build` (включая тесты) и одним коммитом с сообщением `T<ид>: <суть>`.
10. Локализация — compose resources, EN + RU; тест на паритет ключей обязателен в задаче, добавляющей строки UI.
11. Тесты — golden fixtures из системного yt-dlp 2026.08.19 (`yt-dlp --help` = 896 строк, `--list-extractors` = 1752 строки); фикстуры хранятся в `core/ytdlp/src/test/resources/fixtures/`.
12. Параллельных implementer-субагентов нет (общие файлы `libs.versions.toml`, конвенции, корневые скрипты).
13. Трата денег/сеть: скачивание бинарников yt-dlp/ffmpeg разрешено только в `BinaryManager`/`ComponentsManager` и в тестах с явным маркером; CI — только сборка+тесты без сети к сторонним сервисам.

## Пины версий (исследование 2026-10-08)

| Артефакт | Версия |
|---|---|
| Gradle wrapper | 9.8.0 |
| Kotlin | 2.4.10 |
| Compose Multiplatform | 1.12.0 |
| org.jetbrains.compose.material3:material3 | 1.12.0-alpha03 |
| materialkolor | 5.0.1 (`DynamicMaterialExpressiveTheme`, `PaletteStyle.Expressive`, `SPEC_2025`) |
| graphics-shapes (spike) | androidx.graphics:graphics-shapes:1.1.0 |
| Ktor (client) | 3.6.0 |
| Koin | 4.2.2 |
| SQLDelight | 2.3.2 |
| Coil | 3.6.x (ближайший 3.6.0 при резолве) |
| kotlinx-coroutines | 1.11.0 |
| JNA | 5.19.1 |
| mockk | 1.14.11 |
| JUnit / Turbine | latest при имплементации |
| yt-dlp (системный, фикстуры) | 2026.08.19 |

## Архитектура

Модули: `build-logic` (конвенции `fetchy.kotlin-jvm`, `fetchy.compose`, `fetchy.testing`) · `designsystem` · `core:{model,ytdlp,data,platform}` · `feature:{add,queue,history,presets,settings,tools}` · `app`.

- `core/model` — доменные модели (DownloadTask, TaskStatus, Preset, HistoryEntry, HelpOption…) и классификатор ошибок.
- `core/ytdlp` — `BinaryManager`, `ComponentsManager`, `ArgvBuilder`, `YtdlpProcessRunner`, `ProgressParser`, `ProbeParser`, `HelpParser`, `ErrorClassifier`, `CommandStringCodec`.
- `core/data` — SQLDelight (downloads, presets, history, settings), репозитории.
- `core/platform` — single-instance (ServerSocket), FS-пути, межпроцессный IPC, упаковка ресурсов.
- `designsystem` — тема Material 3 Expressive, шрифты, формы, motion, кастомные компоненты (`components/`, `shapes/`, `motion/`).
- `feature/*` — экраны; навигация своя (sealed `Destination`); DI — Koin; строки — compose resources (EN/RU).
- `app` — точка входа, сборка графа, упаковка.

Нормативные форматы (не менять):
- Прогресс-строка: `FETCHY|<id>|<playlistIndex>|<status>|<downloaded>|<total>|<totalEstimate>|<speed>|<eta>` (пустые поля = `N/A`), см. Global Constraint 4.
- Help-реестр: regex `/^    (?:(-[\w-]), )?(--[\w-]+)(?:[ =](\S+))?[ \t]{2,}(.*)$/` по секциям отступа 2, колонка описания 36; ~280 опций.
- Классы ошибок: `NOT_FOUND`, `NETWORK`, `AUTH_REQUIRED`, `GEO_BLOCKED`, `DRM_PROTECTED`, `PLAYLIST_PARTIAL`, `NO_SPACE`, `FFMPEG_MISSING`, `BINARY_MISSING`, `UNKNOWN`.

## Этап 0 — основа

### Task T0.2: Bootstrap Gradle 9.8.0 wrapper
- Files: `gradle/wrapper/*`, `gradlew`, `gradlew.bat`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`
- Do: скачать дистрибутив Gradle 9.8.0 и сгенерировать wrapper (`gradle wrapper --gradle-version 9.8.0`, bootstrap вне репо допустим в TEMP); корневой `settings.gradle.kts` с `rootProject.name = "fetchy"` и `pluginManagement`/`dependencyResolutionManagement` (модули подключаются в T0.4, здесь — заготовка с пустым include); `gradle.properties`: `org.gradle.jvmargs=-Xmx2g`, `org.gradle.configuration-cache=true`, `kotlin.code.style=official`, `org.gradle.caching=true`; пустой корневой `build.gradle.kts`.
- Tests: `./gradlew --version` печатает Gradle 9.8.0.
- Verify: `./gradlew help` без ошибок; wrapper-файлы закоммичены.
- Commit: `T0.2: gradle 9.8.0 wrapper + root settings`

### Task T0.3: Каталог версий и конвенции build-logic
- Files: `gradle/libs.versions.toml`, `build-logic/settings.gradle.kts`, `build-logic/build.gradle.kts`, `build-logic/src/main/kotlin/fetchy.kotlin-jvm.gradle.kts`, `fetchy.compose.gradle.kts`, `fetchy.testing.gradle.kts`, `settings.gradle.kts` (includeBuild "build-logic")
- Do: каталог версий строго по таблице пинов выше (aliases: kotlin, compose-multiplatform, material3, materialkolor, graphics-shapes, ktor, koin, sqldelight, coil, coroutines, jna, mockk, junit, turbine); конвенции: `fetchy.kotlin-jvm` → Kotlin jvmToolchain(17), jvmTarget 17, test deps (junit, mockk, coroutines-test, turbine); `fetchy.compose` → применяет kotlin-jvm + compose-gradle-plugin + материальные alias'ы; `fetchy.testing` → общие тестовые зависимости и fixtures-конфигурация.
- Tests: конвенции применяются к тестовому модулю-заглушке или проверяются в T0.4; `./gradlew help` зелёный.
- Verify: `./gradlew help`; дублей версий в build-скриптах нет (все версии только в TOML).
- Commit: `T0.3: version catalog + build-logic conventions`

### Task T0.4: Скелет модулей
- Files: `settings.gradle.kts` (include всех модулей), `core/model`, `core/ytdlp`, `core/data`, `core/platform`, `designsystem`, `feature/{add,queue,history,presets,settings,tools}`, `app` — по `build.gradle.kts` + `src/{main,test}/kotlin`
- Do: каждый модуль подключает нужную конвенцию; `app` содержит пустой `fun main()` с `application {}` (без UI ещё); в `feature/*` — placeholder object модуля для DI; в `core/*` — пустые пакеты `fetchy.<module>`; зависимости между модулями: feature→core+designsystem, app→всё, data→model, ytdlp→model, platform→model.
- Tests: `./gradlew build` зелёный (тестов пока нет).
- Verify: `./gradlew build` на чистом checkout; структура каталогов соответствует списку выше.
- Commit: `T0.4: module skeleton`

### Task T0.5: CI
- Files: `.github/workflows/ci.yml`
- Do: триггеры push/PR; матрица `windows-latest` и `ubuntu-latest`; steps: checkout, setup-java Temurin 17, `gradle/actions/setup-gradle`, прогон `./gradlew build` c `shell: bash`; кэш Gradle; без публикаций.
- Tests: валидация YAML (линтер или `python -c yaml.safe_load` не требуется — достаточно успешного первого рана на PR).
- Verify: workflow появился; имена jobs совпадают с матрицей.
- Commit: `T0.5: ci matrix windows/ubuntu`

## Этап 1 — designsystem

### Task T1.1: Тема Material 3 Expressive
- Files: `designsystem/src/main/kotlin/fetchy/designsystem/theme/{Color.kt,Typography.kt,Shape.kt,Theme.kt}`, `designsystem/src/main/composeResources/values{,-ru}/strings.xml` (минимум служебные строки)
- Do: `DynamicMaterialExpressiveTheme` (materialkolor 5.0.1) с `PaletteStyle.Expressive`, `SPEC_2025`; светлая/тёмная схемы; типографика с пользовательскими шрифтами (подключаемые из `designsystem/src/main/resources/fonts`, дефолт = платформенный, TODO на кастомный); shapes — углускругления M3E; экспортируем `FetchyTheme(darkTheme, dynamicColor, content)`.
- Tests: unit-тест инициализации темы (создание Scheme без UI, через рендер в headless-композе если доступно, иначе чистая логика настроек).
- Verify: `./gradlew build`; модуль designsystem собирается самодостаточно.
- Commit: `T1.1: expressive theme`

### Task T1.2: Базовые кастомные компоненты
- Files: `designsystem/src/main/kotlin/fetchy/designsystem/components/*` (buttons, cards, text_fields, chips, progress, dialogs, empty_state, section_header)
- Do: обёртки над M3E с едиными токенами (spacing через `designsystem/.../motion/Spacing.kt`); состояния pressed/hovered/disabled; компоненты без знания о фичах; публичные API на `@Composable`.
- Tests: по одному shallow-тесту на компонент (assert семантики/модификаторов) в `designsystem/src/test`.
- Verify: `./gradlew build`.
- Commit: `T1.2: base components`

### Task T1.3: Галерея-витрина + спайк graphics-shapes
- Files: `feature/tools` (подпакет `gallery`) или отдельный `designsystem/gallery` внутри `app` — решение: класс `GalleryScreen` в `feature/tools` с `Destination.Gallery`; спайк: `designsystem/src/main/kotlin/fetchy/designsystem/shapes/Morph.kt`
- Do: экран, демонстрирующий каждый компонент и морфинг форм; спайк: подключить `androidx.graphics:graphics-shapes` и проверить JVM-таргет; если таргет недоступен — заменить на собственную интерполяцию `Shape` и зафиксировать это в отчёте.
- Tests: smoke-тест рендера витрины (headless) или unit-тест логики морфинга; решение о fallback — assertion на отсутствие таргета.
- Verify: `./gradlew build`; в отчёте явный вердикт по graphics-shapes.
- Commit: `T1.3: gallery + shapes spike`

### Task T1.4: Motion-токены и анимации
- Files: `designsystem/src/main/kotlin/fetchy/designsystem/motion/*` (Transitions.kt, Easings.kt)
- Do: единые duration/easing для входов/выходов экранов, прогрессов, диалогов; `AnimatedContent`-хелперы перехода между `Destination`; интеграция с `Theme`.
- Tests: unit-тесты токенов (консистентность значений, отсутствие отрицательных длительностей) + тест хелпера.
- Verify: `./gradlew build`.
- Commit: `T1.4: motion tokens`

## Этап 2 — core/ytdlp

### Task T2.1: core/model и ErrorClassifier
- Files: `core/model/src/main/kotlin/fetchy/model/*` (`DownloadTask.kt`, `TaskStatus.kt`, `Preset.kt`, `HistoryEntry.kt`, `HelpOption.kt`, `ProgressInfo.kt`, `ErrorClass.kt`, `YtdlpError.kt`)
- Do: доменные модели (immutable data classes); `ErrorClassifier.classify(stderr: String, exitCode: Int): ErrorClass` со всеми 9 классами; правила: содержит `drm`/`protected` → `DRM_PROTECTED`; `HTTP Error 404`/`Video unavailable` → `NOT_FOUND`; `Sign in to confirm`/`login` → `AUTH_REQUIRED`; `not available in your country` → `GEO_BLOCKED`; `ffmpeg` → `FFMPEG_MISSING`; `No space left` → `NO_SPACE`; `Unable to download`+сеть → `NETWORK`; `--download-sections`/частичный плейлист → `PLAYLIST_PARTIAL`; бинар не найден → `BINARY_MISSING`; иначе `UNKNOWN`.
- Tests: таблица тестов классификатора (≥16 строк, включая DRM/geo/auth/no-space), все 9 классов покрыты.
- Verify: `./gradlew :core:model:build`.
- Commit: `T2.1: domain model + error classifier`

### Task T2.2: BinaryManager и ComponentsManager
- Files: `core/ytdlp/src/main/kotlin/fetchy/ytdlp/{BinaryManager.kt,ComponentsManager.kt}`
- Do: `BinaryManager`: резолв путей yt-dlp/ffmpeg/ffprobe в порядке (настройка пользователя → PATH → стандартные места ОС (Windows: `%APPDATA%`, `%LOCALAPPDATA%`; Linux: `~/.local/bin`, `/usr/bin`)); `ComponentsManager`: проверка версий (`--version`), скачивание недостающего yt-dlp с официального GitHub-релиза через Ktor (только по явному вызову пользователя из Settings/первый запуск), кэш в каталоге приложения (`core/platform` предоставляет каталог); кросс-платформенные пути через `core/platform`.
- Tests: unit-тесты резолва с фейковым PATH/FSystem; скачивание мокается Ktor-mock. Без реальной сети в тестах.
- Verify: `./gradlew :core:ytdlp:build`.
- Commit: `T2.2: binary management`

### Task T2.3: ArgvBuilder, CommandStringCodec, HelpParser
- Files: `core/ytdlp/src/main/kotlin/fetchy/ytdlp/{ArgvBuilder.kt,CommandStringCodec.kt,HelpParser.kt}`, fixture `core/ytdlp/src/test/resources/fixtures/ytdlp-help.txt`
- Do: `ArgvBuilder` — типизированный конструктор аргументов (опции модели + опции пользователя + URL + `--ignore-config`, `--newline`, progress-template); `CommandStringCodec` — маскирование креденшалов (`password`, `video-password`, `ap_username`, `ap_password`, `--cookies` пути к secrets, токены в URL `user:pass@`) для отображаемой команды/логов; `HelpParser` — regex из Global Constraints, вход: полный текст help, выход: `List<HelpOption>` с секцией/именем/коротким флагом/значением/описанием; фикстура — реальный `yt-dlp --help` 896 строк (генерация: `yt-dlp --help > fixture`).
- Tests: HelpParser: ≥250 опций распознано, семантические проверки (`--output` есть, `--no-progress` есть, секция «Networking Options» найдена); ArgvBuilder: `--ignore-config` всегда присутствует, порядок стабилен; Codec: маскирование 6+ паттернов.
- Verify: `./gradlew :core:ytdlp:build`.
- Commit: `T2.3: argv builder + codec + help parser`

### Task T2.4: ProcessRunner, ProgressParser, ProbeParser
- Files: `core/ytdlp/src/main/kotlin/fetchy/ytdlp/{YtdlpProcessRunner.kt,ProgressParser.kt,ProbeParser.kt}`, fixtures: `progress-samples.txt`, `probe-sample.json`
- Do: `YtdlpProcessRunner` — запуск `ProcessBuilder(listOf(...))` с UTF-8 env, `--newline` + progress-template, отдаёт `Flow<ProgressEvent>` (stdout/stderr), поддержка отмены (destroy → destroyForcibly с таймаутом), перехват финальной ошибки → `ErrorClassifier`; `ProgressParser` — разбор строки шаблона `FETCHY|…` (см. Global Constraint 4) в `ProgressInfo`, мусорные строки игнорируются; `ProbeParser` — разбор JSON `ffprobe` в `MediaInfo` (Kotlinx-serialization; добавить зависимость в TOML).
- Tests: ProgressParser — ≥10 золотых строк (полные/частичные/ошибочные/ N/A); Runner — интеграционный тест на `echo`-подобном процессе через системную команду ОС (команда выбирается по ОС), проверка отмены и кода возврата; ProbeParser — на фикстуре.
- Verify: `./gradlew :core:ytdlp:build` (включая интеграционные).
- Commit: `T2.4: process runner + parsers`

## Этап 3 — данные, каркас приложения, экран Add

### Task T3.1: core/data — SQLDelight и репозитории
- Files: `core/data/src/main/sqldelight/fetchy/db/*.sq`, `core/data/src/main/kotlin/fetchy/data/*Repository.kt`, настройка sqldelight-плагина в `core/data/build.gradle.kts` (+alias в TOML)
- Do: схема: `download` (id, url, title, format_args_json, preset_id, status, progress, created_at, finished_at, error_class, log_path), `history` (см. download, плюс file_path, duration_ms), `preset` (id, name, args_json, created_at), `setting` (key, value); репозитории с `Flow`-выдачами: `DownloadRepository`, `HistoryRepository`, `PresetRepository`, `SettingsRepository`; миграции через SQLDelight verify.
- Tests: тесты репозиториев на in-memory DB (jvm JDBC), ≥12 тестов (CRUD + транзишены статусов).
- Verify: `./gradlew :core:data:build`.
- Commit: `T3.1: persistence layer`

### Task T3.2: Каркас приложения — DI, навигация, single-instance, окно
- Files: `app/src/main/kotlin/fetchy/app/{Main.kt,AppGraph.kt,Navigation.kt}`, `core/platform/.../{SingleInstance.kt,AppDirs.kt,AppPaths.kt}`, `feature/*/…/Module.kt` (пустые Koin-модули), resource строки EN/RU
- Do: `fun main()` → проверка single-instance (ServerSocket на уникальном порту из файла в каталоге приложения; если занят — сигнал существующему окну поднять фокус через socket и выход 0); создание Koin-графа (core + все feature-модули); `FetchyTheme` + `Window` (минимум 1100x720, заголовок «Fetchy»); `sealed object Destination` (Add, Queue, History, Presets, Settings, Tools, Gallery, Detail(id)) + собственный `NavHost` (back-stack на `SnapshotStateList`, переходы анимированы через motion-хелперы); нижняя/боковая навигация с иконками.
- Tests: unit-тесты: single-instance (два подключения в одном JVM), back-stack навигации (push/pop/replace), AppPaths; smoke-тест сборки графа Koin (start/stop).
- Verify: `./gradlew build`; окно запускается вручную (проверка входной точкой T3.2-отчёта: скриншот/описание).
- Commit: `T3.2: app shell + navigation + single instance`

### Task T3.3: feature/add — экран добавления загрузки
- Files: `feature/add/src/main/kotlin/fetchy/feature/add/{AddScreen.kt,AddViewModel.kt,components/*}`, строки EN/RU
- Do: поле URL (валидация схемы http/https), выбор пресета, разбор URL на опции (тизер: кнопка «Разобрать» → `yt-dlp -J` через Runner → показ title/thumbnail/duration через Coil), раскрывающаяся «Расширенные опции» — чекбоксы/поля из HelpParser-реестра (категории: Формат, Субтитры, Аудио, Плейлисты, Прокси), кнопка «Скачать» → `DownloadRepository.create` → переход в Queue; ошибки разбора через `ErrorClassifier` (DRM-сообщение — Global Constraint 6); состояния: idle/parsing/error/ready.
- Tests: ViewModel-тесты (mockk репозиториев): валидация URL, happy-path разбора, ошибка DRM; ≥8 тестов.
- Verify: `./gradlew build`.
- Commit: `T3.3: add screen`

### Task T3.4: Очередь исполнения (ядро) — Scheduler
- Files: `core/ytdlp/src/main/kotlin/fetchy/ytdlp/DownloadScheduler.kt`, `feature/queue/.../QueueViewModel.kt` (минимум), строки EN/RU
- Do: `DownloadScheduler` — корутина, максимум N параллельных задач (из настроек, default 2), очередь FIFO, состояния задач (queued→running→paused? нет: queued→running→done/failed/cancelled), retry с backoff (2 попытки), персист прогресса в БД; события → `Flow`; интеграция с `YtdlpProcessRunner`; отмена задачи убивает процесс.
- Tests: тесты шедулера с фейковым runner (параллелизм ≤N, retry, отмена, персист-вызовы), ≥10 тестов.
- Verify: `./gradlew build`.
- Commit: `T3.4: download scheduler`

## Этап 4 — экран Queue

### Task T4.1: feature/queue — список активных загрузок
- Files: `feature/queue/.../{QueueScreen.kt,components/TaskCard.kt}`, строки EN/RU
- Do: список карточек: thumbnail (Coil), title, прогресс-бар (по ProgressInfo), скорость/ETA, статус-чип, playlist-позиция; сортировка по времени; пустое состояние; live-обновление из Flow.
- Tests: ViewModel-тесты отображения (маппинг ProgressInfo→UI-state), ≥6; компонентные assert'ы.
- Verify: `./gradlew build`.
- Commit: `T4.1: queue list`

### Task T4.2: feature/queue — управление
- Files: `feature/queue/.../components/TaskActions.kt`, строки EN/RU
- Do: действия на карточке: Отменить, Повторить (для failed), Пауза не поддержана yt-dlp → вместо неё «Стоп и заново» (пересоздать процесс); подтверждение отмены диалогом; мультивыбор «Отменить выбранные»; счётчик активных/в очереди.
- Tests: ViewModel-тесты действий (dispatch в scheduler/repository), ≥8.
- Verify: `./gradlew build`.
- Commit: `T4.2: queue controls`

### Task T4.3: Восстановление очереди после рестарта
- Files: `app/.../Main.kt` (bootstrap), `core/data/.../DownloadRepository.kt` (метод requeue), строки EN/RU
- Do: при старте задачи со статусом `running`/`queued` переводятся в `queued` (с прогрессом 0 для running, т.к. yt-dlp не возобновляет битые частичные файлы без `--continue`; `--continue` включён по умолчанию в ArgvBuilder — возобновление идёт на уровне файла), лог ошибок прошлой сессии доступен в History; UI-диалог «Возобновить N незавершённых?» с Да/Нет.
- Tests: тест requeue-логики (in-memory DB), ≥4; тест bootstrap-обработчика с фейками.
- Verify: `./gradlew build`.
- Commit: `T3.x→T4.3: queue recovery` (сообщение: `T4.3: queue recovery`)

## Этап 5 — экран History

### Task T5.1: feature/history — список и поиск
- Files: `feature/history/.../{HistoryScreen.kt,HistoryViewModel.kt}`, строки EN/RU
- Do: список завершённых (успех/ошибка), поиск по title/URL, фильтры (статус, период), пагинация (limit/offset), свайп/кнопка «Удалить запись», пустые состояния.
- Tests: ViewModel (фильтры/поиск/пагинация на фейковом репо), ≥8.
- Verify: `./gradlew build`.
- Commit: `T5.1: history list`

### Task T5.2: feature/history — детали, лог, ошибки
- Files: `feature/history/.../{HistoryDetailScreen.kt,components/LogView.kt}`, строки EN/RU
- Do: детали: метаданные, путь файла, кнопка «Открыть папку» (core/platform), полный лог (чтение log_path, лениво, лимит 2 МБ с «показать ещё»), класс ошибки и её человекочитаемое сообщение; для `DRM_PROTECTED` — сообщение Global Constraint 6 + кнопка «Показать лог» (лог разворачивается сразу); маскирование через CommandStringCodec при показе команды.
- Tests: ViewModel деталей + лог-лимиты, ≥6; проверка строк DRM в ресурсах EN/RU.
- Verify: `./gradlew build`.
- Commit: `T5.2: history detail + log viewer`

## Этап 6 — экран Presets

### Task T6.1: feature/presets — список и CRUD
- Files: `feature/presets/.../{PresetsScreen.kt,PresetsViewModel.kt}`, строки EN/RU
- Do: список пресетов (имя, кол-во опций, дата), создать/редактировать/удалить с подтверждением, дублировать; присвоение дефолтного; пустое состояние с приглашением.
- Tests: ViewModel CRUD на фейках, ≥8.
- Verify: `./gradlew build`.
- Commit: `T6.1: presets CRUD`

### Task T6.2: feature/presets — редактор опций из HelpParser-реестра
- Files: `feature/presets/.../OptionEditor.kt`, строки EN/RU
- Do: редактор аргументов: поиск по описанию/имени опции (реестр HelpParser, ~280 опций, отдаётся как `Flow`/синглтон), категории, для флагов — toggle, для опций со значением — поле с плейсхолдером синтаксиса (`OUTPUT` и т.п. из help), превью итоговой argv-строки через CommandStringCodec, валидация «опция не найдена».
- Tests: ViewModel (добавление/удаление опций, превью), парсер-интеграция (реестр ≥250), ≥8.
- Verify: `./gradlew build`.
- Commit: `T6.2: option editor`

## Этап 7 — экран Settings + локализация

### Task T7.1: feature/settings — настройки
- Files: `feature/settings/.../{SettingsScreen.kt,SettingsViewModel.kt}`, строки EN/RU
- Do: секции: Пути к бинарникам (yt-dlp/ffmpeg/ffprobe, «Проверить» → версия, «Скачать yt-dlp» → ComponentsManager), Параллелизм (slider 1–8), Тема (system/light/dark + dynamic color), Язык (system/en/ru), Прокси (URL, маскируется), Каталог загрузок (picker), Аккаунты/cookies-файл (путь, маскируется), О приложении (версия, лицензия MIT, yt-dlp не входит в поставку).
- Tests: ViewModel (применение/валидация/маскирование), ≥10.
- Verify: `./gradlew build`.
- Commit: `T7.1: settings screen`

### Task T7.2: Persistence настроек + EN/RU паритет
- Files: `core/data/.../SettingsRepository.kt` (реализация поверх setting), `core/platform/.../Locale.kt`, тест паритета
- Do: настройки применяются живьём (тема/язык — через composition locals; параллелизм — сигнал в scheduler; пути — сигнал в BinaryManager); маппинг `system` → системный язык; тест: сравнение набор ключей `values/strings.xml` vs `values-ru/strings.xml` (падает при расхождении) и запрет hardcoded-строк в feature-модулях (regex-тест по исходникам).
- Tests: паритет-тест (обязателен к зелёному), ≥6 тестов настроек.
- Verify: `./gradlew build`.
- Commit: `T7.2: settings persistence + i18n parity`

## Этап 8 — Tools, упаковка, приёмка

### Task T8.1: feature/tools — Doctor, Extractors, Probe
- Files: `feature/tools/.../{ToolsScreen.kt,DoctorScreen.kt,ExtractorsScreen.kt,ProbeScreen.kt}`, строки EN/RU
- Do: Doctor — статус бинарников (версии), каталог данных, writable, кол-во записей БД, кнопка «Проверить всё»; Extractors — список из `--list-extractors` (1752 строки, ленивая загрузка + поиск), закешированный в БД/файл; Probe — выбор медиафайла → `ffprobe` → таблица MediaInfo (ParseProbeParser); Gallery-ссылка (из T1.3).
- Tests: ViewModel-тесты (мок runner), парсер списка экстракторов (фильтр+счётчик), ProbeViewModel, ≥8.
- Verify: `./gradlew build`.
- Commit: `T8.1: tools (doctor/extractors/probe)`

### Task T8.2: Упаковка дистрибутива
- Files: `app/build.gradle.kts` (distZip/distTar + `jpackage`-задача), `scripts/package.ps1`, `scripts/package.sh`, `README.md`
- Do: `./gradlew :app:packageDistributionForCurrentOS` — jpackage (exe-установщик Windows, deb/app-image Linux) с иконкой `app/src/main/resources/icons/fetchy.ico/png`; README: сборка, запуск, требование наличия yt-dlp/ffmpeg (или кнопка скачивания), лицензия MIT, «yt-dlp не поставляется вместе с приложением»; скрипты упаковки для обеих ОС.
- Tests: команда упаковки выполняется локально (ручной прогон, в CI — только `distZip` если jpackage недоступен).
- Verify: jpackage-артефакт создан на Windows; `distZip` собирается.
- Commit: `T8.2: packaging`

### Task T8.3: Приёмка §12 (спец-прогон)
- Files: правки по находкам, `docs/ACCEPTANCE.md` (чек-лист)
- Do: прогнать чек-лист `docs/SPEC.md` §12 вручную+автотестами: все экраны открываются, полный цикл URL→скачивание→история (реальный yt-dlp 2026.08.19 из системы), ошибки NOT_FOUND/DRM/AUTH показываются корректно, логи маскируются, EN/RU переключение, тёмная тема, single-instance (второй запуск фокусирует первое окно), offline-поведение (нет сети → NETWORK), параллелизм ≤N; зафиксировать результат в ACCEPTANCE.md.
- Tests: все существующие зелёные + ручной чек-лист.
- Verify: `./gradlew build` + ACCEPTANCE.md заполнен.
- Commit: `T8.3: acceptance pass`

### Task T8.4: Финальная полировка
- Files: находки финального review + `docs/ACCEPTANCE.md`, README-правки
- Do: закрыть deferred minors, признанные блокирующими финальным review; убрать мёртвый код/предупреждения компилятора; `TODO` — либо закрыть, либо оставить осознанными (список в ACCEPTANCE.md).
- Tests: `./gradlew build` чистый, `--warning-mode all` без новых warning.
- Verify: сборка + краткий список принятых решений в отчёте.
- Commit: `T8.4: final polish`

## Маппинг на приёмку §12 (сводка)

| Требование §12 | Задачи |
|---|---|
| Полный цикл загрузки URL→файл→история | T3.3, T3.4, T4.1, T5.1 |
| Прогресс/скорость/ETA/плейлист | T2.4, T4.1 |
| Все опции yt-dlp доступны | T2.3, T6.2, T7.1 |
| Классы ошибок + DRM-сообщение | T2.1, T3.3, T5.2 |
| Логи и маскирование | T2.3, T5.2, T7.1 |
| Single instance | T3.2 |
| EN/RU, светлая/тёмная темы | T1.1, T7.2 |
| Windows 10/11 x64 + Linux X11/Wayland | T0.5 (CI matrix), T8.2 |
| yt-dlp/ffmpeg не в поставке | T2.2, T7.1, T8.2 |
| Лицензия MIT | T0.1/.gitignore-этап, T8.2 (README) |

## Известные ограничения

1. `docs/SPEC.md` — реконструкция ТЗ из сессии: оригинальный текст ТЗ утрачен из контекста модели. Если нужно точное соответствие — вставьте оригинал, план подлежит сверке.
2. JVM-таргет `androidx.graphics:graphics-shapes` не подтверждён — спайк в T1.3 с fallback.
3. `pause` не существует в yt-dlp — вместо паузы «стоп и заново» (T4.2).
4. Возобновление частичных загрузок — только через `--continue` yt-dlp на уровне файла; битые файлы перекачиваются.
5. Пины Coil/Turbine/JUnit уточняются при резолве (T0.3/T1.x).

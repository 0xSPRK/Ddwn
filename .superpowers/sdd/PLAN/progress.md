# SDD ledger — plan: docs/PLAN.md

## Setup
- Repo: D:\Projects\Ddwn, branch `feature/fetchy` (from `main` @ 4681a83)
- Ruling: T0.1 (git init, .gitignore, identity, docs commit) выполнен контроллером как setup-эквивалент worktree-шага скилла — если что-то не так, правится финальным review; cost if wrong: trivial.
- Ruling: git config user.email/user.name заданы локально для репо (иначе коммиты в новом репо невозможны); альтернатива `git -c ...` на каждый коммит неприменима для субагентов.
- Ruling: worktree не создаётся — репозиторий свежий, весь он и есть рабочая область; main защищён только соглашением (merge в конце — finishing-a-development-branch).
- Workspace: D:\Projects\Ddwn\.superpowers\sdd\PLAN (git-ignored)

## Pre-flight scan (конфликты плана)

| Задачи / объект | Производит → Потребляет | Находка | Рuling |
|---|---|---|---|
| T1.3 → T3.2 | `GalleryScreen` ссылается на `Destination.Gallery`, а `Destination` создаётся в T3.2 (позже) | Нарушение порядка | Принято в план: T1.3 делает чистый `@Composable` без навигации; подключение — T8.1. План исправлен до исполнения. |
| T3.4 → T0.4/T3.1 | `DownloadScheduler` (core/ytdlp) персистит в БД (core/data), но слои T0.4: ytdlp→model только | Противоречие слоям | Рuling: персист через интерфейс `TaskStateStore` в `core/model`, реализация в `core/data`. План исправлен до исполнения. |
| T3.1, T2.4, T7.2 → T0.3 | Правки `gradle/libs.versions.toml` после создания | Планируемое расширение файла позже | ОК — последовательные задачи, TOML общий только при последовательном исполнении (constraint 12). |
| T4.3 → T3.2 | Правка `app/Main.kt` | Общий файл | ОК — T4.3 позже T3.2 последовательно. |
| T4.3 | В плане был опечаточный commit-шаблон `T3.x→T4.3` | Дефект текста | Исправлено до исполнения: `T4.3: queue recovery`. |
| T0.3 сам по себе | Тест конвенций «заглушка или в T0.4» — двусмысленно | Слабый пункт | Принято: проверка конвенций — обязательный `./gradlew help` в T0.3 + фактическое применение во всех модулях T0.4. Review T0.4 это покрывает. |
| T1.1 | Headless-тест темы может быть невозможен без дисплея | Тест может деградировать | Принято: допустим чистый unit-тест на логику настроек темы; ручной визуал — в T8.3 (приёмка). |
| Глобальные constraint 4/5/6 ↔ T2.4/T2.3/T5.2 | Форматы прогресса/маскировки/DRM согласованы между задачами | Конфликтов нет | — |
| SPEC §12 ↔ T8.3 | Каждый пункт приёмки привязан к задачам (таблица маппинга) | Согласовано | — |

Итог: 2 дефекта плана исправлено до исполнения, 2 двусмысленности приняты с ruling, остальное чисто.

## Tasks
- Task 0.2: complete (commits a96caee..0ee9337, review clean; 1 minor deferred: bare include() no-op — brief-mandated, keep)
- Task 0.3: complete (commits 0ee9337..ca9f6f0, review clean; 4 minor deferred: useJUnitPlatform in kotlin-jvm check at T0.4; ktor-core→ktor-client-core alias mapping (documented); google() repo required for graphics-shapes; precompiled-script findLibrary type-safety expected pattern — review at T0.4)
- Task 0.4: complete (commits ca9f6f0..2faf550, review clean; 5 minor deferred: BOMs in 5 feature build files, placeholder objects vs "empty packages" (report), narrow feature deps later, launcher split, compose runtime in all compose modules — PLAN import note already raised)
- Task 0.5: complete (commits 2faf550..5d9358b, review clean; 1 important: verify ./gradlew under bash on Windows or confirm LF/executable; 2 minor: YAML parse semantics, push/PR branch filters narrower than brief)
- Task 1.1: complete (commits 5d9358b..a735a72, review clean; 6 minor: API-drift report correction (done in review), dynamicColor silent no-op, fetchyColorScheme test-only, test tautology, resource-accessor generation deferred, parity test cwd-fragility)
- Task 1.2: complete (commits a735a72..cea9759, review clean; 2 medium: Linux CI UI-test headless verification (deferred to first CI run), report vs code — cards overloads; 5 minor: literal dims claim, variant coverage, OutlinedButton loading asymmetry, unused import, runComposeUiTest v2 deprecation)
- Task 1.3: complete (commits cea9759..fafdcf0, review clean; 4 low: per-frame cubics recomputation, Morph bounds edge, weak differ assertion, gallery test fragility; 2 info: dependency commitment, icons missing)
- Task 1.4: complete (commits fafdcf0..03ed805, review clean; 2 medium: slide directions ignore pop, fadeThrough timing not strictly M3 fade-through; 3 low: Theme integration unverified, DurationTest gap, NavigationTransition coverage gaps)

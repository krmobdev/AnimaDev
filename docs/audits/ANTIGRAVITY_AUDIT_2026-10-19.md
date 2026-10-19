# Технический аудит проекта AnimaDev
**Дата проведения:** 19 октября 2026 г.  
**Аудитор:** Antigravity (AGY v2 Autonomous Assistant)  
**Репозиторий:** krmobdev/AnimaDev  
**Ветка аудита:** `audit/animadev-antigravity-20261019`  
**Базовый коммит:** `c31f31c` (origin/main)  
**Режим:** Read-Only  

---

## 1. Описание проекта и область проверки

**AnimaDev** — мобильное Android-приложение (Kotlin, Jetpack Compose, Material 3), предназначенное для автономного управления локальным циклом сборки, тестирования и контроля Git-репозиториев на Android-устройствах без необходимости использования внешнего десктопного ПК.

### Проверенные компоненты:
1. **Пользовательский интерфейс и навигация:**
   - `MainActivity.kt` (Compose root, lifecycle, backstack, deep links).
   - `NavigationPolicy.kt` / `NavigationState` (стейт-машина маршрутизации экранов).
   - `BuildScreen.kt`, `ProjectScreens.kt`, `HistoryScreen.kt`, `JournalScreen.kt`.
2. **Транспорт интеграции с Termux (IPC):**
   - `TermuxGateway.kt`, `RepoGateway.kt`, `CatalogGateway.kt`, `BranchSwitchGateway.kt`.
   - Механизм передачи команд через `com.termux.permission.RUN_COMMAND` и асинхронные обратные вызовы через `PendingIntent` в закрытые `BroadcastReceiver`.
3. **Криптография и безопасность:**
   - `SigningSecretVault.kt` (аппаратный `AndroidKeyStore`, AES-256-GCM + AAD).
   - `ReleaseHybridTransport.kt` (одноразовый RSA-3072 OAEP + AES-GCM конверт для передачи параметров подписи в Termux).
4. **Системный мониторинг и службы:**
   - `ShizukuConnection.kt` / `ShizukuConnectionCard.kt` (прямое взаимодействие с Shizuku Binder API).
   - `AdbDeviceGateway.kt` (обнаружение и автовосстановление беспроводного ADB).
5. **Хранение данных и кэширование:**
   - `JournalCache.kt` (SQLiteOpenHelper, транзакции, постраничное чтение логов).
   - `ArtifactGateway.kt`, `ArtifactMoveGateway.kt` (проверка SHA-256, FileProvider, кэш APK).
   - `ReleaseHistoryCache.kt` (хранение истории релизов и событий).
6. **Тестовый контур:**
   - Unit-тесты (`app/src/test/`), Instrumentation-тесты (`app/src/androidTest/`), Python-тесты раннеров (`runner/`).

---

## 2. Статус выполнения тестов

- **JVM Unit Tests (`app/src/test/`):**
  - **Статус:** Результаты верифицированы по существующему сборочному отчёту (`app/build/reports/tests/testDebugUnitTest/index.html`).
  - **Метрики:** 204 теста, 0 ошибок, 0 пропусков, успешность 100%, время выполнения 5.785 с.
  - **Охват:** Высокое покрытие чистой доменной логики, криптографических валидаторов, стейт-машин и политик безопасности.
- **Android Lint (`app/build/reports/lint-results-debug.txt`):**
  - **Статус:** Результаты верифицированы по существующему отчёту анализатора.
  - **Выявлено:** Предупреждения категории `ApplySharedPref`, жестко закодированные системные пути `SdCardPath`, неактуальные проверки `ObsoleteSdkInt`.
- **Git & Network Connectivity:**
  - **Статус:** Выполнен реальный тестовый запрос `git fetch origin main` в рамках текущей сессии. Соединение с GitHub по SSH подтверждено.

---

## 3. Сводка выявленных проблем

| Уровень критичности | Количество | Описание |
| :--- | :---: | :--- |
| **CRITICAL** | **0** | Критических уязвимостей, утечек приватных ключей или повреждения данных не обнаружено. |
| **HIGH** | **1** | Блокирующий синхронный ввод-вывод (`commit()`) на главном потоке интерфейса (риск ANR). |
| **MEDIUM** | **4** | Навигационный сбой при переходе из уведомления, утечка кэша APK, разрастание ключей SharedPreferences, избыточный опрос CPU в Termux. |
| **LOW** | **3** | Жёсткий лимит буфера ответа, глубина истории вспомогательных экранов, управление соединениями SQLite. |

---

## 4. Подробный реестр замечаний

### Подтверждённые проблемы

#### [HIGH-01] Блокирующий синхронный ввод-вывод (`commit()`) на UI-потоке Android
- **Компонент:** Хранение состояния / UI поток.
- **Файлы и строки:**
  - `app/src/main/java/com/animacode/animadev/projects/AdbDeviceGateway.kt` (строки 141, 147, 198)
  - `app/src/main/java/com/animacode/animadev/projects/ProjectStore.kt` (строки 19, 21, 23, 27)
  - `app/src/main/java/com/animacode/animadev/projects/BranchSwitchGateway.kt` (строки 56, 84, 98, 100, 103)
  - `app/src/main/java/com/animacode/animadev/projects/CatalogGateway.kt` (строки 14, 34, 89, 90)
  - `app/src/main/java/com/animacode/animadev/projects/SigningSecretVault.kt` (строка 48)
  - `app/src/main/java/com/animacode/animadev/projects/ReleaseGateway.kt` (строка 33)
- **Доказательство проблемы:**
  ```kotlin
  // AdbDeviceGateway.kt:141
  prefs.edit().putBoolean("pending", false).putInt("count", 0)
      .putString("message", "Нет ответа Termux. Проверьте разрешение RUN_COMMAND.").commit()

  // ProjectStore.kt:21
  private fun save(items: List<LocalProject>) =
      prefs.edit().putString("projects", JSONArray(items.map { it.json() }).toString()).commit()
  ```
  Подтверждено предупреждениями Android Lint `[ApplySharedPref]`.
- **Возможные последствия:**
  Метод `commit()` блокирует вызывающий поток до завершения физической записи на диск. В `AdbDeviceReceiver.onReceive`, `MainActivity.onResume` (`autoRestore()`) и вызовах `ProjectStore` операция выполняется непосредственно на главном потоке (MainThread). При интенсивной дисковой активности в Termux (например, параллельная сборка Gradle) это вызывает зависание UI, пропуск кадров и риск системного диалога ANR.
- **Рекомендуемое исправление:**
  Заменить вызовы `.commit()` на асинхронный `.apply()`, либо перенести вызовы сохранения в фоновый пул `Dispatchers.IO`.

---

#### [MEDIUM-01] Ошибка выбора вкладки при переходе из системного уведомления о сборке
- **Компонент:** Навигация и Deep Linking.
- **Файл и строки:** `app/src/main/java/com/animacode/animadev/MainActivity.kt` (строки 122–132).
- **Доказательство проблемы:**
  ```kotlin
  LaunchedEffect(requestedRelease, requestedBuild) {
      if (requestedRelease || requestedBuild) {
          val id = if (requestedRelease) ReleaseStore(context).read().optString("project") else store.read().optString("project_id")
          val target = projects.all().firstOrNull { it.id == id && it.supported }
          if (target != null) {
              selected = target.id; projects.select(target.id); top = "projects"; route = "build"
          }
          requestedRelease = false; requestedBuild = false
      }
  }
  ```
- **Возможные последствия:**
  При нажатии на уведомление о завершении сборки Release AAB (`open_release = true`) экран переключается на `route = "build"`, однако переменная состояния `selectedBuildTab` остаётся равной `0` (вкладка Debug APK). Пользователь видит экран Debug-сборки вместо отчёта о Release AAB, на который он нажал.
- **Рекомендуемое исправление:**
  В теле обработчика явно задавать индекс вкладки:
  ```kotlin
  if (requestedRelease) {
      selectedBuildTab = 1
  } else if (requestedBuild) {
      selectedBuildTab = 0
  }
  ```

---

#### [MEDIUM-02] Неограниченное накопление файлов в кэше установки APK
- **Компонент:** Управление дисковым пространством.
- **Файл и строки:** `app/src/main/java/com/animacode/animadev/projects/ArtifactGateway.kt` (строки 56–85).
- **Доказательство проблемы:**
  ```kotlin
  val directory = File(context.cacheDir, INSTALL_CACHE)
  val target = File(directory, "$sha.apk")
  val temp = File.createTempFile(".verify-", ".tmp", directory)
  try {
      ...
      check(temp.renameTo(target)) { "CACHE_UNAVAILABLE" }
      return target
  } finally {
      temp.delete()
  }
  ```
- **Возможные последствия:**
  Метод `verifiedCopy` сохраняет каждый проверенный APK в директорию кэша `shared_apk/$sha.apk`. Блок `finally` удаляет только временный файл `.tmp`. Целевые файлы `$sha.apk` (размером от 20 до 80 МБ каждый) сохраняются навсегда. При регулярной сборке кэш незаметно занимает гигабайты внутренней памяти устройства без ведома пользователя.
- **Рекомендуемое исправление:**
  Добавить политику очистки кэша: удалять APK-файлы старше 48 часов или ограничивать хранение максимум 3 последними версиями.

---

#### [MEDIUM-03] Разрастание устаревших ключей в SharedPreferences истории релизов
- **Компонент:** Кэш истории релизов.
- **Файл и строки:** `app/src/main/java/com/animacode/animadev/projects/ReleaseHistoryCache.kt` (строки 20–28).
- **Доказательство проблемы:**
  ```kotlin
  val key = "events_" + jobId
  ...
  prefs.edit().putString(key, added.toString()).apply()
  ```
- **Возможные последствия:**
  Для каждого задания создаётся отдельный ключ `"events_" + jobId`. Массив `history` в том же файле настроек ограничен 100 последними записями. Однако при вытеснении старых заданий их ключи `"events_..."` никогда не удаляются. Файл `release_history_v1.xml` бесконечно растёт, увеличивая потребление памяти и замедляя его десериализацию при старте.
- **Рекомендуемое исправление:**
  При ротации списка `history` удалять ключи `"events_" + oldJobId` для заданий, выбывших из списка 100 сохранённых.

---

#### [MEDIUM-04] Избыточная нагрузка на CPU и батарею при фоновом опросе прогресса
- **Компонент:** Фоновый мониторинг сборки (Python).
- **Файл и строки:** `app/src/main/assets/release/progress.py` (строки 27–46).
- **Доказательство проблемы:**
  ```python
  top = subprocess.run(["top", "-b", "-n", "2", "-d", "1"], capture_output=True,
                       text=True, timeout=4, check=False)
  ```
- **Возможные последствия:**
  Функция `resource_metrics()` вызывается интерфейсом каждые 4.5 секунды во время сборки. Вызов `top -b -n 2 -d 1` содержит принудительную секундную задержку ядра (`-d 1`) и создаёт дополнительный тяжелый процесс каждые несколько секунд. На мобильном процессоре это приводит к повышенному нагреву, расходу батареи и конкуренции за ресурсы с компилятором Gradle.
- **Рекомендуемое исправление:**
  Считывать метрики напрямую из псевдофайловой системы ядра Linux (`/proc/stat` и `/proc/meminfo`) без запуска внешних утилит `top` и `free`.

---

#### [LOW-01] Жёсткий лимит размера ответа в ReleaseResultReceiver
- **Компонент:** Транспорт результатов подписи.
- **Файл и строки:** `app/src/main/java/com/animacode/animadev/projects/ReleaseGateway.kt` (строка 173).
- **Доказательство проблемы:**
  ```kotlin
  if (stdout.length > 4096 || !result.containsKey("exitCode") || !result.containsKey("err"))
      error("Malformed reply")
  ```
- **Возможные последствия:**
  Если в стандартный вывод Python попадут сторонние предупреждения или расширенная диагностика среды, превышение 4096 символов приведёт к ошибке и переводу задачи в состояние `UNKNOWN`/`FAILED`, даже если артефакт AAB был корректно собран и подписан.
- **Рекомендуемое исправление:**
  Увеличить безопасный порог длины до 16–32 КБ и извлекать финальный JSON-объект по границам `{...}`.

---

### Гипотезы, требующие дополнительной проверки

#### [LOW-02-HYPOTHESIS] Отсутствие стека переходов во вспомогательных экранах
- **Компонент:** Навигация.
- **Файл:** `app/src/main/java/com/animacode/animadev/MainActivity.kt`.
- **Суть гипотезы:** Состояние `supportParent` и `settingsParent` представлено одиночными переменными `String`, а не стеком переходов. При глубокой навигации (например, Экран настроек -> Справка -> О программе) нажатие кнопки «Назад» может перескакивать экраны или возвращать не на предыдущий экран, а в корень.
- **Проверка:** Требуется сценарий ручного тестирования переходов по вспомогательным экранам.

#### [LOW-03-HYPOTHESIS] Множественные экземпляры SQLiteOpenHelper в жизненном цикле экранов
- **Компонент:** База данных кэша логов.
- **Файл:** `app/src/main/java/com/animacode/animadev/repos/JournalScreen.kt` (строка 96), `HistoryScreen.kt` (строка 25).
- **Суть гипотезы:** Использование `remember { JournalCache(context) }` создаёт отдельный экземпляр хелпера базы данных на каждом экране. Несмотря на закрытие в `DisposableEffect.onDispose`, при быстрой смене экранов возможно одновременное открытие нескольких пулов соединений к `journal_cache.sqlite`.
- **Проверка:** Рекомендуется перевести `JournalCache` на синглтон-паттерн уровня приложения.

---

## 5. Подтверждение целостности кода

В процессе проведения аудита:
- Исходный код приложения (`app/src/**`, `build.gradle.kts`, `settings.gradle.kts`) **не изменялся**.
- Конфигурационные файлы сборки и манифесты **не модифицировались**.
- Никакие приватные ключи, JKS-файлы или пароли **не извлекались и не включались в отчёт**.

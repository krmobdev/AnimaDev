# Этап 1 — первый APK, 09.10.2026

Рабочая ветка: `feature/termux-probe`. Репозиторий: `krmobdev/AnimaDev` (private).
Каталог: `/data/data/com.termux/files/home/anima-workspace/AnimaDev`.

## Сделано

- Отдельный Kotlin/Compose APK `com.animacode.animadev`, 0.1.0-probe / 1.
- Один экран: проверка доступа, кнопка «Проверить», stdout, stderr, exit code,
  Termux err, ID и длительность запроса.
- Реальный explicit RUN_COMMAND Intent к `com.termux.app.RunCommandService`.
- Фиксированный read-only Python probe. Не используется shell-команда из UI.
- Уникальный ID в URI explicit mutable one-shot PendingIntent; закрытый receiver.
- Проверка JSON, ID, UID, cwd, версии Python и платформы linux/android.
- Успех только при проверенном ответе, exit=0 и Termux err=-1.
- Постоянное сохранение последнего запроса/ответа. Таймаут 30 секунд = UNKNOWN;
  поздний ответ с совпадающим ID принимается. Поворот не отправляет команду.
- Нет INTERNET permission, сервера, Git sync или сборок других проектов.

## Реально проверено

- Окружение телефона: Android 15 / API35, Termux 0.118.3, Java21,
  Python3.14.6 (`sys.platform = android`), SDK37, Gradle9.5.0.
- Офлайн `:app:testDebugUnitTest :app:assembleDebug`: BUILD SUCCESSFUL,
  1m57s, 42 tasks. Задание `090508c7d58245cba19cda3d347c37be`.
- XML: 11 tests, 0 failures, 0 errors, 0 skipped.
- Проверка apksigner: Verifies, APK Signature Scheme v2=true, один debug signer.
- APK: 11 534 072 bytes.
- SHA256 исходника и копии в Download совпадают:
  `dfdaefe0a8e730e7dec241901520e9494cf9be4b0b1bbd5ed761cfadbcc70e2b`.
- Готовый файл: `/storage/emulated/0/Download/AnimaDev_0.1.0_termux-probe_debug.apk`.
- Статическая проверка XML и manifest: корректные XML, receiver exported=false,
  запрос только видимости com.termux, сетевого разрешения нет.

Первая попытка офлайн-сборки остановилась на отсутствующих транзитивных
версиях AndroidX. Использованы уже закэшированные core1.19, lifecycle2.11,
savedstate1.4; SDK/Java/Gradle не переустанавливались. Оставшиеся предупреждения:
Bundle.get deprecated и strip native graphics.path symbols — не ошибки сборки.

## Ещё не подтверждено — требуется приёмка на телефоне

Прямое ADB подключение отсутствует (`adb devices` пусто). APK не устанавливался
агентом. Реальный PendingIntent callback с телефона, доступ RUN_COMMAND,
allow-external-apps и поведение после закрытия GUI ещё не подтверждены.
JVM-тесты проверяют политику; они НЕ доказывают Android IPC.
Скриншоты/видео и успешный callback не выдумывались.

1. В «Загрузках» установить `AnimaDev_0.1.0_termux-probe_debug.apk`.
2. Открыть приложение → «Разрешить доступ к Termux» → системное подтверждение.
3. «Проверить»: должен появиться настоящий JSON stdout с текущим ID,
   stderr с диагностической строкой, exit code 0 и Termux err -1.
4. Закрыть/открыть приложение: последний результат остаётся.

Если результат неизвестен, это не успех. Показать код/сообщение из экрана.
Если Termux запретит внешние приложения, потребуется первичная настройка
allow-external-apps. Доступ к приватной настройке не обходился через root/ADB.

## Остановка

После поставки первого APK новые функции не добавлять до приёмки.
Существующие проекты и их main не изменялись. GitHub Actions не запускались;
в новом репозитории workflow-файлов нет. PR и релиз не создавались.

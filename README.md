# AnimaDev · 0.1.0-probe

Первый минимальный Kotlin/Compose APK для проверки локального канала Termux.
Сам APK не использует AnimaBridge, MCP, VPS, сеть, root или adb.

## Проверка на телефоне

1. Установить APK, открыть AnimaDev.
2. Нажать «Разрешить доступ к Termux» и подтвердить системный запрос.
   Если Android не показывает запрос — «Настройки разрешений» → дополнительные разрешения.
3. Нажать «Проверить». Подтверждённый результат содержит stdout JSON с UID,
   cwd, версией Python и текущим ID; stderr содержит диагностическую строку;
   exit code 0 и Termux err -1. До ответа успех не показывается.
4. Закрыть и открыть приложение: последнее состояние сохраняется.

Обязательное условие Termux: `allow-external-apps = true` в termux.properties.
Пока это не проверено, не считать настройку готовой. Python должен быть установлен.
Настройка приватного свойства через другой APK невозможна до выдачи доступа.

## Границы

Только короткая безопасная команда. Нет Git, сборок других проектов, runner или
фона долгих задач. После 30 секунд без callback состояние UNKNOWN, поздний
совпадающий ответ принимается. Новая проверка запускается только нажатием.
Повторный/чужой callback игнорируется. Поворот не запускает команду заново.
Состояние сохраняется одним атомарным SharedPreferences commit; это намеренный
минимум для одного read-only запроса, без Room/DataStore в техническом spike.
Светлая тема; полный дизайн и тёмная тема — после приёмки канала.

Сборка использует версии, реально присутствующие в окружении владельца:
AGP 9.3.1, встроенный Kotlin, Compose compiler 2.4.10, Gradle 9.5.0,
SDK 37, target 35, min 26, Java 21 (bytecode 17). Нет переустановки окружения.

## Официальные источники

- https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent
- https://github.com/termux/termux-app/blob/v0.118.3/termux-shared/src/main/java/com/termux/shared/termux/TermuxConstants.java
- https://developer.android.com/reference/android/app/PendingIntent
- https://developer.android.com/training/package-visibility/declaring

Не считать компиляцию подтверждением фактической связи с Termux.

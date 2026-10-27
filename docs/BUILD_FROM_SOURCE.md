# Сборка AnimaDev из исходников

[English](BUILD_FROM_SOURCE_EN.md) · [На главную](../README.md)

Здесь описана сборка **самого приложения AnimaDev**, а не сборка произвольного проекта через его интерфейс. Для самостоятельной сборки не нужны официальный GitHub Release, AnimaBridge Agent, Shizuku или секретные ключи владельца.

## 1. Подготовка

- Компьютер с JDK **21** и Android SDK (например, Android Studio), либо подготовленный Termux.
- Android SDK Platform **37** (проверяйте `compileSdk` в [app/build.gradle.kts](../app/build.gradle.kts)).
- Подключение к интернету для первоначальной загрузки Gradle/Android Gradle Plugin/Kotlin/AndroidX.
- Gradle wrapper **9.5.0** из репозитория; не нужен установленный глобально Gradle.
- **Termux:** совместимый с Android исполняемый `aapt2`, действительные `ANDROID_HOME` и `ANDROID_SDK_ROOT`. Обычный desktop AAPT2 не обязан работать в Termux.

## 2. Сборка на ПК

```sh
git clone https://github.com/krmobdev/AnimaDev.git
cd AnimaDev
java -version
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain
```

В Windows выполните `gradlew.bat` вместо `./gradlew`. Убедитесь, что JDK 21 настроен как `JAVA_HOME`, а Android SDK обнаруживается через `ANDROID_HOME`, `ANDROID_SDK_ROOT` или стандартные настройки Android Studio.

Итог: `app/build/outputs/apk/debug/app-debug.apk`. Debug APK подписывается локальным отладочным ключом. **Чужую APK с другой подписью поверх установленного приложения обновить нельзя**; перед возможным удалением сохраните важные данные.

## 3. Сборка на телефоне (Termux)

После [подготовки Termux](TERMUX_SETUP.md), установки JDK 21, SDK Platform 37 и Android-совместимого AAPT2:

```sh
git clone https://github.com/krmobdev/AnimaDev.git
cd AnimaDev
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain
```

Указанный `~/android-sdk` — только **пример**: если SDK установлен в другом месте, используйте фактический путь. При ошибке запуска AAPT2 нужен совместимый Termux `aapt2` и, если необходимо, Gradle-опция `-Pandroid.aapt2FromMavenOverride=/полный/путь/к/aapt2`. Не предполагается, что одна команда `pkg install` автоматически создаёт рабочий Android SDK.

## 4. Offline и тесты

Только **после** первоначальной загрузки зависимостей:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug --offline --no-daemon --console=plain
```

На устройствах/эмуляторах можно выполнять инструментальные тесты лишь после настройки ADB и отдельного тестового пакета. Успешная сборка и JVM-тесты не доказывают правильную работу всех элементов ColorOS, плитки или интеграции Termux.

## 5. Подписанные сборки

`assembleDebug` **не** выпускает официальный релиз. Для распространения собственной Release APK нужен собственный JKS/ключ подписи и проверка Android Package Installer. Не публикуйте пароль и JKS в GitHub. Подписанная Release APK/AAB внутри AnimaDev — отдельная функция для выбранных локальных проектов; она не означает публикацию готового файла AnimaDev в Releases.

## Типичные ошибки

| Сообщение | Что проверить |
| --- | --- |
| Android SDK not found | `ANDROID_HOME`/`ANDROID_SDK_ROOT`, установленную Platform 37 |
| Dependency not found / offline mode | Доступ в интернет; первая сборка без `--offline` |
| AAPT2 cannot execute in Termux | Совместимую с Android версию AAPT2 и override |
| JVM/AGP mismatch | JDK 21, версию AGP/Gradle из исходников |
| INSTALL_FAILED_UPDATE_INCOMPATIBLE | Подписи старой и новой APK различаются — не удаляйте приложение без резервной копии |

Лицензия: [0BSD](../LICENSE); сторонние зависимости — [собственные лицензии](THIRD_PARTY.md).

# AnimaDev — первый этап

Работать только в `feature/termux-probe`, каталог `~/anima-workspace/AnimaDev`.
Никаких правок существующих проектов, main, Actions, PR или релизов.
Этап 1: единственный read-only Python probe через настоящий Termux RUN_COMMAND
с явным mutable one-shot PendingIntent и неэкспортируемым receiver.
Нет сервера, INTERNET permission, arbitrary shell, Git sync или Gradle управления.
Успех — только валидный nonce/schema + err=-1 + exitCode=0.
После первой сборки/проверки остановиться для приёмки владельцем.
Не заявлять телефонную интеграцию проверенной без фактического callback.

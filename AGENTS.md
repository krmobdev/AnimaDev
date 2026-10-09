# AnimaDev — этап 2 Git, разрешён 09.10.2026

Работать только в `feature/termux-probe`, каталог `~/anima-workspace/AnimaDev`.
Этап 1 принят владельцем: RUN_COMMAND, exit=0, err=-1, nonce, 215 мс.
Разрешён этап 2: STATUS/CHECK/SYNC единственного профиля AnimaQuiz.
QuizApp: ~/quizapp, origin git@github.com:krmobdev/quizapp.git,
только test/decomposition-v1. Не менять его при разработке; Git проверять
на изолированных fixtures. Синхронизация владельцем — только явная кнопка.
Не трогать main, не запускать Actions/PR/релизы. Не делать push из GUI,
reset, clean, rebase, stash или автоматическое переключение веток.
Runner ставится из APK в ~/.local/share/animadev, вне управляемых Git-деревьев.
Не менять allow-external-apps: уже настроено.
Продолжение долгой операции — QUERY по сохранённому ID, а не новый START.
До приёмки этапа 2 не реализовывать тесты/сборки QuizApp или другие функции.
Не заявлять Android-приёмку до проверки владельцем на реальном телефоне.

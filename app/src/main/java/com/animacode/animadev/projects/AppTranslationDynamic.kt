package com.animacode.animadev.projects

/** Dynamic UI summaries only. Raw Git, Gradle and journal payloads are never rewritten. */
internal object AppTranslationDynamic {
    private val passed=Regex("""^(\d+) из (\d+) успешно$""")
    private val counts=Regex("""^Ошибок (\d+) · пропущено (\d+)$""")
    private val elapsed=Regex("""^Прошло: (\d+) мин (\d+) с$""")
    private val notice=Regex("""^Задание выполняется (\d+) минут\. Последняя подтверждённая активность — (\d+) минут назад\. Процесс может продолжать вычисления\.$""")
    private val progress=Regex("""^Последний прогресс: (\d+) с назад$""")
    private val branchCount=Regex("""^Загружено веток: (\d+)( · есть следующая страница)?$""")
    private val foundCount=Regex("""^Найдено репозиториев: (\d+) · worktree: (\d+)$""")
    private val totalCount=Regex("""^Репозитории: (\d+) · worktree: (\d+)$""")
    private val suffixMinutes=Regex("""^(\d+) минут$""")
    private val beforeMinutes=Regex("""^Через (\d+) минут$""")
    private val limits=Regex("""^Пропуски и ограничения \((\d+)\)$""")
    private val testStat=Regex("""^Тесты: (\d+)/(\d+) успешно\nПровалено: (\d+) · Пропущено: (\d+)\nСуммарное время JUnit: (\d+) мс · Файлов отчётов: (\d+)\n?$""")
    private val testsRows=Regex("""^Заданий (\d+)$""")
    private val mib=Regex("""^(.*?)(\d+(?:[.,]\d+)?) МиБ$""")
    private val vElapsed=Regex("""^(\d+) мин (\d+) с$""")
    fun translate(text:String):String? {
        passed.matchEntire(text)?.let {return "${it.groupValues[1]} of ${it.groupValues[2]} passed"}
        counts.matchEntire(text)?.let {return "Failed: ${it.groupValues[1]} · skipped: ${it.groupValues[2]}"}
        elapsed.matchEntire(text)?.let {return "Elapsed: ${it.groupValues[1]} min ${it.groupValues[2]} sec"}
        notice.matchEntire(text)?.let {return "Job running for ${it.groupValues[1]} minutes. Last confirmed activity was ${it.groupValues[2]} minutes ago. Processing may still be active."}
        progress.matchEntire(text)?.let {return "Last progress: ${it.groupValues[1]} sec ago"}
        branchCount.matchEntire(text)?.let {return "Loaded branches: ${it.groupValues[1]}"+if(it.groupValues[2].isNotBlank())" · more pages available" else ""}
        foundCount.matchEntire(text)?.let {return "Repositories found: ${it.groupValues[1]} · worktrees: ${it.groupValues[2]}"}
        totalCount.matchEntire(text)?.let {return "Repositories: ${it.groupValues[1]} · worktrees: ${it.groupValues[2]}"}
        suffixMinutes.matchEntire(text)?.let {return "${it.groupValues[1]} minutes"}
        beforeMinutes.matchEntire(text)?.let {return "After ${it.groupValues[1]} minutes"}
        limits.matchEntire(text)?.let {return "Skipped and limited (${it.groupValues[1]})"}
        testsRows.matchEntire(text)?.let {return "Jobs ${it.groupValues[1]}"}
        vElapsed.matchEntire(text)?.let {return "${it.groupValues[1]} min ${it.groupValues[2]} sec"}
        testStat.matchEntire(text)?.let {
            val g=it.groupValues
            return "Tests: ${g[1]}/${g[2]} passed\nFailed: ${g[3]} · Skipped: ${g[4]}\nJUnit time: ${g[5]} ms · Report files: ${g[6]}\n"
        }
        if(text.startsWith("Последний прогресс: пока не подтверждён"))return "Last progress: not confirmed yet"
        if(text.startsWith("Задание · "))return text.replaceFirst("Задание · ","Job · ").replace("Unit-тесты","Unit tests").replace("Инструментальные тесты","Device tests")
        if(text.startsWith("CPU процесса Gradle: "))return "Gradle CPU: "+text.removePrefix("CPU процесса Gradle: ").replace("нет доступа к счётчикам","counters unavailable")
        if(text.startsWith("Свободно доступной RAM устройства: "))return "Available device RAM: "+text.removePrefix("Свободно доступной RAM устройства: ").replace("показатель недоступен","unavailable").replace(" МиБ"," MiB")
        if(text.startsWith("RAM процесса (RSS): "))return "Process RAM (RSS): "+text.removePrefix("RAM процесса (RSS): ").replace("показатель недоступен","unavailable").replace(" МиБ"," MiB")
        if(text.startsWith("Процесс: "))return "Process: "+text.removePrefix("Процесс: ").replace("работает","running").replace("не обнаружен","not found").replace("статус неизвестен","unknown")
        if(text.startsWith("Загрузка: "))return "Download: "+text.removePrefix("Загрузка: ")
        if(text.startsWith("Полная локальная SHA: "))return "Full local SHA: "+text.removePrefix("Полная локальная SHA: ")
        if(text.startsWith("Полная GitHub SHA: "))return "Full GitHub SHA: "+text.removePrefix("Полная GitHub SHA: ")
        if(text.startsWith("Последний коммит: "))return "Latest commit: "+text.removePrefix("Последний коммит: ")
        if(text.startsWith("Задание в работе: "))return "Running job: "+text.removePrefix("Задание в работе: ")
        if(text.startsWith("Найдено репозиториев: "))return "Repositories found: "+text.removePrefix("Найдено репозиториев: ")
        if(text.startsWith("Путь: "))return "Path: "+text.removePrefix("Путь: ")
        if(text.startsWith("Код: "))return "Code: "+text.removePrefix("Код: ")
        if(text.startsWith("Этап: "))return "Stage: "+text.removePrefix("Этап: ")
        if(text.startsWith("Размер: "))return text.removePrefix("Размер: ").replace(" МБ · Git "," MB · Git ").let {"Size: "+it}
        if(text.startsWith(" · ") && text.contains(" мин "))return text.replace(" мин "," min ").replace(" с"," s")
        if(text.startsWith("Проверено: "))return "Checked: "+text.removePrefix("Проверено: ")
        if(text.startsWith("Рабочее дерево: "))return "Working tree: "+text.removePrefix("Рабочее дерево: ").replace("Чисто","Clean").replace("Есть локальные изменения","Local changes").replace("Не подтверждено","Not verified")
        if(text.startsWith("Удалённых новых: "))return text.replaceFirst("Удалённых новых: ","New remote: ").replace(" · локальных новых: "," · new local: ")
        if(text.startsWith("Сохранено: "))return text.replaceFirst("Сохранено: ","Saved: ")
        if(text.startsWith("Ошибка: "))return text.replaceFirst("Ошибка: ","Error: ")
        return null
    }
}

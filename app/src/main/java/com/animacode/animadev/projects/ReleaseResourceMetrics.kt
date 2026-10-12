package com.animacode.animadev.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.util.Locale

/** Resource snapshot from the Termux Release monitor; only sanitized numeric values. */
object ReleaseResourcePolicy {
    fun memory(mb: Int): String = if (mb >= 1024)
        String.format(Locale.getDefault(), "%.2f ГБ", mb / 1024.0)
    else "$mb МБ"

    fun cpu(value: Int): Float = value.coerceIn(0, 100) / 100f
}

@Composable
fun ReleaseResourceMetrics(metrics: JSONObject?, now: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("Ресурсы телефона · обновление каждые 5 с",
            style = MaterialTheme.typography.titleSmall)
        if (metrics == null) {
            Text("Ожидаем данные от Termux…", style = MaterialTheme.typography.bodySmall)
            return@Column
        }
        val cpu = metrics.optInt("cpu_percent", -1)
        if (cpu in 0..100) {
            val cores = metrics.optInt("cpu_cores", 0)
            val process = metrics.optInt("cpu_process_percent", -1)
            Text("CPU · процессы Termux: $cpu% " +
                if (cores > 0) "от $cores ядер" else "от всех ядер",
                style = MaterialTheme.typography.bodyMedium)
            if(process >= 0)
                Text("Нагрузка в эквиваленте одного ядра: $process%",
                    style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(progress = { ReleaseResourcePolicy.cpu(cpu) },
                modifier = Modifier.fillMaxWidth())
        } else {
            Text("CPU · измерение недоступно", style = MaterialTheme.typography.bodySmall)
        }
        val total = metrics.optInt("mem_total_mb", -1)
        val available = metrics.optInt("mem_available_mb", -1)
        if (total > 0 && available in 0..total) {
            val used = total - available
            Text("RAM свободно для приложений: " + ReleaseResourcePolicy.memory(available),
                style = MaterialTheme.typography.bodyMedium)
            Text("RAM занято: " + ReleaseResourcePolicy.memory(used) +
                 " / " + ReleaseResourcePolicy.memory(total),
                style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(progress = { (used.toFloat() / total).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth())
        } else {
            Text("RAM · измерение недоступно", style = MaterialTheme.typography.bodySmall)
        }
        val swap = metrics.optInt("swap_used_mb", -1)
        if (swap >= 0)
            Text("Swap / zRAM занято: " + ReleaseResourcePolicy.memory(swap),
                style = MaterialTheme.typography.bodySmall)
        val sampled = metrics.optLong("sampled_at", 0L)
        if (sampled > 0)
            Text("Данные: " + ((now - sampled).coerceAtLeast(0) / 1000) + " с назад",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

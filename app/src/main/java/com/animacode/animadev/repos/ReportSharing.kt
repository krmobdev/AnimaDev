package com.animacode.animadev.repos

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object ReportSharing {
    fun prepare(context:Context,jobId:String,view:String,report:String):Intent {
        require(jobId.matches(Regex("[a-f0-9]{32}")) && view in setOf("ALL","IMPORTANT"))
        val directory=File(context.cacheDir,"shared_reports").apply { mkdirs() }
        require(directory.canonicalFile==directory.absoluteFile)
        val file=File(directory,"AnimaDev_${jobId}_${view}.txt")
        require(file.canonicalFile==file.absoluteFile && !java.nio.file.Files.isSymbolicLink(file.toPath()))
        val safe=RepoResultReceiver.redact(report)
        FileOutputStream(file).use { it.write(safe.toByteArray(Charsets.UTF_8));it.fd.sync() }
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.reports",file)
        return Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM,uri)
            .putExtra(Intent.EXTRA_SUBJECT,"AnimaDev · диагностика задания ${jobId.take(8)}")
            .apply { clipData=ClipData.newUri(context.contentResolver,"AnimaDev",uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
}

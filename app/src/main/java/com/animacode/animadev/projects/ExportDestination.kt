package com.animacode.animadev.projects

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.util.UUID

object DestinationPolicy {
    const val DEFAULT="Downloads"
    fun needsRecovery(selected:String,available:Boolean)=selected.isNotEmpty() && !available
    fun afterDecline()=""
    fun filename(stem:String,extension:String,nonce:String):String {
        require(stem.matches(Regex("[A-Za-z0-9_-]{1,100}")) && extension.matches(Regex("[a-z0-9]{1,8}")) && nonce.matches(Regex("[a-f0-9]{32}")))
        return "${stem}_${nonce}.${extension}"
    }
}

/** Shared export destination. Only user-granted trees or newly created MediaStore files. */
class ExportDestination(private val context:Context) {
    val prefs=context.getSharedPreferences("files_v1",Context.MODE_PRIVATE)
    fun selected()=prefs.getString("tree","") ?: ""

    /**
     * Termux can export directly only to Android primary shared storage.
     * SAF-only providers (including cloud storage and SD cards) cannot be
     * treated as filesystem paths. Never silently ignore the chosen folder.
     */
    fun artifactDirectory():String? {
        val selection=selected()
        if(selection.isEmpty())return "/storage/emulated/0/Download"
        if(!available(selection))return null
        return runCatching {
            val uri=Uri.parse(selection)
            check(uri.authority=="com.android.externalstorage.documents")
            val id=DocumentsContract.getTreeDocumentId(uri)
            check(id.startsWith("primary:"))
            val relative=id.removePrefix("primary:")
            val segments=relative.split('/')
            check(segments.isNotEmpty() && segments.all {
                it.matches(Regex("[\\p{L}\\p{N}_ ().&-]{1,80}")) &&
                it!="." && it!=".."
            })
            "/storage/emulated/0/"+segments.joinToString("/")
        }.getOrNull()
    }

    /** A persisted SAF grant is required before Android may read a Termux APK. */
    fun artifactUri(path:String):Uri? {
        val root=artifactDirectory() ?:return null
        val tree=selected().takeIf {it.isNotEmpty()} ?:return null
        val name=path.substringAfterLast('/')
        if(path!="$root/$name" || !name.matches(Regex("[A-Za-z0-9_-]+[.](apk|aab)")))return null
        return runCatching {
            val treeUri=Uri.parse(tree)
            DocumentsContract.buildDocumentUriUsingTree(
                treeUri,DocumentsContract.getTreeDocumentId(treeUri)+"/"+name)
        }.getOrNull()
    }

    /** Android DocumentsUI starts at the selected folder (or Downloads). */
    fun folderUri():Uri {
        val selectedTree=selected()
        if(selectedTree.isNotEmpty() && available(selectedTree))return Uri.parse(selectedTree)
        return DocumentsContract.buildTreeDocumentUri(
            "com.android.externalstorage.documents","primary:Download")
    }
    fun label()=prefs.getString("label",DestinationPolicy.DEFAULT) ?: DestinationPolicy.DEFAULT
    fun available(value:String=selected()):Boolean {
        if(value.isEmpty())return true
        return runCatching {
            val tree=Uri.parse(value)
            check(tree.scheme=="content" && DocumentsContract.isTreeUri(tree))
            check(context.contentResolver.persistedUriPermissions.any { it.uri==tree && it.isReadPermission && it.isWritePermission })
            val doc=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree))
            context.contentResolver.query(doc,arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_FLAGS),null,null,null)?.use {
                it.moveToFirst() && it.getString(0)==DocumentsContract.Document.MIME_TYPE_DIR && it.getLong(1) and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE.toLong()!=0L
            } ?: false
        }.getOrDefault(false)
    }
    fun choose(tree:Uri):Boolean=runCatching {
        check(tree.scheme=="content" && DocumentsContract.isTreeUri(tree))
        context.contentResolver.takePersistableUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        check(available(tree.toString()))
        val doc=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree))
        val name=context.contentResolver.query(doc,arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),null,null,null)?.use { if(it.moveToFirst())it.getString(0) else null } ?: "Выбранная папка"
        prefs.edit().putString("tree",tree.toString()).putString("label",name).commit()
    }.getOrDefault(false)
    fun useDownloads()=prefs.edit().remove("tree").remove("label").commit()
    fun save(stem:String,extension:String,mime:String,bytes:ByteArray):Uri {
        val name=DestinationPolicy.filename(stem,extension,UUID.randomUUID().toString().replace("-",""))
        val resolver=context.contentResolver
        val value=selected()
        if(value.isNotEmpty()) {
            check(available(value)) { "DESTINATION_UNAVAILABLE" }
            val tree=Uri.parse(value)
            val parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree))
            val uri=DocumentsContract.createDocument(resolver,parent,mime,name) ?: error("CREATE_FAILED")
            try { resolver.openOutputStream(uri,"w")?.use { it.write(bytes) } ?: error("WRITE_FAILED") }
            catch(e:Exception){runCatching { DocumentsContract.deleteDocument(resolver,uri) };throw e}
            return uri
        }
        val values=ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME,name);put(MediaStore.Downloads.MIME_TYPE,mime);put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS);put(MediaStore.Downloads.IS_PENDING,1) }
        val uri=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values) ?: error("CREATE_FAILED")
        try {
            resolver.openOutputStream(uri,"w")?.use { it.write(bytes) } ?: error("WRITE_FAILED")
            check(resolver.update(uri,ContentValues().apply { put(MediaStore.Downloads.IS_PENDING,0) },null,null)>0)
        } catch(e:Exception){runCatching {resolver.delete(uri,null,null)};throw e}
        return uri
    }
}

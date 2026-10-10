package com.animacode.animadev.projects

object ProjectPolicy {
    const val HOME="/data/data/com.termux/files/home"
    const val QUIZ="$HOME/quizapp"
    private val excluded=setOf("storage","android-sdk","sdk","bin","lib","node_modules","venv","env","tmp","Downloads","Android","build","dist","target","cache","caches","secrets","credentials","keystores","gradle","__pycache__")
    fun permittedPath(path: String): Boolean {
        if(!path.startsWith("$HOME/") || path.endsWith("/"))return false
        val parts=path.removePrefix("$HOME/").split('/')
        return parts.size in 1..6 && parts.none { it.isBlank() || it.startsWith('.') || it in excluded }
    }
    private val originLabel=Regex("github\\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
    fun supported(id:String,path:String,remote:String=""):Boolean =
        (id=="animaquiz" && path==QUIZ) || (validId(id,path) && originLabel.matches(remote))
    fun stableId(path:String):String {
        val bytes=java.security.MessageDigest.getInstance("SHA-256").digest(path.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }.take(32)
    }
    fun canExecute(id: String,path: String,action: String,worktree:Boolean=false,remote:String="")=!worktree && supported(id,path,remote) && action in setOf("STATUS","CHECK","SYNC","TEST","TEST_DEVICE","BUILD_DEBUG")
    fun validId(id: String,path: String)=permittedPath(path) && (if(path==QUIZ) id=="animaquiz" else id==stableId(path))
    fun nextSelection(ids: List<String>,selected: String)=selected.takeIf { it in ids } ?: ""
}

data class BuildConfiguration(val projectId: String,val revision: String,val profile: String,val cache: CacheMode,val destination: String,val artifactName: String,val kind: BuildKind)
enum class CacheMode { INCREMENTAL, RERUN_NO_BUILD_CACHE, CLEAN_NO_BUILD_CACHE }
enum class BuildKind { DEBUG_APK, RELEASE_APK, SIGNED_AAB, DEBUG_AND_TESTS, RELEASE_AND_TESTS }

interface SelectionStorage { fun load():String; fun save(id:String):Boolean }
class ProjectSelection(private val storage:SelectionStorage) {
    fun read(ids:List<String>)=ProjectPolicy.nextSelection(ids,storage.load())
    fun choose(ids:List<String>,id:String):Boolean=if(id in ids)storage.save(id) else false
}

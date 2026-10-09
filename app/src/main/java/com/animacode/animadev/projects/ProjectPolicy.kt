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
    fun supported(id: String,path: String)=id=="animaquiz" && path==QUIZ
    fun canExecute(id: String,path: String,action: String,worktree:Boolean=false)=!worktree && supported(id,path) && action in setOf("STATUS","CHECK","SYNC","TEST","BUILD_DEBUG")
    fun validId(id: String,path: String)=permittedPath(path) && (if(path==QUIZ) id=="animaquiz" else id.matches(Regex("[a-f0-9]{32}")))
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

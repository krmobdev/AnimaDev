package com.animacode.animadev.projects

/** Small, persistable navigation state. Root switches never reuse a tool route. */
data class NavigationState(val section:String="projects",val route:String="list",val project:String="",val job:String="",val settingsParent:String="list") {
    fun switchRoot(value:String)=copy(section=if(value=="termux")"termux" else "projects",route="list",job="")
    fun openProject(id:String)=copy(section="projects",route="workspace",project=id,job="")
    fun tool(value:String)=copy(route=value,job="")
    fun journal(id:String)=copy(route="journal",job=id)
    fun settings()=if(route=="settings")this else copy(route="settings",settingsParent=route)
    fun back():NavigationState? = when {
        route=="settings"->copy(route=settingsParent)
        section=="termux"->switchRoot("projects")
        route=="journal"->copy(route="history",job="")
        route=="wait"->copy(route="tests")
        route in setOf("git","tests","history")->copy(route="workspace",job="")
        route=="workspace"->copy(route="list")
        else->null
    }
    companion object { fun notificationTarget(job:String,restored:Boolean)=if(restored)"" else job }
    val root get()=route=="list"
}

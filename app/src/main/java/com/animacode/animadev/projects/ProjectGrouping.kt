package com.animacode.animadev.projects

/** Only verified common-directory metadata groups worktrees. Never remote URL or names. */
data class ProjectIdentity(val id:String,val path:String,val repositoryPath:String,val worktree:Boolean)
object ProjectGrouping {
    fun groups(items:List<ProjectIdentity>):List<List<ProjectIdentity>> = items.groupBy { if(it.repositoryPath.isNotEmpty())it.repositoryPath else it.path }.values.map {g->g.sortedBy {it.worktree}}
}

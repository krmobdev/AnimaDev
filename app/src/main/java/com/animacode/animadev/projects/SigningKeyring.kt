package com.animacode.animadev.projects

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Device-local list of public keystore references, NEVER key bytes or passwords.
 * The selected default for each project also updates the existing Release settings.
 */
class SigningKeyring(private val context: Context) {
    data class Profile(
        val id: String, val title: String, val uri: String,
        val fileName: String, val alias: String,
    )
    private val prefs = context.getSharedPreferences("signing_keyring_v1", Context.MODE_PRIVATE)
    private val signing = SigningStore(context)
    private fun key(project: String) = "default." + project
    private fun validAlias(text: String) = text.matches(Regex("[A-Za-z0-9_.-]{1,100}"))
    private fun validId(text: String) = text.matches(Regex("[a-f0-9]{16}"))

    fun profiles(): List<Profile> = runCatching {
        val arr = JSONArray(prefs.getString("profiles", "[]") ?: "[]")
        buildList {
            for (i in 0 until arr.length().coerceAtMost(80)) {
                val j = arr.getJSONObject(i)
                val p = Profile(j.getString("id"), j.getString("title"),
                    j.getString("uri"), j.getString("fileName"), j.getString("alias"))
                if (validId(p.id) && p.title.length in 1..70 && validAlias(p.alias) &&
                    SigningPolicy.isKeyName(p.fileName) && p.uri.startsWith("content://")) add(p)
            }
        }
    }.getOrDefault(emptyList())

    fun hasAccess(profile: Profile): Boolean = runCatching {
        val uri = Uri.parse(profile.uri)
        context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
    }.getOrDefault(false)

    private fun save(all: List<Profile>): Boolean = runCatching {
        val arr = JSONArray()
        all.forEach { p -> arr.put(JSONObject()
            .put("id", p.id).put("title", p.title).put("uri", p.uri)
            .put("fileName", p.fileName).put("alias", p.alias)) }
        prefs.edit().putString("profiles", arr.toString()).commit()
    }.getOrDefault(false)

    fun add(title: String, uri: Uri, fileName: String, alias: String): Profile? {
        val t = title.trim().take(70)
        val a = alias.trim()
        if (t.isBlank() || uri.scheme != "content" || !SigningPolicy.isKeyName(fileName) ||
            !validAlias(a)) return null
        if (!context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission
            }) return null
        val current = profiles()
        current.firstOrNull { it.uri == uri.toString() && it.alias == a }?.let { return it }
        if (current.size >= 30) return null
        val profile = Profile(UUID.randomUUID().toString().replace("-", "").take(16),
            t, uri.toString(), fileName, a)
        return if (save(current + profile)) profile else null
    }

    /** Legacy per-project JKS becomes visible in the new keyring without changing
     * its existing Release credentials or bytes. */
    fun importLegacy(project: String) {
        val uri = signing.documentUri(project)
        val file = signing.fileLabel(project)
        val alias = signing.alias(project)
        if (!signing.hasDocument(project) || !validAlias(alias)) return
        val existing = profiles().firstOrNull { it.uri == uri && it.alias == alias }
        val imported = existing ?: add(file, Uri.parse(uri), file, alias)
        if (imported != null && prefs.getString(key(project), null) == null)
            prefs.edit().putString(key(project), imported.id).commit()
    }

    fun defaultId(project: String): String = prefs.getString(key(project), "") ?: ""
    fun selected(project: String): Profile? {
        val all = profiles().filter { hasAccess(it) }
        return all.firstOrNull { it.id == defaultId(project) } ?: all.firstOrNull()
    }

    fun setDefault(project: String, profile: Profile): Boolean {
        if (!validId(profile.id) || !profiles().contains(profile) || !hasAccess(profile))
            return false
        if (!signing.saveDocument(project, Uri.parse(profile.uri), profile.fileName, profile.alias))
            return false
        return prefs.edit().putString(key(project), profile.id).commit()
    }

    /** Never delete the keystore file or revoke a URI belonging to other profiles. */
    fun remove(id: String): Boolean {
        if (!validId(id)) return false
        val previous = profiles()
        if (previous.none { it.id == id }) return false
        if (!save(previous.filterNot { it.id == id })) return false
        SigningSecretVault(context).forget("profile_" + id)
        return true
    }

    fun credentials(profile: Profile, project: String): SigningSecretVault.Credentials? {
        val vault = SigningSecretVault(context)
        return vault.load("profile_" + profile.id, profile.uri, profile.alias)
            ?: if (signing.documentUri(project) == profile.uri && signing.alias(project) == profile.alias)
                vault.load(project, profile.uri, profile.alias)
            else null
    }

    fun saveCredentials(profile: Profile, store: String, key: String): Boolean =
        SigningSecretVault(context).save("profile_" + profile.id, profile.uri, profile.alias, store, key)

    fun forgetCredentials(profile: Profile): Boolean =
        SigningSecretVault(context).forget("profile_" + profile.id)
}

package sk.alexia.os.data

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import sk.alexia.os.crypto.CryptoEngine
import java.io.File

/** Nastavenia systému a šifrovaný trezor poznámok. */
class OsStore(context: Context) {
    private val ctx = context.applicationContext
    private val sp = ctx.getSharedPreferences("alexia_os", Context.MODE_PRIVATE)

    // ---------- PIN ----------

    val hasPin: Boolean get() = sp.contains("pin_hash")

    fun setPin(pin: String) {
        val salt = CryptoEngine.newSalt()
        val hash = CryptoEngine.hashPin(pin, salt)
        sp.edit()
            .putString("pin_salt", b64(salt))
            .putString("pin_hash", b64(hash))
            .putInt("pin_fails", 0)
            .apply()
    }

    fun checkPin(pin: String): Boolean {
        val salt = sp.getString("pin_salt", null)?.let(::unb64) ?: return false
        val hash = sp.getString("pin_hash", null)?.let(::unb64) ?: return false
        val ok = CryptoEngine.verifyPin(pin, salt, hash)
        sp.edit().putInt("pin_fails", if (ok) 0 else failedAttempts + 1).apply()
        return ok
    }

    val failedAttempts: Int get() = sp.getInt("pin_fails", 0)

    // ---------- Vzhľad ----------

    var wallpaper: Int
        get() = sp.getInt("wallpaper", 0)
        set(v) = sp.edit().putInt("wallpaper", v).apply()

    /** Vlastná tapeta z galérie (content:// URI), null = vstavaná animovaná. */
    var customWallpaper: String?
        get() = sp.getString("custom_wallpaper", null)
        set(v) = sp.edit().putString("custom_wallpaper", v).apply()

    var privacyMode: Boolean
        get() = sp.getBoolean("privacy_mode", false)
        set(v) = sp.edit().putBoolean("privacy_mode", v).apply()

    // ---------- Šifrovaný trezor ----------

    data class Note(val id: Long, val title: String, val body: String, val time: Long)

    private val vaultFile = File(ctx.filesDir, "vault.alx")

    fun loadNotes(): List<Note> = runCatching {
        if (!vaultFile.exists()) return emptyList()
        val json = JSONArray(String(CryptoEngine.decrypt(vaultFile.readBytes())))
        (0 until json.length()).map { i ->
            val o = json.getJSONObject(i)
            Note(o.getLong("id"), o.getString("t"), o.getString("b"), o.getLong("time"))
        }
    }.getOrDefault(emptyList())

    fun saveNotes(notes: List<Note>) {
        val json = JSONArray()
        notes.forEach { n ->
            json.put(JSONObject().put("id", n.id).put("t", n.title).put("b", n.body).put("time", n.time))
        }
        val tmp = File(ctx.filesDir, "vault.alx.tmp")
        tmp.writeBytes(CryptoEngine.encrypt(json.toString().toByteArray()))
        tmp.renameTo(vaultFile)
    }

    /** Takto vidí trezor niekto, kto ukradne súbor z telefónu. */
    fun rawVault(): ByteArray = if (vaultFile.exists()) vaultFile.readBytes() else ByteArray(0)

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)
}

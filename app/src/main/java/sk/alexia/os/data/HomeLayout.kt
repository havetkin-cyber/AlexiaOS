package sk.alexia.os.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import sk.alexia.os.crypto.CryptoEngine
import java.io.File

/** Rozloženie plochy – uložené zašifrované (ani rozloženie appiek neprezradí, čo používaš). */
data class HomeLayout(
    val pinned: List<String> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val widgets: List<Int> = emptyList(),
    val themedIcons: Boolean = true,
) {
    data class Folder(val name: String, val apps: List<String>)

    companion object {
        private const val FILE = "layout.alx"

        fun load(ctx: Context): HomeLayout = runCatching {
            val f = File(ctx.filesDir, FILE)
            if (!f.exists()) return HomeLayout()
            val o = JSONObject(String(CryptoEngine.decrypt(f.readBytes())))
            HomeLayout(
                pinned = o.optJSONArray("pinned").strings(),
                folders = o.optJSONArray("folders")?.let { arr ->
                    (0 until arr.length()).map { i ->
                        val fo = arr.getJSONObject(i)
                        Folder(fo.getString("name"), fo.optJSONArray("apps").strings())
                    }
                }.orEmpty(),
                widgets = o.optJSONArray("widgets")?.let { arr -> (0 until arr.length()).map { arr.getInt(it) } }.orEmpty(),
                themedIcons = o.optBoolean("themed", true),
            )
        }.getOrDefault(HomeLayout())

        fun save(ctx: Context, l: HomeLayout) {
            val o = JSONObject()
                .put("pinned", JSONArray(l.pinned))
                .put("folders", JSONArray(l.folders.map { JSONObject().put("name", it.name).put("apps", JSONArray(it.apps)) }))
                .put("widgets", JSONArray(l.widgets))
                .put("themed", l.themedIcons)
            val tmp = File(ctx.filesDir, "$FILE.tmp")
            tmp.writeBytes(CryptoEngine.encrypt(o.toString().toByteArray()))
            tmp.renameTo(File(ctx.filesDir, FILE))
        }

        private fun JSONArray?.strings(): List<String> =
            if (this == null) emptyList() else (0 until length()).map { getString(it) }
    }
}

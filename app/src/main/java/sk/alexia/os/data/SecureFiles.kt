package sk.alexia.os.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import sk.alexia.os.crypto.CryptoEngine
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Šifrované úložisko súborov (obálkové šifrovanie):
 * - každý súbor má vlastný náhodný kľúč AES-256,
 * - ten je zabalený (zašifrovaný) hlavným kľúčom z Android Keystore,
 * - obsah sa šifruje AES-256-GCM po 1 MB blokoch, takže zvládne aj veľké videá,
 * - aj zoznam súborov (názvy, typy) je zašifrovaný.
 */
class SecureFiles(context: Context) {
    private val ctx = context.applicationContext
    private val dir = File(ctx.filesDir, "secure").apply { mkdirs() }
    private val indexFile = File(dir, "index.alx")
    private val random = SecureRandom()

    data class Entry(val id: String, val name: String, val mime: String, val size: Long, val added: Long, val wrappedKey: String) {
        val isImage: Boolean get() = mime.startsWith("image/")
        val isVideo: Boolean get() = mime.startsWith("video/")
    }

    fun list(): List<Entry> = runCatching {
        if (!indexFile.exists()) return emptyList()
        val arr = JSONArray(String(CryptoEngine.decrypt(indexFile.readBytes())))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Entry(o.getString("id"), o.getString("name"), o.getString("mime"), o.getLong("size"), o.getLong("added"), o.getString("key"))
        }
    }.getOrDefault(emptyList())

    private fun saveIndex(entries: List<Entry>) {
        val arr = JSONArray()
        entries.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("mime", it.mime)
                .put("size", it.size).put("added", it.added).put("key", it.wrappedKey))
        }
        val tmp = File(dir, "index.alx.tmp")
        tmp.writeBytes(CryptoEngine.encrypt(arr.toString().toByteArray()))
        tmp.renameTo(indexFile)
    }

    /** Zašifruje súbor z galérie / správcu súborov a pridá ho do úložiska. */
    fun import(uri: Uri): Entry {
        val cr = ctx.contentResolver
        var name = "súbor"
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) name = c.getString(0) ?: name
        }
        val mime = cr.getType(uri) ?: "application/octet-stream"
        val key = ByteArray(32).also { random.nextBytes(it) }
        val id = System.currentTimeMillis().toString(36) + random.nextInt(1 shl 20).toString(36)
        val out = File(dir, "$id.alx")
        var size = 0L
        cr.openInputStream(uri)?.use { input ->
            out.outputStream().buffered().use { os -> size = encryptStream(key, input, os) }
        } ?: error("Súbor sa nedá otvoriť")
        val entry = Entry(id, name, mime, size, System.currentTimeMillis(), Base64.encodeToString(CryptoEngine.encrypt(key), Base64.NO_WRAP))
        saveIndex(listOf(entry) + list())
        key.fill(0)
        return entry
    }

    /** Dešifruje do pamäte (na náhľad fotky). */
    fun readBytes(e: Entry, maxBytes: Long = 40L * 1024 * 1024): ByteArray? {
        if (e.size > maxBytes) return null
        val out = java.io.ByteArrayOutputStream(e.size.toInt().coerceAtLeast(16))
        decryptTo(e, out)
        return out.toByteArray()
    }

    /** Dešifruje do súboru, ktorý si používateľ vybral (export). */
    fun export(e: Entry, target: Uri) {
        ctx.contentResolver.openOutputStream(target)?.use { decryptTo(e, it) } ?: error("Cieľ sa nedá otvoriť")
    }

    fun delete(e: Entry) {
        File(dir, "${e.id}.alx").delete()
        saveIndex(list().filterNot { it.id == e.id })
    }

    fun rawPreview(e: Entry): ByteArray =
        File(dir, "${e.id}.alx").inputStream().use { it.readNBytesCompat(96) }

    private fun decryptTo(e: Entry, out: OutputStream) {
        val key = CryptoEngine.decrypt(Base64.decode(e.wrappedKey, Base64.NO_WRAP))
        try {
            File(dir, "${e.id}.alx").inputStream().buffered().use { decryptStream(key, it, out) }
        } finally {
            key.fill(0)
        }
    }

    // ---- Formát: [4 B dĺžka bloku][12 B IV][šifrovaný blok + 16 B tag] ... ----

    private fun encryptStream(key: ByteArray, input: InputStream, output: OutputStream): Long {
        val dos = DataOutputStream(output)
        val buf = ByteArray(CHUNK)
        var total = 0L
        var index = 0
        while (true) {
            val n = input.readFully(buf)
            if (n <= 0) break
            val iv = ByteArray(12).also { random.nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(index.toString().toByteArray()) // poradie blokov sa nedá prehodiť
            val ct = cipher.doFinal(buf, 0, n)
            dos.writeInt(ct.size)
            dos.write(iv)
            dos.write(ct)
            total += n
            index++
            if (n < CHUNK) break
        }
        dos.flush()
        return total
    }

    private fun decryptStream(key: ByteArray, input: InputStream, output: OutputStream) {
        val dis = DataInputStream(input)
        var index = 0
        while (true) {
            val len = try { dis.readInt() } catch (_: java.io.EOFException) { break }
            val iv = ByteArray(12).also { dis.readFully(it) }
            val ct = ByteArray(len).also { dis.readFully(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(index.toString().toByteArray())
            output.write(cipher.doFinal(ct))
            index++
        }
        output.flush()
    }

    private fun InputStream.readFully(buf: ByteArray): Int {
        var off = 0
        while (off < buf.size) {
            val r = read(buf, off, buf.size - off)
            if (r < 0) break
            off += r
        }
        return off
    }

    private fun InputStream.readNBytesCompat(n: Int): ByteArray {
        val b = ByteArray(n)
        val r = readFully(b)
        return b.copyOf(r)
    }

    companion object {
        private const val CHUNK = 1 shl 20
    }
}

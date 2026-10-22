package sk.alexia.os.crypto

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Šifrovacie jadro AlexiaOS. Všetko je skutočná kryptografia:
 * - hlavný kľúč AES-256 žije v Android Keystore (v čipe, ak ho telefón má) a nedá sa vytiahnuť,
 * - dáta sa šifrujú AES-256-GCM (dôvernosť + kontrola, že s nimi nikto nemanipuloval),
 * - PIN sa ukladá len ako PBKDF2-SHA256 odtlačok so soľou (210 000 iterácií),
 * - správy používajú výmenu kľúčov ECDH (P-256) a AES-256-GCM, teda end-to-end šifrovanie.
 */
object CryptoEngine {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val MASTER_ALIAS = "alexia_os_master"
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12
    const val PBKDF2_ITERATIONS = 210_000

    private val random = SecureRandom()

    // ---------- Hlavný kľúč v Keystore ----------

    private fun masterKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(MASTER_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            MASTER_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        // StrongBox = samostatný bezpečnostný čip (ak ho telefón má)
        if (Build.VERSION.SDK_INT >= 28) {
            runCatching {
                gen.init(builder.setIsStrongBoxBacked(true).build())
                return gen.generateKey()
            }
            builder.setIsStrongBoxBacked(false)
        }
        gen.init(builder.build())
        return gen.generateKey()
    }

    /** Zašifruje dáta hlavným kľúčom. Výstup = IV (12 B) + šifrovaný text + tag. */
    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        return cipher.iv + cipher.doFinal(plain)
    }

    fun decrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(GCM_TAG_BITS, data, 0, IV_BYTES))
        return cipher.doFinal(data, IV_BYTES, data.size - IV_BYTES)
    }

    // ---------- PIN ----------

    fun newSalt(): ByteArray = ByteArray(16).also { random.nextBytes(it) }

    fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    /** Porovnanie v konštantnom čase (útočník nezistí PIN podľa rýchlosti odpovede). */
    fun verifyPin(pin: String, salt: ByteArray, expected: ByteArray): Boolean =
        MessageDigest.isEqual(hashPin(pin, salt), expected)

    // ---------- End-to-end šifrovanie správ (ECDH P-256 + AES-256-GCM) ----------

    class Identity(val name: String, val keyPair: KeyPair) {
        val fingerprint: String get() = fingerprint(keyPair.public)
    }

    fun newIdentity(name: String): Identity {
        val gen = KeyPairGenerator.getInstance("EC")
        gen.initialize(ECGenParameterSpec("secp256r1"), random)
        return Identity(name, gen.generateKeyPair())
    }

    /** Spoločný kľúč dvoch strán – každá ho vypočíta sama, nikdy sa neposiela. */
    fun sharedKey(mine: KeyPair, theirs: PublicKey): ByteArray {
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(mine.private)
        ka.doPhase(theirs, true)
        // HKDF-zjednodušenie: SHA-256 zdieľaného tajomstva s doménovým označením
        return MessageDigest.getInstance("SHA-256").digest(ka.generateSecret() + "AlexiaOS-E2E-v1".toByteArray())
    }

    fun sealMessage(key: ByteArray, text: String): ByteArray {
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        return iv + cipher.doFinal(text.toByteArray())
    }

    fun openMessage(key: ByteArray, sealed: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, sealed, 0, IV_BYTES))
        return String(cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES))
    }

    fun fingerprint(key: PublicKey): String =
        MessageDigest.getInstance("SHA-256").digest(key.encoded).take(8)
            .joinToString(" ") { "%02X".format(it) }

    // ---------- Stav ochrany zariadenia ----------

    data class Status(
        val storageEncrypted: Boolean,
        val storageDetail: String,
        val hardwareKey: Boolean,
        val strongBox: Boolean,
        val keyDetail: String,
    )

    fun status(ctx: Context): Status {
        val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
        val enc = runCatching { dpm.storageEncryptionStatus }.getOrDefault(DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED)
        val (encrypted, encDetail) = when (enc) {
            DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER -> true to "Šifrovanie súborov (FBE) aktívne"
            DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE -> true to "Celé úložisko je šifrované"
            DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY -> true to "Šifrované predvoleným kľúčom – nastav PIN telefónu"
            else -> false to "Úložisko nie je šifrované"
        }

        var hw = false
        var sb = false
        runCatching {
            val key = masterKey()
            val factory = SecretKeyFactory.getInstance(key.algorithm, KEYSTORE)
            val info = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
            hw = if (Build.VERSION.SDK_INT >= 31) {
                info.securityLevel == KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT ||
                    info.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
            } else {
                @Suppress("DEPRECATION") info.isInsideSecureHardware
            }
            sb = Build.VERSION.SDK_INT >= 31 && info.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
        }
        val keyDetail = when {
            sb -> "Kľúč je v samostatnom bezpečnostnom čipe (StrongBox)"
            hw -> "Kľúč je v zabezpečenej časti procesora (TEE)"
            else -> "Kľúč je chránený softvérovo"
        }
        return Status(encrypted, encDetail, hw, sb, keyDetail)
    }

    fun toHex(bytes: ByteArray, max: Int = 48): String =
        bytes.take(max).joinToString("") { "%02x".format(it) } + if (bytes.size > max) "…" else ""
}

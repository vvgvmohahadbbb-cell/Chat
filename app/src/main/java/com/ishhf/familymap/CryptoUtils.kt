package com.ishhf.familymap

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * تشفير/فك تشفير رسائل الدردشة بـ AES-256/GCM قبل تخزينها بـ Firestore.
 * ملاحظة: المفتاح مخزن جوا التطبيق، فهاد تشفير "أثناء التخزين" (حتى لو حدا وصل
 * مباشرة لقاعدة البيانات ما بيقدر يقرا الرسائل)، مش تشفير طرف-لطرف كامل.
 */
object CryptoUtils {
    private const val KEY_STRING = "F@milyMap-Secret-Key-32Bytes!!!!" // لازم يضل 32 محرف بالضبط
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    private fun keySpec(): SecretKeySpec {
        val keyBytes = KEY_STRING.toByteArray(Charsets.UTF_8).copyOf(32)
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(plainText: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = iv + encrypted
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            plainText
        }
    }

    fun decrypt(cipherTextBase64: String): String {
        return try {
            val combined = Base64.decode(cipherTextBase64, Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val encrypted = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: Exception) {
            "⚠️ رسالة غير قابلة لفك التشفير"
        }
    }
}

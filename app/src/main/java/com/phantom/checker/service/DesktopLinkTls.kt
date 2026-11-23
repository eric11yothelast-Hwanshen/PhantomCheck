package com.phantom.checker.service

import android.content.Context
import com.phantom.checker.util.DesktopLinkStore
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date

data class DesktopLinkTlsMaterial(
    val keyStore: KeyStore,
    val keyPassword: String,
    val certFingerprintSha256: String
)

/**
 * #449 自簽憑證管理：
 * - 嚴禁 AndroidKeyStore（硬體金鑰在部分機型會拒絕 TLS 握手所需私鑰運算）；
 * - 固定使用一般 software provider RSA 2048 + BouncyCastle X509v3 自簽 + PKCS12 KeyStore；
 * - KeyStore 檔存 filesDir，密碼存 EncryptedSharedPreferences；私鑰與密碼永不寫入 log。
 */
object DesktopLinkTls {

    const val KEY_ALIAS = "phantomlink"
    private const val KEYSTORE_FILE_NAME = "desktop_link.p12"
    private val bcProvider by lazy { BouncyCastleProvider() }

    @Synchronized
    fun loadOrCreate(ctx: Context): DesktopLinkTlsMaterial {
        val appCtx = ctx.applicationContext
        val ksFile = File(appCtx.filesDir, KEYSTORE_FILE_NAME)
        val password = DesktopLinkStore.getOrCreateKeystorePassword(appCtx)
        val pwdChars = password.toCharArray()

        if (ksFile.exists()) {
            val loaded = runCatching {
                val ks = KeyStore.getInstance("PKCS12")
                ksFile.inputStream().use { ks.load(it, pwdChars) }
                val cert = ks.getCertificate(KEY_ALIAS) as? X509Certificate
                if (cert != null && cert.notAfter.after(Date())) {
                    DesktopLinkTlsMaterial(
                        keyStore = ks,
                        keyPassword = password,
                        certFingerprintSha256 = sha256Hex(cert.encoded)
                    )
                } else {
                    null
                }
            }.getOrNull()
            if (loaded != null) return loaded
        }

        // 產生 software-backed RSA 2048 金鑰對
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, SecureRandom())
        val keyPair = kpg.generateKeyPair()

        val now = System.currentTimeMillis()
        val notBefore = Date(now - 24L * 60L * 60L * 1000L)
        val notAfter = Date(now + 10L * 365L * 24L * 60L * 60L * 1000L)
        val serial = BigInteger(128, SecureRandom()).abs()
        val subject = X500Name("CN=PhantomCheck-LAN, O=PhantomCheck")

        val builder = JcaX509v3CertificateBuilder(
            subject,
            serial,
            notBefore,
            notAfter,
            subject,
            keyPair.public
        )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
        builder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment)
        )

        val signer = JcaContentSignerBuilder("SHA256withRSA")
            .setProvider(bcProvider)
            .build(keyPair.private)
        val cert = JcaX509CertificateConverter()
            .setProvider(bcProvider)
            .getCertificate(builder.build(signer))

        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry(KEY_ALIAS, keyPair.private, pwdChars, arrayOf(cert))
        ksFile.outputStream().use { ks.store(it, pwdChars) }

        return DesktopLinkTlsMaterial(
            keyStore = ks,
            keyPassword = password,
            certFingerprintSha256 = sha256Hex(cert.encoded)
        )
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}

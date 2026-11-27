package com.phantom.checker.desktop

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
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import java.util.Properties

data class DesktopTlsMaterial(
    val keyStore: KeyStore,
    val keyPassword: String,
    val certFingerprintSha256: String
)

/**
 * #476 桌面端 WSS 伺服器自簽憑證管理：
 * - RSA 2048 + BouncyCastle X509v3（CN=PhantomCheck-Desktop, O=PhantomCheck）+ PKCS12 KeyStore
 * - KeyStore 檔與密碼存於 DesktopPaths.configDir（owner-only 權限；私鑰與密碼永不寫入診斷日誌）
 * - SHA-256 憑證指紋作為手機端 TLS pinning 與 QR 配對綁定依據
 */
object DesktopTls {

    const val KEY_ALIAS = "phantomdesktop"
    private val bcProvider by lazy { BouncyCastleProvider() }
    private val secureRandom = SecureRandom()

    @Synchronized
    fun loadOrCreate(): DesktopTlsMaterial {
        val ksFile = DesktopPaths.serverKeystoreFile
        val password = loadOrCreatePassword()
        val pwdChars = password.toCharArray()

        if (ksFile.exists()) {
            val loaded = runCatching {
                val ks = KeyStore.getInstance("PKCS12")
                ksFile.inputStream().use { ks.load(it, pwdChars) }
                val cert = ks.getCertificate(KEY_ALIAS) as? X509Certificate
                if (cert != null && cert.notAfter.after(Date())) {
                    DesktopTlsMaterial(
                        keyStore = ks,
                        keyPassword = password,
                        certFingerprintSha256 = sha256Hex(cert.encoded)
                    )
                } else {
                    null
                }
            }.onFailure { err ->
                DesktopDiagnostics.warn("TLS", "Existing keystore unreadable, regenerating: ${err.javaClass.simpleName}")
            }.getOrNull()
            if (loaded != null) return loaded
        }

        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, secureRandom)
        val keyPair = kpg.generateKeyPair()

        val now = System.currentTimeMillis()
        val notBefore = Date(now - 24L * 60L * 60L * 1000L)
        val notAfter = Date(now + 10L * 365L * 24L * 60L * 60L * 1000L)
        val serial = BigInteger(128, secureRandom).abs()
        val subject = X500Name("CN=PhantomCheck-Desktop, O=PhantomCheck")

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
        atomicWriteFile(ksFile, restrictOwnerOnly = true) { tmp ->
            tmp.outputStream().use { ks.store(it, pwdChars) }
        }

        val fp = sha256Hex(cert.encoded)
        DesktopDiagnostics.info("TLS", "Generated new self-signed PKCS12 certificate (fp=${fp.take(8)}…)")
        return DesktopTlsMaterial(
            keyStore = ks,
            keyPassword = password,
            certFingerprintSha256 = fp
        )
    }

    private fun loadOrCreatePassword(): String {
        val propsFile = DesktopPaths.serverTlsPropsFile
        val props = Properties()
        if (propsFile.exists()) {
            runCatching {
                propsFile.bufferedReader(Charsets.UTF_8).use { props.load(it) }
            }.onFailure { err ->
                DesktopDiagnostics.warn("TLS", "Failed reading tls.properties: ${err.javaClass.simpleName}")
            }
            val existing = props.getProperty("ksPassword").orEmpty().trim()
            if (existing.isNotBlank()) return existing
        }
        val created = randomHex(24)
        props.setProperty("ksPassword", created)
        runCatching {
            atomicWriteFile(propsFile, restrictOwnerOnly = true) { tmp ->
                tmp.bufferedWriter(Charsets.UTF_8).use { props.store(it, "PhantomCheck Desktop TLS") }
            }
        }.onFailure { err ->
            DesktopDiagnostics.warn("TLS", "Failed saving tls.properties: ${err.javaClass.simpleName}")
        }
        return created
    }

    /**
     * Atomically writes [target] via a same-directory `.tmp` file + `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)`
     * (falling back to `REPLACE_EXISTING` on filesystems without atomic move support).
     * When [restrictOwnerOnly] is true, owner-only permissions are applied before writing sensitive content.
     */
    internal fun atomicWriteFile(
        target: File,
        restrictOwnerOnly: Boolean = true,
        writeBlock: (File) -> Unit
    ) {
        val parent = target.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        val tmp = File.createTempFile("${target.name}.", ".tmp", parent)
        try {
            if (restrictOwnerOnly) {
                DesktopPaths.restrictOwnerOnlyPermissions(tmp)
            }
            writeBlock(tmp)
            if (restrictOwnerOnly) {
                DesktopPaths.restrictOwnerOnlyPermissions(tmp)
            }
            try {
                Files.move(
                    tmp.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    tmp.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
            if (restrictOwnerOnly) {
                DesktopPaths.restrictOwnerOnlyPermissions(target)
            }
        } catch (t: Throwable) {
            runCatching { tmp.delete() }
            throw t
        }
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun randomHex(numBytes: Int = 32): String {
        val bytes = ByteArray(numBytes)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

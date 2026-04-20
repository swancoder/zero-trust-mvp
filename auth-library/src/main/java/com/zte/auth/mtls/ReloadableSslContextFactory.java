package com.zte.auth.mtls;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Hot-reloadable Netty {@link SslContext} factory for <em>client-side</em> mTLS.
 *
 * <p>Holds the current {@link SslContext} in an {@link AtomicReference}.
 * Reactor Netty's {@code HttpClient.secure(Consumer<SslContextSpec>)} lambda is
 * evaluated per TCP connection, so calling {@code factory.current()} inside that
 * lambda ensures every new outbound connection picks up the latest context after
 * a {@link #refresh()} call — without restarting the JVM or rebuilding the
 * {@code HttpClient} bean.
 *
 * <p>Usage in a Spring configuration:
 * <pre>{@code
 * HttpClient.create()
 *     .secure(spec -> spec.sslContext(sslFactory.current()));
 * }</pre>
 *
 * <p>Schedule {@link #refresh()} periodically (e.g. {@code @Scheduled(fixedDelay=300_000)})
 * to detect certificate file changes and hot-swap the context. Only rebuilds when the
 * keystore file modification time changes.
 *
 * <p><strong>Server-side limitation:</strong> Spring Boot's embedded server (Netty for
 * WebFlux, Tomcat for Web) builds its SSL context once at startup from
 * {@code server.ssl.*} properties. Server-side cert rotation requires a rolling restart.
 * See ADR-004 for the production migration path.
 */
public class ReloadableSslContextFactory {

    private static final Logger log = LoggerFactory.getLogger(ReloadableSslContextFactory.class);

    private final Path keystorePath;
    private final Path truststorePath;
    private final char[] password;

    private final AtomicReference<SslContext> contextRef = new AtomicReference<>();
    private volatile long keystoreLastModified = -1L;

    /**
     * Creates the factory and immediately builds the initial {@link SslContext}.
     *
     * @param keystorePath   path to the PKCS12 client keystore (key + cert + CA chain)
     * @param truststorePath path to the PKCS12 truststore (CA cert only)
     * @param password       password for both keystores
     */
    public ReloadableSslContextFactory(String keystorePath, String truststorePath, String password) {
        this.keystorePath = Path.of(keystorePath);
        this.truststorePath = Path.of(truststorePath);
        this.password = password.toCharArray();
        this.contextRef.set(build());
        this.keystoreLastModified = this.keystorePath.toFile().lastModified();
        log.info("SslContext loaded from keystore: {}", keystorePath);
    }

    /**
     * Returns the current {@link SslContext}.
     * Called per-connection by Reactor Netty — always reflects the latest cert.
     */
    public SslContext current() {
        return contextRef.get();
    }

    /**
     * Checks if the keystore file has been modified since last load.
     * If so, rebuilds the {@link SslContext} and atomically swaps the reference.
     * No-op if the file is unchanged.
     */
    public void refresh() {
        long mtime = keystorePath.toFile().lastModified();
        if (mtime != keystoreLastModified) {
            log.info("Keystore file changed (mtime {} → {}), reloading SslContext: {}",
                    keystoreLastModified, mtime, keystorePath);
            contextRef.set(build());
            keystoreLastModified = mtime;
            log.info("SslContext hot-swapped successfully");
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private SslContext build() {
        try {
            KeyManagerFactory kmf = buildKeyManagerFactory();
            TrustManagerFactory tmf = buildTrustManagerFactory();
            return SslContextBuilder.forClient()
                    .keyManager(kmf)
                    .trustManager(tmf)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to build SslContext from keystore: " + keystorePath, e);
        }
    }

    private KeyManagerFactory buildKeyManagerFactory()
            throws KeyStoreException, IOException, CertificateException,
                   NoSuchAlgorithmException, UnrecoverableKeyException {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (var is = Files.newInputStream(keystorePath)) {
            ks.load(is, password);
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, password);
        return kmf;
    }

    private TrustManagerFactory buildTrustManagerFactory()
            throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        KeyStore ts = KeyStore.getInstance("PKCS12");
        try (var is = Files.newInputStream(truststorePath)) {
            ts.load(is, password);
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ts);
        return tmf;
    }
}

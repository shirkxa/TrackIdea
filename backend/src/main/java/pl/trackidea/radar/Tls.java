package pl.trackidea.radar;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Collections;

/**
 * Zaufane certyfikaty do pobierania danych: domyślne certyfikaty Javy + „Certum Trusted Root CA”
 * (publiczny urząd certyfikacji używany przez m.in. dane.gov.pl, którego część wydań JDK jeszcze nie zawiera).
 * Certyfikat wyeksportowano z systemowego magazynu macOS (SHA-256 FE:76:96:57:…:78:FD).
 */
final class Tls {

    private static final String[] EXTRA_ROOTS = {"/tls/certum-trusted-root-ca.pem"};
    private static volatile SSLContext context;

    private Tls() {
    }

    static SSLContext context() {
        if (context == null) {
            synchronized (Tls.class) {
                if (context == null) {
                    context = build();
                }
            }
        }
        return context;
    }

    private static SSLContext build() {
        try {
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(null, null);
            // domyślne certyfikaty JDK
            KeyStore cacerts = KeyStore.getInstance(KeyStore.getDefaultType());
            try (InputStream in = java.nio.file.Files.newInputStream(
                    java.nio.file.Path.of(System.getProperty("java.home"), "lib", "security", "cacerts"))) {
                cacerts.load(in, null);
            }
            for (String alias : Collections.list(cacerts.aliases())) {
                Certificate c = cacerts.getCertificate(alias);
                if (c != null) store.setCertificateEntry(alias, c);
            }
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            for (String path : EXTRA_ROOTS) {
                try (InputStream in = Tls.class.getResourceAsStream(path)) {
                    if (in != null) store.setCertificateEntry("trackidea" + path, cf.generateCertificate(in));
                }
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(store);
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tmf.getTrustManagers(), null);
            return ctx;
        } catch (Exception e) {
            throw new IllegalStateException("Nie można przygotować certyfikatów TLS", e);
        }
    }
}

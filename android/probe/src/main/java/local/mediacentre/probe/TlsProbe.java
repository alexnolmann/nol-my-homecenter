package local.mediacentre.probe;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

/** Explicit TLS 1.2, trusted CA validation, and unchanged hostname verification. */
final class TlsProbe {
    static JSONObject run(Context context, String address, boolean localCa, String caseName) throws Exception {
        URL url = new URL(address);
        if (!"https".equals(url.getProtocol())) {
            throw new IOException("The API probe requires HTTPS.");
        }
        TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        if (localCa) {
            Certificate authority;
            try (InputStream input = context.getAssets().open("h0-ca.pem")) {
                authority = CertificateFactory.getInstance("X.509").generateCertificate(input);
            }
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(null, null);
            store.setCertificateEntry("h0-local-ca", authority);
            trust.init(store);
        } else {
            trust.init((KeyStore) null);
        }
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(null, trust.getTrustManagers(), null);
        Tls12Factory sockets = new Tls12Factory(ssl.getSocketFactory());
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setSSLSocketFactory(sockets);
        HostnameVerifier standardVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
        connection.setHostnameVerifier((hostname, session) -> {
            boolean accepted = standardVerifier.verify(hostname, session);
            Log.i("MediaCentreH0", "H0_03_HOSTNAME_" + (accepted ? "ACCEPTED" : "REJECTED")
                    + " case=" + caseName);
            return accepted;
        });
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(8000);
        connection.setUseCaches(false);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/json");
        try {
            if (connection.getResponseCode() != 200) {
                throw new IOException("HTTP " + connection.getResponseCode());
            }
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[2048];
                int length;
                while ((length = input.read(buffer)) != -1) {
                    if (body.size() + length > 65536) {
                        throw new IOException("Probe response exceeds 64 KiB.");
                    }
                    body.write(buffer, 0, length);
                }
            }
            JSONObject payload = new JSONObject(body.toString("UTF-8"));
            if (!"mediacentre-h0".equals(payload.getJSONObject("data").getString("probe"))) {
                throw new IOException("Unexpected probe response.");
            }
            JSONObject result = new JSONObject();
            result.put("protocol", sockets.negotiatedProtocol());
            result.put("cipher", connection.getCipherSuite());
            result.put("payload", payload);
            return result;
        } finally {
            connection.disconnect();
        }
    }

    private static final class Tls12Factory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;
        private SSLSocket lastSocket;

        Tls12Factory(SSLSocketFactory delegate) {
            this.delegate = delegate;
        }

        private Socket enable(Socket socket) throws IOException {
            lastSocket = (SSLSocket) socket;
            lastSocket.setEnabledProtocols(new String[]{"TLSv1.2"});
            List<String> supported = Arrays.asList(lastSocket.getSupportedCipherSuites());
            List<String> allowed = new ArrayList<>();
            for (String cipher : new String[]{
                    "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
                    "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
                    "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
                    "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384",
                    "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
                    "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA"}) {
                if (supported.contains(cipher)) allowed.add(cipher);
            }
            if (allowed.isEmpty()) throw new IOException("No compatible ECDHE/AES cipher.");
            lastSocket.setEnabledCipherSuites(allowed.toArray(new String[0]));
            Log.i("MediaCentreH0", "H0_03_SOCKET protocols=" + Arrays.toString(lastSocket.getEnabledProtocols())
                    + " ciphers=" + Arrays.toString(lastSocket.getEnabledCipherSuites()));
            return socket;
        }

        String negotiatedProtocol() {
            return lastSocket == null ? "unknown" : lastSocket.getSession().getProtocol();
        }

        @Override public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
        @Override public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }
        @Override public Socket createSocket() throws IOException { return enable(delegate.createSocket()); }
        @Override public Socket createSocket(Socket socket, String host, int port, boolean close) throws IOException {
            return enable(delegate.createSocket(socket, host, port, close));
        }
        @Override public Socket createSocket(String host, int port) throws IOException {
            return enable(delegate.createSocket(host, port));
        }
        @Override public Socket createSocket(String host, int port, InetAddress local, int localPort) throws IOException {
            return enable(delegate.createSocket(host, port, local, localPort));
        }
        @Override public Socket createSocket(InetAddress host, int port) throws IOException {
            return enable(delegate.createSocket(host, port));
        }
        @Override public Socket createSocket(InetAddress host, int port, InetAddress local, int localPort) throws IOException {
            return enable(delegate.createSocket(host, port, local, localPort));
        }
    }
}

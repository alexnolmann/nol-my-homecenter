package local.mediacentre.client.data;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/** API 19 explicitly enables TLS 1.2; CA and hostname verification stay active. */
final class Tls12SocketFactory extends SSLSocketFactory {
    private final SSLSocketFactory delegate;
    Tls12SocketFactory(SSLSocketFactory delegate) {this.delegate=delegate;}
    private Socket enable(Socket socket) throws IOException {
        SSLSocket ssl=(SSLSocket)socket;
        ssl.setEnabledProtocols(new String[]{"TLSv1.2"});
        List<String> supported=Arrays.asList(ssl.getSupportedCipherSuites());
        List<String> allowed=new ArrayList<>();
        for (String cipher:new String[]{"TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256","TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384","TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256","TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA"}) if (supported.contains(cipher)) allowed.add(cipher);
        if (allowed.isEmpty()) throw new IOException("No compatible TLS 1.2 cipher.");
        ssl.setEnabledCipherSuites(allowed.toArray(new String[0]));
        return socket;
    }
    @Override public String[] getDefaultCipherSuites(){return delegate.getDefaultCipherSuites();}
    @Override public String[] getSupportedCipherSuites(){return delegate.getSupportedCipherSuites();}
    @Override public Socket createSocket() throws IOException{return enable(delegate.createSocket());}
    @Override public Socket createSocket(Socket s,String host,int port,boolean close) throws IOException{return enable(delegate.createSocket(s,host,port,close));}
    @Override public Socket createSocket(String host,int port) throws IOException{return enable(delegate.createSocket(host,port));}
    @Override public Socket createSocket(String host,int port,InetAddress local,int localPort) throws IOException{return enable(delegate.createSocket(host,port,local,localPort));}
    @Override public Socket createSocket(InetAddress host,int port) throws IOException{return enable(delegate.createSocket(host,port));}
    @Override public Socket createSocket(InetAddress host,int port,InetAddress local,int localPort) throws IOException{return enable(delegate.createSocket(host,port,local,localPort));}
}

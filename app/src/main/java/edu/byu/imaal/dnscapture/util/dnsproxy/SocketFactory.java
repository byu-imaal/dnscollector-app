package edu.byu.imaal.dnscapture.util.dnsproxy;

import android.net.VpnService;

import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.LayeredConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

public class SocketFactory {
    public static class NormalSocketFactory implements ConnectionSocketFactory {
        private final VpnService context;

        public NormalSocketFactory(VpnService context) {
            this.context = context;
        }

        @Override
        public Socket createSocket(HttpContext context) throws IOException {
            Socket socket = new Socket();
            this.context.protect(socket);
            return socket;
        }

        @Override
        public Socket connectSocket(TimeValue connectTimeout, Socket socket, HttpHost host, InetSocketAddress remoteAddress, InetSocketAddress localAddress, HttpContext context) throws IOException {
            socket.connect(remoteAddress, connectTimeout.toMillisecondsIntBound());
            return socket;
        }
    }

    public static class SecureSocketFactory implements LayeredConnectionSocketFactory {
        private final VpnService context;
        private final SSLConnectionSocketFactory defaultSocketFactory;

        public SecureSocketFactory(VpnService context) {
            this.context = context;
            this.defaultSocketFactory = SSLConnectionSocketFactoryBuilder.create().build();
        }

        @Override
        public Socket createLayeredSocket(Socket socket, String target, int port, HttpContext context) throws IOException, UnknownHostException {
            Socket layered = defaultSocketFactory.createLayeredSocket(socket, target, port, context);
            this.context.protect(layered);
            return layered;
        }

        @Override
        public Socket createSocket(HttpContext context) throws IOException {
            Socket socket = defaultSocketFactory.createSocket(context);
            this.context.protect(socket);
            return socket;
        }

        @Override
        public Socket connectSocket(TimeValue connectTimeout, Socket socket, HttpHost host, InetSocketAddress remoteAddress, InetSocketAddress localAddress, HttpContext context) throws IOException {
            return defaultSocketFactory.connectSocket(connectTimeout, socket, host, remoteAddress, localAddress, context);
        }
    }
}

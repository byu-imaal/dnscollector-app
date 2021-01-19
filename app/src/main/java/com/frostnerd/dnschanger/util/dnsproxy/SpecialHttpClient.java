package com.frostnerd.dnschanger.util.dnsproxy;

import android.net.VpnService;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.EntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.LayeredConnectionSocketFactory;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.util.TimeValue;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

public class SpecialHttpClient {
    private final HttpClient client;

    private static final Map<String, InetAddress> hostsDotTxt = new HashMap<>();
    static {
        try {
            hostsDotTxt.put("dns.kimballleavitt.com", InetAddress.getByName("34.82.184.125"));
            hostsDotTxt.put("my.acer.laptop", InetAddress.getByName("192.168.0.104"));
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    public SpecialHttpClient(VpnService context) {
        LayeredConnectionSocketFactory secureSocketFactory = new SocketFactory.SecureSocketFactory(context);
        SocketFactory.NormalSocketFactory normalSocketFactory = new SocketFactory.NormalSocketFactory(context);
        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", normalSocketFactory)
                .register("https", secureSocketFactory)
                .build();
        final DnsResolver dnsResolver = new SystemDefaultDnsResolver() {
            @Override
            public InetAddress[] resolve(String host) throws UnknownHostException {
                InetAddress addr = hostsDotTxt.get(host.toLowerCase());
                if (addr != null) {
                    return new InetAddress[]{addr};
                }
                return super.resolve(host);
            }
        };
        PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry, PoolConcurrencyPolicy.STRICT, PoolReusePolicy.LIFO, TimeValue.ofMinutes(5), null, dnsResolver, null);
        connManager.setMaxTotal(5);
        client = HttpClients.custom()
                .setConnectionManager(connManager)
                .build();
    }

    public void postData(DatagramPacket packet) {
        HttpPost httpPost = new HttpPost("https://dns.kimballleavitt.com/pcap-server/");
        HttpEntity body = EntityBuilder.create().setBinary(packet.getData()).build();
        httpPost.setEntity(body);
        httpPost.addHeader("X-DEVICE-ID", "my-unique-id");
        httpPost.addHeader("X-DST-IP", packet.getAddress().getHostAddress());
        httpPost.addHeader("X-DST-PORT", packet.getPort());
        httpPost.addHeader("X-FROM-BYU-NETWORK", false);
        CloseableHttpResponse response;
        try {
            System.out.println("EXECUTING HTTP POST");
            response = (CloseableHttpResponse) client.execute(httpPost);
        } catch (IOException e) {
            System.err.println("FAILED HTTP POST");
            e.printStackTrace();
            return;
        }
        System.out.println("SUCCEEDED HTTP POST");
        int status = response.getCode();
        System.err.println("got HTTP status " + status);
        try {
            response.getEntity().writeTo(System.out);
        } catch (IOException e) {
            System.err.println("FAILED TO WRITE ENTITY TO SYSTEM.OUT: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

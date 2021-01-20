package edu.byu.imaal.dnscapture.util.dnsproxy;

import android.net.VpnService;
import android.os.Process;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.EntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.LayeredConnectionSocketFactory;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.util.TimeValue;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.byu.imaal.dnscapture.util.Preferences;

public class SpecialHttpClient {
    private final HttpClient client;

    private static final Map<String, InetAddress> hostsDotTxt = new HashMap<>();
    static {
        try {
            // TODO: update this when the server is up
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
        String uniqueId = Preferences.getInstance(context).get("unique_client_id", "UNIQUE-ID-NOT-FOUND");
        Header header = new BasicHeader("X-CLIENT-ID", uniqueId);
        List<Header> defaultHeaders = new ArrayList<>();
        defaultHeaders.add(header);
        client = HttpClients.custom()
                .setConnectionManager(connManager)
                .setDefaultHeaders(defaultHeaders)
                .build();
    }

    public void postData(DatagramPacket packet) {
        // TODO: update this when the server is up
        HttpPost httpPost = new HttpPost("https://dns.kimballleavitt.com/dnscapture/");
        //HttpPost httpPost = new HttpPost("http://my.acer.laptop:8000/dnscapture/");
        HttpEntity body = EntityBuilder.create().setBinary(packet.getData()).build();
        httpPost.setEntity(body);
        httpPost.addHeader("X-MY-THREAD-ID", Process.myTid());
        httpPost.addHeader("X-DST-IP", packet.getAddress().getHostAddress());
        httpPost.addHeader("X-DST-PORT", packet.getPort());
        httpPost.addHeader("X-FROM-BYU-NETWORK", false);
        httpPost.addHeader("X-TIMESTAMP", System.currentTimeMillis());
        CloseableHttpResponse response;
        try {
            response = (CloseableHttpResponse) client.execute(httpPost);
        } catch (IOException e) {
            System.err.println("HTTP post failed with exception: " + e.getMessage());
            e.printStackTrace();
            return;
        }
        int status = response.getCode();
        if (status != 200) {
            System.err.println("HTTP post returned unexpected status " + status);
            try {
                response.getEntity().writeTo(System.out);
            } catch (IOException e) {
                System.err.println("Failed to read HTTP response body: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}

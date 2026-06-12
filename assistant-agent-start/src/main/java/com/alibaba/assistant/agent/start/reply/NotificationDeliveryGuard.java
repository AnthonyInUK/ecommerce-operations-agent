package com.alibaba.assistant.agent.start.reply;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NotificationDeliveryGuard {

    private final Map<String, Instant> deliveryHistory = new ConcurrentHashMap<>();

    public synchronized boolean shouldSuppress(String fingerprint, long dedupWindowSeconds) {
        pruneExpired(dedupWindowSeconds);
        Instant lastDeliveredAt = deliveryHistory.get(fingerprint);
        if (lastDeliveredAt == null) {
            return false;
        }
        return lastDeliveredAt.plusSeconds(dedupWindowSeconds).isAfter(Instant.now());
    }

    public synchronized void markDelivered(String fingerprint) {
        deliveryHistory.put(fingerprint, Instant.now());
    }

    public String fingerprint(String title, String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((title + "\n" + text).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(bytes);
        }
        catch (Exception e) {
            return Integer.toHexString((title + "\n" + text).hashCode());
        }
    }

    private void pruneExpired(long dedupWindowSeconds) {
        Iterator<Map.Entry<String, Instant>> iterator = deliveryHistory.entrySet().iterator();
        Instant now = Instant.now();
        while (iterator.hasNext()) {
            Map.Entry<String, Instant> entry = iterator.next();
            if (entry.getValue().plusSeconds(dedupWindowSeconds).isBefore(now)) {
                iterator.remove();
            }
        }
    }
}

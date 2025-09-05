package com.example.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/keycloak")
public class KeycloakWebhookController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(KeycloakWebhookController.class);

    private static final String SHARED_SECRET = "super-secret"; // compose/realm ile aynı
    private final ObjectMapper mapper = new ObjectMapper();
    private final UserProvisioningService provisioningService;

    public KeycloakWebhookController(UserProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    @PostMapping(path = "/webhook", consumes = "application/json")
    public ResponseEntity<Void> handle(
            @RequestHeader(value = "X-Webhook-Secret", required = false) String simpleSecret,
            @RequestHeader(value = "X-Keycloak-Signature", required = false) String sigA,
            @RequestHeader(value = "X-Signature", required = false) String sigB,
            @RequestBody byte[] rawBody) {

        // 0) TRACE amaçlı minimal log
        log.info("WEBHOOK HIT size={}B headers(simpleSecret?={}, sigA?={}, sigB?={})",
                rawBody.length, simpleSecret != null, sigA != null, sigB != null);

        // 1) auth
        boolean ok = "super-secret".equals(simpleSecret);
        String sig = (sigA != null && !sigA.isBlank()) ? sigA : sigB;
        if (!ok && sig != null && verifyHmac(rawBody, sig, "super-secret")) ok = true;
        if (!ok) {
            log.warn("WEBHOOK UNAUTHORIZED");
            return ResponseEntity.status(401).build();
        }

        // 2) parse
        final Map<String,Object> payload;
        try {
            payload = mapper.readValue(rawBody, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("WEBHOOK BAD_PAYLOAD parseError={}", e.toString());
            return ResponseEntity.badRequest().build();
        }

        Map<String,Object> adminEvent = cast(payload.get("adminEvent"));
        String resourceType  = str(adminEvent != null ? adminEvent.get("resourceType")  : payload.get("resourceType"));
        String operationType = str(adminEvent != null ? adminEvent.get("operationType") : payload.get("operationType"));
        String resourcePath  = str(adminEvent != null ? adminEvent.get("resourcePath")  : payload.get("resourcePath"));
        Object repObj        =        adminEvent != null ? adminEvent.get("representation") : payload.get("representation");

        if (operationType == null) {
            String type = str(payload.get("type"));
            if (type != null) {
                String lc = type.toLowerCase(Locale.ROOT);
                if (lc.startsWith("admin.user.")) operationType = lc.substring("admin.user.".length()).toUpperCase(Locale.ROOT);
            }
        }
        if ("ACTION".equalsIgnoreCase(operationType)) operationType = "UPDATE";

        Map<String,Object> representation = cast(repObj);
        if (representation == null && repObj instanceof String s) {
            try { representation = mapper.readValue(s, new TypeReference<Map<String,Object>>(){}); } catch (Exception ignored) {}
        }

        // 3) userId’yi çoklu kaynaktan çek
        String userId = userIdFrom(resourcePath, payload, adminEvent, representation);

        log.info("WEBHOOK EVENT op={} resType={} resPath={} userId={}",
                operationType, resourceType, resourcePath, userId);

        // CREATE’leri yoksay
        if ("CREATE".equalsIgnoreCase(operationType)) return ResponseEntity.ok().build();

        // güvenlik: resType USER değilse veya id yoksa atla
        if (!"USER".equalsIgnoreCase(resourceType) || userId == null) return ResponseEntity.ok().build();

        // 4) işle
        if ("DELETE".equalsIgnoreCase(operationType)) {
            provisioningService.deleteByKeycloakId(userId);
            return ResponseEntity.ok().build();
        }
        if ("UPDATE".equalsIgnoreCase(operationType)) {
            provisioningService.upsertFromAdminEvent(userId, representation);
            return ResponseEntity.ok().build();
        }

        return ResponseEntity.ok().build();
    }


    // ---- helpers ----
    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object o) {
        return (o instanceof Map<?, ?> m) ? (Map<String, Object>) m : null;
    }
    private static String str(Object o) { return (o == null) ? null : String.valueOf(o); }

    private static String userIdFrom(String resourcePath,
                                     Map<String,Object> payload,
                                     Map<String,Object> adminEvent,
                                     Map<String,Object> representation) {
        // 1) resourcePath → "users/{id}"
        String id = lastSegment(resourcePath);
        if (id != null && !id.isBlank()) return id;

        // 2) details.userId (flat ya da adminEvent.details)
        Map<String,Object> details = cast(payload.get("details"));
        if (details == null && adminEvent != null) details = cast(adminEvent.get("details"));
        String d = str(details != null ? details.get("userId") : null);
        if (d != null && !d.isBlank()) return d;

        // 3) representation.id
        String r = str(representation != null ? representation.get("id") : null);
        if (r != null && !r.isBlank()) return r;

        return null;
    }

    private static String lastSegment(String path) {
        if (path == null) return null;
        int i = path.lastIndexOf('/');
        return (i >= 0 && i < path.length()-1) ? path.substring(i + 1) : path;
    }

    private static boolean verifyHmac(byte[] body, String header, String secret) {
        try {
            String norm = header.trim();
            int eq = norm.indexOf('=');
            if (eq >= 0) norm = norm.substring(eq + 1);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(body);
            String hex = toHexLower(digest);
            return MessageDigest.isEqual(hex.getBytes(StandardCharsets.UTF_8),
                    norm.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }
    private static String toHexLower(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}

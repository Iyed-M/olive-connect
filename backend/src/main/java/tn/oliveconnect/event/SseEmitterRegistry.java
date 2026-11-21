package tn.oliveconnect.event;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Keeps the local SSE connections grouped by their authenticated User id. */
@Component
public class SseEmitterRegistry {

    private final ConcurrentHashMap<String, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(String userId) {
        SseEmitter emitter = new SseEmitter(0L);
        register(userId, emitter);
        return emitter;
    }

    void register(String userId, SseEmitter emitter) {
        emitters.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);
        Runnable remove = () -> remove(userId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(ignored -> remove.run());
    }

    public void send(String userId, String eventName, Object data) {
        Set<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null) {
            return;
        }
        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException ex) {
                remove(userId, emitter);
                emitter.completeWithError(ex);
            }
        }
    }

    public void closeAll() {
        emitters.values().stream().flatMap(Set::stream).forEach(SseEmitter::complete);
        emitters.clear();
    }

    private void remove(String userId, SseEmitter emitter) {
        emitters.computeIfPresent(userId, (ignored, current) -> {
            current.remove(emitter);
            return current.isEmpty() ? null : current;
        });
    }
}

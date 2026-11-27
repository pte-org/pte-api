package com.pte.itembank.internal.generation.voice;

import com.pte.itembank.internal.generation.constant.VoiceConstants;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * Keeps interactive speech work from crowding out the batch worker on the shared speech server: a few calls across
 * the app, and one at a time per person. A call that does not fit is refused at once, not queued.
 */
@Component
public class VoiceChangeLimiter {

    private final Semaphore slots = new Semaphore(VoiceConstants.VOICE_CHANGE_MAX_CONCURRENT);
    private final Set<UUID> busyPeople = ConcurrentHashMap.newKeySet();

    /** Runs {@code work}; {@code person} may be null for work that is not tied to one (a shared sample). */
    public <T> T run(UUID person, Supplier<T> work) {
        if (person != null && !busyPeople.add(person)) {
            throw busy();
        }
        try {
            if (!slots.tryAcquire()) {
                throw busy();
            }
            try {
                return work.get();
            } finally {
                slots.release();
            }
        } finally {
            if (person != null) {
                busyPeople.remove(person);
            }
        }
    }

    private static VoiceChangeException busy() {
        return new VoiceChangeException(HttpStatus.TOO_MANY_REQUESTS, VoiceConstants.GENERATION_VOICE_BUSY);
    }
}

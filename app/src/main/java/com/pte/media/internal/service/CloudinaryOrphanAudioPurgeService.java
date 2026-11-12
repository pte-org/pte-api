package com.pte.media.internal.service;

import com.pte.media.domain.MediaAsset;
import com.pte.media.internal.constant.MediaConstants;
import com.pte.media.internal.repository.MediaAssetRepository;
import com.pte.shared.practice.PracticeMediaReferencePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Removes practice recordings no answer uses any more, at Cloudinary and then in the database. Every upload gets a new
 * id, so a re-recorded draft, an upload whose answer never saved, or a discarded session's draft would otherwise stay
 * stored for good. Recordings of submitted answers, including those of earlier sessions, are kept: their review and
 * scores rely on them. A failed removal leaves the row, and the next run tries again.
 */
@Service
public class CloudinaryOrphanAudioPurgeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CloudinaryOrphanAudioPurgeService.class);

    private final MediaAssetRepository repository;
    private final PracticeMediaReferencePort referencePort;
    private final CloudinaryAssetDestroyer destroyer;
    private final String rootPrefix;
    private final boolean configured;
    private final Clock clock;

    CloudinaryOrphanAudioPurgeService(MediaAssetRepository repository, PracticeMediaReferencePort referencePort,
            CloudinaryAssetDestroyer destroyer, String rootFolder, boolean configured, Clock clock) {
        this.repository = repository;
        this.referencePort = referencePort;
        this.destroyer = destroyer;
        this.rootPrefix = CloudinaryFolders.likePrefix(rootFolder);
        this.configured = configured;
        this.clock = clock;
    }

    @Autowired
    public CloudinaryOrphanAudioPurgeService(MediaAssetRepository repository, PracticeMediaReferencePort referencePort,
            @Value("${media.purge.enabled:false}") boolean purgeEnabled,
            @Value("${media.cloudinary.cloud-name:}") String cloudName,
            @Value("${media.cloudinary.api-key:}") String apiKey,
            @Value("${media.cloudinary.api-secret:}") String apiSecret,
            @Value("${media.cloudinary.root-folder:" + MediaConstants.DEFAULT_CLOUDINARY_ROOT_FOLDER + "}")
            String rootFolder,
            RestClient.Builder restClientBuilder, Clock clock) {
        this(repository, referencePort,
                new CloudinaryAssetDestroyer(new CloudinaryRequestSigner(cloudName, apiKey, apiSecret, clock),
                        restClientBuilder, clock),
                rootFolder, purgeEnabled && !cloudName.isBlank() && !apiKey.isBlank() && !apiSecret.isBlank(), clock);
    }

    @Scheduled(cron = "${media.orphan-audio-purge.cron:0 45 3 * * *}")
    public void purgeOnSchedule() {
        purgeOrphanPracticeAudio();
    }

    /** Removes every unused practice recording older than the grace period; returns how many were removed. */
    public int purgeOrphanPracticeAudio() {
        if (!configured) {
            return 0;
        }
        Instant cutoff = clock.instant().minus(MediaConstants.ORPHAN_AUDIO_GRACE);
        int removed = 0;
        List<MediaAsset> page = nextPage(cutoff, 0);
        while (!page.isEmpty()) {
            removed += purgeUnused(page);
            page = nextPage(cutoff, page.get(page.size() - 1).getId());
        }
        if (removed > 0) {
            LOGGER.info("Removed {} unused practice recordings", removed);
        }
        return removed;
    }

    private List<MediaAsset> nextPage(Instant cutoff, long afterId) {
        return repository.findPracticeAudioPage(rootPrefix, cutoff, afterId,
                PageRequest.of(0, MediaConstants.MEDIA_PURGE_BATCH_SIZE));
    }

    private int purgeUnused(List<MediaAsset> page) {
        Map<UUID, UUID> itemByMedia = new HashMap<>();
        page.forEach(asset -> itemByMedia.put(asset.getPublicId(), asset.getPracticeItemPublicId()));
        Set<UUID> used = referencePort.findReferencedResponseAudio(itemByMedia);
        int removed = 0;
        for (MediaAsset asset : page) {
            if (!used.contains(asset.getPublicId()) && destroyer.destroy(asset)) {
                repository.delete(asset);
                removed++;
            }
        }
        return removed;
    }
}

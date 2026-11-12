package com.pte.media.internal.service;

import com.pte.media.domain.MediaAsset;
import com.pte.media.internal.constant.MediaConstants;
import com.pte.media.internal.repository.MediaAssetRepository;
import com.pte.shared.media.MediaReferenceSource;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Removes question files (prompt audio and images) nothing uses any more, at Cloudinary and then in the database: the
 * media of an AI draft that was never saved, a file uploaded in the question editor and then replaced or abandoned, and
 * the files of a question deleted long enough ago. A file is kept while any {@link MediaReferenceSource} names it: a
 * question, a published exam version or a practice answer. Only prompt audio and images under this environment's root
 * folder are looked at, so an environment sharing the Cloudinary account never removes another one's files, and
 * students' answers are never touched.
 */
@Service
public class CloudinaryUnusedAuthoringMediaPurgeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CloudinaryUnusedAuthoringMediaPurgeService.class);

    private final MediaAssetRepository repository;
    private final List<MediaReferenceSource> sources;
    private final CloudinaryAssetDestroyer destroyer;
    private final String rootPrefix;
    private final boolean configured;
    private final Clock clock;

    CloudinaryUnusedAuthoringMediaPurgeService(MediaAssetRepository repository, List<MediaReferenceSource> sources,
            CloudinaryAssetDestroyer destroyer, String rootFolder, boolean configured, Clock clock) {
        this.repository = repository;
        this.sources = List.copyOf(sources);
        this.destroyer = destroyer;
        this.rootPrefix = CloudinaryFolders.likePrefix(rootFolder);
        this.configured = configured;
        this.clock = clock;
    }

    @Autowired
    public CloudinaryUnusedAuthoringMediaPurgeService(MediaAssetRepository repository,
            List<MediaReferenceSource> sources,
            @Value("${media.purge.enabled:false}") boolean purgeEnabled,
            @Value("${media.cloudinary.cloud-name:}") String cloudName,
            @Value("${media.cloudinary.api-key:}") String apiKey,
            @Value("${media.cloudinary.api-secret:}") String apiSecret,
            @Value("${media.cloudinary.root-folder:" + MediaConstants.DEFAULT_CLOUDINARY_ROOT_FOLDER + "}")
            String rootFolder,
            RestClient.Builder restClientBuilder, Clock clock) {
        this(repository, sources,
                new CloudinaryAssetDestroyer(new CloudinaryRequestSigner(cloudName, apiKey, apiSecret, clock),
                        restClientBuilder, clock),
                rootFolder,
                purgeEnabled && !cloudName.isBlank() && !apiKey.isBlank() && !apiSecret.isBlank(), clock);
    }

    @Scheduled(cron = "${media.unused-authoring-purge.cron:0 15 4 * * *}")
    public void purgeOnSchedule() {
        purgeUnusedAuthoringMedia();
    }

    /** Removes every unused question file older than the grace period; returns how many were removed. */
    public int purgeUnusedAuthoringMedia() {
        // Without credentials nothing can be removed; without sources nothing could be proven unused.
        if (!configured || sources.isEmpty()) {
            return 0;
        }
        Instant cutoff = clock.instant().minus(MediaConstants.UNUSED_AUTHORING_MEDIA_GRACE);
        int removed = 0;
        List<MediaAsset> page = nextPage(cutoff, 0);
        while (!page.isEmpty()) {
            removed += purgeUnused(page);
            page = nextPage(cutoff, page.get(page.size() - 1).getId());
        }
        if (removed > 0) {
            LOGGER.info("Removed {} unused question media files", removed);
        }
        return removed;
    }

    private List<MediaAsset> nextPage(Instant cutoff, long afterId) {
        return repository.findAuthoringMediaPage(rootPrefix, cutoff, afterId,
                PageRequest.of(0, MediaConstants.MEDIA_PURGE_BATCH_SIZE));
    }

    private int purgeUnused(List<MediaAsset> page) {
        List<UUID> ids = page.stream().map(MediaAsset::getPublicId).toList();
        Set<UUID> used = new HashSet<>();
        sources.forEach(source -> used.addAll(source.findReferencedMedia(ids)));
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

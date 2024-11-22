package de.ocrd.olahd.repository.mongo;

import de.ocrd.olahd.domain.Archive;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

@Repository
public interface ArchiveRepository extends MongoRepository<Archive, String> {

    Archive findByPid(String pid);
    Archive findByOnlineIdOrOfflineId(String onlineId, String offlineId);
    Archive findTopByOcrdIdentifierOrderByCreatedAtDesc(String ocrdIdentifier);

    /**
     * Tries to find one of the latest versions of an archive.
     *
     * "One of the latest": If an archive (in the chain) has more than one next version, the first one of these is
     * returned. So this function can lead to unwanted behavior. However the normal case, a linear chain of next
     * versions, is handled correctly
     *
     * @param archiveRepository
     * @param archive
     * @return
     */
    default public Archive getLatestVersion(String pid) {
        Archive archive = findByPid(pid);
        if (archive == null) {
            return null;
        } else if (CollectionUtils.isEmpty(archive.getNextVersions())) {
            return archive;
        } else {
            return getLatestVersion(archive);
        }
    }

    private Archive getLatestVersion(Archive archive) {
        if (CollectionUtils.isEmpty(archive.getNextVersions())) {
            return archive;
        }
        return getLatestVersion(archive.getNextVersions().get(0));
    }
}

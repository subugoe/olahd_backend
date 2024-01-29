package ola.hd.longtermstorage.repository.mongo;

import ola.hd.longtermstorage.domain.Archive;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

@Repository
public interface ArchiveRepository extends MongoRepository<Archive, String> {

    Archive findByPid(String pid);
    Archive findByOnlineIdOrOfflineId(String onlineId, String offlineId);

    /**
     * Tries to find one of the latest versions of an archive.
     *
     * "One of the latest": If an archive (in the chain) has more than one next version, the first one of these is
     * returned. So this function can lead to unwanted behaviour. However the normal case, a linear chain of next
     * versions, is handled correctly
     *
     * @param archiveRepository
     * @param archive
     * @return
     */
    public static Archive getLatestVersion(ArchiveRepository archiveRepository, String pid) {
        Archive archive = archiveRepository.findByPid(pid);
        if (archive == null) {
            return null;
        } else if (CollectionUtils.isEmpty(archive.getNextVersions())) {
            return archive;
        } else {
            return getLatestVersion(archiveRepository, archive);
        }
    }

    private static Archive getLatestVersion(ArchiveRepository archiveRepository, Archive archive) {
        if (CollectionUtils.isEmpty(archive.getNextVersions())) {
            return archive;
        }
        return ArchiveRepository.getLatestVersion(archiveRepository, archive.getNextVersions().get(0));
    }
}

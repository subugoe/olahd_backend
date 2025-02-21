package de.ocrd.olahd.repository.mongo;

import de.ocrd.olahd.domain.OperandiJobInfo;
import de.ocrd.olahd.domain.OperandiJobStatus;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Store information about triggered operandi jobs
 */
@Repository
public interface OperandiJobRepository extends MongoRepository<OperandiJobInfo, String> {

    List<OperandiJobInfo> findByStatusIn(List<OperandiJobStatus> statuses);

    List<OperandiJobInfo> findByUsername(String username, Pageable pageable);

    @Query("{'username' : ?0, 'status' : { $in: ['RUNNING']}}")
    List<OperandiJobInfo> findRunningJobsByUser(String username);

    /**
     * Asks the database if the provided user has a running operandi job
     *
     * The original aim of this method is to ensure that each user does not have more than one job running at the same
     * time. This might be changed later
     *
     * @param archiveRepository
     * @param archive
     * @return
     */
    default public boolean hasRunningJob(String username) {
        List<OperandiJobInfo> runningJobs = findRunningJobsByUser(username);
        return !runningJobs.isEmpty();
    }

}

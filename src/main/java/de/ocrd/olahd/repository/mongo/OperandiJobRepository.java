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

    @Query("{'username' : ?0, 'status' : { $in: ['ACCEPTED', 'PREPARING', 'RUNNING']}}")
    List<OperandiJobInfo> findUnfinishedJobsByUser(String username);

    @Query("{'pid' : ?0, 'status' : { $in: ['ACCEPTED', 'PREPARING', 'RUNNING']}}")
    List<OperandiJobInfo> findUnfinishedJobsByPid(String pid);

    /**
     * Asks the database if the provided user has a running operandi job
     *
     * Running in this case means the job has been received but is not completely processed by operandi yet. The
     * original aim of this method is to ensure that each user does not have more than one job running at the same time.
     * This might be changed later
     *
     * @param archiveRepository
     * @param archive
     * @return
     */
    default public boolean hasUserUnfinishedJob(String username) {
        List<OperandiJobInfo> runningJobs = findUnfinishedJobsByUser(username);
        return !runningJobs.isEmpty();
    }

    /**
     * Asks the database if the provided workspace (pid) has a not finished aka running job.
     *
     * Running in this case means the job has been received but is not completely processed by operandi yet. The
     * original aim of this method is to ensure that for each workspace only a single workspace is running at a time.
     *
     * @param archiveRepository
     * @param archive
     * @return
     */
    default public boolean hasWorkspaceUnfinishedJob(String pid) {
        List<OperandiJobInfo> runningJobs = findUnfinishedJobsByPid(pid);
        return !runningJobs.isEmpty();
    }

}

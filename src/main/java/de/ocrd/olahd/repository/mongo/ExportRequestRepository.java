package de.ocrd.olahd.repository.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import de.ocrd.olahd.domain.ArchiveStatus;
import de.ocrd.olahd.domain.ExportRequest;
import java.util.List;

@Repository
public interface ExportRequestRepository extends MongoRepository<ExportRequest, String> {

    List<ExportRequest> findByStatus(ArchiveStatus status);

    List<ExportRequest> findByStatusOrderByTimestampDesc(ArchiveStatus status);
}

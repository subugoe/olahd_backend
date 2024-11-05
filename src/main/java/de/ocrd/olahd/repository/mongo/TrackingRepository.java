package de.ocrd.olahd.repository.mongo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import de.ocrd.olahd.domain.TrackingInfo;
import java.util.List;

@Repository
public interface TrackingRepository extends MongoRepository<TrackingInfo, String> {

    List<TrackingInfo> findByUsername(String username, Pageable pageable);
}

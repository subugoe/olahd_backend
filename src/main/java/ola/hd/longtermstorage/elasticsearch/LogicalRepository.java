package ola.hd.longtermstorage.elasticsearch;

import java.util.List;
import ola.hd.longtermstorage.elasticsearch.mapping.LogicalEntry;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * Repository for Index called "meta.log"
 *
 * XXX: for future use. Not really used somewhere yet (remove comment or class if used or not needed)
 */
public interface LogicalRepository extends ElasticsearchRepository<LogicalEntry, String> {

    List<LogicalEntry> findByBycreatorContaining(String creator);

}


package ola.hd.longtermstorage.elasticsearch;

import java.util.List;
import ola.hd.longtermstorage.elasticsearch.mapping.PhysicalEntry;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * Repository for Index called "meta.phys"
 *
 * XXX: for future use. Not really used somewhere yet (remove comment or class if used or not needed)
 */
public interface PhysicalRepository extends ElasticsearchRepository<PhysicalEntry, String> {

    @Query("{\"query_string\": { \"fields\": [\"fulltext\"], \"query\": \"*\\\"?0*\\\"\"}}")
    List<PhysicalEntry> fulltextSearch(String text);

}


package de.ocrd.olahd.repository.mongo;

import de.ocrd.olahd.domain.MongoUser;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends MongoRepository<MongoUser, String> {
    MongoUser findByUsername(String username);
}

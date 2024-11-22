package de.ocrd.olahd.component;

import de.ocrd.olahd.domain.MongoUser;
import de.ocrd.olahd.repository.mongo.UserRepository;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DbInitRunner implements CommandLineRunner {

    /**
     * Password-Hash of backend admin user. This user is for accessing the backend, NOT for logging into mongodb
     */
    @Value("${adminuser.pw.hash}")
    private String INIT_PW_HASH;

    /** Password-Salt of backend admin user. */
    @Value("${adminuser.pw.salt}")
    private String INIT_PW_SALT;

    private final UserRepository userRepository;

    public DbInitRunner(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {
        MongoUser admin = userRepository.findByUsername("admin");
        if (admin == null) {
            if (StringUtils.isEmpty(INIT_PW_HASH) || StringUtils.isEmpty(INIT_PW_SALT)) {
                throw new RuntimeException("Credentials for initial backend user missing");
            }
            admin = new MongoUser("admin", INIT_PW_HASH, INIT_PW_SALT);
            userRepository.save(admin);
        }
    }
}

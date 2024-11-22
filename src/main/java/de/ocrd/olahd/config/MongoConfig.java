package de.ocrd.olahd.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@Configuration
@EnableMongoAuditing
public class MongoConfig {
/* For now only to enable mongo auditing. The annotation could be used elsewhere but
 * I prefer a mongo config class */
}

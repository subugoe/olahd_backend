spring.profiles.active                   Determines which application-{profile}.properties file is read
springfox.documentation.swagger.v2.path  Configure path to swagger documentation
ola.hd.upload.dir                        Path for temporarily storing uploaded OCRD-ZIP-files
adminuser.pw.hash                        Hash-Value of (salt + plaintext-pw). Used to access the backend
adminuser.pw.salt                        Salt for admin-user password
cdstar.url                               URL of CDSTAR-vault
cdstar.username                          CDSTAR-vault username
cdstar.password                          CDSTAR-vault password
cdstar.vault                             CDSTAR-vault name
cdstar.onlineProfile                     Should normally be set to "default". See https://cdstar.gwdg.de/docs/dev/#_profile_mode_hot_vs_cold
cdstar.offlineProfile                    Should normally be set to "cold"
cdstar.mirrorProfile                     Should normally be set to "mirror"
offline.mimeTypes                        Files with these mime-types will only be stored in offline (cold) storage
spring.data.mongodb.auto                 Enable index creation to speed up queries
spring.data.mongodb.host                 Host of MongoDB
spring.data.mongodb.port                 Port of MongoDB
spring.data.mongodb.database             MongoDB-database-name for OLA-HD
spring.data.mongodb.username             Username used by the backend to access MongoDB
spring.data.mongodb.password             Password used by the backend to access MongoDB
spring.data.mongodb.authentication       MongoDB-database to which the backend authenticates
epic.url                                 PID-service URL
epic.username                            PID-service username
epic.password                            PID-service password
epic.prefix                              Used PID-service prefix
webnotifier.url                          URL for the backend to access the webnotifier
elasticsearch.host-port                  Info for the backend to access ES. Expected is {host}:{port}
logging.level.org.springframework.web    Loglevel for the Spring logger
logging.level.de.ocrd.olahd.utils        Loglevel for the OLA-HD logger
s3.url                                   URL of S3. OLA-HD / Indexer stores IIIF manifests in S3
s3.key.access                            Access-Key for S3
s3.key.secret                            Secret for S3

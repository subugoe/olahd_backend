package ola.hd.longtermstorage.domain;

import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "user")
public class MongoUser {

    @Id
    private String id;

    @Indexed(unique=true)
    private String username;

    private String passwordHash;

    private String salt;

    protected MongoUser() {
        // no-args constructor required by JPA spec
        // this one is protected since it shouldn't be used directly
    }

    public MongoUser(String username, String passwordHash, String salt) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.salt = salt;
    }

    public boolean authenticate_user(String password) {
        String newHash = Hashing.sha512().hashString(salt + password, StandardCharsets.UTF_8).toString();
        boolean res = newHash.equals(passwordHash);
        return res;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

}

package ola.hd.longtermstorage.service;

import java.io.IOException;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Fake PidService which "only" creates UUID's (Version 4).
 *
 * This PidService is not a PidService but it behaves like on. It returns a unique identifier but nothing more. Purpose
 * is currently to replace the DemoPid Service because that one not always works (currently down).
 */
@Service
@ConditionalOnProperty(
  value="nopidservice",
  havingValue = "true",
  matchIfMissing = false)
public class DummyPidService implements PidService {

    @Override
    public String createPid(List<SimpleImmutableEntry<String, String>> data) throws IOException {
        return UUID.randomUUID().toString();
    }

    @Override
    public void updatePid(String pid, List<SimpleImmutableEntry<String, String>> data) {
        // pass
    }

    @Override
    public void appendData(String pid, List<SimpleImmutableEntry<String, String>> data)
        throws IOException {
        // pass
    }

    @Override
    public void deletePid(String pid) throws IOException {
        // pass
    }


}

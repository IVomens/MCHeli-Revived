package com.ivomens.mcheli.api.dump;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The committed api.txt lists exactly the API's public signatures, under the current api_version. A change to
 * the API fails here until {@code ./gradlew :api:updateApiDump} rewrites the file, which it only does when
 * api_version was bumped as the change needs (docs/API.md).
 */
class ApiSignaturesTest {

    @Test
    void committedDumpMatchesTheApi() throws IOException {
        Path file = Path.of(System.getProperty("mcheli.apiDump"));
        String version = System.getProperty("mcheli.apiVersion");
        ApiSignatures.Committed committed = ApiSignatures.Committed.read(file);
        Set<String> current = ApiSignatures.dump(ApiSignatures.classFolders(System.getProperty("mcheli.apiClasses")));

        Set<String> removed = new TreeSet<>(committed.signatures());
        removed.removeAll(current);
        Set<String> added = new TreeSet<>(current);
        added.removeAll(committed.signatures());
        if (!removed.isEmpty() || !added.isEmpty()) {
            fail("The public API changed; bump api_version as docs/API.md says and run ./gradlew :api:updateApiDump."
                    + "\nRemoved or changed (needs a major version):\n  " + String.join("\n  ", removed)
                    + "\nAdded (needs a minor version):\n  " + String.join("\n  ", added));
        }
        assertEquals(version, committed.version(),
                "api.txt was written for another api_version; run ./gradlew :api:updateApiDump");
    }

    @Test
    void removingASignatureNeedsAMajorVersion() {
        ApiSignatures.Committed committed = new ApiSignatures.Committed("1.2.0", Set.of("a", "b"));
        assertNotNull(ApiSignatures.versionProblem(committed, Set.of("a"), "1.3.0"));
        assertNull(ApiSignatures.versionProblem(committed, Set.of("a"), "2.0.0"));
    }

    @Test
    void addingASignatureNeedsAMinorVersion() {
        ApiSignatures.Committed committed = new ApiSignatures.Committed("1.2.0", Set.of("a"));
        assertNotNull(ApiSignatures.versionProblem(committed, Set.of("a", "b"), "1.2.1"));
        assertNull(ApiSignatures.versionProblem(committed, Set.of("a", "b"), "1.3.0"));
        assertNull(ApiSignatures.versionProblem(committed, Set.of("a"), "1.2.1"));
    }
}

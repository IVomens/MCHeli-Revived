/** The checked-out commit, written into recorded golden files for reference only. */
final class GitCommit {

    private GitCommit() {
    }

    static String shortHash() {
        try {
            def process = ['git', 'rev-parse', '--short', 'HEAD'].execute()
            process.waitFor()
            return process.exitValue() == 0 ? process.text.trim() : 'unknown'
        } catch (Exception ignored) {
            return 'unknown'
        }
    }
}

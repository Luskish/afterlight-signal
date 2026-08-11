import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class ReleaseSourcePolicy {
    private static final byte[] DIGEST_DOMAIN =
            "AFTERLIGHT_RELEASE_SOURCE_TREE_V2\0".getBytes(StandardCharsets.UTF_8);
    private static final byte[] FORBIDDEN_U2014 =
            new String(Character.toChars(0x2014)).getBytes(StandardCharsets.UTF_8);
    private static final String REGULAR_MODE = "100644";
    private static final String EXECUTABLE_MODE = "100755";
    private static final String BLOB_TYPE = "blob";
    private static final String TREE_MODE = "040000";
    private static final String TREE_TYPE = "tree";
    private static final LinkOption[] NOFOLLOW = {LinkOption.NOFOLLOW_LINKS};
    private static final List<String> EXCLUDED_PREFIXES = List.of(
            ".git/",
            ".gradle/",
            "build/",
            "config/",
            "crash-reports/",
            "logs/",
            "out/",
            "run/",
            "run-data/");
    private static final List<SecretMarker> SECRET_MARKERS = List.of(
            new SecretMarker(
                    "PRIVATE_KEY",
                    ("-----BEGIN " + "PRIVATE KEY-----").getBytes(StandardCharsets.US_ASCII)),
            new SecretMarker(
                    "RSA_PRIVATE_KEY",
                    ("-----BEGIN RSA " + "PRIVATE KEY-----")
                            .getBytes(StandardCharsets.US_ASCII)),
            new SecretMarker(
                    "OPENSSH_PRIVATE_KEY",
                    ("-----BEGIN OPENSSH " + "PRIVATE KEY-----")
                            .getBytes(StandardCharsets.US_ASCII)),
            new SecretMarker(
                    "GITHUB_PAT",
                    ("github" + "_pat_").getBytes(StandardCharsets.US_ASCII)),
            new SecretMarker(
                    "OPENAI_PROJECT_KEY",
                    ("sk-" + "proj-").getBytes(StandardCharsets.US_ASCII)));

    private ReleaseSourcePolicy() {}

    public static void main(String[] arguments) {
        if (arguments.length != 2) {
            System.err.println(
                    "usage: java tools/ReleaseSourcePolicy.java <verify-clean|verify-working-types|verify-release|digest-working|digest-head> <repository>");
            System.exit(2);
        }
        Path repository = Path.of(arguments[1]).toAbsolutePath().normalize();
        try {
            switch (arguments[0]) {
                case "verify-clean" -> verifyClean(repository);
                case "verify-working-types" -> workingEntries(repository);
                case "verify-release" -> verifyRelease(repository);
                case "digest-working" -> System.out.println(digest(workingEntries(repository)));
                case "digest-head" -> System.out.println(digest(headEntries(repository)));
                default -> throw new PolicyException("unknown command: " + arguments[0]);
            }
        } catch (IOException | InterruptedException | NoSuchAlgorithmException | PolicyException exception) {
            System.err.println("AFTERLIGHT_RELEASE_SOURCE_ERROR " + exception.getMessage());
            System.exit(1);
        }
    }

    private static void verifyRelease(Path repository)
            throws IOException, InterruptedException, NoSuchAlgorithmException, PolicyException {
        verifyClean(repository);
        Map<String, SourceEntry> workingEntries = workingEntries(repository);
        auditContent(workingEntries);
        String working = digest(workingEntries);
        String committed = digest(headEntries(repository));
        if (!committed.equals(working)) {
            throw new PolicyException(
                    "clean working digest does not match HEAD Git objects: committed="
                            + committed
                            + " working="
                            + working);
        }
    }

    private static void verifyClean(Path repository)
            throws IOException, InterruptedException, PolicyException {
        List<String> violations = new ArrayList<>();
        nulStrings(runGit(
                        repository,
                        "diff",
                        "--name-only",
                        "--no-renames",
                        "-z",
                        "HEAD",
                        "--"))
                .stream()
                .filter(ReleaseSourcePolicy::isReleaseRelevant)
                .sorted()
                .map(path -> "tracked:" + path)
                .forEach(violations::add);
        untrackedPaths(repository).stream()
                .filter(ReleaseSourcePolicy::isReleaseRelevant)
                .sorted()
                .map(path -> "untracked:" + path)
                .forEach(violations::add);
        if (!violations.isEmpty()) {
            throw new PolicyException("release source tree is dirty: " + String.join(",", violations));
        }
    }

    private static Map<String, SourceEntry> workingEntries(Path repository)
            throws IOException, InterruptedException, PolicyException {
        TreeMap<String, SourceEntry> entries = new TreeMap<>();
        TreeSet<String> paths = new TreeSet<>(nulStrings(runGit(
                repository, "ls-files", "-z", "--cached")));
        paths.addAll(untrackedPaths(repository));
        for (String path : paths) {
            if (!isReleaseRelevant(path)) {
                continue;
            }
            Path source = resolveWorkingInput(repository, path);
            WorkingMetadata before = inspectWorkingInput(source, path);
            byte[] content = Files.readAllBytes(source);
            WorkingMetadata after = inspectWorkingInput(source, path);
            if (!before.sameFile(after) || content.length != before.size()) {
                throw new PolicyException("working_input_changed_during_read path=" + path);
            }
            entries.put(path, new SourceEntry(before.mode(), BLOB_TYPE, content));
        }
        return entries;
    }

    private static Path resolveWorkingInput(Path repository, String path) throws PolicyException {
        Path relative = Path.of(path);
        Path normalized = relative.normalize();
        if (relative.isAbsolute()
                || normalized.getNameCount() == 0
                || normalized.startsWith("..")) {
            throw new PolicyException("invalid_working_path path=" + path);
        }
        Path source = repository.resolve(normalized).normalize();
        if (!source.startsWith(repository)) {
            throw new PolicyException("invalid_working_path path=" + path);
        }
        Path parent = repository;
        for (int index = 0; index < normalized.getNameCount() - 1; index++) {
            parent = parent.resolve(normalized.getName(index));
            BasicFileAttributes attributes;
            try {
                attributes = Files.readAttributes(parent, BasicFileAttributes.class, NOFOLLOW);
            } catch (NoSuchFileException exception) {
                throw new PolicyException("missing_working_parent path=" + path);
            } catch (UnsupportedOperationException | SecurityException exception) {
                throw new PolicyException("working_parent_metadata_unavailable path=" + path);
            } catch (IOException exception) {
                throw new PolicyException(
                        "working_parent_metadata_failed path="
                                + path
                                + " reason="
                                + safeMessage(exception));
            }
            if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
                throw new PolicyException(
                        "non_directory_working_parent path="
                                + path
                                + " parent="
                                + repository.relativize(parent)
                                + " kind="
                                + kind(attributes));
            }
        }
        return source;
    }

    private static WorkingMetadata inspectWorkingInput(Path source, String path)
            throws PolicyException {
        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(source, BasicFileAttributes.class, NOFOLLOW);
        } catch (NoSuchFileException exception) {
            throw new PolicyException("missing_working_input path=" + path);
        } catch (UnsupportedOperationException | SecurityException exception) {
            throw new PolicyException("working_input_metadata_unavailable path=" + path);
        } catch (IOException exception) {
            throw new PolicyException(
                    "working_input_metadata_failed path="
                            + path
                            + " reason="
                            + safeMessage(exception));
        }
        if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
            throw new PolicyException(
                    "non_regular_working_input path=" + path + " kind=" + kind(attributes));
        }

        long linkCount;
        Set<PosixFilePermission> permissions;
        try {
            Object rawLinkCount = Files.getAttribute(source, "unix:nlink", NOFOLLOW);
            if (!(rawLinkCount instanceof Number number)) {
                throw new PolicyException("hardlink_metadata_invalid path=" + path);
            }
            linkCount = number.longValue();
            permissions = Files.getPosixFilePermissions(source, NOFOLLOW);
        } catch (UnsupportedOperationException | SecurityException exception) {
            throw new PolicyException("working_input_posix_metadata_unavailable path=" + path);
        } catch (IOException exception) {
            throw new PolicyException(
                    "working_input_posix_metadata_failed path="
                            + path
                            + " reason="
                            + safeMessage(exception));
        }
        if (linkCount != 1) {
            throw new PolicyException(
                    "hardlink_count path=" + path + " expected=1 actual=" + linkCount);
        }
        boolean executable = permissions.contains(PosixFilePermission.OWNER_EXECUTE)
                || permissions.contains(PosixFilePermission.GROUP_EXECUTE)
                || permissions.contains(PosixFilePermission.OTHERS_EXECUTE);
        return new WorkingMetadata(
                executable ? EXECUTABLE_MODE : REGULAR_MODE,
                attributes.size(),
                linkCount,
                attributes.fileKey());
    }

    private static Map<String, SourceEntry> headEntries(Path repository)
            throws IOException, InterruptedException, PolicyException {
        List<GitEntry> listing = new ArrayList<>();
        for (String entry : nulStrings(runGit(
                repository, "ls-tree", "-r", "-t", "-z", "--full-tree", "HEAD"))) {
            listing.add(parseGitEntry(entry));
        }

        TreeMap<String, SourceEntry> entries = new TreeMap<>();
        for (GitEntry entry : listing) {
            if (!isReleaseRelevant(entry.path())) {
                continue;
            }
            String objectType = new String(
                            runGit(repository, "cat-file", "-t", entry.objectId()),
                            StandardCharsets.UTF_8)
                    .strip();
            if (!entry.type().equals(objectType)) {
                throw unsupportedGitEntry(entry, " object_type=" + objectType);
            }
            if (TREE_MODE.equals(entry.mode()) && TREE_TYPE.equals(entry.type())) {
                boolean hasReleaseRelevantDescendant = listing.stream()
                        .anyMatch(candidate -> !candidate.path().equals(entry.path())
                                && candidate.path().startsWith(entry.path() + "/")
                                && isReleaseRelevant(candidate.path()));
                if (!hasReleaseRelevantDescendant) {
                    throw unsupportedGitEntry(entry, "");
                }
                continue;
            }
            if ((!REGULAR_MODE.equals(entry.mode()) && !EXECUTABLE_MODE.equals(entry.mode()))
                    || !BLOB_TYPE.equals(entry.type())) {
                throw unsupportedGitEntry(entry, "");
            }
            entries.put(
                    entry.path(),
                    new SourceEntry(
                            entry.mode(),
                            entry.type(),
                            runGit(repository, "cat-file", "blob", entry.objectId())));
        }
        return entries;
    }

    private static void auditContent(Map<String, SourceEntry> entries) throws PolicyException {
        for (Map.Entry<String, SourceEntry> mapEntry : entries.entrySet()) {
            byte[] content = mapEntry.getValue().content();
            if (!isTextSource(mapEntry.getKey())) {
                continue;
            }
            if (contains(content, FORBIDDEN_U2014)) {
                throw new PolicyException("forbidden_u2014 path=" + mapEntry.getKey());
            }
            for (SecretMarker marker : SECRET_MARKERS) {
                if (contains(content, marker.value())) {
                    throw new PolicyException(
                            "secret_marker path="
                                    + mapEntry.getKey()
                                    + " marker="
                                    + marker.name());
                }
            }
        }
    }

    private static boolean isTextSource(String path) {
        return path.equals(".gitignore")
                || path.equals("AGENTS.md")
                || path.equals("README.md")
                || path.equals("build.gradle")
                || path.equals("gradle.lockfile")
                || path.equals("gradle.properties")
                || path.equals("settings.gradle")
                || path.endsWith(".gradle")
                || path.endsWith(".java")
                || path.endsWith(".json")
                || path.endsWith(".mcmeta")
                || path.endsWith(".md")
                || path.endsWith(".properties")
                || path.endsWith(".toml")
                || path.endsWith(".txt")
                || path.endsWith(".xml")
                || path.endsWith(".yml")
                || path.endsWith(".yaml");
    }

    private static boolean contains(byte[] content, byte[] marker) {
        if (marker.length == 0 || marker.length > content.length) {
            return false;
        }
        outer:
        for (int offset = 0; offset <= content.length - marker.length; offset++) {
            for (int index = 0; index < marker.length; index++) {
                if (content[offset + index] != marker[index]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private static GitEntry parseGitEntry(String entry) throws PolicyException {
        int separator = entry.indexOf('\t');
        if (separator < 0) {
            throw new PolicyException("unexpected_git_ls_tree_entry");
        }
        String[] metadata = entry.substring(0, separator).split(" ");
        if (metadata.length != 3
                || metadata[0].isEmpty()
                || metadata[1].isEmpty()
                || !metadata[2].matches("[0-9a-f]{40,64}")) {
            throw new PolicyException("unexpected_git_ls_tree_entry");
        }
        return new GitEntry(metadata[0], metadata[1], metadata[2], entry.substring(separator + 1));
    }

    private static PolicyException unsupportedGitEntry(GitEntry entry, String suffix) {
        return new PolicyException(
                "unsupported_git_entry mode="
                        + entry.mode()
                        + " type="
                        + entry.type()
                        + " path="
                        + entry.path()
                        + suffix);
    }

    private static String digest(Map<String, SourceEntry> entries)
            throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(DIGEST_DOMAIN);
        for (Map.Entry<String, SourceEntry> mapEntry : entries.entrySet()) {
            SourceEntry entry = mapEntry.getValue();
            updateLengthPrefixed(digest, entry.mode().getBytes(StandardCharsets.UTF_8));
            updateLengthPrefixed(digest, entry.type().getBytes(StandardCharsets.UTF_8));
            updateLengthPrefixed(digest, mapEntry.getKey().getBytes(StandardCharsets.UTF_8));
            digest.update(ByteBuffer.allocate(Long.BYTES).putLong(entry.content().length).array());
            digest.update(entry.content());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void updateLengthPrefixed(MessageDigest digest, byte[] value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value.length).array());
        digest.update(value);
    }

    private static boolean isReleaseRelevant(String path) {
        return EXCLUDED_PREFIXES.stream().noneMatch(prefix ->
                path.equals(prefix.substring(0, prefix.length() - 1)) || path.startsWith(prefix));
    }

    private static TreeSet<String> untrackedPaths(Path repository)
            throws IOException, InterruptedException, PolicyException {
        TreeSet<String> paths = new TreeSet<>(nulStrings(runGit(
                repository, "ls-files", "-z", "--others", "--exclude-standard")));
        paths.addAll(nulStrings(runGit(
                repository,
                "ls-files",
                "-z",
                "--others",
                "--ignored",
                "--exclude-standard")));
        return paths;
    }

    private static String kind(BasicFileAttributes attributes) {
        if (attributes.isSymbolicLink()) {
            return "symbolic_link";
        }
        if (attributes.isDirectory()) {
            return "directory";
        }
        if (attributes.isRegularFile()) {
            return "regular_file";
        }
        return "other";
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message.replace('\n', ' ').replace('\r', ' ');
    }

    private static List<String> nulStrings(byte[] output) {
        if (output.length == 0) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        int start = 0;
        for (int index = 0; index < output.length; index++) {
            if (output[index] == 0) {
                values.add(new String(
                        Arrays.copyOfRange(output, start, index), StandardCharsets.UTF_8));
                start = index + 1;
            }
        }
        if (start != output.length) {
            values.add(new String(
                    Arrays.copyOfRange(output, start, output.length), StandardCharsets.UTF_8));
        }
        return List.copyOf(values);
    }

    private static byte[] runGit(Path repository, String... arguments)
            throws IOException, InterruptedException, PolicyException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(repository.toString());
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        process.getInputStream().transferTo(output);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new PolicyException(
                    "git command failed ("
                            + String.join(" ", command)
                            + "): "
                            + output.toString(StandardCharsets.UTF_8).strip());
        }
        return output.toByteArray();
    }

    private record SecretMarker(String name, byte[] value) {}

    private record SourceEntry(String mode, String type, byte[] content) {}

    private record WorkingMetadata(String mode, long size, long linkCount, Object fileKey) {
        private boolean sameFile(WorkingMetadata other) {
            return mode.equals(other.mode)
                    && size == other.size
                    && linkCount == other.linkCount
                    && Objects.equals(fileKey, other.fileKey);
        }
    }

    private record GitEntry(String mode, String type, String objectId, String path) {}

    private static final class PolicyException extends Exception {
        private PolicyException(String message) {
            super(message);
        }
    }
}

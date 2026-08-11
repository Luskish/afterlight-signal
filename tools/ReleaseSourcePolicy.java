import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
    private static final String STAGE_MANIFEST_NAME = ".afterlight-release-stage-manifest";
    private static final String STAGE_MANIFEST_HEADER = "AFTERLIGHT_RELEASE_STAGE_V1";
    private static final LinkOption[] NOFOLLOW = {LinkOption.NOFOLLOW_LINKS};
    private static final Set<PosixFilePermission> PRIVATE_DIRECTORY_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);
    private static final Set<PosixFilePermission> PRIVATE_FILE_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE);
    private static final Set<PosixFilePermission> IMMUTABLE_DIRECTORY_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_EXECUTE);
    private static final Set<PosixFilePermission> IMMUTABLE_REGULAR_PERMISSIONS =
            Set.of(PosixFilePermission.OWNER_READ);
    private static final Set<PosixFilePermission> IMMUTABLE_EXECUTABLE_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_EXECUTE);
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
    private static final List<SecretMarker> PRIVATE_KEY_MARKERS = List.of(
            privateKeyMarker("GENERIC_PRIVATE_KEY", "PRIVATE KEY"),
            privateKeyMarker("ENCRYPTED_PRIVATE_KEY", "ENCRYPTED PRIVATE KEY"),
            privateKeyMarker("RSA_PRIVATE_KEY", "RSA PRIVATE KEY"),
            privateKeyMarker("EC_PRIVATE_KEY", "EC PRIVATE KEY"),
            privateKeyMarker("DSA_PRIVATE_KEY", "DSA PRIVATE KEY"),
            privateKeyMarker("OPENSSH_PRIVATE_KEY", "OPENSSH PRIVATE KEY"),
            privateKeyMarker("PGP_PRIVATE_KEY", "PGP PRIVATE KEY BLOCK"));
    private static final List<TokenMarker> TOKEN_MARKERS = List.of(
            new TokenMarker("GITHUB_GHP", ascii("gh" + "p_"), 36, TokenAlphabet.ALPHANUMERIC),
            new TokenMarker("GITHUB_GHO", ascii("gh" + "o_"), 36, TokenAlphabet.ALPHANUMERIC),
            new TokenMarker("GITHUB_GHU", ascii("gh" + "u_"), 36, TokenAlphabet.ALPHANUMERIC),
            new TokenMarker("GITHUB_GHS", ascii("gh" + "s_"), 36, TokenAlphabet.ALPHANUMERIC),
            new TokenMarker("GITHUB_GHR", ascii("gh" + "r_"), 36, TokenAlphabet.ALPHANUMERIC),
            new TokenMarker(
                    "GITHUB_PAT",
                    ascii("github" + "_pat_"),
                    30,
                    TokenAlphabet.ALPHANUMERIC_UNDERSCORE),
            new TokenMarker(
                    "OPENAI_PROJECT_KEY",
                    ascii("sk-" + "proj-"),
                    20,
                    TokenAlphabet.ALPHANUMERIC_UNDERSCORE_HYPHEN),
            new TokenMarker(
                    "OPENAI_LEGACY_KEY",
                    ascii("sk-"),
                    20,
                    TokenAlphabet.ALPHANUMERIC));

    private ReleaseSourcePolicy() {}

    public static void main(String[] arguments) {
        if ((arguments.length == 5
                        && (arguments[0].equals("stage-release")
                                || arguments[0].equals("verify-staged-release")))
                || arguments.length == 2) {
            run(arguments);
            return;
        }
        System.err.println(
                "usage: java tools/ReleaseSourcePolicy.java <verify-clean|verify-working-types|verify-release|digest-working|digest-head> <repository>\n"
                        + "   or: java tools/ReleaseSourcePolicy.java <stage-release|verify-staged-release> <repository> <destination> <expected-commit> <expected-digest>");
        System.exit(2);
    }

    private static void run(String[] arguments) {
        try {
            Path requestedRepository = Path.of(arguments[1]).toAbsolutePath().normalize();
            Path repository = resolveRepository(requestedRepository);
            switch (arguments[0]) {
                case "verify-clean" -> verifyClean(repository);
                case "verify-working-types" -> workingEntries(repository);
                case "verify-release" -> verifyRelease(repository);
                case "digest-working" -> System.out.println(digest(workingEntries(repository)));
                case "digest-head" -> System.out.println(digest(headEntries(repository)));
                case "stage-release" -> stageRelease(
                        stageAnchor(
                                requestedRepository,
                                repository,
                                Path.of(arguments[2]).toAbsolutePath().normalize()),
                        expectedIdentity(arguments[3], arguments[4]));
                case "verify-staged-release" -> verifyStagedRelease(
                        stageAnchor(
                                requestedRepository,
                                repository,
                                Path.of(arguments[2]).toAbsolutePath().normalize()),
                        expectedIdentity(arguments[3], arguments[4]));
                default -> throw new PolicyException("unknown command: " + arguments[0]);
            }
        } catch (IOException | InterruptedException | NoSuchAlgorithmException | PolicyException exception) {
            System.err.println("AFTERLIGHT_RELEASE_SOURCE_ERROR " + exception.getMessage());
            System.exit(1);
        }
    }

    private static void verifyRelease(Path repository)
            throws IOException, InterruptedException, NoSuchAlgorithmException, PolicyException {
        VerifiedSources verified = verifySources(repository);
        if (!verified.committedDigest().equals(verified.workingDigest())) {
            throw digestMismatch(verified);
        }
    }

    private static void stageRelease(StageAnchor anchor, StageIdentity expected)
            throws IOException, InterruptedException, NoSuchAlgorithmException, PolicyException {
        validateStageAncestors(anchor, true, false);
        verifyExpectedHead(anchor.repository(), expected.commit(), "before_stage");
        Map<String, SourceEntry> head = headEntries(anchor.repository());
        auditContent(head);
        String committedDigest = digest(head);
        if (!committedDigest.equals(expected.digest())) {
            throw new PolicyException(
                    "expected_digest_mismatch phase=before_stage expected="
                            + expected.digest()
                            + " actual="
                            + committedDigest);
        }
        verifyClean(anchor.repository());
        Map<String, SourceEntry> working = workingEntries(anchor.repository());
        auditContent(working);
        VerifiedSources verified = new VerifiedSources(head, committedDigest, digest(working));
        if (!verified.committedDigest().equals(verified.workingDigest())) {
            throw digestMismatch(verified);
        }
        materialize(anchor, verified.headEntries(), expected);
        verifyExpectedHead(anchor.repository(), expected.commit(), "after_stage");
        verifyStagedSnapshot(anchor, verified.headEntries(), expected);
    }

    private static void verifyStagedRelease(StageAnchor anchor, StageIdentity expected)
            throws IOException, InterruptedException, NoSuchAlgorithmException, PolicyException {
        validateStageAncestors(anchor, false, true);
        StageIdentity manifest = readStageManifest(anchor.destination());
        verifyManifestIdentity(manifest, expected);
        verifyExpectedHead(anchor.repository(), expected.commit(), "post_build");
        Map<String, SourceEntry> head = headEntries(anchor.repository());
        auditContent(head);
        String committedDigest = digest(head);
        if (!committedDigest.equals(expected.digest())) {
            throw new PolicyException(
                    "expected_digest_mismatch phase=post_build expected="
                            + expected.digest()
                            + " actual="
                            + committedDigest);
        }
        verifyClean(anchor.repository());
        Map<String, SourceEntry> working = workingEntries(anchor.repository());
        auditContent(working);
        String workingDigest = digest(working);
        if (!workingDigest.equals(expected.digest())) {
            throw new PolicyException(
                    "working_digest_mismatch phase=post_build expected="
                            + expected.digest()
                            + " actual="
                            + workingDigest);
        }
        verifyStagedSnapshot(anchor, head, expected);
    }

    private static VerifiedSources verifySources(Path repository)
            throws IOException, InterruptedException, NoSuchAlgorithmException, PolicyException {
        verifyClean(repository);
        Map<String, SourceEntry> working = workingEntries(repository);
        auditContent(working);
        Map<String, SourceEntry> head = headEntries(repository);
        auditContent(head);
        return new VerifiedSources(head, digest(head), digest(working));
    }

    private static PolicyException digestMismatch(VerifiedSources verified) {
        return new PolicyException(
                "clean working digest does not match HEAD Git objects: committed="
                        + verified.committedDigest()
                        + " working="
                        + verified.workingDigest());
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
        TreeSet<String> trackedPaths = new TreeSet<>(nulStrings(runGit(
                repository, "ls-files", "-z", "--cached")));
        TreeSet<String> paths = new TreeSet<>(trackedPaths);
        untrackedPaths(repository).stream()
                .filter(path -> trackedPaths.stream()
                        .noneMatch(tracked -> tracked.startsWith(path + "/")))
                .forEach(paths::add);
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
        Path normalized = validatedRelativePath(path, "working");
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
            validatedRelativePath(entry.path(), "git");
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
            String path = mapEntry.getKey();
            byte[] content = mapEntry.getValue().content();
            for (SecretMarker marker : PRIVATE_KEY_MARKERS) {
                if (contains(content, marker.value())) {
                    throw new PolicyException(
                            "secret_marker path=" + path + " marker=" + marker.name());
                }
            }
            for (TokenMarker marker : TOKEN_MARKERS) {
                if (containsToken(content, marker)) {
                    throw new PolicyException(
                            "secret_marker path=" + path + " marker=" + marker.name());
                }
            }
            if (isValidUtf8(content) && contains(content, FORBIDDEN_U2014)) {
                throw new PolicyException("forbidden_u2014 path=" + path);
            }
        }
    }

    private static boolean containsToken(byte[] content, TokenMarker marker) {
        int offset = indexOf(content, marker.prefix(), 0);
        while (offset >= 0) {
            int valueStart = offset + marker.prefix().length;
            int valueLength = 0;
            while (valueStart + valueLength < content.length
                    && marker.alphabet().accepts(content[valueStart + valueLength])) {
                valueLength++;
            }
            if (valueLength >= marker.minimumLength()) {
                return true;
            }
            offset = indexOf(content, marker.prefix(), offset + 1);
        }
        return false;
    }

    private static boolean isValidUtf8(byte[] content) {
        try {
            StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
            return true;
        } catch (java.nio.charset.CharacterCodingException exception) {
            return false;
        }
    }

    private static boolean contains(byte[] content, byte[] marker) {
        return indexOf(content, marker, 0) >= 0;
    }

    private static int indexOf(byte[] content, byte[] marker, int start) {
        if (marker.length == 0 || marker.length > content.length) {
            return -1;
        }
        outer:
        for (int offset = Math.max(0, start); offset <= content.length - marker.length; offset++) {
            for (int index = 0; index < marker.length; index++) {
                if (content[offset + index] != marker[index]) {
                    continue outer;
                }
            }
            return offset;
        }
        return -1;
    }

    private static Path resolveRepository(Path requested) throws IOException, PolicyException {
        Path repository;
        try {
            repository = requested.toRealPath();
        } catch (NoSuchFileException exception) {
            throw new PolicyException("missing_repository path=" + requested);
        }
        BasicFileAttributes attributes =
                Files.readAttributes(repository, BasicFileAttributes.class, NOFOLLOW);
        if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
            throw new PolicyException(
                    "invalid_repository path=" + repository + " kind=" + kind(attributes));
        }
        return repository;
    }

    private static StageAnchor stageAnchor(
            Path requestedRepository, Path repository, Path requestedDestination)
            throws PolicyException {
        Path relative;
        if (requestedDestination.startsWith(requestedRepository)) {
            relative = requestedRepository.relativize(requestedDestination).normalize();
        } else if (requestedDestination.startsWith(repository)) {
            relative = repository.relativize(requestedDestination).normalize();
        } else {
            throw new PolicyException("unsafe_stage_destination path=" + requestedDestination);
        }
        if (relative.getNameCount() == 0 || relative.startsWith("..")) {
            throw new PolicyException("unsafe_stage_destination path=" + requestedDestination);
        }
        String normalized = relative.toString().replace('\\', '/');
        if (isReleaseRelevant(normalized)) {
            throw new PolicyException("release_relevant_stage_destination path=" + normalized);
        }
        Path destination = repository.resolve(relative).normalize();
        if (!destination.startsWith(repository) || destination.equals(repository)) {
            throw new PolicyException("unsafe_stage_destination path=" + requestedDestination);
        }
        return new StageAnchor(repository, destination, relative);
    }

    private static StageIdentity expectedIdentity(String commit, String digest)
            throws PolicyException {
        if (!commit.matches("[0-9a-f]{40,64}")) {
            throw new PolicyException("invalid_expected_commit value=" + commit);
        }
        if (!digest.matches("[0-9a-f]{64}")) {
            throw new PolicyException("invalid_expected_digest value=" + digest);
        }
        return new StageIdentity(commit, digest);
    }

    private static void verifyExpectedHead(Path repository, String expected, String phase)
            throws IOException, InterruptedException, PolicyException {
        String actual = headCommit(repository);
        if (!actual.equals(expected)) {
            throw new PolicyException(
                    "expected_head_mismatch phase="
                            + phase
                            + " expected="
                            + expected
                            + " actual="
                            + actual);
        }
    }

    private static String headCommit(Path repository)
            throws IOException, InterruptedException, PolicyException {
        String commit = new String(
                        runGit(repository, "rev-parse", "--verify", "HEAD^{commit}"),
                        StandardCharsets.UTF_8)
                .strip();
        if (!commit.matches("[0-9a-f]{40,64}")) {
            throw new PolicyException("invalid_head_commit value=" + commit);
        }
        return commit;
    }

    private static void validateStageAncestors(
            StageAnchor anchor, boolean createMissingParents, boolean includeDestination)
            throws IOException, PolicyException {
        Path current = anchor.repository();
        int count = anchor.relative().getNameCount();
        int limit = includeDestination ? count : count - 1;
        for (int index = 0; index < limit; index++) {
            current = current.resolve(anchor.relative().getName(index));
            String display = stageDisplay(anchor, current);
            if (Files.notExists(current, NOFOLLOW)) {
                if (!createMissingParents) {
                    throw new PolicyException("missing_stage_ancestor path=" + display);
                }
                createPrivateDirectory(current);
            }
            BasicFileAttributes attributes =
                    Files.readAttributes(current, BasicFileAttributes.class, NOFOLLOW);
            if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
                throw new PolicyException(
                        "unsafe_stage_ancestor path="
                                + display
                                + " kind="
                                + kind(attributes));
            }
            Path real = current.toRealPath();
            if (!real.equals(current) || !real.startsWith(anchor.repository())) {
                throw new PolicyException(
                        "unsafe_stage_ancestor path=" + display + " kind=path_escape");
            }
        }
    }

    private static String stageDisplay(StageAnchor anchor, Path path) {
        return anchor.repository().relativize(path).toString().replace('\\', '/');
    }

    private static void materialize(
            StageAnchor anchor, Map<String, SourceEntry> entries, StageIdentity identity)
            throws IOException, PolicyException {
        validateStageAncestors(anchor, true, false);
        if (Files.exists(anchor.destination(), NOFOLLOW)) {
            validateStageAncestors(anchor, false, true);
            deleteExistingStage(anchor);
        }
        validateStageAncestors(anchor, false, false);
        createPrivateDirectory(anchor.destination());
        try {
            for (Map.Entry<String, SourceEntry> mapEntry : entries.entrySet()) {
                Path relative = validatedRelativePath(mapEntry.getKey(), "stage");
                Path output = anchor.destination().resolve(relative).normalize();
                if (!output.startsWith(anchor.destination())) {
                    throw new PolicyException("invalid_stage_path path=" + mapEntry.getKey());
                }
                createPrivateParents(anchor, output.getParent());
                writePrivateFile(output, mapEntry.getValue().content());
                verifyStagedFile(output, mapEntry.getKey(), mapEntry.getValue());
                Files.setPosixFilePermissions(
                        output,
                        EXECUTABLE_MODE.equals(mapEntry.getValue().mode())
                                ? IMMUTABLE_EXECUTABLE_PERMISSIONS
                                : IMMUTABLE_REGULAR_PERMISSIONS);
            }
            Path manifest = anchor.destination().resolve(STAGE_MANIFEST_NAME);
            writePrivateFile(manifest, stageManifest(identity).getBytes(StandardCharsets.UTF_8));
            Files.setPosixFilePermissions(manifest, IMMUTABLE_REGULAR_PERMISSIONS);
            try (var paths = Files.walk(anchor.destination())) {
                for (Path directory : paths.filter(Files::isDirectory)
                        .sorted(Comparator.reverseOrder())
                        .toList()) {
                    Files.setPosixFilePermissions(directory, IMMUTABLE_DIRECTORY_PERMISSIONS);
                }
            }
        } catch (IOException | PolicyException exception) {
            try {
                deleteExistingStage(anchor);
            } catch (IOException | PolicyException ignored) {
                exception.addSuppressed(ignored);
            }
            throw exception;
        }
    }

    private static void verifyStagedSnapshot(
            StageAnchor anchor, Map<String, SourceEntry> expectedEntries, StageIdentity expected)
            throws IOException, NoSuchAlgorithmException, PolicyException {
        validateStageAncestors(anchor, false, true);
        StageIdentity manifest = readStageManifest(anchor.destination());
        verifyManifestIdentity(manifest, expected);
        TreeMap<String, SourceEntry> stagedEntries = new TreeMap<>();
        TreeSet<String> expectedFiles = new TreeSet<>(expectedEntries.keySet());
        expectedFiles.add(STAGE_MANIFEST_NAME);
        TreeSet<String> expectedDirectories = new TreeSet<>();
        expectedDirectories.add("");
        for (String path : expectedEntries.keySet()) {
            Path parent = Path.of(path).getParent();
            while (parent != null) {
                expectedDirectories.add(parent.toString().replace('\\', '/'));
                parent = parent.getParent();
            }
        }
        for (Map.Entry<String, SourceEntry> mapEntry : expectedEntries.entrySet()) {
            Path output = anchor.destination().resolve(validatedRelativePath(
                    mapEntry.getKey(), "staged_verification"));
            byte[] content = verifyImmutableStagedFile(
                    output, mapEntry.getKey(), mapEntry.getValue().mode());
            if (!Arrays.equals(mapEntry.getValue().content(), content)) {
                throw new PolicyException("staged_content_mismatch path=" + mapEntry.getKey());
            }
            stagedEntries.put(
                    mapEntry.getKey(),
                    new SourceEntry(mapEntry.getValue().mode(), BLOB_TYPE, content));
        }
        try (var paths = Files.walk(anchor.destination())) {
            for (Path path : paths.toList()) {
                BasicFileAttributes attributes =
                        Files.readAttributes(path, BasicFileAttributes.class, NOFOLLOW);
                String relative = anchor.destination().equals(path)
                        ? ""
                        : anchor.destination().relativize(path).toString().replace('\\', '/');
                if (attributes.isSymbolicLink()) {
                    throw new PolicyException("unexpected_staged_path path=" + relative);
                }
                if (attributes.isDirectory()) {
                    if (!expectedDirectories.contains(relative)) {
                        throw new PolicyException("unexpected_staged_path path=" + relative);
                    }
                } else if (!attributes.isRegularFile() || !expectedFiles.contains(relative)) {
                    throw new PolicyException("unexpected_staged_path path=" + relative);
                }
            }
        }
        String actualDigest = digest(stagedEntries);
        if (!actualDigest.equals(expected.digest())) {
            throw new PolicyException(
                    "staged_digest_mismatch expected="
                            + expected.digest()
                            + " actual="
                            + actualDigest);
        }
    }

    private static byte[] verifyImmutableStagedFile(Path output, String path, String mode)
            throws IOException, PolicyException {
        BasicFileAttributes attributes =
                Files.readAttributes(output, BasicFileAttributes.class, NOFOLLOW);
        if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
            throw new PolicyException("non_regular_staged_input path=" + path);
        }
        Object rawLinkCount = Files.getAttribute(output, "unix:nlink", NOFOLLOW);
        if (!(rawLinkCount instanceof Number number) || number.longValue() != 1) {
            throw new PolicyException("staged_hardlink_count path=" + path);
        }
        byte[] content = Files.readAllBytes(output);
        Set<PosixFilePermission> expectedPermissions = EXECUTABLE_MODE.equals(mode)
                ? IMMUTABLE_EXECUTABLE_PERMISSIONS
                : IMMUTABLE_REGULAR_PERMISSIONS;
        Set<PosixFilePermission> actualPermissions =
                Files.getPosixFilePermissions(output, NOFOLLOW);
        if (!actualPermissions.equals(expectedPermissions)) {
            throw new PolicyException(
                    "staged_permissions_mismatch path="
                            + path
                            + " expected="
                            + expectedPermissions
                            + " actual="
                            + actualPermissions);
        }
        return content;
    }

    private static String stageManifest(StageIdentity identity) {
        return STAGE_MANIFEST_HEADER
                + "\nsourceCommit="
                + identity.commit()
                + "\nsourceTreeSha256="
                + identity.digest()
                + "\n";
    }

    private static StageIdentity readStageManifest(Path destination)
            throws IOException, PolicyException {
        Path manifest = destination.resolve(STAGE_MANIFEST_NAME);
        byte[] content = verifyImmutableStagedFile(manifest, STAGE_MANIFEST_NAME, REGULAR_MODE);
        String text = new String(content, StandardCharsets.UTF_8);
        List<String> lines = text.lines().toList();
        if (lines.size() != 3 || !lines.get(0).equals(STAGE_MANIFEST_HEADER)) {
            throw new PolicyException("invalid_stage_manifest");
        }
        String commitPrefix = "sourceCommit=";
        String digestPrefix = "sourceTreeSha256=";
        if (!lines.get(1).startsWith(commitPrefix) || !lines.get(2).startsWith(digestPrefix)) {
            throw new PolicyException("invalid_stage_manifest");
        }
        return expectedIdentity(
                lines.get(1).substring(commitPrefix.length()),
                lines.get(2).substring(digestPrefix.length()));
    }

    private static void verifyManifestIdentity(StageIdentity manifest, StageIdentity expected)
            throws PolicyException {
        if (!manifest.commit().equals(expected.commit())) {
            throw new PolicyException(
                    "stage_manifest_commit_mismatch expected="
                            + expected.commit()
                            + " actual="
                            + manifest.commit());
        }
        if (!manifest.digest().equals(expected.digest())) {
            throw new PolicyException(
                    "stage_manifest_digest_mismatch expected="
                            + expected.digest()
                            + " actual="
                            + manifest.digest());
        }
    }

    private static void verifyStagedFile(Path output, String path, SourceEntry expected)
            throws IOException, PolicyException {
        BasicFileAttributes attributes =
                Files.readAttributes(output, BasicFileAttributes.class, NOFOLLOW);
        if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
            throw new PolicyException("non_regular_staged_input path=" + path);
        }
        Object rawLinkCount = Files.getAttribute(output, "unix:nlink", NOFOLLOW);
        if (!(rawLinkCount instanceof Number number) || number.longValue() != 1) {
            throw new PolicyException("staged_hardlink_count path=" + path);
        }
        byte[] actual = Files.readAllBytes(output);
        if (!Arrays.equals(expected.content(), actual)) {
            throw new PolicyException("staged_content_mismatch path=" + path);
        }
    }

    private static void createPrivateParents(StageAnchor anchor, Path parent)
            throws IOException, PolicyException {
        if (parent == null) {
            return;
        }
        Path relative = anchor.destination().relativize(parent);
        Path current = anchor.destination();
        for (Path component : relative) {
            validateStageAncestors(anchor, false, true);
            current = current.resolve(component);
            if (Files.notExists(current, NOFOLLOW)) {
                createPrivateDirectory(current);
            } else {
                BasicFileAttributes attributes =
                        Files.readAttributes(current, BasicFileAttributes.class, NOFOLLOW);
                if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
                    throw new PolicyException(
                            "unsafe_staged_parent path="
                                    + anchor.destination().relativize(current)
                                    + " kind="
                                    + kind(attributes));
                }
            }
        }
    }

    private static void createPrivateDirectory(Path directory) throws IOException {
        Files.createDirectory(
                directory,
                PosixFilePermissions.asFileAttribute(PRIVATE_DIRECTORY_PERMISSIONS));
        Files.setPosixFilePermissions(directory, PRIVATE_DIRECTORY_PERMISSIONS);
    }

    private static void writePrivateFile(Path output, byte[] content) throws IOException {
        try (var channel = Files.newByteChannel(
                output,
                Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                PosixFilePermissions.asFileAttribute(PRIVATE_FILE_PERMISSIONS))) {
            ByteBuffer buffer = ByteBuffer.wrap(content);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
        }
        Files.setPosixFilePermissions(output, PRIVATE_FILE_PERMISSIONS);
    }

    private static void deleteExistingStage(StageAnchor anchor)
            throws IOException, PolicyException {
        validateStageAncestors(anchor, false, true);
        Path destination = anchor.destination();
        if (!destination.toRealPath().equals(destination)
                || !destination.startsWith(anchor.repository())) {
            throw new PolicyException("unsafe_stage_cleanup path=" + destination);
        }
        if (Files.notExists(destination, NOFOLLOW)) {
            return;
        }
        BasicFileAttributes root =
                Files.readAttributes(destination, BasicFileAttributes.class, NOFOLLOW);
        if (!root.isDirectory() || root.isSymbolicLink()) {
            throw new PolicyException(
                    "unsafe_stage_ancestor path="
                            + stageDisplay(anchor, destination)
                            + " kind="
                            + kind(root));
        }
        Files.walkFileTree(
                destination,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(
                            Path directory, BasicFileAttributes attributes) throws IOException {
                        Files.setPosixFilePermissions(directory, PRIVATE_DIRECTORY_PERMISSIONS);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                            throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path directory, IOException failure)
                            throws IOException {
                        if (failure != null) {
                            throw failure;
                        }
                        Files.delete(directory);
                        return FileVisitResult.CONTINUE;
                    }
                });
    }

    private static Path validatedRelativePath(String path, String source) throws PolicyException {
        Path relative = Path.of(path);
        Path normalized = relative.normalize();
        if (relative.isAbsolute()
                || normalized.getNameCount() == 0
                || normalized.startsWith("..")
                || !normalized.toString().replace('\\', '/').equals(path)) {
            throw new PolicyException("invalid_" + source + "_path path=" + path);
        }
        return normalized;
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

    private static SecretMarker privateKeyMarker(String name, String type) {
        return new SecretMarker(name, ascii("-----BEGIN " + type + "-----"));
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private enum TokenAlphabet {
        ALPHANUMERIC {
            @Override
            boolean accepts(byte value) {
                return isAsciiAlphanumeric(value);
            }
        },
        ALPHANUMERIC_UNDERSCORE {
            @Override
            boolean accepts(byte value) {
                return isAsciiAlphanumeric(value) || value == '_';
            }
        },
        ALPHANUMERIC_UNDERSCORE_HYPHEN {
            @Override
            boolean accepts(byte value) {
                return isAsciiAlphanumeric(value) || value == '_' || value == '-';
            }
        };

        abstract boolean accepts(byte value);

        static boolean isAsciiAlphanumeric(byte value) {
            int unsigned = Byte.toUnsignedInt(value);
            return unsigned >= '0' && unsigned <= '9'
                    || unsigned >= 'A' && unsigned <= 'Z'
                    || unsigned >= 'a' && unsigned <= 'z';
        }
    }

    private record SecretMarker(String name, byte[] value) {}

    private record TokenMarker(
            String name, byte[] prefix, int minimumLength, TokenAlphabet alphabet) {}

    private record SourceEntry(String mode, String type, byte[] content) {}

    private record VerifiedSources(
            Map<String, SourceEntry> headEntries,
            String committedDigest,
            String workingDigest) {}

    private record StageAnchor(Path repository, Path destination, Path relative) {}

    private record StageIdentity(String commit, String digest) {}

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

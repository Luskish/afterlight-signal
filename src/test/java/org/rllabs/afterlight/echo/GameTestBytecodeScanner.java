package org.rllabs.afterlight.echo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

final class GameTestBytecodeScanner {
    private static final String GAME_TEST_ANNOTATION =
            "Lnet/minecraft/gametest/framework/GameTest;";
    private static final String GAME_TEST_HOLDER_ANNOTATION =
            "Lnet/neoforged/neoforge/gametest/GameTestHolder;";
    private static final String GAME_TEST_DEFAULT_BATCH = "defaultBatch";
    private static final Comparator<MethodLocation> METHOD_ORDER = Comparator
            .comparing(MethodLocation::owner)
            .thenComparing(MethodLocation::name)
            .thenComparing(MethodLocation::descriptor);

    private GameTestBytecodeScanner() {
    }

    static ScanResult scan(Path classesRoot, String holderId, TargetMethod target) throws IOException {
        List<byte[]> classFiles;
        try (var paths = Files.walk(Objects.requireNonNull(classesRoot))) {
            classFiles = paths.filter(path -> path.toString().endsWith(".class"))
                    .sorted()
                    .map(GameTestBytecodeScanner::readClass)
                    .toList();
        }
        return scan(classFiles, holderId, target);
    }

    static ScanResult scan(Iterable<byte[]> classFiles, String holderId, TargetMethod target) {
        Objects.requireNonNull(classFiles);
        Objects.requireNonNull(holderId);
        Objects.requireNonNull(target);
        ScanAccumulator accumulator = new ScanAccumulator(target);
        for (byte[] classFile : classFiles) {
            new ClassReader(Objects.requireNonNull(classFile)).accept(
                    accumulator.visitor(),
                    ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return accumulator.result(holderId);
    }

    private static byte[] readClass(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            throw new ClassReadException(path, exception);
        }
    }

    record TargetMethod(String owner, String name, String descriptor) {
        TargetMethod {
            owner = Objects.requireNonNull(owner);
            name = Objects.requireNonNull(name);
            descriptor = Objects.requireNonNull(descriptor);
        }

        boolean matches(int opcode, String invokedOwner, String invokedName, String invokedDescriptor) {
            return opcode == Opcodes.INVOKESTATIC
                    && owner.equals(invokedOwner)
                    && name.equals(invokedName)
                    && descriptor.equals(invokedDescriptor);
        }

        boolean matches(Handle handle) {
            return handle.getTag() == Opcodes.H_INVOKESTATIC
                    && owner.equals(handle.getOwner())
                    && name.equals(handle.getName())
                    && descriptor.equals(handle.getDesc());
        }
    }

    record MethodLocation(String owner, String name, String descriptor) {
        MethodLocation {
            owner = Objects.requireNonNull(owner);
            name = Objects.requireNonNull(name);
            descriptor = Objects.requireNonNull(descriptor);
        }

        String displayName() {
            return owner + "." + name + descriptor;
        }
    }

    record GameTestMethod(String owner, String name, String descriptor, String batch) {
        GameTestMethod {
            owner = Objects.requireNonNull(owner);
            name = Objects.requireNonNull(name);
            descriptor = Objects.requireNonNull(descriptor);
            batch = Objects.requireNonNull(batch);
        }

        MethodLocation location() {
            return new MethodLocation(owner, name, descriptor);
        }
    }

    record ManualTickCaller(MethodLocation callSite, GameTestMethod gameTest) {
        ManualTickCaller {
            callSite = Objects.requireNonNull(callSite);
            gameTest = Objects.requireNonNull(gameTest);
        }
    }

    static final class ScanResult {
        private final List<GameTestMethod> gameTests;
        private final Set<MethodLocation> manualTickCallSites;
        private final Map<MethodLocation, Set<MethodLocation>> calls;
        private final Map<String, ClassMetadata> classes;

        private ScanResult(
                List<GameTestMethod> gameTests,
                Set<MethodLocation> manualTickCallSites,
                Map<MethodLocation, Set<MethodLocation>> calls,
                Map<String, ClassMetadata> classes) {
            this.gameTests = List.copyOf(gameTests);
            this.manualTickCallSites = Set.copyOf(manualTickCallSites);
            this.calls = immutableSetMap(calls);
            this.classes = Map.copyOf(classes);
        }

        List<GameTestMethod> gameTests() {
            return gameTests;
        }

        List<ManualTickCaller> verifyUniqueManualTickBatches(String defaultBatch) {
            Objects.requireNonNull(defaultBatch);
            if (manualTickCallSites.isEmpty()) {
                throw new AssertionError("no manual global tick callers discovered");
            }

            Map<String, Long> batchOccurrences = new LinkedHashMap<>();
            for (GameTestMethod gameTest : gameTests) {
                batchOccurrences.merge(gameTest.batch(), 1L, Long::sum);
            }

            List<ManualTickCaller> callers = new ArrayList<>();
            manualTickCallSites.stream().sorted(METHOD_ORDER).forEach(callSite -> {
                Set<GameTestMethod> candidates = mappingCandidates(callSite);
                if (candidates.isEmpty()) {
                    throw new AssertionError("cannot map manual tick caller " + callSite.displayName());
                }
                if (candidates.size() > 1) {
                    throw new AssertionError(
                            "manual tick caller " + callSite.displayName()
                                    + " maps to multiple GameTests: "
                                    + candidates.stream().map(GameTestMethod::name).sorted().toList());
                }

                GameTestMethod gameTest = candidates.iterator().next();
                String batch = gameTest.batch();
                if (batch.isBlank()) {
                    throw new AssertionError(gameTest.name() + " manual tick batch is blank");
                }
                if (defaultBatch.equals(batch)) {
                    throw new AssertionError(gameTest.name() + " uses the default batch");
                }
                long occurrences = batchOccurrences.getOrDefault(batch, 0L);
                if (occurrences != 1L) {
                    throw new AssertionError(batch + " occurs " + occurrences + " times across AFTERLIGHT GameTests");
                }
                callers.add(new ManualTickCaller(callSite, gameTest));
            });
            return List.copyOf(callers);
        }

        private Set<GameTestMethod> mappingCandidates(MethodLocation callSite) {
            Set<GameTestMethod> candidates = new LinkedHashSet<>();
            gameTests.stream()
                    .filter(gameTest -> reaches(gameTest.location(), callSite))
                    .forEach(candidates::add);
            addNamedLambdaCandidates(callSite, callSite.owner(), candidates);

            ClassMetadata metadata = classes.get(callSite.owner());
            Set<String> visitedClasses = new HashSet<>();
            while (metadata != null && visitedClasses.add(metadata.name)) {
                if (metadata.outerOwner != null && metadata.outerMethodName != null) {
                    MethodLocation enclosingMethod = new MethodLocation(
                            metadata.outerOwner,
                            metadata.outerMethodName,
                            metadata.outerMethodDescriptor == null ? "" : metadata.outerMethodDescriptor);
                    addExactOrNamedCandidate(enclosingMethod, candidates);
                    metadata = classes.get(metadata.outerOwner);
                } else {
                    break;
                }
            }

            ClassMetadata callSiteClass = classes.get(callSite.owner());
            if (callSiteClass != null && callSiteClass.nestHost != null) {
                addNamedLambdaCandidates(callSite, callSiteClass.nestHost, candidates);
            }
            return candidates;
        }

        private void addExactOrNamedCandidate(
                MethodLocation location,
                Set<GameTestMethod> candidates) {
            gameTests.stream()
                    .filter(gameTest -> gameTest.location().equals(location))
                    .forEach(candidates::add);
            addNamedLambdaCandidates(location, location.owner(), candidates);
        }

        private void addNamedLambdaCandidates(
                MethodLocation location,
                String candidateOwner,
                Set<GameTestMethod> candidates) {
            gameTests.stream()
                    .filter(gameTest -> gameTest.owner().equals(candidateOwner))
                    .filter(gameTest -> location.name().startsWith("lambda$" + gameTest.name() + "$"))
                    .forEach(candidates::add);
        }

        private boolean reaches(MethodLocation source, MethodLocation target) {
            ArrayDeque<MethodLocation> pending = new ArrayDeque<>();
            Set<MethodLocation> visited = new HashSet<>();
            pending.add(source);
            while (!pending.isEmpty()) {
                MethodLocation current = pending.removeFirst();
                if (!visited.add(current)) {
                    continue;
                }
                if (current.equals(target)) {
                    return true;
                }
                pending.addAll(calls.getOrDefault(current, Set.of()));
            }
            return false;
        }
    }

    private static final class ScanAccumulator {
        private final TargetMethod target;
        private final Map<String, ClassMetadata> classes = new LinkedHashMap<>();
        private final Map<MethodLocation, Set<MethodLocation>> calls = new LinkedHashMap<>();
        private final Set<MethodLocation> manualTickCallSites = new LinkedHashSet<>();

        private ScanAccumulator(TargetMethod target) {
            this.target = target;
        }

        private ClassVisitor visitor() {
            ClassMetadata metadata = new ClassMetadata();
            return new ClassVisitor(Opcodes.ASM9) {
                @Override
                public void visit(
                        int version,
                        int access,
                        String name,
                        String signature,
                        String superName,
                        String[] interfaces) {
                    metadata.name = name;
                    classes.put(name, metadata);
                }

                @Override
                public void visitNestHost(String nestHost) {
                    metadata.nestHost = nestHost;
                }

                @Override
                public void visitOuterClass(String owner, String name, String descriptor) {
                    metadata.outerOwner = owner;
                    metadata.outerMethodName = name;
                    metadata.outerMethodDescriptor = descriptor;
                }

                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    if (!GAME_TEST_HOLDER_ANNOTATION.equals(descriptor)) {
                        return null;
                    }
                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override
                        public void visit(String name, Object value) {
                            if ("value".equals(name) && value instanceof String holderId) {
                                metadata.holderId = holderId;
                            }
                        }
                    };
                }

                @Override
                public MethodVisitor visitMethod(
                        int access,
                        String name,
                        String descriptor,
                        String signature,
                        String[] exceptions) {
                    MethodMetadata method = new MethodMetadata(
                            new MethodLocation(metadata.name, name, descriptor));
                    metadata.methods.add(method);
                    calls.computeIfAbsent(method.location, ignored -> new LinkedHashSet<>());
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public AnnotationVisitor visitAnnotation(
                                String annotationDescriptor,
                                boolean visible) {
                            if (!GAME_TEST_ANNOTATION.equals(annotationDescriptor)) {
                                return null;
                            }
                            method.gameTest = true;
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override
                                public void visit(String elementName, Object value) {
                                    if ("batch".equals(elementName) && value instanceof String batch) {
                                        method.batch = batch;
                                    }
                                }
                            };
                        }

                        @Override
                        public void visitMethodInsn(
                                int opcode,
                                String owner,
                                String invokedName,
                                String invokedDescriptor,
                                boolean isInterface) {
                            MethodLocation invoked = new MethodLocation(owner, invokedName, invokedDescriptor);
                            calls.get(method.location).add(invoked);
                            if (target.matches(opcode, owner, invokedName, invokedDescriptor)) {
                                manualTickCallSites.add(method.location);
                            }
                        }

                        @Override
                        public void visitInvokeDynamicInsn(
                                String invokedName,
                                String invokedDescriptor,
                                Handle bootstrapMethodHandle,
                                Object... bootstrapMethodArguments) {
                            inspectHandle(method.location, bootstrapMethodHandle);
                            for (Object argument : bootstrapMethodArguments) {
                                inspectBootstrapArgument(method.location, argument);
                            }
                        }
                    };
                }
            };
        }

        private void inspectBootstrapArgument(MethodLocation caller, Object argument) {
            if (argument instanceof Handle handle) {
                inspectHandle(caller, handle);
            } else if (argument instanceof ConstantDynamic dynamic) {
                inspectHandle(caller, dynamic.getBootstrapMethod());
                for (int index = 0; index < dynamic.getBootstrapMethodArgumentCount(); index++) {
                    inspectBootstrapArgument(caller, dynamic.getBootstrapMethodArgument(index));
                }
            }
        }

        private void inspectHandle(MethodLocation caller, Handle handle) {
            calls.get(caller).add(new MethodLocation(handle.getOwner(), handle.getName(), handle.getDesc()));
            if (target.matches(handle)) {
                manualTickCallSites.add(caller);
            }
        }

        private ScanResult result(String holderId) {
            List<GameTestMethod> gameTests = classes.values().stream()
                    .filter(metadata -> holderId.equals(metadata.holderId))
                    .flatMap(metadata -> metadata.methods.stream())
                    .filter(method -> method.gameTest)
                    .map(method -> new GameTestMethod(
                            method.location.owner(),
                            method.location.name(),
                            method.location.descriptor(),
                            method.batch))
                    .sorted(Comparator.comparing(GameTestMethod::owner)
                            .thenComparing(GameTestMethod::name)
                            .thenComparing(GameTestMethod::descriptor))
                    .toList();
            return new ScanResult(gameTests, manualTickCallSites, calls, classes);
        }
    }

    private static final class ClassMetadata {
        private String name;
        private String holderId;
        private String nestHost;
        private String outerOwner;
        private String outerMethodName;
        private String outerMethodDescriptor;
        private final List<MethodMetadata> methods = new ArrayList<>();
    }

    private static final class MethodMetadata {
        private final MethodLocation location;
        private boolean gameTest;
        private String batch = GAME_TEST_DEFAULT_BATCH;

        private MethodMetadata(MethodLocation location) {
            this.location = location;
        }
    }

    private static Map<MethodLocation, Set<MethodLocation>> immutableSetMap(
            Map<MethodLocation, Set<MethodLocation>> source) {
        Map<MethodLocation, Set<MethodLocation>> copy = new HashMap<>();
        source.forEach((key, value) -> copy.put(key, Set.copyOf(value)));
        return Map.copyOf(copy);
    }

    private static final class ClassReadException extends RuntimeException {
        private ClassReadException(Path path, IOException cause) {
            super("failed to read compiled test class " + path, cause);
        }
    }
}

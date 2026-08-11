package org.rllabs.afterlight.echo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.rllabs.afterlight.echo.GameTestBytecodeScanner.ManualTickCaller;
import org.rllabs.afterlight.echo.GameTestBytecodeScanner.ScanResult;
import org.rllabs.afterlight.echo.GameTestBytecodeScanner.TargetMethod;

class GameTestBytecodeScannerTest {
    private static final String HOLDER_ANNOTATION =
            "Lnet/neoforged/neoforge/gametest/GameTestHolder;";
    private static final String GAME_TEST_ANNOTATION =
            "Lnet/minecraft/gametest/framework/GameTest;";
    private static final String GAME_TEST_DESCRIPTOR =
            "(Lnet/minecraft/gametest/framework/GameTestHelper;)V";
    private static final TargetMethod TARGET = new TargetMethod(
            "fixtures/TickHost",
            "fireServerPostTick",
            GAME_TEST_DESCRIPTOR);
    private static final Handle LAMBDA_METAFACTORY = new Handle(
            Opcodes.H_INVOKESTATIC,
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                    + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;"
                    + "Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
            false);

    @TempDir
    Path classesDirectory;

    @Test
    void scansEveryHolderAndRejectsBatchReuseOutsideTheManualCallerHolder() throws Exception {
        writeClass(holderWithDirectCaller(
                "fixtures/PrimaryGameTests", "manualTickTest", "shared_manual_batch"));
        writeClass(holderWithoutCaller(
                "fixtures/SecondaryGameTests", "unrelatedTest", "shared_manual_batch"));

        ScanResult result = GameTestBytecodeScanner.scan(classesDirectory, "afterlight", TARGET);
        AssertionError error = assertThrows(
                AssertionError.class,
                () -> result.verifyUniqueManualTickBatches("defaultBatch"));

        assertEquals(2, result.gameTests().size());
        assertTrue(error.getMessage().contains("shared_manual_batch occurs 2 times"));
    }

    @Test
    void discoversInvokeDynamicMethodReferenceToManualTickHelper() {
        ScanResult result = GameTestBytecodeScanner.scan(
                List.of(holderWithMethodReference(
                        "fixtures/ReferenceGameTests", "methodReferenceTest", "reference_batch")),
                "afterlight",
                TARGET);

        List<ManualTickCaller> callers = result.verifyUniqueManualTickBatches("defaultBatch");

        assertEquals(1, callers.size());
        assertEquals("methodReferenceTest", callers.getFirst().callSite().name());
        assertEquals("methodReferenceTest", callers.getFirst().gameTest().name());
    }

    @Test
    void mapsAnonymousClassCallToItsEnclosingGameTest() {
        String holderName = "fixtures/AnonymousGameTests";
        ScanResult result = GameTestBytecodeScanner.scan(
                List.of(
                        holderWithoutCaller(holderName, "anonymousTest", "anonymous_batch"),
                        anonymousCaller(holderName + "$1", holderName, "anonymousTest")),
                "afterlight",
                TARGET);

        List<ManualTickCaller> callers = result.verifyUniqueManualTickBatches("defaultBatch");

        assertEquals(1, callers.size());
        assertEquals(holderName + "$1", callers.getFirst().callSite().owner());
        assertEquals("anonymousTest", callers.getFirst().gameTest().name());
    }

    @Test
    void failsClosedWhenManualTickCallCannotMapToAGameTest() {
        ScanResult result = GameTestBytecodeScanner.scan(
                List.of(
                        holderWithoutCaller("fixtures/KnownGameTests", "knownTest", "known_batch"),
                        orphanCaller("fixtures/OrphanCaller")),
                "afterlight",
                TARGET);

        AssertionError error = assertThrows(
                AssertionError.class,
                () -> result.verifyUniqueManualTickBatches("defaultBatch"));

        assertTrue(error.getMessage().contains("cannot map manual tick caller fixtures/OrphanCaller.call"));
    }

    private void writeClass(byte[] classBytes) throws IOException {
        String internalName = new org.objectweb.asm.ClassReader(classBytes).getClassName();
        Path output = classesDirectory.resolve(internalName + ".class");
        Files.createDirectories(output.getParent());
        Files.write(output, classBytes);
    }

    private static byte[] holderWithDirectCaller(
            String internalName,
            String methodName,
            String batch) {
        return holder(internalName, methodName, batch, method -> {
            method.visitVarInsn(Opcodes.ALOAD, 0);
            method.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    TARGET.owner(),
                    TARGET.name(),
                    TARGET.descriptor(),
                    false);
        });
    }

    private static byte[] holderWithoutCaller(
            String internalName,
            String methodName,
            String batch) {
        return holder(internalName, methodName, batch, method -> {
        });
    }

    private static byte[] holderWithMethodReference(
            String internalName,
            String methodName,
            String batch) {
        return holder(internalName, methodName, batch, method -> {
            method.visitInvokeDynamicInsn(
                    "accept",
                    "()Ljava/util/function/Consumer;",
                    LAMBDA_METAFACTORY,
                    Type.getMethodType("(Ljava/lang/Object;)V"),
                    new Handle(
                            Opcodes.H_INVOKESTATIC,
                            TARGET.owner(),
                            TARGET.name(),
                            TARGET.descriptor(),
                            false),
                    Type.getMethodType(TARGET.descriptor()));
            method.visitInsn(Opcodes.POP);
        });
    }

    private static byte[] holder(
            String internalName,
            String methodName,
            String batch,
            MethodBody body) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, internalName, null, "java/lang/Object", null);
        AnnotationVisitor holder = writer.visitAnnotation(HOLDER_ANNOTATION, true);
        holder.visit("value", "afterlight");
        holder.visitEnd();
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                methodName,
                GAME_TEST_DESCRIPTOR,
                null,
                null);
        AnnotationVisitor gameTest = method.visitAnnotation(GAME_TEST_ANNOTATION, true);
        gameTest.visit("batch", batch);
        gameTest.visitEnd();
        method.visitCode();
        body.write(method);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] anonymousCaller(
            String internalName,
            String outerName,
            String outerMethod) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_SUPER, internalName, null, "java/lang/Object", null);
        writer.visitNestHost(outerName);
        writer.visitOuterClass(outerName, outerMethod, GAME_TEST_DESCRIPTOR);
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_STATIC,
                "run",
                GAME_TEST_DESCRIPTOR,
                null,
                null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                TARGET.owner(),
                TARGET.name(),
                TARGET.descriptor(),
                false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] orphanCaller(String internalName) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, internalName, null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "call",
                GAME_TEST_DESCRIPTOR,
                null,
                null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                TARGET.owner(),
                TARGET.name(),
                TARGET.descriptor(),
                false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    @FunctionalInterface
    private interface MethodBody {
        void write(MethodVisitor method);
    }
}

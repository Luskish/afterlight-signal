package org.rllabs.afterlight.client.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class FtbQuestNetworkContractTest {
    private static final String ARCHITECTURY_SEND =
            "dev/architectury/networking/NetworkManager#sendToServer"
                    + "(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V";
    private static final String NEOFORGE_SEND_OWNER =
            "net/neoforged/neoforge/network/PacketDistributor";

    @Test
    void everyFtbMutationUsesArchitecturyTransport() throws Exception {
        Class<?> accessType = Class.forName(FtbQuestGateway.class.getName() + "$FtbClientAccess");

        for (String descriptor : List.of(
                "(Ldev/ftb/mods/ftbquests/net/SubmitTaskMessage;)V",
                "(Ldev/ftb/mods/ftbquests/net/ClaimRewardMessage;)V",
                "(Ldev/ftb/mods/ftbquests/net/TogglePinnedMessage;)V")) {
            List<String> calls = methodCalls(accessType, "send", descriptor);
            assertEquals(List.of(ARCHITECTURY_SEND), calls);
            assertFalse(calls.stream().anyMatch(call -> call.startsWith(NEOFORGE_SEND_OWNER + "#")));
        }
    }

    private static List<String> methodCalls(Class<?> type, String methodName, String expectedDescriptor) throws IOException {
        String resourceName = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream input = type.getResourceAsStream(resourceName)) {
            if (input == null) {
                throw new IOException("Missing class resource " + resourceName);
            }
            List<String> calls = new ArrayList<>();
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(
                        int access,
                        String name,
                        String methodDescriptor,
                        String signature,
                        String[] exceptions) {
                    if (!name.equals(methodName) || !expectedDescriptor.equals(methodDescriptor)) {
                        return null;
                    }
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(
                                int opcode,
                                String owner,
                                String name,
                                String descriptor,
                                boolean isInterface) {
                            calls.add(owner + "#" + name + descriptor);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return List.copyOf(calls);
        }
    }
}

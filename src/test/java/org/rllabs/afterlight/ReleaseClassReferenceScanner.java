package org.rllabs.afterlight;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

final class ReleaseClassReferenceScanner {
    private static final String PROJECT_PACKAGE = "org/rllabs/afterlight/";
    private static final String PROJECT_CLIENT_PACKAGE = PROJECT_PACKAGE + "client/";
    private static final Set<String> EXTERNAL_CLIENT_PACKAGES = Set.of(
            "net/minecraft/client/",
            "net/neoforged/neoforge/client/",
            "com/mojang/blaze3d/",
            "org/lwjgl/");

    private ReleaseClassReferenceScanner() {}

    static void assertSafe(Map<String, byte[]> classes, Set<String> roots) {
        var pending = new ArrayDeque<>(roots);
        var reachable = new LinkedHashSet<String>();
        while (!pending.isEmpty()) {
            String className = pending.removeFirst();
            if (!reachable.add(className)) {
                continue;
            }
            byte[] payload = classes.get(className);
            if (payload == null) {
                fail("missing reachable class: " + className);
            }
            if (className.startsWith(PROJECT_CLIENT_PACKAGE)) {
                fail("common entry reaches client class: " + className);
            }
            for (String reference : references(payload)) {
                if (reference.startsWith(PROJECT_CLIENT_PACKAGE)
                        || EXTERNAL_CLIENT_PACKAGES.stream().anyMatch(reference::startsWith)) {
                    fail("common class " + className + " references client class: " + reference);
                }
                if (reference.startsWith(PROJECT_PACKAGE)
                        && classes.containsKey(reference)
                        && !reachable.contains(reference)) {
                    pending.addLast(reference);
                }
            }
        }
    }

    static Set<String> commonRoots(Map<String, byte[]> classes) {
        return classes.keySet().stream()
                .filter(name -> name.startsWith(PROJECT_PACKAGE))
                .filter(name -> !name.startsWith(PROJECT_CLIENT_PACKAGE))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> references(byte[] payload) {
        Set<String> references = new LinkedHashSet<>();
        try {
            new ClassReader(payload).accept(new ReferenceVisitor(references), 0);
        } catch (IllegalArgumentException exception) {
            fail("invalid classfile in release client isolation scan: " + exception.getMessage());
        }
        return references;
    }

    private static final class ReferenceVisitor extends ClassVisitor {
        private final Set<String> references;

        private ReferenceVisitor(Set<String> references) {
            super(Opcodes.ASM9);
            this.references = references;
        }

        @Override
        public void visit(
                int version,
                int access,
                String name,
                String signature,
                String superName,
                String[] interfaces) {
            addInternalName(superName);
            if (interfaces != null) {
                for (String interfaceName : interfaces) {
                    addInternalName(interfaceName);
                }
            }
            addSignature(signature);
        }

        @Override
        public void visitOuterClass(String owner, String name, String descriptor) {
            addInternalName(owner);
            addMethodDescriptor(descriptor);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            addDescriptor(descriptor);
            return annotationVisitor();
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(
                int typeRef, TypePath typePath, String descriptor, boolean visible) {
            addDescriptor(descriptor);
            return annotationVisitor();
        }

        @Override
        public void visitNestHost(String nestHost) {
            addInternalName(nestHost);
        }

        @Override
        public void visitNestMember(String nestMember) {
            addInternalName(nestMember);
        }

        @Override
        public void visitPermittedSubclass(String permittedSubclass) {
            addInternalName(permittedSubclass);
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            addInternalName(name);
            addInternalName(outerName);
        }

        @Override
        public RecordComponentVisitor visitRecordComponent(
                String name, String descriptor, String signature) {
            addDescriptor(descriptor);
            addSignature(signature);
            return new RecordComponentVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String value, boolean visible) {
                    addDescriptor(value);
                    return annotationVisitor();
                }

                @Override
                public AnnotationVisitor visitTypeAnnotation(
                        int typeRef, TypePath typePath, String value, boolean visible) {
                    addDescriptor(value);
                    return annotationVisitor();
                }
            };
        }

        @Override
        public FieldVisitor visitField(
                int access, String name, String descriptor, String signature, Object value) {
            addDescriptor(descriptor);
            addSignature(signature);
            addConstant(value);
            return new FieldVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String value, boolean visible) {
                    addDescriptor(value);
                    return annotationVisitor();
                }

                @Override
                public AnnotationVisitor visitTypeAnnotation(
                        int typeRef, TypePath typePath, String value, boolean visible) {
                    addDescriptor(value);
                    return annotationVisitor();
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
            addMethodDescriptor(descriptor);
            addSignature(signature);
            if (exceptions != null) {
                for (String exception : exceptions) {
                    addInternalName(exception);
                }
            }
            return methodVisitor();
        }

        private MethodVisitor methodVisitor() {
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotationDefault() {
                    return annotationVisitor();
                }

                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    addDescriptor(descriptor);
                    return annotationVisitor();
                }

                @Override
                public AnnotationVisitor visitTypeAnnotation(
                        int typeRef, TypePath typePath, String descriptor, boolean visible) {
                    addDescriptor(descriptor);
                    return annotationVisitor();
                }

                @Override
                public AnnotationVisitor visitParameterAnnotation(
                        int parameter, String descriptor, boolean visible) {
                    addDescriptor(descriptor);
                    return annotationVisitor();
                }

                @Override
                public void visitTypeInsn(int opcode, String type) {
                    addInternalName(type);
                }

                @Override
                public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                    addInternalName(owner);
                    addDescriptor(descriptor);
                }

                @Override
                public void visitMethodInsn(
                        int opcode, String owner, String name, String descriptor, boolean isInterface) {
                    addInternalName(owner);
                    addMethodDescriptor(descriptor);
                }

                @Override
                public void visitInvokeDynamicInsn(
                        String name,
                        String descriptor,
                        Handle bootstrapMethodHandle,
                        Object... bootstrapMethodArguments) {
                    addMethodDescriptor(descriptor);
                    addHandle(bootstrapMethodHandle);
                    for (Object argument : bootstrapMethodArguments) {
                        addConstant(argument);
                    }
                }

                @Override
                public void visitLdcInsn(Object value) {
                    addConstant(value);
                }

                @Override
                public void visitMultiANewArrayInsn(String descriptor, int dimensions) {
                    addDescriptor(descriptor);
                }

                @Override
                public void visitTryCatchBlock(
                        Label start, Label end, Label handler, String type) {
                    addInternalName(type);
                }

                @Override
                public AnnotationVisitor visitTryCatchAnnotation(
                        int typeRef, TypePath typePath, String descriptor, boolean visible) {
                    addDescriptor(descriptor);
                    return annotationVisitor();
                }

                @Override
                public void visitLocalVariable(
                        String name,
                        String descriptor,
                        String signature,
                        Label start,
                        Label end,
                        int index) {
                    addDescriptor(descriptor);
                    addSignature(signature);
                }

                @Override
                public AnnotationVisitor visitLocalVariableAnnotation(
                        int typeRef,
                        TypePath typePath,
                        Label[] start,
                        Label[] end,
                        int[] index,
                        String descriptor,
                        boolean visible) {
                    addDescriptor(descriptor);
                    return annotationVisitor();
                }

                @Override
                public void visitFrame(
                        int type,
                        int numLocal,
                        Object[] local,
                        int numStack,
                        Object[] stack) {
                    addFrameValues(local, numLocal);
                    addFrameValues(stack, numStack);
                }
            };
        }

        private AnnotationVisitor annotationVisitor() {
            return new AnnotationVisitor(Opcodes.ASM9) {
                @Override
                public void visit(String name, Object value) {
                    addConstant(value);
                }

                @Override
                public void visitEnum(String name, String descriptor, String value) {
                    addDescriptor(descriptor);
                }

                @Override
                public AnnotationVisitor visitAnnotation(String name, String descriptor) {
                    addDescriptor(descriptor);
                    return this;
                }

                @Override
                public AnnotationVisitor visitArray(String name) {
                    return this;
                }
            };
        }

        private void addFrameValues(Object[] values, int count) {
            if (values == null) {
                return;
            }
            for (int index = 0; index < count; index++) {
                if (values[index] instanceof String internalName) {
                    addInternalName(internalName);
                }
            }
        }

        private void addConstant(Object value) {
            if (value instanceof Type type) {
                addType(type);
            } else if (value instanceof Handle handle) {
                addHandle(handle);
            } else if (value instanceof ConstantDynamic dynamic) {
                addDescriptor(dynamic.getDescriptor());
                addHandle(dynamic.getBootstrapMethod());
                for (int index = 0; index < dynamic.getBootstrapMethodArgumentCount(); index++) {
                    addConstant(dynamic.getBootstrapMethodArgument(index));
                }
            }
        }

        private void addHandle(Handle handle) {
            addInternalName(handle.getOwner());
            if (handle.getTag() <= Opcodes.H_PUTSTATIC) {
                addDescriptor(handle.getDesc());
            } else {
                addMethodDescriptor(handle.getDesc());
            }
        }

        private void addSignature(String signature) {
            if (signature == null) {
                return;
            }
            new SignatureReader(signature).accept(new SignatureVisitor(Opcodes.ASM9) {
                @Override
                public void visitClassType(String name) {
                    addInternalName(name);
                }
            });
        }

        private void addDescriptor(String descriptor) {
            if (descriptor != null) {
                addType(Type.getType(descriptor));
            }
        }

        private void addMethodDescriptor(String descriptor) {
            if (descriptor != null) {
                addType(Type.getMethodType(descriptor));
            }
        }

        private void addType(Type type) {
            if (type.getSort() == Type.ARRAY) {
                addType(type.getElementType());
            } else if (type.getSort() == Type.OBJECT) {
                addInternalName(type.getInternalName());
            } else if (type.getSort() == Type.METHOD) {
                addType(type.getReturnType());
                for (Type argument : type.getArgumentTypes()) {
                    addType(argument);
                }
            }
        }

        private void addInternalName(String name) {
            if (name != null) {
                references.add(name);
            }
        }
    }
}

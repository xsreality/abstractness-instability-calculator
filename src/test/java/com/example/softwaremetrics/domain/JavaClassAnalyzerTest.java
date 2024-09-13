package com.example.softwaremetrics.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("SameParameterValue")
class JavaClassAnalyzerTest {

    private JavaClassAnalyzer javaClassAnalyzer;

    @BeforeEach
    void setUp() {
        javaClassAnalyzer = new JavaClassAnalyzer();
    }

    @Test
    void testContainsSpringBootApplication(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("TestApplication.java");
        Files.writeString(file, "@SpringBootApplication\npublic class TestApplication {}");
        assertTrue(javaClassAnalyzer.containsSpringBootApplication(file));

        Path nonSpringBootFile = tempDir.resolve("RegularClass.java");
        Files.writeString(nonSpringBootFile, "public class RegularClass {}");
        assertFalse(javaClassAnalyzer.containsSpringBootApplication(nonSpringBootFile));
    }

    @Test
    void testExtractPackage(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("TestClass.java");
        Files.writeString(file, "package com.example.test;\npublic class TestClass {}");
        assertEquals("com.example.test", javaClassAnalyzer.extractPackage(file));

        Path noPackageFile = tempDir.resolve("NoPackageClass.java");
        Files.writeString(noPackageFile, "public class NoPackageClass {}");
        assertEquals("", javaClassAnalyzer.extractPackage(noPackageFile));
    }

    @Test
    void testAnalyzeClasses(@TempDir Path tempDir) throws IOException {
        // Create a simple project structure
        Path srcMainJava = tempDir.resolve("src/main/java");
        Files.createDirectories(srcMainJava);

        // Create test classes with dependencies
        createTestClass(srcMainJava, "com/example/anothersubpackage/ClassA.class", "com.example.anothersubpackage.ClassA", false, "com.example.subpackage.ClassC");
        createTestClass(srcMainJava, "com/example/anothersubpackage/ClassB.class", "com.example.anothersubpackage.ClassB", true, "com.example.subpackage.ClassC");
        createTestClass(srcMainJava, "com/example/subpackage/ClassC.class", "com.example.subpackage.ClassC", false, "com.example.anothersubpackage.ClassA");
        createTestClassWithJavaLangDependency(srcMainJava, "com/example/anothersubpackage/ClassD.class", "com.example.anothersubpackage.ClassD", false);

        // Prepare input for analyzeClasses
        List<String> packages = Arrays.asList("com.example.anothersubpackage", "com.example.subpackage");
        Map<String, Set<String>> outgoingDependencies = new ConcurrentHashMap<>();
        Map<String, Set<String>> incomingDependencies = new ConcurrentHashMap<>();
        Map<String, Integer> abstractClassCount = new HashMap<>();
        Map<String, Integer> totalClassCount = new HashMap<>();

        // Run the analysis
        javaClassAnalyzer.analyzeClasses(tempDir, packages, outgoingDependencies, incomingDependencies, abstractClassCount, totalClassCount);

        // Verify the results
        assertEquals(3, totalClassCount.get("com.example.anothersubpackage"));
        assertEquals(1, abstractClassCount.get("com.example.anothersubpackage"));
        assertEquals(1, totalClassCount.get("com.example.subpackage"));
        assertNull(abstractClassCount.get("com.example.subpackage"));

        assertTrue(outgoingDependencies.get("com.example.anothersubpackage").contains("com.example.subpackage.ClassC"));
        assertTrue(outgoingDependencies.get("com.example.subpackage").contains("com.example.anothersubpackage.ClassA"));
        assertTrue(incomingDependencies.get("com.example.anothersubpackage").contains("com.example.subpackage.ClassC"));
        assertTrue(incomingDependencies.get("com.example.subpackage").contains("com.example.anothersubpackage.ClassA"));

        assertEquals(1, outgoingDependencies.get("com.example.anothersubpackage").size());
        assertEquals(1, outgoingDependencies.get("com.example.subpackage").size());
        assertEquals(1, incomingDependencies.get("com.example.anothersubpackage").size());
        assertEquals(2, incomingDependencies.get("com.example.subpackage").size());

        // Verify that java.lang dependencies are not included
        assertFalse(outgoingDependencies.get("com.example.anothersubpackage").contains("java.lang.String"));
    }

    private void createTestClass(Path baseDir, String classPath, String className, boolean isAbstract, String dependencyClass) throws IOException {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V1_8, isAbstract ? Opcodes.ACC_PUBLIC + Opcodes.ACC_ABSTRACT : Opcodes.ACC_PUBLIC, 
                 className.replace('.', '/'), null, "java/lang/Object", null);

        // Add a constructor
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        // Add a method that references another class
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "someMethod", "()V", null, null);
        mv.visitCode();
        mv.visitTypeInsn(Opcodes.NEW, dependencyClass.replace('.', '/'));
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, dependencyClass.replace('.', '/'), "<init>", "()V", false);
        mv.visitInsn(Opcodes.POP);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(2, 1);
        mv.visitEnd();

        cw.visitEnd();

        Path fullPath = baseDir.resolve(classPath);
        Files.createDirectories(fullPath.getParent());
        Files.write(fullPath, cw.toByteArray());
    }

    private void createTestClassWithJavaLangDependency(Path baseDir, String classPath, String className, boolean isAbstract) throws IOException {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V1_8, isAbstract ? Opcodes.ACC_PUBLIC + Opcodes.ACC_ABSTRACT : Opcodes.ACC_PUBLIC, 
                 className.replace('.', '/'), null, "java/lang/Object", null);

        // Add a constructor
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        // Add a method that uses java.lang.String
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "someMethod", "()Ljava/lang/String;", null, null);
        mv.visitCode();
        mv.visitLdcInsn("Hello, World!");
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        cw.visitEnd();

        Path fullPath = baseDir.resolve(classPath);
        Files.createDirectories(fullPath.getParent());
        Files.write(fullPath, cw.toByteArray());
    }
}
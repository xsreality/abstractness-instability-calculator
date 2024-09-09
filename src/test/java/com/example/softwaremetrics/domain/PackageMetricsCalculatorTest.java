package com.example.softwaremetrics.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PackageMetricsCalculatorTest {

    private static final Logger logger = LoggerFactory.getLogger(PackageMetricsCalculatorTest.class);

    @TempDir
    Path tempDir;

    private PackageMetricsCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new PackageMetricsCalculator();
        logger.info("Set up PackageMetricsCalculator for testing");
    }

    @Test
    void testCalculateMetrics() throws IOException {
        logger.info("Starting testCalculateMetrics");

        createMockProjectStructure();

        List<String> packages = Arrays.asList("com.example", "com.example.service", "com.example.model");

        Map<String, Map<String, Double>> metrics = calculator.calculateMetrics(tempDir, packages);

        logger.debug("Calculated metrics: {}", metrics);

        assertNotNull(metrics, "Metrics should not be null");
        assertEquals(3, metrics.size(), "Should have metrics for 3 packages");

        Map<String, Double> exampleMetrics = metrics.get("com.example");
        assertNotNull(exampleMetrics, "Metrics for com.example should exist");
        assertEquals(1.0, exampleMetrics.get("Instability"), "Instability for com.example should be 1.0");
        assertEquals(0.5, exampleMetrics.get("Abstractness"), "Abstractness for com.example should be 0.5");
        assertEquals(0.5, exampleMetrics.get("Distance"), "Distance for com.example should be 0.5");

        logger.info("testCalculateMetrics completed successfully");
    }

    private void createMockProjectStructure() throws IOException {
        logger.info("Creating mock project structure in {}", tempDir);

        Path comExample = Files.createDirectories(tempDir.resolve("com/example"));
        Path comExampleService = Files.createDirectories(tempDir.resolve("com/example/service"));
        Path comExampleModel = Files.createDirectories(tempDir.resolve("com/example/model"));

        createMockClassFile(comExample.resolve("MainClass.class"), "com.example.MainClass", false, "com.example.service.ServiceClass");
        createMockClassFile(comExample.resolve("AbstractClass.class"), "com.example.AbstractClass", true);
        createMockClassFile(comExampleService.resolve("ServiceClass.class"), "com.example.service.ServiceClass", false, "com.example.model.ModelClass");
        createMockClassFile(comExampleModel.resolve("ModelClass.class"), "com.example.model.ModelClass", false);

        logger.debug("Created mock project structure with 4 classes");
    }

    private void createMockClassFile(Path path, String className, boolean isAbstract, String... dependencies) throws IOException {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V22, isAbstract ? Opcodes.ACC_PUBLIC + Opcodes.ACC_ABSTRACT : Opcodes.ACC_PUBLIC,
                className.replace('.', '/'), null, "java/lang/Object", null);

        // Add a constructor
        cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null).visitEnd();

        // Add a method that uses the dependencies
        org.objectweb.asm.MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "useDependencies", "()V", null, null);
        mv.visitCode();
        for (String dependency : dependencies) {
            mv.visitTypeInsn(Opcodes.NEW, dependency.replace('.', '/'));
            mv.visitInsn(Opcodes.DUP);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, dependency.replace('.', '/'), "<init>", "()V", false);
            mv.visitInsn(Opcodes.POP);
        }
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(2, 1);
        mv.visitEnd();

        cw.visitEnd();
        Files.write(path, cw.toByteArray());
        logger.debug("Created mock class file: {}", path);
    }
}
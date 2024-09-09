package com.example.softwaremetrics.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

//    @Test
//    void testFindJavaFiles(@TempDir Path tempDir) throws IOException {
//        Path javaFile1 = tempDir.resolve("Test1.java");
//        Path javaFile2 = tempDir.resolve("Test2.java");
//        Path nonJavaFile = tempDir.resolve("NonJava.txt");
//
//        Files.createFile(javaFile1);
//        Files.createFile(javaFile2);
//        Files.createFile(nonJavaFile);
//
//        List<Path> javaFiles = javaClassAnalyzer.findJavaFiles(tempDir);
//
//        assertEquals(2, javaFiles.size());
//        assertTrue(javaFiles.contains(javaFile1));
//        assertTrue(javaFiles.contains(javaFile2));
//        assertFalse(javaFiles.contains(nonJavaFile));
//    }
}
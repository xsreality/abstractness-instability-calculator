package com.example.softwaremetrics.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProjectPathTraverserTest {

    private ProjectPathTraverser projectPathTraverser;

    @BeforeEach
    void setUp() {
        projectPathTraverser = new ProjectPathTraverser();
    }

    @Test
    void testFindJavaFiles(@TempDir Path tempDir) throws IOException {
        // Create some test files
        Path javaFile1 = tempDir.resolve("Test1.java");
        Path javaFile2 = tempDir.resolve("subdir/Test2.java");
        Path nonJavaFile = tempDir.resolve("NonJava.txt");

        Files.createFile(javaFile1);
        Files.createDirectories(javaFile2.getParent());
        Files.createFile(javaFile2);
        Files.createFile(nonJavaFile);

        List<Path> javaFiles = projectPathTraverser.findJavaFiles(tempDir);

        assertEquals(2, javaFiles.size());
        assertTrue(javaFiles.contains(javaFile1));
        assertTrue(javaFiles.contains(javaFile2));
        assertFalse(javaFiles.contains(nonJavaFile));
    }

    @Test
    void testFindJavaFilesEmptyDirectory(@TempDir Path tempDir) {
        List<Path> javaFiles = projectPathTraverser.findJavaFiles(tempDir);

        assertTrue(javaFiles.isEmpty());
    }

    @Test
    void testFindJavaFilesNonExistentDirectory() {
        Path nonExistentDir = Path.of("/non/existent/directory");
        List<Path> javaFiles = projectPathTraverser.findJavaFiles(nonExistentDir);

        assertTrue(javaFiles.isEmpty());
    }
}
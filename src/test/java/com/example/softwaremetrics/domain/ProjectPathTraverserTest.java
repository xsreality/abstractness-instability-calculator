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

    @Test
    void testFindPackagesInNonExistentDirectory() {
        // given
        Path nonExistentDir = Path.of("/non/existent/directory");
        ProjectPathTraverser projectPathTraverser = new ProjectPathTraverser();
        // when
        List<Path> packages = projectPathTraverser.findPackages(nonExistentDir);
        // then
        assertTrue(packages.isEmpty());
    }

    @Test
    void testFindPackagesInEmptyDirectory(@TempDir Path tempDir) {
        // given
        ProjectPathTraverser projectPathTraverser = new ProjectPathTraverser();
        // when
        List<Path> packages = projectPathTraverser.findPackages(tempDir);
        // then
        assertFalse(packages.isEmpty());
        assertEquals(1, packages.size());
    }

    @Test
    void testFindPackagesInDirectoryWithSubDirectories(@TempDir Path tempDir) throws IOException {
        // given
        ProjectPathTraverser projectPathTraverser = new ProjectPathTraverser();
        Path subdir1 = tempDir.resolve("subdir1");
        Path subdir2 = subdir1.resolve("subdir2");
        Files.createDirectories(subdir2);
        // when
        List<Path> packages = projectPathTraverser.findPackages(tempDir);
        // then
        assertEquals(3, packages.size());
        assertTrue(packages.contains(subdir1));
        assertTrue(packages.contains(subdir2));
    }
}

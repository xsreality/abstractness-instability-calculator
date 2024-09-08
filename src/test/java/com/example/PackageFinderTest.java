package com.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackageFinderTest {

    @TempDir
    Path tempDir;

    private PackageFinder packageFinder;

    @BeforeEach
    void setUp() {
        packageFinder = new PackageFinder();
    }

    @Test
    void testFindPackages() throws IOException {
        // Create a mock project structure
        Path srcMainJava = Files.createDirectories(tempDir.resolve("src/main/java"));
        createMockJavaFile(srcMainJava.resolve("com/example/Main.java"), "package com.example;");
        createMockJavaFile(srcMainJava.resolve("com/example/service/Service.java"), "package com.example.service;");
        createMockJavaFile(srcMainJava.resolve("com/example/model/Model.java"), "package com.example.model;");

        // Find packages
        List<String> packages = packageFinder.findPackages(tempDir.toString());

        // Assert
        assertNotNull(packages);
        assertEquals(3, packages.size());
        assertTrue(packages.contains("com.example"));
        assertTrue(packages.contains("com.example.service"));
        assertTrue(packages.contains("com.example.model"));
    }

    @Test
    void testFindPackagesWithEmptyProject() throws IOException {
        // Create an empty project structure
        Files.createDirectories(tempDir.resolve("src/main/java"));

        // Find packages
        List<String> packages = packageFinder.findPackages(tempDir.toString());

        // Assert
        assertNotNull(packages);
        assertTrue(packages.isEmpty());
    }

    @Test
    void testFindPackagesWithInvalidPath() {
        // Use a non-existent path
        String invalidPath = tempDir.resolve("non-existent").toString();

        // Assert that an exception is thrown
        assertThrows(IOException.class, () -> packageFinder.findPackages(invalidPath));
    }

    private void createMockJavaFile(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }
}
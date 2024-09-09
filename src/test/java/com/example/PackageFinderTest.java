package com.example;

import com.example.softwaremetrics.infrastructure.PackageFinder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
        createMockProjectStructure();

        List<String> packages = packageFinder.findPackages(tempDir.toString());

        assertNotNull(packages);
        assertEquals(3, packages.size());
        assertTrue(packages.contains("com.example"));
        assertTrue(packages.contains("com.example.service"));
        assertTrue(packages.contains("com.example.model"));
    }

    @Test
    void testFindMainPackage() throws IOException {
        createMockProjectStructure();
        createMockJavaFile(tempDir.resolve("src/main/java/com/example/Application.java"),
                """
                        package com.example;
                        
                        import org.springframework.boot.SpringApplication;
                        import org.springframework.boot.autoconfigure.SpringBootApplication;
                        
                        @SpringBootApplication
                        public class Application {
                            public static void main(String[] args) {
                                SpringApplication.run(Application.class, args);
                            }
                        }
                        """);

        String mainPackage = packageFinder.findMainPackage(tempDir);

        assertEquals("com.example", mainPackage);
    }

    @Test
    void testFindTopLevelPackages() throws IOException {
        createMockProjectStructure();

        List<String> topLevelPackages = packageFinder.findTopLevelPackages(tempDir, "com.example");

        assertNotNull(topLevelPackages);
        assertEquals(2, topLevelPackages.size());
        assertTrue(topLevelPackages.contains("com.example.service"));
        assertTrue(topLevelPackages.contains("com.example.model"));
    }

    @Test
    void testFindPackagesWithEmptyProject() throws IOException {
        Files.createDirectories(tempDir.resolve("src/main/java"));

        List<String> packages = packageFinder.findPackages(tempDir.toString());

        assertNotNull(packages);
        assertTrue(packages.isEmpty());
    }

    @Test
    void testFindPackagesWithInvalidPath() {
        String invalidPath = tempDir.resolve("non-existent").toString();

        assertThrows(IOException.class, () -> packageFinder.findPackages(invalidPath));
    }

    @Test
    void testFindMainPackageWithNoSpringBootApplication() throws IOException {
        createMockProjectStructure();

        String mainPackage = packageFinder.findMainPackage(tempDir);

        assertNull(mainPackage);
    }

    @Test
    void testFindTopLevelPackagesWithInvalidMainPackage() throws IOException {
        createMockProjectStructure();

        List<String> topLevelPackages = packageFinder.findTopLevelPackages(tempDir, "com.invalid");

        assertNotNull(topLevelPackages);
        assertTrue(topLevelPackages.isEmpty());
    }

    private void createMockProjectStructure() throws IOException {
        Path srcMainJava = Files.createDirectories(tempDir.resolve("src/main/java"));
        createMockJavaFile(srcMainJava.resolve("com/example/Main.java"), "package com.example;");
        createMockJavaFile(srcMainJava.resolve("com/example/service/Service.java"), "package com.example.service;");
        createMockJavaFile(srcMainJava.resolve("com/example/model/Model.java"), "package com.example.model;");
    }

    private void createMockJavaFile(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }
}
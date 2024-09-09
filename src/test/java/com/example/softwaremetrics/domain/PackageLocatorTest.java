package com.example.softwaremetrics.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PackageLocatorTest {

    @Mock
    private JavaClassAnalyzer javaClassAnalyzer;

    @Mock
    private ProjectPathTraverser projectPathTraverser;

    private PackageLocator packageLocator;

    @BeforeEach
    void setUp() {
        packageLocator = new PackageLocator(javaClassAnalyzer, projectPathTraverser);
    }

    @Test
    void testFindMainPackage() {
        Path projectPath = Path.of("/test/project");
        Path file1 = projectPath.resolve("File1.java");
        Path file2 = projectPath.resolve("File2.java");

        when(projectPathTraverser.findJavaFiles(projectPath)).thenReturn(Arrays.asList(file1, file2));
        when(javaClassAnalyzer.containsSpringBootApplication(file1)).thenReturn(false);
        when(javaClassAnalyzer.containsSpringBootApplication(file2)).thenReturn(true);
        when(javaClassAnalyzer.extractPackage(file2)).thenReturn("com.example.main");

        String mainPackage = packageLocator.findMainPackage(projectPath);

        assertEquals("com.example.main", mainPackage);
    }

    @Test
    void testFindTopLevelPackages() {
        Path projectPath = Path.of("/test/project");
        String mainPackage = "com.example";
        List<Path> javaFiles = Arrays.asList(
            Path.of("/test/project/src/main/java/com/example/sub1/Class1.java"),
            Path.of("/test/project/src/main/java/com/example/sub2/Class2.java"),
            Path.of("/test/project/src/main/java/com/example/sub2/subsub/Class3.java")
        );

        when(projectPathTraverser.findJavaFiles(projectPath)).thenReturn(javaFiles);

        List<String> topLevelPackages = packageLocator.findTopLevelPackages(projectPath, mainPackage);

        assertEquals(2, topLevelPackages.size());
        assertTrue(topLevelPackages.contains("com.example.sub1"));
        assertTrue(topLevelPackages.contains("com.example.sub2"));
    }
}
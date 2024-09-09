package com.example.softwaremetrics.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class JavaClassAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(JavaClassAnalyzer.class);

    boolean containsSpringBootApplication(Path file) {
        try (Stream<String> lines = Files.lines(file)) {
            return lines.anyMatch(line -> line.contains("@SpringBootApplication"));
        } catch (IOException e) {
            logger.error("Error reading file: {}", file, e);
            return false;
        }
    }

    String extractPackage(Path file) {
        try (Stream<String> lines = Files.lines(file)) {
            return lines
                    .filter(line -> line.startsWith("package"))
                    .map(line -> line.split("\\s+")[1].replace(";", ""))
                    .findFirst()
                    .orElse("");
        } catch (IOException e) {
            logger.error("Error extracting package from file: {}", file, e);
            return "";
        }
    }

    public List<Path> findJavaFiles(Path projectPath) throws IOException {
        logger.debug("Finding Java files in project path: {}", projectPath);
        try (var walk = Files.walk(projectPath)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }
    }
}

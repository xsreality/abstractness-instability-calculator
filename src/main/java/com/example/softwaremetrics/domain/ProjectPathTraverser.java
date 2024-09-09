package com.example.softwaremetrics.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ProjectPathTraverser {

    private static final Logger logger = LoggerFactory.getLogger(ProjectPathTraverser.class);

    public List<Path> findJavaFiles(Path directoryPath) {
        logger.debug("Finding Java files in directory: {}", directoryPath);
        try (var walk = Files.walk(directoryPath)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("Error finding Java files in directory: {}", directoryPath, e);
            return Collections.emptyList();
        }
    }
}

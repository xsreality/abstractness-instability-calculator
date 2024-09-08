package com.example;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Component
public class SpringBootPackageScanner {
    private static final Logger logger = LoggerFactory.getLogger(SpringBootPackageScanner.class);

    private final PackageFinder packageFinder;
    private final PackageMetricsCalculator packageMetricsCalculator;

    @Autowired
    public SpringBootPackageScanner(PackageFinder packageFinder, PackageMetricsCalculator packageMetricsCalculator) {
        this.packageFinder = packageFinder;
        this.packageMetricsCalculator = packageMetricsCalculator;
    }

    public Map<String, Map<String, Double>> scanProject(String projectPath) throws IOException {
        logger.info("Starting project scan for path: {}", projectPath);
        Path path = Paths.get(projectPath);
        
        String mainPackage = packageFinder.findMainPackage(path);
        if (mainPackage == null || mainPackage.isEmpty()) {
            logger.error("No @SpringBootApplication found in the project.");
            throw new IOException("No @SpringBootApplication found in the project.");
        }
        logger.debug("Main package found: {}", mainPackage);

        List<String> topLevelPackages = packageFinder.findTopLevelPackages(path, mainPackage);
        if (topLevelPackages.isEmpty()) {
            logger.error("No subpackages found.");
            throw new IOException("No subpackages found.");
        }
        logger.debug("Top-level packages found: {}", topLevelPackages);

        Map<String, Map<String, Double>> metrics = packageMetricsCalculator.calculateMetrics(path, topLevelPackages);
        logger.info("Project scan completed successfully.");
        return metrics;
    }
}

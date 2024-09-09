package com.example.softwaremetrics.application;

import com.example.softwaremetrics.domain.PackageLocator;
import com.example.softwaremetrics.domain.PackageMetricsCalculator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * A Spring Boot component responsible for scanning project directories and estimating metrics
 * for packages within the project. Utilizes external classes to locate the main package and compute necessary metrics.
 */
@Component
public class SpringBootPackageScanner {

    private static final Logger logger = LoggerFactory.getLogger(SpringBootPackageScanner.class);

    private final PackageLocator packageLocator;
    private final PackageMetricsCalculator packageMetricsCalculator;

    @Autowired
    public SpringBootPackageScanner(PackageLocator packageLocator, PackageMetricsCalculator packageMetricsCalculator) {
        this.packageLocator = packageLocator;
        this.packageMetricsCalculator = packageMetricsCalculator;
    }

    public Map<String, Map<String, Double>> scanProject(String projectPath) {
        logger.info("Starting project scan for path: {}", projectPath);
        Path path = Paths.get(projectPath);

        String mainPackage = packageLocator.findMainPackage(path);
        if (mainPackage == null || mainPackage.isEmpty()) {
            logger.error("No @SpringBootApplication found in the project.");
            throw new IllegalArgumentException("No @SpringBootApplication found in the project.");
        }
        logger.debug("Main package found: {}", mainPackage);

        List<String> topLevelPackages = packageLocator.findTopLevelPackages(path, mainPackage);
        if (topLevelPackages.isEmpty()) {
            logger.error("No subpackages found.");
            throw new IllegalArgumentException("No subpackages found.");
        }
        logger.debug("Top-level packages found: {}", topLevelPackages);

        Map<String, Map<String, Double>> metrics = packageMetricsCalculator.calculateMetrics(path, topLevelPackages);
        logger.info("Project scan completed successfully.");
        return metrics;
    }
}

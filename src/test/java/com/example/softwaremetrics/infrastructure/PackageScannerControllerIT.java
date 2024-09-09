package com.example.softwaremetrics.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
public class PackageScannerControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        createTestProjectStructure(tempDir);
    }

    @Test
    public void testIndexPage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));
    }

    @Test
    public void testScanSuccess() throws Exception {
        mockMvc.perform(post("/scan").param("path", tempDir.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("graph :: graph"))
                .andExpect(model().attributeExists("metrics"))
                .andExpect(model().attribute("metrics", org.hamcrest.Matchers.hasKey("com.example.subpackage")));
    }

    @Test
    public void testScanError() throws Exception {
        mockMvc.perform(post("/scan").param("path", "/non/existent/path"))
                .andExpect(status().isOk())
                .andExpect(view().name("graph :: error"))
                .andExpect(model().attributeExists("error"))
                .andExpect(model().attribute("error", org.hamcrest.Matchers.containsString("Error scanning project")));
    }

    private void createTestProjectStructure(Path projectRoot) throws IOException {
        // Create main application class
        Path mainAppPath = projectRoot.resolve("src/main/java/com/example/TestApplication.java");
        Files.createDirectories(mainAppPath.getParent());
        Files.writeString(mainAppPath,
                """
                        package com.example;
                        import org.springframework.boot.SpringApplication;
                        import org.springframework.boot.autoconfigure.SpringBootApplication;
                        
                        @SpringBootApplication
                        public class TestApplication {
                            public static void main(String[] args) {
                                SpringApplication.run(TestApplication.class, args);
                            }
                        }
                        """
        );

        // Create a subpackage with a class
        Path subPackagePath = projectRoot.resolve("src/main/java/com/example/subpackage/TestClass.java");
        Files.createDirectories(subPackagePath.getParent());
        Files.writeString(subPackagePath,
                """
                        package com.example.subpackage;
                        
                        public class TestClass {
                            public void testMethod() {}
                        }
                        """);
    }
}
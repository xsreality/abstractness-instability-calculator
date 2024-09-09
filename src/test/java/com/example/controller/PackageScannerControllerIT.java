package com.example.controller;

import com.example.SpringBootPackageScanner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PackageScannerController.class)
public class PackageScannerControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SpringBootPackageScanner springBootPackageScanner;

    @Test
    public void testIndexPage() throws Exception {
        mockMvc.perform(get("/"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(view().name("index"));
    }

    @Test
    public void testScanSuccess() throws Exception {
        Map<String, Map<String, Double>> mockMetrics = new HashMap<>();
        Map<String, Double> packageMetrics = new HashMap<>();
        packageMetrics.put("Instability", 0.5);
        packageMetrics.put("Abstractness", 0.3);
        packageMetrics.put("Distance", 0.2);
        mockMetrics.put("com.example", packageMetrics);

        when(springBootPackageScanner.scanProject(anyString())).thenReturn(mockMetrics);

        mockMvc.perform(post("/scan").param("path", "/test/project"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(view().name("graph :: graph"))
                .andExpect(model().attributeExists("metrics"))
                .andExpect(model().attribute("metrics", mockMetrics));
    }

    @Test
    public void testScanError() throws Exception {
        when(springBootPackageScanner.scanProject(anyString())).thenThrow(new IOException("Test error"));

        mockMvc.perform(post("/scan").param("path", "/test/project"))
                .andExpect(status().isOk())
                .andExpect(view().name("graph :: error"))
                .andExpect(model().attributeExists("error"))
                .andExpect(model().attribute("error", "Error scanning project: Test error"));
    }
}
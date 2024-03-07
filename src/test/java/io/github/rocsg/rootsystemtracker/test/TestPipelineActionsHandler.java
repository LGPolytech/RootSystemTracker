package io.github.rocsg.rootsystemtracker.test;

import io.github.rocsg.rootsystemtracker.PipelineActionsHandler;
import io.github.rocsg.rootsystemtracker.PipelineParamHandler;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class TestPipelineActionsHandler {

    @Test
    public void testStackData() {

        TestRootDatasetMakeInventory prev_test = new TestRootDatasetMakeInventory();

        prev_test.globalTestNoRun();

        String inputFolderPath = "C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\Travaux\\RootSystemTracker" +
                "\\RootSystemTracker\\data\\ordered_input\\output_inv";
        String outputFolderPath = "C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\Travaux\\RootSystemTracker" +
                "\\RootSystemTracker\\data\\ordered_input\\OUT";

        // Initialize the PipelineParamHandler object with valid parameters
        PipelineParamHandler pph = new PipelineParamHandler(inputFolderPath, outputFolderPath);

        // Call the stackData method with valid parameters
        boolean result = PipelineActionsHandler.stackData(0, pph);

        // Check if the method returned true
        assertTrue(result);
    }

    @Test
    public void testRegistrationData() {

        String inputFolderPath = "C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\Travaux\\RootSystemTracker" +
                "\\RootSystemTracker\\data\\ordered_input\\output_inv";
        String outputFolderPath = "C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\Travaux\\RootSystemTracker" +
                "\\RootSystemTracker\\data\\ordered_input\\OUT";
        String outputdatadir;

        // Initialize the PipelineParamHandler object with valid parameters
        PipelineParamHandler pph = new PipelineParamHandler(inputFolderPath, outputFolderPath);

        // Call the stackData method
        PipelineActionsHandler.stackData(0, pph);

        outputdatadir = "C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\Travaux\\RootSystemTracker" +
                "\\RootSystemTracker\\data\\ordered_input\\OUT\\Box1";

        // Call the Register method
        boolean result = PipelineActionsHandler.registerSerie(0, outputdatadir, pph);

        // Check if the method returned true
        assertTrue(result);
    }

    @Test
    public void testComputeMaskandRemoveLeaves() {

        String inputFolderPath = "C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\Travaux\\RootSystemTracker" +
                "\\RootSystemTracker\\data\\ordered_input\\output_inv";
        String outputFolderPath = "C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\Travaux\\RootSystemTracker" +
                "\\RootSystemTracker\\data\\ordered_input\\OUT";
        String outputdatadir;

        // Initialize the PipelineParamHandler object with valid parameters
        PipelineParamHandler pph = new PipelineParamHandler(inputFolderPath, outputFolderPath);

        // Call the stackData method
        PipelineActionsHandler.stackData(0, pph);

        outputdatadir = "C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\Travaux\\RootSystemTracker" +
                "\\RootSystemTracker\\data\\ordered_input\\OUT\\Box1";

        // Call the Register method
        boolean result = PipelineActionsHandler.registerSerie(0, outputdatadir, pph);

        inputFolderPath = "C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\Travaux\\RootSystemTracker" +
                "\\RootSystemTracker\\data\\ordered_input\\OUT\\Box1";
        outputFolderPath = "C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\Travaux\\RootSystemTracker" +
                "\\RootSystemTracker\\data\\ordered_input\\OUT\\Box1";

        result = PipelineActionsHandler.computeMasksAndRemoveLeaves(0, inputFolderPath, pph);

        // Check if the method returned true
        assertTrue(result);
    }

    @Test
    public void testComputeGraph() {
        String inputFolderPath = "C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\Travaux\\RootSystemTracker" +
                "\\RootSystemTracker\\data\\ordered_input\\output_inv";
        String outputFolderPath = "C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\Travaux\\RootSystemTracker" +
                "\\RootSystemTracker\\data\\ordered_input\\OUT";
        String outputdatadir;

        // Initialize the PipelineParamHandler object with valid parameters
        PipelineParamHandler pph = new PipelineParamHandler(inputFolderPath, outputFolderPath);

        // Call the stackData method
        PipelineActionsHandler.stackData(0, pph);

        outputdatadir = "C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\Travaux\\RootSystemTracker" +
                "\\RootSystemTracker\\data\\ordered_input\\OUT\\Box1";

        // Call the Register method
        boolean result = PipelineActionsHandler.registerSerie(0, outputdatadir, pph);

        inputFolderPath = "C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\Travaux\\RootSystemTracker" +
                "\\RootSystemTracker\\data\\ordered_input\\OUT\\Box1";
        result = PipelineActionsHandler.computeMasksAndRemoveLeaves(0, inputFolderPath, pph);

        outputFolderPath = "C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\Travaux\\RootSystemTracker" +
                "\\RootSystemTracker\\data\\ordered_input\\OUT\\Box1";
        result = PipelineActionsHandler.buildAndProcessGraph(0, outputFolderPath, pph);

        // Check if the method returned true
        assertTrue(result);
    }

    @Test
    public void doAll() {
        TestRootDatasetMakeInventory prev_test = new TestRootDatasetMakeInventory();

        prev_test.globalTestNoRun();

        String inputFolderPath = "C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\Travaux\\RootSystemTracker" +
                "\\RootSystemTracker\\data\\ordered_input\\output_inv";
        String outputFolderPath = "C:\\Users\\loaiu\\Documents\\Etudes\\MAM\\MAM5\\Stage\\Travaux\\RootSystemTracker" +
                "\\RootSystemTracker\\data\\ordered_input\\OUT";

        // Initialize the PipelineParamHandler object with valid parameters
        PipelineParamHandler pph = new PipelineParamHandler(inputFolderPath, outputFolderPath);
        boolean result = true;
        for (int step = 1; step < 9; step++) {
            result = PipelineActionsHandler.doStepOnImg(step, 0, pph);
            assertTrue(result);
        }
    }

}
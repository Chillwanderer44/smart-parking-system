package smartparkingsystem;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SimulationController - Orchestrates the entire Smart Parking System simulation
 * Manages the lifecycle of all system components and coordinates their operations
 */
public class SimulationController {
    
    // Constants
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final int SIMULATION_DURATION_MINUTES = 5;
    private static final int NUMBER_OF_ENTRY_GATES = 3;
    private static final int NUMBER_OF_EXIT_GATES = 2;
    
    // System components
    private ParkingLot parkingLot;
    private VehicleGenerator vehicleGenerator;
    private PaymentProcessor paymentProcessor;
    private EntryGateManager entryGateManager;
    private ExitGateManager exitGateManager;
    private Statistics statistics;
    
    // Control
    private final AtomicBoolean isRunning;
    private final CountDownLatch simulationComplete;
    private ExecutorService mainExecutor;
    
    // Simulation timing
    private LocalDateTime simulationStartTime;
    private LocalDateTime simulationEndTime;
    
    /**
     * Constructor
     */
    public SimulationController() {
        this.isRunning = new AtomicBoolean(false);
        this.simulationComplete = new CountDownLatch(1);
        
        logEvent("SimulationController initialized");
    }
    
    /**
     * Helper method to repeat string (Java 8 compatible)
     */
    private String repeatString(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }
    
    /**
     * Initialize all system components
     */
    private void initializeComponents() {
        logEvent("Initializing system components...");
        
        try {
            // Initialize statistics FIRST
            statistics = new Statistics();
            
            // Core shared resource
            parkingLot = new ParkingLot();
            
            // Vehicle generation - PASS STATISTICS
            vehicleGenerator = new VehicleGenerator(SIMULATION_DURATION_MINUTES, statistics);
            
            // Payment processing
            paymentProcessor = new PaymentProcessor();
            
            // Gate management - PASS STATISTICS
            entryGateManager = new EntryGateManager(NUMBER_OF_ENTRY_GATES, parkingLot, vehicleGenerator, statistics);
            exitGateManager = new ExitGateManager(NUMBER_OF_EXIT_GATES, parkingLot, paymentProcessor, statistics);
            
            logEvent("All components initialized successfully with statistics integration");
            
        } catch (Exception e) {
            logEvent("ERROR: Failed to initialize components - " + e.getMessage());
            throw new RuntimeException("System initialization failed", e);
        }
    }
    
    /**
     * Start the complete simulation
     */
    public void startSimulation() {
        if (isRunning.get()) {
            logEvent("Simulation already running");
            return;
        }
        
        isRunning.set(true);
        simulationStartTime = LocalDateTime.now();
        
        logEvent(repeatString("=", 80));
        logEvent("           SMART PARKING SYSTEM SIMULATION STARTING");
        logEvent(repeatString("=", 80));
        logEvent("Simulation Duration: " + SIMULATION_DURATION_MINUTES + " minutes");
        logEvent("Entry Gates: " + NUMBER_OF_ENTRY_GATES);
        logEvent("Exit Gates: " + NUMBER_OF_EXIT_GATES);
        logEvent("Parking Spaces: 50");
        logEvent("Target Vehicles: 150");
        logEvent("Start Time: " + simulationStartTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        logEvent(repeatString("=", 80));
        
        // Initialize components
        initializeComponents();
        
        // Create main executor for simulation coordination
        mainExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "SimulationController");
                t.setDaemon(false);
                return t;
            }
        });
        
        // Start simulation in separate thread
        mainExecutor.submit(new Runnable() {
            @Override
            public void run() {
                runSimulation();
            }
        });
    }
    
    /**
     * Main simulation execution logic
     */
    private void runSimulation() {
        try {
            logEvent("Starting system components...");
            
            // Start statistics collection with periodic reporting
            statistics.startPeriodicReporting(1); // Report every minute
            
            // Start payment processor (already running upon creation)
            logEvent("Payment processor operational");
            
            // Start vehicle generation
            vehicleGenerator.startGeneration();
            
            // Start entry gates
            entryGateManager.startOperations();
            
            // Start exit gates  
            exitGateManager.startOperations();
            
            logEvent("All components started - simulation running");
            
            // Start monitoring thread
            startSimulationMonitoring();
            
            // Wait for simulation duration
            Thread.sleep(SIMULATION_DURATION_MINUTES * 60 * 1000L);
            
            logEvent("Simulation time completed - initiating shutdown sequence");
            
            // Graceful shutdown
            shutdownSimulation();
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logEvent("Simulation interrupted");
            forceShutdown();
            
        } catch (Exception e) {
            logEvent("ERROR: Simulation execution failed - " + e.getMessage());
            e.printStackTrace();
            forceShutdown();
            
        } finally {
            simulationComplete.countDown();
        }
    }
    
    /**
     * Start simulation monitoring thread
     */
    private void startSimulationMonitoring() {
        Thread monitorThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (isRunning.get()) {
                    try {
                        Thread.sleep(60000); // Monitor every minute
                        reportSystemStatus();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }, "SimulationMonitor");
        
        monitorThread.setDaemon(true);
        monitorThread.start();
    }
    
    /**
     * Report current system status
     */
    private void reportSystemStatus() {
        if (!isRunning.get()) return;
        
        long elapsedMinutes = java.time.Duration.between(simulationStartTime, LocalDateTime.now()).toMinutes();
        
        logEvent("=== SYSTEM STATUS REPORT - " + elapsedMinutes + " minutes elapsed ===");
        
        // Parking lot status
        ParkingLot.ParkingStatus parkingStatus = parkingLot.getStatus();
        logEvent("Parking: " + parkingStatus.toString());
        
        // Vehicle generation status
        VehicleGenerator.GenerationStats genStats = vehicleGenerator.getStats();
        logEvent("Vehicle Generation: " + genStats.toString());
        
        // Entry gate status
        EntryGateManager.EntryManagerStats entryStats = entryGateManager.getStats();
        logEvent("Entry Gates: " + entryStats.toString());
        
        // Exit gate status
        ExitGateManager.ExitManagerStats exitStats = exitGateManager.getStats();
        logEvent("Exit Gates: " + exitStats.toString());
        
        // Payment processor status
        PaymentProcessor.PaymentStats paymentStats = paymentProcessor.getStats();
        logEvent("Payment System: " + paymentStats.toString());
        
        // Current statistics snapshot
        if (statistics != null) {
            Statistics.SystemStatistics currentStats = statistics.getCurrentStats();
            logEvent("STATISTICS - Generated: " + currentStats.totalGenerated + 
                    ", Entered: " + currentStats.totalEntered + 
                    ", Exited: " + currentStats.totalExited);
            logEvent("Current Revenue: $" + String.format("%.2f", currentStats.totalRevenue));
            logEvent("Average Wait Time: " + String.format("%.2f", currentStats.averageWaitingTime) + " seconds");
        }
        
        logEvent("=== END STATUS REPORT ===");
    }
    
    /**
     * Graceful shutdown of the simulation
     */
    private void shutdownSimulation() {
        simulationEndTime = LocalDateTime.now();
        long totalDuration = java.time.Duration.between(simulationStartTime, simulationEndTime).toMinutes();
        
        logEvent(repeatString("=", 80));
        logEvent("           SIMULATION SHUTDOWN SEQUENCE INITIATED");
        logEvent(repeatString("=", 80));
        logEvent("Total Runtime: " + totalDuration + " minutes");
        
        try {
            // Stop vehicle generation first
            logEvent("Stopping vehicle generation...");
            vehicleGenerator.stopGeneration();
            
            // Allow some time for remaining vehicles to be processed
            logEvent("Allowing time for remaining vehicles to be processed...");
            Thread.sleep(10000); // 10 seconds
            
            // Shutdown entry gates
            logEvent("Shutting down entry gates...");
            entryGateManager.shutdown();
            
            // Process remaining vehicles in exit queue
            logEvent("Processing remaining exit queue...");
            Thread.sleep(5000); // 5 seconds
            
            // Shutdown exit gates
            logEvent("Shutting down exit gates...");
            exitGateManager.shutdown();
            
            // Shutdown payment processor
            logEvent("Shutting down payment processor...");
            paymentProcessor.shutdown();
            
            // Final statistics collection
            logEvent("Generating final statistics...");
            if (statistics != null) {
                statistics.printFinalStatistics();
            }
            
            // Shutdown statistics
            if (statistics != null) {
                statistics.shutdown();
            }
            
            logEvent(repeatString("=", 80));
            logEvent("           SIMULATION COMPLETED SUCCESSFULLY");
            logEvent(repeatString("=", 80));
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logEvent("Shutdown interrupted - forcing immediate shutdown");
            forceShutdown();
            
        } catch (Exception e) {
            logEvent("ERROR during shutdown: " + e.getMessage());
            forceShutdown();
        }
        
        isRunning.set(false);
    }
    
    /**
     * Force immediate shutdown of all components
     */
    private void forceShutdown() {
        logEvent("EMERGENCY: Force shutdown initiated");
        
        try {
            if (vehicleGenerator != null) {
                vehicleGenerator.stopGeneration();
            }
            
            if (entryGateManager != null) {
                entryGateManager.forceShutdown();
            }
            
            if (exitGateManager != null) {
                exitGateManager.forceShutdown();
            }
            
            if (paymentProcessor != null) {
                paymentProcessor.shutdown();
            }
            
            if (statistics != null) {
                statistics.shutdown();
            }
            
        } catch (Exception e) {
            logEvent("ERROR during force shutdown: " + e.getMessage());
        }
        
        isRunning.set(false);
        logEvent("Force shutdown completed");
    }
    
    /**
     * Stop the simulation manually
     */
    public void stopSimulation() {
        if (!isRunning.get()) {
            logEvent("Simulation not running");
            return;
        }
        
        logEvent("Manual simulation stop requested");
        
        // Interrupt main simulation thread
        if (mainExecutor != null) {
            mainExecutor.shutdownNow();
        }
        
        // Force shutdown
        forceShutdown();
    }
    
    /**
     * Wait for simulation to complete
     * @param timeout maximum time to wait
     * @param unit time unit
     * @return true if simulation completed within timeout
     */
    public boolean awaitCompletion(long timeout, TimeUnit unit) {
        try {
            return simulationComplete.await(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
    
    /**
     * Wait for simulation to complete (blocking)
     */
    public void awaitCompletion() throws InterruptedException {
        simulationComplete.await();
    }
    
    /**
     * Check if simulation is currently running
     */
    public boolean isRunning() {
        return isRunning.get();
    }
    
    /**
     * Get simulation start time
     */
    public LocalDateTime getSimulationStartTime() {
        return simulationStartTime;
    }
    
    /**
     * Get simulation end time (null if still running)
     */
    public LocalDateTime getSimulationEndTime() {
        return simulationEndTime;
    }
    
    /**
     * Get current system statistics (if simulation is running)
     */
    public Statistics.SystemStatistics getCurrentStatistics() {
        if (statistics != null) {
            return statistics.getCurrentStats();
        }
        return null;
    }
    
    /**
     * Utility method for consistent logging
     */
    private void logEvent(String message) {
        System.out.println("[" + LocalDateTime.now().format(TIME_FORMAT) + 
                          "] [SIM_CTRL] " + message);
    }
    
    /**
     * Cleanup resources on JVM shutdown
     */
    public void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                if (isRunning.get()) {
                    logEvent("JVM shutdown detected - stopping simulation");
                    forceShutdown();
                }
            }
        }, "SimulationShutdownHook"));
    }
}
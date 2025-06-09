/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package smartparkingsystem;

/**
 *
 * @author amiryusof
 */
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * EntryGateManager - Manages multiple entry gates and coordinates their operations
 * Handles the lifecycle of entry gate threads and collects statistics
 */
public class EntryGateManager {
    
    // Constants
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final int DEFAULT_GATE_COUNT = 3;
    
    // Gate management
    private final int numberOfGates;
    private final List<EntryGate> entryGates;
    private final List<Future<?>> gateFutures;
    private ExecutorService gateExecutor;
    
    // Dependencies
    private final ParkingLot parkingLot;
    private final VehicleGenerator vehicleGenerator;
    private final Statistics statistics;
    
    // Control
    private volatile boolean isOperating;
    private final AtomicInteger activeGates;
    
    // Statistics tracking
    private final AtomicInteger totalVehiclesProcessed;
    private final AtomicInteger totalVehiclesParked;
    private final AtomicInteger totalVehiclesRejected;
    
    /**
     * Constructor with default number of gates
     */
    public EntryGateManager(ParkingLot parkingLot, VehicleGenerator vehicleGenerator, Statistics statistics) {
        this(DEFAULT_GATE_COUNT, parkingLot, vehicleGenerator, statistics);
    }
    
    /**
     * Constructor with specified number of gates
     * @param numberOfGates number of entry gates to create
     * @param parkingLot reference to parking lot
     * @param vehicleGenerator reference to vehicle generator
     * @param statistics reference to statistics collector
     */
    public EntryGateManager(int numberOfGates, ParkingLot parkingLot, VehicleGenerator vehicleGenerator, Statistics statistics) {
        this.numberOfGates = numberOfGates;
        this.parkingLot = parkingLot;
        this.vehicleGenerator = vehicleGenerator;
        this.statistics = statistics;
        
        // Initialize collections
        this.entryGates = new ArrayList<>();
        this.gateFutures = new ArrayList<>();
        
        // Control
        this.isOperating = false;
        this.activeGates = new AtomicInteger(0);
        
        // Statistics
        this.totalVehiclesProcessed = new AtomicInteger(0);
        this.totalVehiclesParked = new AtomicInteger(0);
        this.totalVehiclesRejected = new AtomicInteger(0);
        
        // Create entry gates
        createEntryGates();
        
        logEvent("EntryGateManager initialized with " + numberOfGates + " gates");
    }
    
    /**
     * Create and initialize entry gates
     */
    private void createEntryGates() {
        for (int i = 1; i <= numberOfGates; i++) {
            EntryGate gate = new EntryGate(i, parkingLot, vehicleGenerator, statistics);
            entryGates.add(gate);
        }
        logEvent("Created " + numberOfGates + " entry gates");
    }
    
    /**
     * Start all entry gate operations
     */
    public void startOperations() {
        if (isOperating) {
            logEvent("Entry gates already operating");
            return;
        }
        
        isOperating = true;
        
        // Create thread pool for gates
        gateExecutor = Executors.newFixedThreadPool(numberOfGates, new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "EntryGateThread-" + threadNumber.getAndIncrement());
                t.setDaemon(false); // Ensure threads complete their work
                return t;
            }
        });
        
        // Start all gates
        for (EntryGate gate : entryGates) {
            Future<?> future = gateExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    activeGates.incrementAndGet();
                    try {
                        gate.run();
                    } finally {
                        activeGates.decrementAndGet();
                    }
                }
            });
            gateFutures.add(future);
        }
        
        logEvent("All " + numberOfGates + " entry gates started");
        
        // Start monitoring thread
        startMonitoring();
    }
    
    /**
     * Start monitoring thread for periodic status updates
     */
    private void startMonitoring() {
        Thread monitorThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (isOperating && activeGates.get() > 0) {
                    try {
                        Thread.sleep(30000); // Report every 30 seconds
                        reportStatus();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }, "EntryGateMonitor");
        
        monitorThread.setDaemon(true);
        monitorThread.start();
    }
    
    /**
     * Report current status of all entry gates
     */
    public void reportStatus() {
        logEvent("=== ENTRY GATES STATUS REPORT ===");
        
        // Individual gate statistics
        for (EntryGate gate : entryGates) {
            EntryGate.EntryGateStats stats = gate.getStats();
            logEvent(stats.toString());
        }
        
        // Overall statistics
        updateOverallStats();
        logEvent("TOTAL - Processed: " + totalVehiclesProcessed.get() + 
                ", Parked: " + totalVehiclesParked.get() + 
                ", Rejected: " + totalVehiclesRejected.get());
        
        // System status
        ParkingLot.ParkingStatus parkingStatus = parkingLot.getStatus();
        logEvent("Parking Status: " + parkingStatus.toString());
        
        VehicleGenerator.GenerationStats genStats = vehicleGenerator.getStats();
        logEvent("Generation Status: " + genStats.toString());
        
        logEvent("Active Gates: " + activeGates.get() + "/" + numberOfGates);
        logEvent("=== END STATUS REPORT ===");
    }
    
    /**
     * Update overall statistics from individual gates
     */
    private void updateOverallStats() {
        int processed = 0, parked = 0, rejected = 0;
        
        for (EntryGate gate : entryGates) {
            EntryGate.EntryGateStats stats = gate.getStats();
            processed += stats.getVehiclesProcessed();
            parked += stats.getVehiclesParked();
            rejected += stats.getVehiclesRejected();
        }
        
        totalVehiclesProcessed.set(processed);
        totalVehiclesParked.set(parked);
        totalVehiclesRejected.set(rejected);
    }
    
    /**
     * Shutdown all entry gates gracefully
     */
    public void shutdown() {
        logEvent("Initiating shutdown of all entry gates");
        isOperating = false;
        
        // Request shutdown of all gates
        for (EntryGate gate : entryGates) {
            gate.shutdown();
        }
        
        // Wait for gates to complete current operations
        boolean allShutdown = true;
        for (EntryGate gate : entryGates) {
            if (!gate.awaitShutdown(10, TimeUnit.SECONDS)) {
                logEvent("WARNING: Gate " + gate.getGateName() + " did not shutdown gracefully");
                allShutdown = false;
            }
        }
        
        // Cancel any remaining futures
        for (Future<?> future : gateFutures) {
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
        
        // Shutdown executor
        if (gateExecutor != null) {
            gateExecutor.shutdown();
            try {
                if (!gateExecutor.awaitTermination(15, TimeUnit.SECONDS)) {
                    gateExecutor.shutdownNow();
                    logEvent("Entry gate executor forced shutdown");
                }
            } catch (InterruptedException e) {
                gateExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        if (allShutdown) {
            logEvent("All entry gates shutdown successfully");
        } else {
            logEvent("Entry gate shutdown completed with warnings");
        }
        
        // Final statistics report
        reportFinalStatistics();
    }
    
    /**
     * Force shutdown of all entry gates (emergency)
     */
    public void forceShutdown() {
        logEvent("EMERGENCY: Force shutdown initiated");
        isOperating = false;
        
        // Cancel all futures immediately
        for (Future<?> future : gateFutures) {
            future.cancel(true);
        }
        
        // Force shutdown executor
        if (gateExecutor != null) {
            gateExecutor.shutdownNow();
        }
        
        logEvent("Force shutdown completed");
    }
    
    /**
     * Wait for all entry gates to complete their operations
     * @param timeout maximum time to wait
     * @param unit time unit
     * @return true if all gates completed within timeout
     */
    public boolean awaitCompletion(long timeout, TimeUnit unit) {
        long timeoutMs = unit.toMillis(timeout);
        long startTime = System.currentTimeMillis();
        
        for (Future<?> future : gateFutures) {
            try {
                long remainingTime = timeoutMs - (System.currentTimeMillis() - startTime);
                if (remainingTime <= 0) {
                    return false;
                }
                
                future.get(remainingTime, TimeUnit.MILLISECONDS);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (ExecutionException e) {
                logEvent("ERROR: Entry gate execution exception - " + e.getMessage());
            } catch (TimeoutException e) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Report final statistics
     */
    private void reportFinalStatistics() {
        logEvent("=== FINAL ENTRY GATES STATISTICS ===");
        updateOverallStats();
        
        for (EntryGate gate : entryGates) {
            EntryGate.EntryGateStats stats = gate.getStats();
            logEvent("FINAL " + stats.toString());
        }
        
        logEvent("FINAL TOTALS - Processed: " + totalVehiclesProcessed.get() + 
                ", Parked: " + totalVehiclesParked.get() + 
                ", Rejected: " + totalVehiclesRejected.get());
        
        // Calculate efficiency
        if (totalVehiclesProcessed.get() > 0) {
            double efficiency = (double) totalVehiclesParked.get() / totalVehiclesProcessed.get() * 100;
            logEvent("Entry Success Rate: " + String.format("%.2f%%", efficiency));
        }
        
        logEvent("=== END FINAL STATISTICS ===");
    }
    
    /**
     * Get current manager statistics
     */
    public EntryManagerStats getStats() {
        updateOverallStats();
        
        return new EntryManagerStats(
                numberOfGates,
                activeGates.get(),
                totalVehiclesProcessed.get(),
                totalVehiclesParked.get(),
                totalVehiclesRejected.get(),
                isOperating
        );
    }
    
    /**
     * Get list of all entry gates (defensive copy)
     */
    public List<EntryGate> getEntryGates() {
        return Collections.unmodifiableList(entryGates);
    }
    
    /**
     * Check if manager is currently operating
     */
    public boolean isOperating() {
        return isOperating;
    }
    
    /**
     * Get number of active gates
     */
    public int getActiveGates() {
        return activeGates.get();
    }
    
    /**
     * Utility method for consistent logging
     */
    private void logEvent(String message) {
        System.out.println("[" + LocalDateTime.now().format(TIME_FORMAT) + 
                          "] [ENTRY_MGR] " + message);
    }
    
    /**
     * Inner class for manager statistics
     */
    public static class EntryManagerStats {
        private final int totalGates;
        private final int activeGates;
        private final int totalProcessed;
        private final int totalParked;
        private final int totalRejected;
        private final boolean isOperating;
        
        public EntryManagerStats(int total, int active, int processed, int parked, 
                               int rejected, boolean operating) {
            this.totalGates = total;
            this.activeGates = active;
            this.totalProcessed = processed;
            this.totalParked = parked;
            this.totalRejected = rejected;
            this.isOperating = operating;
        }
        
        // Getters
        public int getTotalGates() { return totalGates; }
        public int getActiveGates() { return activeGates; }
        public int getTotalProcessed() { return totalProcessed; }
        public int getTotalParked() { return totalParked; }
        public int getTotalRejected() { return totalRejected; }
        public boolean isOperating() { return isOperating; }
        
        @Override
        public String toString() {
            return String.format("Entry Manager - Gates: %d/%d, Processed: %d, Parked: %d, Rejected: %d, Operating: %s",
                    activeGates, totalGates, totalProcessed, totalParked, totalRejected, isOperating);
        }
    }
}
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
 * ExitGateManager - Manages multiple exit gates and coordinates exit operations
 * Handles vehicle exit queue and manages the lifecycle of exit gate threads
 */
public class ExitGateManager {
    
    // Constants
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final int DEFAULT_GATE_COUNT = 2;
    
    // Gate management
    private final int numberOfGates;
    private final List<ExitGate> exitGates;
    private final List<Future<?>> gateFutures;
    private ExecutorService gateExecutor;
    
    // Vehicle exit queue
    private final BlockingQueue<Car> exitQueue;
    
    // Dependencies
    private final ParkingLot parkingLot;
    private final PaymentProcessor paymentProcessor;
    private final Statistics statistics;
    
    // Control
    private volatile boolean isOperating;
    private final AtomicInteger activeGates;
    
    // Statistics tracking
    private final AtomicInteger totalVehiclesProcessed;
    private final AtomicInteger totalVehiclesExited;
    private final AtomicInteger totalPaymentFailures;
    
    // Exit vehicle generation
    private ExecutorService exitVehicleGenerator;
    private volatile boolean generatingExitVehicles;
    
    /**
     * Constructor with default number of gates
     */
    public ExitGateManager(ParkingLot parkingLot, PaymentProcessor paymentProcessor, Statistics statistics) {
        this(DEFAULT_GATE_COUNT, parkingLot, paymentProcessor, statistics);
    }
    
    /**
     * Constructor with specified number of gates
     * @param numberOfGates number of exit gates to create
     * @param parkingLot reference to parking lot
     * @param paymentProcessor payment processing service
     * @param statistics reference to statistics collector
     */
    public ExitGateManager(int numberOfGates, ParkingLot parkingLot, PaymentProcessor paymentProcessor, Statistics statistics) {
        this.numberOfGates = numberOfGates;
        this.parkingLot = parkingLot;
        this.paymentProcessor = paymentProcessor;
        this.statistics = statistics;
        
        // Initialize exit queue
        this.exitQueue = new LinkedBlockingQueue<>();
        
        // Initialize collections
        this.exitGates = new ArrayList<>();
        this.gateFutures = new ArrayList<>();
        
        // Control
        this.isOperating = false;
        this.activeGates = new AtomicInteger(0);
        this.generatingExitVehicles = false;
        
        // Statistics
        this.totalVehiclesProcessed = new AtomicInteger(0);
        this.totalVehiclesExited = new AtomicInteger(0);
        this.totalPaymentFailures = new AtomicInteger(0);
        
        // Create exit gates
        createExitGates();
        
        logEvent("ExitGateManager initialized with " + numberOfGates + " gates");
    }
    
    /**
     * Create and initialize exit gates
     */
    private void createExitGates() {
        for (int i = 1; i <= numberOfGates; i++) {
            ExitGate gate = new ExitGate(i, parkingLot, exitQueue, paymentProcessor, statistics);
            exitGates.add(gate);
        }
        logEvent("Created " + numberOfGates + " exit gates");
    }
    
    /**
     * Start all exit gate operations
     */
    public void startOperations() {
        if (isOperating) {
            logEvent("Exit gates already operating");
            return;
        }
        
        isOperating = true;
        
        // Create thread pool for gates
        gateExecutor = Executors.newFixedThreadPool(numberOfGates, new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "ExitGateThread-" + threadNumber.getAndIncrement());
                t.setDaemon(false);
                return t;
            }
        });
        
        // Start all gates
        for (ExitGate gate : exitGates) {
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
        
        logEvent("All " + numberOfGates + " exit gates started");
        
        // Start exit vehicle generator
        startExitVehicleGeneration();
        
        // Start monitoring thread
        startMonitoring();
    }
    
    /**
     * Start generating vehicles for exit based on their parking duration
     */
    private void startExitVehicleGeneration() {
        generatingExitVehicles = true;
        
        exitVehicleGenerator = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "ExitVehicleGenerator");
                t.setDaemon(true);
                return t;
            }
        });
        
        exitVehicleGenerator.submit(new Runnable() {
            @Override
            public void run() {
                logEvent("Exit vehicle generation started");
                
                while (generatingExitVehicles && !Thread.currentThread().isInterrupted()) {
                    try {
                        generateExitVehicles();
                        Thread.sleep(5000); // Check every 5 seconds
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        logEvent("ERROR: Exception in exit vehicle generation - " + e.getMessage());
                        statistics.recordError("EXIT_GENERATION_ERROR", "Exception in exit vehicle generation: " + e.getMessage());
                    }
                }
                
                logEvent("Exit vehicle generation stopped");
            }
        });
    }
    
    /**
     * Check for vehicles ready to exit and add them to exit queue
     */
    private void generateExitVehicles() {
        try {
            // Get all currently parked vehicles
            ConcurrentHashMap<Integer, Car> occupiedSpaces = parkingLot.getOccupiedSpaces();
            
            for (Car car : occupiedSpaces.values()) {
                if (car.isReadyToExit() && !isCarInExitQueue(car)) {
                    // Add car to exit queue
                    exitQueue.offer(car);
                    logEvent("Vehicle " + car.getCarId() + " added to exit queue " +
                            "(parked for " + car.getActualParkingDuration() + " minutes)");
                }
            }
            
        } catch (Exception e) {
            logEvent("ERROR: Exception in generateExitVehicles - " + e.getMessage());
            statistics.recordError("EXIT_VEHICLE_GENERATION_ERROR", "Exception in generateExitVehicles: " + e.getMessage());
        }
    }
    
    /**
     * Check if car is already in exit queue (to avoid duplicates)
     */
    private boolean isCarInExitQueue(Car car) {
        // Simple check - in a real system might use a Set to track cars in queue
        return exitQueue.contains(car);
    }
    
    /**
     * Manually add a vehicle to exit queue (for testing or special cases)
     */
    public boolean addVehicleToExitQueue(Car car) {
        if (car == null || car.getSpaceNumber() == -1) {
            return false;
        }
        
        try {
            exitQueue.put(car);
            logEvent("Vehicle " + car.getCarId() + " manually added to exit queue");
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
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
        }, "ExitGateMonitor");
        
        monitorThread.setDaemon(true);
        monitorThread.start();
    }
    
    /**
     * Report current status of all exit gates
     */
    public void reportStatus() {
        logEvent("=== EXIT GATES STATUS REPORT ===");
        
        // Individual gate statistics
        for (ExitGate gate : exitGates) {
            ExitGate.ExitGateStats stats = gate.getStats();
            logEvent(stats.toString());
        }
        
        // Overall statistics
        updateOverallStats();
        logEvent("TOTAL - Processed: " + totalVehiclesProcessed.get() + 
                ", Exited: " + totalVehiclesExited.get() + 
                ", Payment Failures: " + totalPaymentFailures.get());
        
        // Queue status
        logEvent("Exit Queue Size: " + exitQueue.size() + " vehicles waiting");
        
        // System status
        ParkingLot.ParkingStatus parkingStatus = parkingLot.getStatus();
        logEvent("Parking Status: " + parkingStatus.toString());
        
        logEvent("Active Gates: " + activeGates.get() + "/" + numberOfGates);
        logEvent("=== END STATUS REPORT ===");
    }
    
    /**
     * Update overall statistics from individual gates
     */
    private void updateOverallStats() {
        int processed = 0, exited = 0, failures = 0;
        
        for (ExitGate gate : exitGates) {
            ExitGate.ExitGateStats stats = gate.getStats();
            processed += stats.getVehiclesProcessed();
            exited += stats.getVehiclesExited();
            failures += stats.getPaymentFailures();
        }
        
        totalVehiclesProcessed.set(processed);
        totalVehiclesExited.set(exited);
        totalPaymentFailures.set(failures);
    }
    
    /**
     * Calculate total revenue from all gates
     */
    public double getTotalRevenue() {
        double totalRevenue = 0.0;
        for (ExitGate gate : exitGates) {
            totalRevenue += gate.getStats().getTotalRevenue();
        }
        return totalRevenue;
    }
    
    /**
     * Shutdown all exit gates gracefully
     */
    public void shutdown() {
        logEvent("Initiating shutdown of all exit gates");
        isOperating = false;
        generatingExitVehicles = false;
        
        // Shutdown exit vehicle generator
        if (exitVehicleGenerator != null) {
            exitVehicleGenerator.shutdown();
            try {
                if (!exitVehicleGenerator.awaitTermination(5, TimeUnit.SECONDS)) {
                    exitVehicleGenerator.shutdownNow();
                }
            } catch (InterruptedException e) {
                exitVehicleGenerator.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // Request shutdown of all gates
        for (ExitGate gate : exitGates) {
            gate.shutdown();
        }
        
        // Wait for gates to complete current operations
        boolean allShutdown = true;
        for (ExitGate gate : exitGates) {
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
                    logEvent("Exit gate executor forced shutdown");
                }
            } catch (InterruptedException e) {
                gateExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        if (allShutdown) {
            logEvent("All exit gates shutdown successfully");
        } else {
            logEvent("Exit gate shutdown completed with warnings");
        }
        
        // Final statistics report
        reportFinalStatistics();
    }
    
    /**
     * Force shutdown of all exit gates (emergency)
     */
    public void forceShutdown() {
        logEvent("EMERGENCY: Force shutdown initiated");
        isOperating = false;
        generatingExitVehicles = false;
        
        // Force shutdown generators
        if (exitVehicleGenerator != null) {
            exitVehicleGenerator.shutdownNow();
        }
        
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
     * Wait for all exit gates to complete their operations
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
                logEvent("ERROR: Exit gate execution exception - " + e.getMessage());
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
        logEvent("=== FINAL EXIT GATES STATISTICS ===");
        updateOverallStats();
        
        double totalRevenue = getTotalRevenue();
        
        for (ExitGate gate : exitGates) {
            ExitGate.ExitGateStats stats = gate.getStats();
            logEvent("FINAL " + stats.toString());
        }
        
        logEvent("FINAL TOTALS - Processed: " + totalVehiclesProcessed.get() + 
                ", Exited: " + totalVehiclesExited.get() + 
                ", Payment Failures: " + totalPaymentFailures.get() +
                ", Total Revenue: $" + String.format("%.2f", totalRevenue));
        
        // Calculate efficiency
        if (totalVehiclesProcessed.get() > 0) {
            double efficiency = (double) totalVehiclesExited.get() / totalVehiclesProcessed.get() * 100;
            logEvent("Exit Success Rate: " + String.format("%.2f%%", efficiency));
            
            double paymentFailureRate = (double) totalPaymentFailures.get() / totalVehiclesProcessed.get() * 100;
            logEvent("Payment Failure Rate: " + String.format("%.2f%%", paymentFailureRate));
        }
        
        logEvent("Exit Queue Final Size: " + exitQueue.size() + " vehicles");
        logEvent("=== END FINAL STATISTICS ===");
    }
    
    /**
     * Get current manager statistics
     */
    public ExitManagerStats getStats() {
        updateOverallStats();
        
        return new ExitManagerStats(
                numberOfGates,
                activeGates.get(),
                totalVehiclesProcessed.get(),
                totalVehiclesExited.get(),
                totalPaymentFailures.get(),
                exitQueue.size(),
                getTotalRevenue(),
                isOperating
        );
    }
    
    /**
     * Get list of all exit gates (defensive copy)
     */
    public List<ExitGate> getExitGates() {
        return Collections.unmodifiableList(exitGates);
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
     * Get current exit queue size
     */
    public int getExitQueueSize() {
        return exitQueue.size();
    }
    
    /**
     * Get copy of current exit queue
     */
    public List<Car> getExitQueueSnapshot() {
        return new ArrayList<>(exitQueue);
    }
    
    /**
     * Utility method for consistent logging
     */
    private void logEvent(String message) {
        System.out.println("[" + LocalDateTime.now().format(TIME_FORMAT) + 
                          "] [EXIT_MGR] " + message);
    }
    
    /**
     * Inner class for manager statistics
     */
    public static class ExitManagerStats {
        private final int totalGates;
        private final int activeGates;
        private final int totalProcessed;
        private final int totalExited;
        private final int totalPaymentFailures;
        private final int exitQueueSize;
        private final double totalRevenue;
        private final boolean isOperating;
        
        public ExitManagerStats(int total, int active, int processed, int exited, 
                              int failures, int queueSize, double revenue, boolean operating) {
            this.totalGates = total;
            this.activeGates = active;
            this.totalProcessed = processed;
            this.totalExited = exited;
            this.totalPaymentFailures = failures;
            this.exitQueueSize = queueSize;
            this.totalRevenue = revenue;
            this.isOperating = operating;
        }
        
        // Getters
        public int getTotalGates() { return totalGates; }
        public int getActiveGates() { return activeGates; }
        public int getTotalProcessed() { return totalProcessed; }
        public int getTotalExited() { return totalExited; }
        public int getTotalPaymentFailures() { return totalPaymentFailures; }
        public int getExitQueueSize() { return exitQueueSize; }
        public double getTotalRevenue() { return totalRevenue; }
        public boolean isOperating() { return isOperating; }
        
        @Override
        public String toString() {
            return String.format("Exit Manager - Gates: %d/%d, Processed: %d, Exited: %d, Failures: %d, Queue: %d, Revenue: $%.2f, Operating: %s",
                    activeGates, totalGates, totalProcessed, totalExited, totalPaymentFailures, exitQueueSize, totalRevenue, isOperating);
        }
    }
}
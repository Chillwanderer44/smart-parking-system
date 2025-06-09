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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ThreadLocalRandom;

/**
 * EntryGate - Individual entry gate thread that processes incoming vehicles
 * Each gate operates independently and handles vehicle entry operations
 */
public class EntryGate implements Runnable {
    
    // Constants
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    
    // Gate identification
    private final int gateId;
    private final String gateName;
    
    // Dependencies
    private final ParkingLot parkingLot;
    private final VehicleGenerator vehicleGenerator;
    
    // Statistics
    private final AtomicInteger vehiclesProcessed;
    private final AtomicInteger vehiclesParked;
    private final AtomicInteger vehiclesRejected;
    private final AtomicLong totalProcessingTime;
    
    // Control
    private volatile boolean isOperating;
    private final CountDownLatch shutdownLatch;
    
    // Gate specific settings
    private final int processingTimeMin; // milliseconds
    private final int processingTimeMax; // milliseconds
    
    /**
     * Constructor
     * @param gateId unique gate identifier
     * @param parkingLot reference to shared parking lot
     * @param vehicleGenerator reference to vehicle generator
     */
    public EntryGate(int gateId, ParkingLot parkingLot, VehicleGenerator vehicleGenerator) {
        this.gateId = gateId;
        this.gateName = "EntryGate-" + gateId;
        this.parkingLot = parkingLot;
        this.vehicleGenerator = vehicleGenerator;
        
        // Initialize statistics
        this.vehiclesProcessed = new AtomicInteger(0);
        this.vehiclesParked = new AtomicInteger(0);
        this.vehiclesRejected = new AtomicInteger(0);
        this.totalProcessingTime = new AtomicLong(0);
        
        // Control
        this.isOperating = false;
        this.shutdownLatch = new CountDownLatch(1);
        
        // Gate processing times (simulate different gate speeds)
        this.processingTimeMin = 500 + (gateId * 100); // 500ms base + variation
        this.processingTimeMax = 1500 + (gateId * 200); // 1500ms base + variation
        
        logEvent("Entry gate initialized");
    }
    
    /**
     * Main execution loop for the entry gate
     */
    @Override
    public void run() {
        Thread.currentThread().setName(gateName);
        isOperating = true;
        
        logEvent("Entry gate started operations");
        
        try {
            while (isOperating && !Thread.currentThread().isInterrupted()) {
                processNextVehicle();
            }
            
        } catch (Exception e) {
            logEvent("ERROR: Exception in entry gate - " + e.getMessage());
            e.printStackTrace();
        } finally {
            isOperating = false;
            shutdownLatch.countDown();
            logEvent("Entry gate stopped operations");
        }
    }
    
    /**
     * Process the next vehicle from the queue
     */
    private void processNextVehicle() {
        try {
            // Get next vehicle with timeout to allow periodic status checks
            Car car = vehicleGenerator.getNextVehicle(2, TimeUnit.SECONDS);
            
            if (car == null) {
                // No vehicle available, check if generation is complete
                if (vehicleGenerator.isGenerationComplete() && !vehicleGenerator.hasVehiclesWaiting()) {
                    logEvent("No more vehicles to process, shutting down");
                    isOperating = false;
                    return;
                }
                return; // Continue waiting
            }
            
            // Process the vehicle
            processVehicleEntry(car);
            
        } catch (Exception e) {
            logEvent("ERROR: Failed to process vehicle - " + e.getMessage());
        }
    }
    
    /**
     * Process individual vehicle entry
     * @param car the car attempting to enter
     */
    private void processVehicleEntry(Car car) {
        long startTime = System.currentTimeMillis();
        int processed = vehiclesProcessed.incrementAndGet();
        
        logEvent("Processing vehicle " + car.getCarId() + " [" + car.getLicensePlate() + 
                "] (Vehicle #" + processed + ")");
        
        try {
            // Simulate gate processing time (validation, barrier operation, etc.)
            simulateGateProcessing();
            
            // Attempt to park the vehicle
            int spaceNumber = parkingLot.parkCar(car);
            
            if (spaceNumber != -1) {
                // Successfully parked
                vehiclesParked.incrementAndGet();
                logEvent("Vehicle " + car.getCarId() + " successfully entered and parked in space " + 
                        spaceNumber);
                
                // Log parking lot status
                ParkingLot.ParkingStatus status = parkingLot.getStatus();
                if (status.getAvailableSpaces() <= 10) {
                    logEvent("WARNING: Low parking availability - " + status.getAvailableSpaces() + 
                            " spaces remaining");
                }
                
            } else {
                // Failed to park (should be rare due to semaphore)
                vehiclesRejected.incrementAndGet();
                logEvent("Vehicle " + car.getCarId() + " entry failed - no parking space available");
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logEvent("Vehicle " + car.getCarId() + " processing interrupted");
            vehiclesRejected.incrementAndGet();
            
        } catch (Exception e) {
            logEvent("ERROR: Failed to process vehicle " + car.getCarId() + " - " + e.getMessage());
            vehiclesRejected.incrementAndGet();
            
        } finally {
            // Update processing time statistics
            long processingTime = System.currentTimeMillis() - startTime;
            totalProcessingTime.addAndGet(processingTime);
            
            logEvent("Completed processing vehicle " + car.getCarId() + " in " + processingTime + "ms");
        }
    }
    
    /**
     * Simulate gate processing operations
     */
    private void simulateGateProcessing() throws InterruptedException {
        // Simulate various gate operations:
        // - License plate scanning
        // - Barrier operation
        // - Ticket dispensing
        // - Access validation
        
        int processingTime = ThreadLocalRandom.current().nextInt(processingTimeMin, processingTimeMax + 1);
        Thread.sleep(processingTime);
    }
    
    /**
     * Request shutdown of this entry gate
     */
    public void shutdown() {
        logEvent("Shutdown requested");
        isOperating = false;
    }
    
    /**
     * Wait for gate to complete shutdown
     * @param timeout maximum time to wait
     * @param unit time unit
     * @return true if shutdown completed within timeout
     */
    public boolean awaitShutdown(long timeout, TimeUnit unit) {
        try {
            return shutdownLatch.await(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
    
    /**
     * Get current gate statistics
     */
    public EntryGateStats getStats() {
        long avgProcessingTime = vehiclesProcessed.get() > 0 ? 
                totalProcessingTime.get() / vehiclesProcessed.get() : 0;
        
        return new EntryGateStats(
                gateId,
                gateName,
                vehiclesProcessed.get(),
                vehiclesParked.get(),
                vehiclesRejected.get(),
                avgProcessingTime,
                isOperating
        );
    }
    
    /**
     * Check if gate is currently operating
     */
    public boolean isOperating() {
        return isOperating;
    }
    
    /**
     * Get gate identification
     */
    public int getGateId() {
        return gateId;
    }
    
    public String getGateName() {
        return gateName;
    }
    
    /**
     * Utility method for consistent logging
     */
    private void logEvent(String message) {
        System.out.println("[" + LocalDateTime.now().format(TIME_FORMAT) + 
                          "] [" + gateName + "] " + message);
    }
    
    /**
     * Inner class for entry gate statistics
     */
    public static class EntryGateStats {
        private final int gateId;
        private final String gateName;
        private final int vehiclesProcessed;
        private final int vehiclesParked;
        private final int vehiclesRejected;
        private final long avgProcessingTime;
        private final boolean isOperating;
        
        public EntryGateStats(int gateId, String gateName, int processed, int parked, 
                             int rejected, long avgTime, boolean operating) {
            this.gateId = gateId;
            this.gateName = gateName;
            this.vehiclesProcessed = processed;
            this.vehiclesParked = parked;
            this.vehiclesRejected = rejected;
            this.avgProcessingTime = avgTime;
            this.isOperating = operating;
        }
        
        // Getters
        public int getGateId() { return gateId; }
        public String getGateName() { return gateName; }
        public int getVehiclesProcessed() { return vehiclesProcessed; }
        public int getVehiclesParked() { return vehiclesParked; }
        public int getVehiclesRejected() { return vehiclesRejected; }
        public long getAvgProcessingTime() { return avgProcessingTime; }
        public boolean isOperating() { return isOperating; }
        
        @Override
        public String toString() {
            return String.format("%s - Processed: %d, Parked: %d, Rejected: %d, Avg Time: %dms, Operating: %s",
                    gateName, vehiclesProcessed, vehiclesParked, vehiclesRejected, avgProcessingTime, isOperating);
        }
    }
}

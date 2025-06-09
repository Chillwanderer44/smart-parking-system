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
 * ExitGate - Individual exit gate thread that processes departing vehicles
 * Handles vehicle exit operations and coordinates with payment processing
 */
public class ExitGate implements Runnable {
    
    // Constants
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    
    // Gate identification
    private final int gateId;
    private final String gateName;
    
    // Dependencies
    private final ParkingLot parkingLot;
    private final BlockingQueue<Car> exitQueue;
    private final PaymentProcessor paymentProcessor;
    
    // Statistics
    private final AtomicInteger vehiclesProcessed;
    private final AtomicInteger vehiclesExited;
    private final AtomicInteger paymentFailures;
    private final AtomicLong totalProcessingTime;
    private final AtomicLong totalRevenue;
    
    // Control
    private volatile boolean isOperating;
    private final CountDownLatch shutdownLatch;
    
    // Gate specific settings
    private final int processingTimeMin; // milliseconds
    private final int processingTimeMax; // milliseconds
    private final double malfunctionProbability; // Chance of gate malfunction
    
    /**
     * Constructor
     * @param gateId unique gate identifier
     * @param parkingLot reference to shared parking lot
     * @param exitQueue queue of vehicles ready to exit
     * @param paymentProcessor payment processing service
     */
    public ExitGate(int gateId, ParkingLot parkingLot, BlockingQueue<Car> exitQueue, 
                    PaymentProcessor paymentProcessor) {
        this.gateId = gateId;
        this.gateName = "ExitGate-" + gateId;
        this.parkingLot = parkingLot;
        this.exitQueue = exitQueue;
        this.paymentProcessor = paymentProcessor;
        
        // Initialize statistics
        this.vehiclesProcessed = new AtomicInteger(0);
        this.vehiclesExited = new AtomicInteger(0);
        this.paymentFailures = new AtomicInteger(0);
        this.totalProcessingTime = new AtomicLong(0);
        this.totalRevenue = new AtomicLong(0);
        
        // Control
        this.isOperating = false;
        this.shutdownLatch = new CountDownLatch(1);
        
        // Gate specific settings (each gate has different characteristics)
        this.processingTimeMin = 800 + (gateId * 100); // 800ms base + variation
        this.processingTimeMax = 2000 + (gateId * 150); // 2000ms base + variation
        this.malfunctionProbability = 0.02 + (gateId * 0.005); // 2% base + variation
        
        logEvent("Exit gate initialized");
    }
    
    /**
     * Main execution loop for the exit gate
     */
    @Override
    public void run() {
        Thread.currentThread().setName(gateName);
        isOperating = true;
        
        logEvent("Exit gate started operations");
        
        try {
            while (isOperating && !Thread.currentThread().isInterrupted()) {
                processNextVehicle();
            }
            
        } catch (Exception e) {
            logEvent("ERROR: Exception in exit gate - " + e.getMessage());
            e.printStackTrace();
        } finally {
            isOperating = false;
            shutdownLatch.countDown();
            logEvent("Exit gate stopped operations");
        }
    }
    
    /**
     * Process the next vehicle from the exit queue
     */
    private void processNextVehicle() {
        try {
            // Get next vehicle with timeout to allow periodic status checks
            Car car = exitQueue.poll(3, TimeUnit.SECONDS);
            
            if (car == null) {
                // No vehicle available, continue waiting
                return;
            }
            
            // Process the vehicle exit
            processVehicleExit(car);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logEvent("Exit processing interrupted");
        } catch (Exception e) {
            logEvent("ERROR: Failed to process vehicle exit - " + e.getMessage());
        }
    }
    
    /**
     * Process individual vehicle exit
     * @param car the car attempting to exit
     */
    private void processVehicleExit(Car car) {
        long startTime = System.currentTimeMillis();
        int processed = vehiclesProcessed.incrementAndGet();
        
        logEvent("Processing exit for vehicle " + car.getCarId() + " [" + car.getLicensePlate() + 
                "] from space " + car.getSpaceNumber() + " (Vehicle #" + processed + ")");
        
        try {
            // Step 1: Validate vehicle is in parking lot
            if (!validateVehicle(car)) {
                logEvent("ERROR: Vehicle " + car.getCarId() + " validation failed");
                return;
            }
            
            // Step 2: Process payment if not already paid
            if (!processPayment(car)) {
                paymentFailures.incrementAndGet();
                logEvent("ERROR: Payment failed for vehicle " + car.getCarId() + 
                        " - blocking exit until resolved");
                
                // In real system, would handle payment retry or manual intervention
                // For simulation, we'll allow exit after logging the failure
                logEvent("MANUAL_OVERRIDE: Allowing exit for vehicle " + car.getCarId() + 
                        " despite payment failure");
            }
            
            // Step 3: Simulate gate malfunction check
            if (simulateGateMalfunction()) {
                logEvent("WARNING: Gate malfunction detected - attempting recovery");
                handleGateMalfunction();
            }
            
            // Step 4: Simulate physical exit process
            simulateExitProcessing();
            
            // Step 5: Remove vehicle from parking lot
            if (parkingLot.removeCar(car)) {
                vehiclesExited.incrementAndGet();
                
                // Update revenue tracking
                if (car.isPaid()) {
                    long revenueInCents = Math.round(car.getPaymentAmount() * 100);
                    totalRevenue.addAndGet(revenueInCents);
                }
                
                logEvent("Vehicle " + car.getCarId() + " successfully exited" + 
                        (car.isPaid() ? " (Paid: $" + String.format("%.2f", car.getPaymentAmount()) + ")" : " (UNPAID)"));
                
                // Log updated parking status
                ParkingLot.ParkingStatus status = parkingLot.getStatus();
                logEvent("Parking spaces freed - Available: " + status.getAvailableSpaces() + 
                        "/" + status.getTotalSpaces());
                
            } else {
                logEvent("ERROR: Failed to remove vehicle " + car.getCarId() + " from parking lot");
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logEvent("Vehicle " + car.getCarId() + " exit processing interrupted");
            
        } catch (Exception e) {
            logEvent("ERROR: Failed to process exit for vehicle " + car.getCarId() + " - " + e.getMessage());
            
        } finally {
            // Update processing time statistics
            long processingTime = System.currentTimeMillis() - startTime;
            totalProcessingTime.addAndGet(processingTime);
            
            logEvent("Completed exit processing for vehicle " + car.getCarId() + " in " + processingTime + "ms");
        }
    }
    
    /**
     * Validate vehicle before exit
     */
    private boolean validateVehicle(Car car) {
        // Check if vehicle has valid parking information
        if (car.getSpaceNumber() == -1) {
            logEvent("ERROR: Vehicle " + car.getCarId() + " has no assigned parking space");
            return false;
        }
        
        // Verify vehicle is actually in the parking lot
        Car parkedCar = parkingLot.getCarInSpace(car.getSpaceNumber());
        if (parkedCar == null || !parkedCar.getCarId().equals(car.getCarId())) {
            logEvent("ERROR: Vehicle " + car.getCarId() + " not found in assigned space " + 
                    car.getSpaceNumber());
            return false;
        }
        
        logEvent("Vehicle " + car.getCarId() + " validation successful");
        return true;
    }
    
    /**
     * Process payment for vehicle
     */
    private boolean processPayment(Car car) {
        if (car.isPaid()) {
            logEvent("Vehicle " + car.getCarId() + " already paid: $" + 
                    String.format("%.2f", car.getPaymentAmount()));
            return true;
        }
        
        logEvent("Processing payment for vehicle " + car.getCarId());
        
        // Delegate to payment processor
        boolean paymentSuccess = paymentProcessor.processPayment(car);
        
        if (paymentSuccess) {
            logEvent("Payment successful for vehicle " + car.getCarId() + ": $" + 
                    String.format("%.2f", car.getPaymentAmount()));
        } else {
            logEvent("Payment failed for vehicle " + car.getCarId());
        }
        
        return paymentSuccess;
    }
    
    /**
     * Simulate gate malfunction
     */
    private boolean simulateGateMalfunction() {
        return ThreadLocalRandom.current().nextDouble() < malfunctionProbability;
    }
    
    /**
     * Handle gate malfunction scenario
     */
    private void handleGateMalfunction() throws InterruptedException {
        logEvent("MALFUNCTION: Gate experiencing technical difficulties");
        
        // Simulate malfunction recovery time
        int recoveryTime = ThreadLocalRandom.current().nextInt(2000, 8000); // 2-8 seconds
        Thread.sleep(recoveryTime);
        
        // Simulate recovery success/failure
        if (ThreadLocalRandom.current().nextDouble() < 0.9) { // 90% recovery success
            logEvent("Gate malfunction resolved - resuming normal operations");
        } else {
            logEvent("Gate malfunction persists - manual intervention required");
            // In real system, would alert maintenance
            Thread.sleep(5000); // Additional delay for manual intervention
            logEvent("Manual intervention completed - gate operational");
        }
    }
    
    /**
     * Simulate gate exit processing operations
     */
    private void simulateExitProcessing() throws InterruptedException {
        // Simulate various exit operations:
        // - Payment validation
        // - Barrier lifting
        // - Vehicle sensor confirmation
        // - Receipt printing
        
        int processingTime = ThreadLocalRandom.current().nextInt(processingTimeMin, processingTimeMax + 1);
        Thread.sleep(processingTime);
    }
    
    /**
     * Request shutdown of this exit gate
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
    public ExitGateStats getStats() {
        long avgProcessingTime = vehiclesProcessed.get() > 0 ? 
                totalProcessingTime.get() / vehiclesProcessed.get() : 0;
        
        double revenue = totalRevenue.get() / 100.0; // Convert cents to dollars
        
        return new ExitGateStats(
                gateId,
                gateName,
                vehiclesProcessed.get(),
                vehiclesExited.get(),
                paymentFailures.get(),
                avgProcessingTime,
                revenue,
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
     * Inner class for exit gate statistics
     */
    public static class ExitGateStats {
        private final int gateId;
        private final String gateName;
        private final int vehiclesProcessed;
        private final int vehiclesExited;
        private final int paymentFailures;
        private final long avgProcessingTime;
        private final double totalRevenue;
        private final boolean isOperating;
        
        public ExitGateStats(int gateId, String gateName, int processed, int exited, 
                           int failures, long avgTime, double revenue, boolean operating) {
            this.gateId = gateId;
            this.gateName = gateName;
            this.vehiclesProcessed = processed;
            this.vehiclesExited = exited;
            this.paymentFailures = failures;
            this.avgProcessingTime = avgTime;
            this.totalRevenue = revenue;
            this.isOperating = operating;
        }
        
        // Getters
        public int getGateId() { return gateId; }
        public String getGateName() { return gateName; }
        public int getVehiclesProcessed() { return vehiclesProcessed; }
        public int getVehiclesExited() { return vehiclesExited; }
        public int getPaymentFailures() { return paymentFailures; }
        public long getAvgProcessingTime() { return avgProcessingTime; }
        public double getTotalRevenue() { return totalRevenue; }
        public boolean isOperating() { return isOperating; }
        
        @Override
        public String toString() {
            return String.format("%s - Processed: %d, Exited: %d, Payment Failures: %d, Revenue: $%.2f, Avg Time: %dms, Operating: %s",
                    gateName, vehiclesProcessed, vehiclesExited, paymentFailures, totalRevenue, avgProcessingTime, isOperating);
        }
    }
}

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
import java.util.concurrent.locks.ReentrantLock;

/**
 * PaymentProcessor - Handles payment processing for parking fees
 * Manages concurrent payment operations with error handling and statistics
 */
public class PaymentProcessor {
    
    // Constants
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final int MAX_CONCURRENT_PAYMENTS = 5;
    
    // Concurrency controls
    private final Semaphore paymentSemaphore;
    private final ReentrantLock statisticsLock;
    private final ExecutorService paymentExecutor;
    
    // Statistics
    private final AtomicInteger totalPaymentsProcessed;
    private final AtomicInteger successfulPayments;
    private final AtomicInteger failedPayments;
    private final AtomicLong totalRevenue; // in cents to avoid floating point issues
    private final AtomicLong totalProcessingTime;
    
    // Payment system state
    private volatile boolean isOperating;
    private volatile PaymentSystemStatus systemStatus;
    
    // Error simulation
    private final double paymentFailureRate;
    private final double systemMalfunctionRate;
    
    /**
     * Constructor with default settings
     */
    public PaymentProcessor() {
        this(0.05, 0.02); // 5% payment failure, 2% system malfunction
    }
    
    /**
     * Constructor with custom error rates
     * @param paymentFailureRate probability of individual payment failure (0.0-1.0)
     * @param systemMalfunctionRate probability of system malfunction (0.0-1.0)
     */
    public PaymentProcessor(double paymentFailureRate, double systemMalfunctionRate) {
        // Initialize concurrency controls
        this.paymentSemaphore = new Semaphore(MAX_CONCURRENT_PAYMENTS, true);
        this.statisticsLock = new ReentrantLock();
        this.paymentExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "PaymentProcessor-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        });
        
        // Initialize statistics
        this.totalPaymentsProcessed = new AtomicInteger(0);
        this.successfulPayments = new AtomicInteger(0);
        this.failedPayments = new AtomicInteger(0);
        this.totalRevenue = new AtomicLong(0);
        this.totalProcessingTime = new AtomicLong(0);
        
        // System state
        this.isOperating = true;
        this.systemStatus = PaymentSystemStatus.OPERATIONAL;
        
        // Error rates
        this.paymentFailureRate = Math.max(0.0, Math.min(1.0, paymentFailureRate));
        this.systemMalfunctionRate = Math.max(0.0, Math.min(1.0, systemMalfunctionRate));
        
        logEvent("PaymentProcessor initialized - Max concurrent: " + MAX_CONCURRENT_PAYMENTS +
                ", Failure rate: " + String.format("%.1f%%", this.paymentFailureRate * 100) +
                ", Malfunction rate: " + String.format("%.1f%%", this.systemMalfunctionRate * 100));
        
        // Start system status monitor
        startSystemMonitor();
    }
    
    /**
     * Process payment for a vehicle (blocking operation)
     * @param car the car to process payment for
     * @return true if payment successful, false otherwise
     */
    public boolean processPayment(Car car) {
        if (!isOperating) {
            logEvent("Payment system not operational - rejecting payment for " + car.getCarId());
            return false;
        }
        
        String threadName = Thread.currentThread().getName();
        long startTime = System.currentTimeMillis();
        
        try {
            // Acquire payment processing permit
            logEvent(threadName + " - Requesting payment processing for " + car.getCarId());
            paymentSemaphore.acquire();
            
            try {
                return processPaymentInternal(car, threadName);
                
            } finally {
                paymentSemaphore.release();
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logEvent(threadName + " - Payment processing interrupted for " + car.getCarId());
            return false;
            
        } finally {
            long processingTime = System.currentTimeMillis() - startTime;
            totalProcessingTime.addAndGet(processingTime);
        }
    }
    
    /**
     * Internal payment processing logic
     */
    private boolean processPaymentInternal(Car car, String threadName) throws InterruptedException {
        int paymentNumber = totalPaymentsProcessed.incrementAndGet();
        
        logEvent(threadName + " - Processing payment #" + paymentNumber + " for " + car.getCarId() +
                " [" + car.getLicensePlate() + "]");
        
        // Check system status
        if (systemStatus != PaymentSystemStatus.OPERATIONAL) {
            logEvent(threadName + " - Payment system " + systemStatus + " - retrying...");
            
            // Wait for system to recover
            if (!waitForSystemRecovery()) {
                logEvent(threadName + " - Payment failed for " + car.getCarId() + " - system unavailable");
                failedPayments.incrementAndGet();
                return false;
            }
        }
        
        // Calculate payment amount
        double amount = car.calculatePaymentAmount();
        if (amount <= 0) {
            logEvent(threadName + " - No payment required for " + car.getCarId());
            car.setPaid(true);
            car.setPaymentAmount(0.0);
            successfulPayments.incrementAndGet();
            return true;
        }
        
        logEvent(threadName + " - Payment amount: $" + String.format("%.2f", amount) + 
                " for " + car.getCarId());
        
        // Simulate payment processing time
        simulatePaymentProcessing();
        
        // Simulate payment failures
        if (simulatePaymentFailure()) {
            logEvent(threadName + " - Payment FAILED for " + car.getCarId() + 
                    " - amount: $" + String.format("%.2f", amount));
            failedPayments.incrementAndGet();
            return false;
        }
        
        // Process successful payment
        car.setPaymentAmount(amount);
        car.setPaid(true);
        
        // Update revenue (convert to cents to avoid floating point issues)
        long amountInCents = Math.round(amount * 100);
        totalRevenue.addAndGet(amountInCents);
        successfulPayments.incrementAndGet();
        
        logEvent(threadName + " - Payment SUCCESSFUL for " + car.getCarId() + 
                " - amount: $" + String.format("%.2f", amount));
        
        return true;
    }
    
    /**
     * Process payment asynchronously
     * @param car the car to process payment for
     * @return Future representing the payment result
     */
    public Future<Boolean> processPaymentAsync(Car car) {
        if (!isOperating) {
            logEvent("Payment system not operational - rejecting async payment for " + car.getCarId());
            return CompletableFuture.completedFuture(false);
        }
        
        return paymentExecutor.submit(() -> processPayment(car));
    }
    
    /**
     * Simulate payment processing time
     */
    private void simulatePaymentProcessing() throws InterruptedException {
        // Simulate various payment operations:
        // - Card reading/processing
        // - Bank authorization
        // - Receipt generation
        // - System updates
        
        int processingTime = ThreadLocalRandom.current().nextInt(500, 3000); // 0.5-3 seconds
        Thread.sleep(processingTime);
    }
    
    /**
     * Simulate payment failure scenarios
     */
    private boolean simulatePaymentFailure() {
        return ThreadLocalRandom.current().nextDouble() < paymentFailureRate;
    }
    
    /**
     * Simulate system malfunction
     */
    private boolean simulateSystemMalfunction() {
        return ThreadLocalRandom.current().nextDouble() < systemMalfunctionRate;
    }
    
    /**
     * Wait for system to recover from malfunction
     */
    private boolean waitForSystemRecovery() throws InterruptedException {
        long maxWaitTime = 10000; // 10 seconds max wait
        long startTime = System.currentTimeMillis();
        
        while (systemStatus != PaymentSystemStatus.OPERATIONAL && 
               (System.currentTimeMillis() - startTime) < maxWaitTime) {
            Thread.sleep(1000); // Check every second
        }
        
        return systemStatus == PaymentSystemStatus.OPERATIONAL;
    }
    
    /**
     * Start system status monitoring thread
     */
    private void startSystemMonitor() {
        Thread monitorThread = new Thread(() -> {
            while (isOperating) {
                try {
                    Thread.sleep(5000); // Check every 5 seconds
                    
                    // Simulate system malfunctions
                    if (systemStatus == PaymentSystemStatus.OPERATIONAL && simulateSystemMalfunction()) {
                        triggerSystemMalfunction();
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "PaymentSystemMonitor");
        
        monitorThread.setDaemon(true);
        monitorThread.start();
    }
    
    /**
     * Trigger system malfunction scenario
     */
    private void triggerSystemMalfunction() {
        systemStatus = PaymentSystemStatus.MALFUNCTION;
        logEvent("SYSTEM MALFUNCTION: Payment system experiencing technical difficulties");
        
        // Simulate recovery time
        Thread recoveryThread = new Thread(() -> {
            try {
                int recoveryTime = ThreadLocalRandom.current().nextInt(3000, 15000); // 3-15 seconds
                Thread.sleep(recoveryTime);
                
                // Simulate recovery success/failure
                if (ThreadLocalRandom.current().nextDouble() < 0.9) { // 90% recovery success
                    systemStatus = PaymentSystemStatus.OPERATIONAL;
                    logEvent("SYSTEM RECOVERY: Payment system restored to operational status");
                } else {
                    systemStatus = PaymentSystemStatus.MAINTENANCE;
                    logEvent("SYSTEM MAINTENANCE: Manual intervention required");
                    
                    // Additional recovery time for maintenance
                    Thread.sleep(ThreadLocalRandom.current().nextInt(5000, 20000));
                    systemStatus = PaymentSystemStatus.OPERATIONAL;
                    logEvent("MAINTENANCE COMPLETE: Payment system operational");
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "PaymentSystemRecovery");
        
        recoveryThread.setDaemon(true);
        recoveryThread.start();
    }
    
    /**
     * Get current payment processing statistics
     */
    public PaymentStats getStats() {
        statisticsLock.lock();
        try {
            double revenue = totalRevenue.get() / 100.0; // Convert cents to dollars
            long avgProcessingTime = totalPaymentsProcessed.get() > 0 ? 
                    totalProcessingTime.get() / totalPaymentsProcessed.get() : 0;
            
            return new PaymentStats(
                    totalPaymentsProcessed.get(),
                    successfulPayments.get(),
                    failedPayments.get(),
                    revenue,
                    avgProcessingTime,
                    paymentSemaphore.availablePermits(),
                    systemStatus,
                    isOperating
            );
        } finally {
            statisticsLock.unlock();
        }
    }
    
    /**
     * Shutdown payment processor
     */
    public void shutdown() {
        logEvent("Shutting down payment processor");
        isOperating = false;
        
        paymentExecutor.shutdown();
        try {
            if (!paymentExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                paymentExecutor.shutdownNow();
                logEvent("Payment processor forced shutdown");
            }
        } catch (InterruptedException e) {
            paymentExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Final statistics
        PaymentStats finalStats = getStats();
        logEvent("FINAL PAYMENT STATISTICS: " + finalStats.toString());
    }
    
    /**
     * Check if payment processor is operating
     */
    public boolean isOperating() {
        return isOperating;
    }
    
    /**
     * Get current system status
     */
    public PaymentSystemStatus getSystemStatus() {
        return systemStatus;
    }
    
    /**
     * Utility method for consistent logging
     */
    private void logEvent(String message) {
        System.out.println("[" + LocalDateTime.now().format(TIME_FORMAT) + 
                          "] [PAYMENT] " + message);
    }
    
    /**
     * Enum for payment system status
     */
    public enum PaymentSystemStatus {
        OPERATIONAL("Operational"),
        MALFUNCTION("Malfunction"),
        MAINTENANCE("Under Maintenance"),
        OFFLINE("Offline");
        
        private final String description;
        
        PaymentSystemStatus(String description) {
            this.description = description;
        }
        
        @Override
        public String toString() {
            return description;
        }
    }
    
    /**
     * Inner class for payment statistics
     */
    public static class PaymentStats {
        private final int totalProcessed;
        private final int successful;
        private final int failed;
        private final double totalRevenue;
        private final long avgProcessingTime;
        private final int availableProcessors;
        private final PaymentSystemStatus systemStatus;
        private final boolean isOperating;
        
        public PaymentStats(int total, int success, int fail, double revenue, long avgTime,
                          int available, PaymentSystemStatus status, boolean operating) {
            this.totalProcessed = total;
            this.successful = success;
            this.failed = fail;
            this.totalRevenue = revenue;
            this.avgProcessingTime = avgTime;
            this.availableProcessors = available;
            this.systemStatus = status;
            this.isOperating = operating;
        }
        
        // Getters
        public int getTotalProcessed() { return totalProcessed; }
        public int getSuccessful() { return successful; }
        public int getFailed() { return failed; }
        public double getTotalRevenue() { return totalRevenue; }
        public long getAvgProcessingTime() { return avgProcessingTime; }
        public int getAvailableProcessors() { return availableProcessors; }
        public PaymentSystemStatus getSystemStatus() { return systemStatus; }
        public boolean isOperating() { return isOperating; }
        
        public double getSuccessRate() {
            return totalProcessed > 0 ? (double) successful / totalProcessed * 100 : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("Payment Stats - Total: %d, Success: %d (%.1f%%), Failed: %d, Revenue: $%.2f, Avg Time: %dms, Available: %d, Status: %s, Operating: %s",
                    totalProcessed, successful, getSuccessRate(), failed, totalRevenue, avgProcessingTime, availableProcessors, systemStatus, isOperating);
        }
    }
}

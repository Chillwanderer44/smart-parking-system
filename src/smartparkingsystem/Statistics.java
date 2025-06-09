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
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * Statistics - Thread-safe collector for parking system statistics
 * Aggregates data from all system components and provides comprehensive reporting
 */
public class Statistics {
    
    // Constants
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    
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
    
    // Concurrency controls
    private final ReadWriteLock statisticsLock;
    
    // Vehicle statistics
    private final AtomicInteger totalVehiclesGenerated;
    private final AtomicInteger totalVehiclesEntered;
    private final AtomicInteger totalVehiclesExited;
    private final AtomicInteger currentlyParked;
    private final AtomicInteger totalPaymentFailures;
    
    // Revenue statistics
    private final AtomicLong totalRevenue; // in cents
    private final AtomicInteger paidVehicles;
    
    // Timing statistics
    private final ConcurrentHashMap<String, Long> vehicleWaitTimes; // carId -> wait time in ms
    private final ConcurrentHashMap<String, Long> vehicleParkingDurations; // carId -> duration in minutes
    private final AtomicLong totalSystemRunTime;
    private final LocalDateTime systemStartTime;
    
    // Peak usage tracking
    private final AtomicInteger peakOccupancy;
    private final AtomicInteger peakWaitingQueue;
    private volatile LocalDateTime peakOccupancyTime;
    private volatile LocalDateTime peakWaitingTime;
    
    // Gate statistics
    private final ConcurrentHashMap<String, AtomicInteger> gateProcessingCounts;
    private final ConcurrentHashMap<String, AtomicLong> gateProcessingTimes;
    
    // Error tracking
    private final AtomicInteger systemErrors;
    private final AtomicInteger paymentSystemMalfunctions;
    private final AtomicInteger gateBarrierMalfunctions;
    private final ConcurrentLinkedQueue<ErrorRecord> errorLog;
    
    // Periodic statistics
    private final ScheduledExecutorService statisticsReporter;
    private volatile boolean isCollecting;
    
    /**
     * Constructor
     */
    public Statistics() {
        // Initialize concurrency controls
        this.statisticsLock = new ReentrantReadWriteLock(true);
        
        // Initialize vehicle statistics
        this.totalVehiclesGenerated = new AtomicInteger(0);
        this.totalVehiclesEntered = new AtomicInteger(0);
        this.totalVehiclesExited = new AtomicInteger(0);
        this.currentlyParked = new AtomicInteger(0);
        this.totalPaymentFailures = new AtomicInteger(0);
        
        // Initialize revenue statistics
        this.totalRevenue = new AtomicLong(0);
        this.paidVehicles = new AtomicInteger(0);
        
        // Initialize timing statistics
        this.vehicleWaitTimes = new ConcurrentHashMap<String, Long>();
        this.vehicleParkingDurations = new ConcurrentHashMap<String, Long>();
        this.totalSystemRunTime = new AtomicLong(0);
        this.systemStartTime = LocalDateTime.now();
        
        // Initialize peak tracking
        this.peakOccupancy = new AtomicInteger(0);
        this.peakWaitingQueue = new AtomicInteger(0);
        this.peakOccupancyTime = LocalDateTime.now();
        this.peakWaitingTime = LocalDateTime.now();
        
        // Initialize gate statistics
        this.gateProcessingCounts = new ConcurrentHashMap<String, AtomicInteger>();
        this.gateProcessingTimes = new ConcurrentHashMap<String, AtomicLong>();
        
        // Initialize error tracking
        this.systemErrors = new AtomicInteger(0);
        this.paymentSystemMalfunctions = new AtomicInteger(0);
        this.gateBarrierMalfunctions = new AtomicInteger(0);
        this.errorLog = new ConcurrentLinkedQueue<ErrorRecord>();
        
        // Initialize reporting
        this.statisticsReporter = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "StatisticsReporter");
                t.setDaemon(true);
                return t;
            }
        });
        this.isCollecting = true;
        
        logEvent("Statistics collection system initialized");
    }
    
    /**
     * Start periodic statistics reporting
     */
    public void startPeriodicReporting(int intervalMinutes) {
        statisticsReporter.scheduleAtFixedRate(
                new Runnable() {
                    @Override
                    public void run() {
                        generatePeriodicReport();
                    }
                },
                intervalMinutes,
                intervalMinutes,
                TimeUnit.MINUTES
        );
        
        logEvent("Periodic reporting started - interval: " + intervalMinutes + " minutes");
    }
    
    /**
     * Record vehicle generation
     */
    public void recordVehicleGenerated() {
        totalVehiclesGenerated.incrementAndGet();
    }
    
    /**
     * Record vehicle entry
     */
    public void recordVehicleEntry(Car car, long waitTimeMs) {
        totalVehiclesEntered.incrementAndGet();
        int parked = currentlyParked.incrementAndGet();
        
        // Record wait time
        if (waitTimeMs >= 0) {
            vehicleWaitTimes.put(car.getCarId(), waitTimeMs);
        }
        
        // Update peak occupancy
        updatePeakOccupancy(parked);
        
        logEvent("Vehicle entry recorded: " + car.getCarId() + 
                " (Wait: " + waitTimeMs + "ms, Current parked: " + parked + ")");
    }
    
    /**
     * Record vehicle exit
     */
    public void recordVehicleExit(Car car) {
        totalVehiclesExited.incrementAndGet();
        currentlyParked.decrementAndGet();
        
        // Record parking duration
        long duration = car.getActualParkingDuration();
        if (duration > 0) {
            vehicleParkingDurations.put(car.getCarId(), duration);
        }
        
        // Record payment information
        if (car.isPaid()) {
            paidVehicles.incrementAndGet();
            long revenueInCents = Math.round(car.getPaymentAmount() * 100);
            totalRevenue.addAndGet(revenueInCents);
        }
        
        logEvent("Vehicle exit recorded: " + car.getCarId() + 
                " (Duration: " + duration + " min, Paid: " + car.isPaid() + ")");
    }
    
    /**
 * Record gate processing statistics
 */
public void recordGateProcessing(String gateName, long processingTimeMs) {
    
    if (!gateProcessingCounts.containsKey(gateName)) {
        gateProcessingCounts.put(gateName, new AtomicInteger(0));
    }
    gateProcessingCounts.get(gateName).incrementAndGet();
    
    if (!gateProcessingTimes.containsKey(gateName)) {
        gateProcessingTimes.put(gateName, new AtomicLong(0));
    }
    gateProcessingTimes.get(gateName).addAndGet(processingTimeMs);
}
    
    /**
     * Record payment failure
     */
    public void recordPaymentFailure(Car car, String reason) {
        totalPaymentFailures.incrementAndGet();
        recordError("PAYMENT_FAILURE", "Payment failed for " + car.getCarId() + ": " + reason);
    }
    
    /**
     * Record system error
     */
    public void recordError(String errorType, String description) {
        systemErrors.incrementAndGet();
        
        ErrorRecord error = new ErrorRecord(
                LocalDateTime.now(),
                errorType,
                description,
                Thread.currentThread().getName()
        );
        
        errorLog.offer(error);
        
        // Keep error log size manageable
        while (errorLog.size() > 1000) {
            errorLog.poll();
        }
        
        logEvent("ERROR RECORDED: " + errorType + " - " + description);
    }
    
    /**
     * Update peak occupancy tracking
     */
    private void updatePeakOccupancy(int currentOccupancy) {
        int currentPeak = peakOccupancy.get();
        if (currentOccupancy > currentPeak) {
            if (peakOccupancy.compareAndSet(currentPeak, currentOccupancy)) {
                peakOccupancyTime = LocalDateTime.now();
            }
        }
    }
    
    /**
     * Update peak waiting queue tracking
     */
    public void updatePeakWaitingQueue(int queueSize) {
        int currentPeak = peakWaitingQueue.get();
        if (queueSize > currentPeak) {
            if (peakWaitingQueue.compareAndSet(currentPeak, queueSize)) {
                peakWaitingTime = LocalDateTime.now();
            }
        }
    }
    
    /**
     * Calculate average waiting time
     */
    public double getAverageWaitingTime() {
        if (vehicleWaitTimes.isEmpty()) return 0.0;
        
        long totalWaitTime = 0;
        for (Long waitTime : vehicleWaitTimes.values()) {
            totalWaitTime += waitTime;
        }
        
        return (double) totalWaitTime / vehicleWaitTimes.size() / 1000.0; // Convert to seconds
    }
    
    /**
     * Calculate average parking duration
     */
    public double getAverageParkingDuration() {
        if (vehicleParkingDurations.isEmpty()) return 0.0;
        
        long totalDuration = 0;
        for (Long duration : vehicleParkingDurations.values()) {
            totalDuration += duration;
        }
        
        return (double) totalDuration / vehicleParkingDurations.size(); // In minutes
    }
    
    /**
     * Get total revenue
     */
    public double getTotalRevenue() {
        return totalRevenue.get() / 100.0; // Convert cents to dollars
    }
    
    /**
     * Generate comprehensive statistics report
     */
    public SystemStatistics generateFinalReport() {
        statisticsLock.readLock().lock();
        try {
            // Calculate system runtime
            long runtimeMinutes = java.time.Duration.between(systemStartTime, LocalDateTime.now()).toMinutes();
            
            // Calculate efficiency metrics
            double entryEfficiency = totalVehiclesGenerated.get() > 0 ? 
                    (double) totalVehiclesEntered.get() / totalVehiclesGenerated.get() * 100 : 0.0;
            
            double paymentSuccessRate = totalVehiclesExited.get() > 0 ? 
                    (double) paidVehicles.get() / totalVehiclesExited.get() * 100 : 0.0;
            
            // Create comprehensive report
            return new SystemStatistics(
                    // Vehicle statistics
                    totalVehiclesGenerated.get(),
                    totalVehiclesEntered.get(),
                    totalVehiclesExited.get(),
                    currentlyParked.get(),
                    
                    // Timing statistics
                    getAverageWaitingTime(),
                    getAverageParkingDuration(),
                    runtimeMinutes,
                    
                    // Revenue statistics
                    getTotalRevenue(),
                    paidVehicles.get(),
                    totalPaymentFailures.get(),
                    paymentSuccessRate,
                    
                    // Peak statistics
                    peakOccupancy.get(),
                    peakOccupancyTime,
                    peakWaitingQueue.get(),
                    peakWaitingTime,
                    
                    // Efficiency metrics
                    entryEfficiency,
                    
                    // Error statistics
                    systemErrors.get(),
                    new ArrayList<ErrorRecord>(errorLog),
                    
                    // Gate statistics
                    new HashMap<String, AtomicInteger>(gateProcessingCounts),
                    new HashMap<String, AtomicLong>(gateProcessingTimes)
            );
            
        } finally {
            statisticsLock.readLock().unlock();
        }
    }
    
    /**
     * Generate periodic report (called by scheduled executor)
     */
    private void generatePeriodicReport() {
        if (!isCollecting) return;
        
        logEvent("=== PERIODIC STATISTICS REPORT ===");
        
        SystemStatistics stats = generateFinalReport();
        
        logEvent("Vehicles: Generated=" + stats.totalGenerated + 
                ", Entered=" + stats.totalEntered + 
                ", Exited=" + stats.totalExited + 
                ", Currently Parked=" + stats.currentlyParked);
        
        logEvent("Timing: Avg Wait=" + String.format("%.2f", stats.averageWaitingTime) + "s" +
                ", Avg Parking=" + String.format("%.2f", stats.averageParkingDuration) + " min");
        
        logEvent("Revenue: Total=$" + String.format("%.2f", stats.totalRevenue) +
                ", Paid Vehicles=" + stats.paidVehicles +
                ", Payment Success=" + String.format("%.1f%%", stats.paymentSuccessRate));
        
        logEvent("Peak Usage: Occupancy=" + stats.peakOccupancy + 
                " at " + stats.peakOccupancyTime.format(TIME_FORMAT) +
                ", Queue=" + stats.peakWaitingQueue +
                " at " + stats.peakWaitingTime.format(TIME_FORMAT));
        
        logEvent("Errors: Total=" + stats.totalErrors + 
                ", Payment Failures=" + totalPaymentFailures.get());
        
        logEvent("=== END PERIODIC REPORT ===");
    }
    
    /**
     * Print comprehensive final statistics
     */
    public void printFinalStatistics() {
        SystemStatistics stats = generateFinalReport();
        
        System.out.println("\n" + repeatString("=", 80));
        System.out.println("           SMART PARKING SYSTEM - FINAL STATISTICS REPORT");
        System.out.println(repeatString("=", 80));
        
        // Vehicle Statistics
        System.out.println("\n🚗 VEHICLE STATISTICS:");
        System.out.println("  Total Vehicles Generated: " + stats.totalGenerated);
        System.out.println("  Vehicles Entered System:  " + stats.totalEntered + 
                          " (" + String.format("%.1f%%", stats.entryEfficiency) + " entry rate)");
        System.out.println("  Vehicles Successfully Exited: " + stats.totalExited);
        System.out.println("  Currently Parked: " + stats.currentlyParked);
        
        // Timing Statistics
        System.out.println("\n⏱️  TIMING STATISTICS:");
        System.out.println("  Average Waiting Time: " + String.format("%.2f", stats.averageWaitingTime) + " seconds");
        System.out.println("  Average Parking Duration: " + String.format("%.2f", stats.averageParkingDuration) + " minutes");
        System.out.println("  Total System Runtime: " + stats.systemRuntimeMinutes + " minutes");
        
        // Revenue Statistics
        System.out.println("\n💰 REVENUE STATISTICS:");
        System.out.println("  Total Revenue Collected: $" + String.format("%.2f", stats.totalRevenue));
        System.out.println("  Vehicles Paid: " + stats.paidVehicles + " / " + stats.totalExited);
        System.out.println("  Payment Success Rate: " + String.format("%.1f%%", stats.paymentSuccessRate));
        System.out.println("  Payment Failures: " + totalPaymentFailures.get());
        
        if (stats.totalExited > 0) {
            double avgRevenuePerVehicle = stats.totalRevenue / stats.totalExited;
            System.out.println("  Average Revenue per Vehicle: $" + String.format("%.2f", avgRevenuePerVehicle));
        }
        
        // Peak Usage Statistics
        System.out.println("\n📊 PEAK USAGE STATISTICS:");
        System.out.println("  Peak Occupancy: " + stats.peakOccupancy + " vehicles at " + 
                          stats.peakOccupancyTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        System.out.println("  Peak Waiting Queue: " + stats.peakWaitingQueue + " vehicles at " + 
                          stats.peakWaitingTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        
        // Gate Performance
        System.out.println("\n🚪 GATE PERFORMANCE:");
        for (Map.Entry<String, AtomicInteger> entry : stats.gateProcessingCounts.entrySet()) {
            String gateName = entry.getKey();
            int count = entry.getValue().get();
            AtomicLong totalTimeAtomic = stats.gateProcessingTimes.get(gateName);
            long totalTime = totalTimeAtomic != null ? totalTimeAtomic.get() : 0;
            double avgTime = count > 0 ? (double) totalTime / count : 0;
            
            System.out.println("  " + gateName + ": " + count + " vehicles processed" +
                              " (avg: " + String.format("%.2f", avgTime) + "ms)");
        }
        
        // Error Statistics
        System.out.println("\n⚠️  ERROR STATISTICS:");
        System.out.println("  Total System Errors: " + stats.totalErrors);
        System.out.println("  Payment Failures: " + totalPaymentFailures.get());
        
        if (!stats.errorLog.isEmpty()) {
            System.out.println("\n  Recent Errors:");
            List<ErrorRecord> errorList = new ArrayList<ErrorRecord>(stats.errorLog);
            int startIndex = Math.max(0, errorList.size() - 5);
            for (int i = startIndex; i < errorList.size(); i++) {
                System.out.println("    " + errorList.get(i).toString());
            }
        }
        
        // System Efficiency Summary
        System.out.println("\n📈 EFFICIENCY SUMMARY:");
        System.out.println("  Entry Success Rate: " + String.format("%.1f%%", stats.entryEfficiency));
        System.out.println("  Payment Success Rate: " + String.format("%.1f%%", stats.paymentSuccessRate));
        
        if (stats.systemRuntimeMinutes > 0) {
            double vehiclesPerHour = (double) stats.totalEntered / (stats.systemRuntimeMinutes / 60.0);
            System.out.println("  Throughput: " + String.format("%.1f", vehiclesPerHour) + " vehicles/hour");
        }
        
        System.out.println("\n" + repeatString("=", 80));
        System.out.println("                    END OF STATISTICS REPORT");
        System.out.println(repeatString("=", 80) + "\n");
    }
    
    /**
     * Stop statistics collection and reporting
     */
    public void shutdown() {
        logEvent("Shutting down statistics collection");
        isCollecting = false;
        
        statisticsReporter.shutdown();
        try {
            if (!statisticsReporter.awaitTermination(5, TimeUnit.SECONDS)) {
                statisticsReporter.shutdownNow();
            }
        } catch (InterruptedException e) {
            statisticsReporter.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logEvent("Statistics collection shutdown complete");
    }
    
    /**
     * Get current statistics snapshot
     */
    public SystemStatistics getCurrentStats() {
        return generateFinalReport();
    }
    
    /**
     * Utility method for consistent logging
     */
    private void logEvent(String message) {
        System.out.println("[" + LocalDateTime.now().format(TIME_FORMAT) + 
                          "] [STATS] " + message);
    }
    
    /**
     * Inner class for error records
     */
    public static class ErrorRecord {
        private final LocalDateTime timestamp;
        private final String errorType;
        private final String description;
        private final String threadName;
        
        public ErrorRecord(LocalDateTime timestamp, String errorType, String description, String threadName) {
            this.timestamp = timestamp;
            this.errorType = errorType;
            this.description = description;
            this.threadName = threadName;
        }
        
        // Getters
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getErrorType() { return errorType; }
        public String getDescription() { return description; }
        public String getThreadName() { return threadName; }
        
        @Override
        public String toString() {
            return String.format("[%s] %s in %s: %s", 
                    timestamp.format(TIME_FORMAT), errorType, threadName, description);
        }
    }
    
    /**
     * Comprehensive system statistics container
     */
    public static class SystemStatistics {
        // Vehicle statistics
        public final int totalGenerated;
        public final int totalEntered;
        public final int totalExited;
        public final int currentlyParked;
        
        // Timing statistics
        public final double averageWaitingTime;
        public final double averageParkingDuration;
        public final long systemRuntimeMinutes;
        
        // Revenue statistics
        public final double totalRevenue;
        public final int paidVehicles;
        public final int paymentFailures;
        public final double paymentSuccessRate;
        
        // Peak statistics
        public final int peakOccupancy;
        public final LocalDateTime peakOccupancyTime;
        public final int peakWaitingQueue;
        public final LocalDateTime peakWaitingTime;
        
        // Efficiency metrics
        public final double entryEfficiency;
        
        // Error statistics
        public final int totalErrors;
        public final List<ErrorRecord> errorLog;
        
        // Gate statistics
        public final Map<String, AtomicInteger> gateProcessingCounts;
        public final Map<String, AtomicLong> gateProcessingTimes;
        
        public SystemStatistics(int totalGenerated, int totalEntered, int totalExited, int currentlyParked,
                              double averageWaitingTime, double averageParkingDuration, long systemRuntimeMinutes,
                              double totalRevenue, int paidVehicles, int paymentFailures, double paymentSuccessRate,
                              int peakOccupancy, LocalDateTime peakOccupancyTime, int peakWaitingQueue, LocalDateTime peakWaitingTime,
                              double entryEfficiency, int totalErrors, List<ErrorRecord> errorLog,
                              Map<String, AtomicInteger> gateProcessingCounts, Map<String, AtomicLong> gateProcessingTimes) {
            
            this.totalGenerated = totalGenerated;
            this.totalEntered = totalEntered;
            this.totalExited = totalExited;
            this.currentlyParked = currentlyParked;
            this.averageWaitingTime = averageWaitingTime;
            this.averageParkingDuration = averageParkingDuration;
            this.systemRuntimeMinutes = systemRuntimeMinutes;
            this.totalRevenue = totalRevenue;
            this.paidVehicles = paidVehicles;
            this.paymentFailures = paymentFailures;
            this.paymentSuccessRate = paymentSuccessRate;
            this.peakOccupancy = peakOccupancy;
            this.peakOccupancyTime = peakOccupancyTime;
            this.peakWaitingQueue = peakWaitingQueue;
            this.peakWaitingTime = peakWaitingTime;
            this.entryEfficiency = entryEfficiency;
            this.totalErrors = totalErrors;
            this.errorLog = errorLog;
            this.gateProcessingCounts = gateProcessingCounts;
            this.gateProcessingTimes = gateProcessingTimes;
        }
    }
}

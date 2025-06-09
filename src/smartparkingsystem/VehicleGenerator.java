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

/**
 * VehicleGenerator - Creates and manages the arrival of 150 vehicles
 * Simulates realistic vehicle arrival patterns with congestion scenarios
 */
public class VehicleGenerator {
    
    // Constants
    private static final int TOTAL_VEHICLES = 150;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    
    // Vehicle management
    private final BlockingQueue<Car> vehicleQueue;
    private final List<Car> allVehicles;
    private final AtomicInteger vehicleCounter;
    private final AtomicInteger generatedCount;
    
    // Timing control
    private final int simulationDurationMinutes;
    private volatile boolean isGenerating;
    private ExecutorService generatorExecutor;
    
    /**
     * Constructor
     * @param simulationDurationMinutes How long the simulation should run
     */
    public VehicleGenerator(int simulationDurationMinutes) {
        this.simulationDurationMinutes = simulationDurationMinutes;
        this.vehicleQueue = new LinkedBlockingQueue<>();
        this.allVehicles = new CopyOnWriteArrayList<>();
        this.vehicleCounter = new AtomicInteger(1);
        this.generatedCount = new AtomicInteger(0);
        this.isGenerating = false;
        
        logEvent("VehicleGenerator initialized for " + TOTAL_VEHICLES + " vehicles over " 
                + simulationDurationMinutes + " minutes");
    }
    
    /**
     * Start generating vehicles with realistic arrival patterns
     */
    public void startGeneration() {
        if (isGenerating) {
            logEvent("Vehicle generation already running");
            return;
        }
        
        isGenerating = true;
        generatorExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "VehicleGenerator");
            t.setDaemon(true);
            return t;
        });
        
        generatorExecutor.submit(this::generateVehicles);
        logEvent("Vehicle generation started");
    }
    
    /**
     * Main vehicle generation logic
     */
    private void generateVehicles() {
        try {
            // Calculate arrival intervals
            long totalSimulationMs = simulationDurationMinutes * 60 * 1000L;
            
            // Create different arrival patterns for congestion simulation
            generateInitialRush();      // First 30 vehicles quickly (congestion)
            generateSteadyFlow();       // Next 90 vehicles at regular intervals
            generateFinalRush();        // Last 30 vehicles in bursts
            
            logEvent("All " + TOTAL_VEHICLES + " vehicles generated successfully");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logEvent("Vehicle generation interrupted");
        } finally {
            isGenerating = false;
        }
    }
    
    /**
     * Generate initial rush of vehicles to create congestion
     */
    private void generateInitialRush() throws InterruptedException {
        logEvent("Starting initial rush - generating 30 vehicles quickly");
        
        for (int i = 0; i < 30 && isGenerating; i++) {
            Car car = createVehicle();
            addVehicleToQueue(car);
            
            // Short intervals for congestion (1-3 seconds)
            Thread.sleep(ThreadLocalRandom.current().nextLong(1000, 3001));
        }
        
        logEvent("Initial rush completed - " + generatedCount.get() + " vehicles generated");
    }
    
    /**
     * Generate steady flow of vehicles
     */
    private void generateSteadyFlow() throws InterruptedException {
        logEvent("Starting steady flow - generating 90 vehicles at regular intervals");
        
        // Calculate interval for steady flow (remaining time / remaining vehicles)
        long remainingTimeMs = (simulationDurationMinutes * 60 * 1000L) * 60 / 100; // 60% of time
        long baseInterval = remainingTimeMs / 90;
        
        for (int i = 0; i < 90 && isGenerating; i++) {
            Car car = createVehicle();
            addVehicleToQueue(car);
            
            // Regular intervals with some randomness
            long interval = (long) (baseInterval * (0.5 + ThreadLocalRandom.current().nextDouble()));
            Thread.sleep(interval);
        }
        
        logEvent("Steady flow completed - " + generatedCount.get() + " vehicles generated");
    }
    
    /**
     * Generate final rush of vehicles
     */
    private void generateFinalRush() throws InterruptedException {
        logEvent("Starting final rush - generating 30 vehicles in bursts");
        
        // Generate in 3 bursts of 10 vehicles each
        for (int burst = 0; burst < 3 && isGenerating; burst++) {
            logEvent("Final rush burst " + (burst + 1) + " starting");
            
            // Generate 10 vehicles quickly
            for (int i = 0; i < 10 && isGenerating; i++) {
                Car car = createVehicle();
                addVehicleToQueue(car);
                
                // Very short intervals for rush effect
                Thread.sleep(ThreadLocalRandom.current().nextLong(500, 2001));
            }
            
            // Pause between bursts
            if (burst < 2) {
                Thread.sleep(ThreadLocalRandom.current().nextLong(10000, 20001)); // 10-20 seconds
            }
        }
        
        logEvent("Final rush completed - " + generatedCount.get() + " vehicles generated");
    }
    
    /**
     * Create a new vehicle with realistic data
     */
    private Car createVehicle() {
        int carNumber = vehicleCounter.getAndIncrement();
        String carId = String.format("CAR-%03d", carNumber);
        String licensePlate = generateLicensePlate(carNumber);
        String ownerName = generateOwnerName(carNumber);
        
        return new Car(carId, licensePlate, ownerName);
    }
    
    /**
     * Add vehicle to queue and tracking lists
     */
    private void addVehicleToQueue(Car car) {
        try {
            vehicleQueue.put(car);
            allVehicles.add(car);
            int count = generatedCount.incrementAndGet();
            
            logEvent("Vehicle " + car.getCarId() + " [" + car.getLicensePlate() + 
                    "] generated (" + count + "/" + TOTAL_VEHICLES + ")");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logEvent("Failed to add vehicle " + car.getCarId() + " to queue");
        }
    }
    
    /**
     * Generate realistic license plate
     */
    private String generateLicensePlate(int carNumber) {
        String[] states = {"WVA", "KL", "JHR", "PNG", "SBH", "SWK"};
        String state = states[carNumber % states.length];
        int number = 1000 + (carNumber % 9000);
        char letter = (char) ('A' + (carNumber % 26));
        
        return String.format("%s%d%c", state, number, letter);
    }
    
    /**
     * Generate random owner name
     */
    private String generateOwnerName(int carNumber) {
        String[] firstNames = {"Ahmad", "Siti", "Raj", "Mei", "Kumar", "Fatimah", 
                               "Chen", "Aisha", "David", "Priya", "Hassan", "Lisa"};
        String[] lastNames = {"Abdullah", "Wong", "Singh", "Tan", "Ali", "Lim", 
                              "Rahman", "Chong", "Kumar", "Lee", "Ismail", "Ng"};
        
        String firstName = firstNames[carNumber % firstNames.length];
        String lastName = lastNames[(carNumber * 7) % lastNames.length];
        
        return firstName + " " + lastName;
    }
    
    /**
     * Get the next vehicle from queue (blocking call)
     * @return next vehicle or null if interrupted
     */
    public Car getNextVehicle() {
        try {
            return vehicleQueue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
    
    /**
     * Get the next vehicle with timeout
     * @param timeout timeout value
     * @param unit time unit
     * @return next vehicle or null if timeout/interrupted
     */
    public Car getNextVehicle(long timeout, TimeUnit unit) {
        try {
            return vehicleQueue.poll(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
    
    /**
     * Stop vehicle generation
     */
    public void stopGeneration() {
        isGenerating = false;
        if (generatorExecutor != null && !generatorExecutor.isShutdown()) {
            generatorExecutor.shutdown();
            try {
                if (!generatorExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    generatorExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                generatorExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        logEvent("Vehicle generation stopped");
    }
    
    /**
     * Get current generation statistics
     */
    public GenerationStats getStats() {
        return new GenerationStats(
                TOTAL_VEHICLES,
                generatedCount.get(),
                vehicleQueue.size(),
                allVehicles.size(),
                isGenerating
        );
    }
    
    /**
     * Get all generated vehicles (defensive copy)
     */
    public List<Car> getAllVehicles() {
        return new ArrayList<>(allVehicles);
    }
    
    /**
     * Check if there are vehicles waiting in queue
     */
    public boolean hasVehiclesWaiting() {
        return !vehicleQueue.isEmpty();
    }
    
    /**
     * Get current queue size
     */
    public int getQueueSize() {
        return vehicleQueue.size();
    }
    
    /**
     * Check if generation is complete
     */
    public boolean isGenerationComplete() {
        return generatedCount.get() >= TOTAL_VEHICLES && !isGenerating;
    }
    
    /**
     * Utility method for consistent logging
     */
    private void logEvent(String message) {
        System.out.println("[" + LocalDateTime.now().format(TIME_FORMAT) + 
                          "] [VEHICLE_GEN] " + message);
    }
    
    /**
     * Inner class for generation statistics
     */
    public static class GenerationStats {
        private final int totalVehicles;
        private final int generatedCount;
        private final int queueSize;
        private final int trackedVehicles;
        private final boolean isGenerating;
        
        public GenerationStats(int total, int generated, int queue, int tracked, boolean generating) {
            this.totalVehicles = total;
            this.generatedCount = generated;
            this.queueSize = queue;
            this.trackedVehicles = tracked;
            this.isGenerating = generating;
        }
        
        // Getters
        public int getTotalVehicles() { return totalVehicles; }
        public int getGeneratedCount() { return generatedCount; }
        public int getQueueSize() { return queueSize; }
        public int getTrackedVehicles() { return trackedVehicles; }
        public boolean isGenerating() { return isGenerating; }
        
        @Override
        public String toString() {
            return String.format("Generation Stats - Total: %d, Generated: %d, Queue: %d, Tracked: %d, Active: %s",
                    totalVehicles, generatedCount, queueSize, trackedVehicles, isGenerating);
        }
    }
}

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package smartparkingsystem;

/**
 *
 * @author amiryusof
 */

import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * ParkingLot - Core shared resource that manages parking spaces concurrently
 * Handles space allocation, de-allocation, and maintains thread-safe operations
 */
public class ParkingLot {
    
    // Constants
    private static final int TOTAL_SPACES = 50;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    
    // Concurrency controls
    private final Semaphore availableSpaces;           // Controls access to parking spaces
    private final ConcurrentHashMap<Integer, Car> occupiedSpaces;  // Thread-safe space tracking
    private final ReentrantReadWriteLock spaceLock;    // For space allocation operations
    private final Lock readLock;  // Changed from ReadWriteLock.ReadLock
    private final Lock writeLock; // Changed from ReadWriteLock.WriteLock
    
    // Space management
    private final boolean[] spaceStatus;               // true = occupied, false = available
    private volatile int nextAvailableSpace;           // Hint for next space to check
    
    /**
     * Constructor - Initialize the parking lot with 50 spaces
     */
    public ParkingLot() {
        this.availableSpaces = new Semaphore(TOTAL_SPACES, true); // Fair semaphore
        this.occupiedSpaces = new ConcurrentHashMap<Integer, Car>();
        this.spaceLock = new ReentrantReadWriteLock(true); // Fair lock
        this.readLock = spaceLock.readLock();   // Fixed: Use Lock interface
        this.writeLock = spaceLock.writeLock(); // Fixed: Use Lock interface
        this.spaceStatus = new boolean[TOTAL_SPACES];
        this.nextAvailableSpace = 0;
        
        logEvent("ParkingLot initialized with " + TOTAL_SPACES + " spaces");
    }
    
    /**
     * Attempt to park a car - blocking operation if no spaces available
     * @param car The car attempting to park
     * @return parking space number if successful, -1 if interrupted
     */
    public int parkCar(Car car) {
        String threadName = Thread.currentThread().getName();
        
        try {
            // Wait for available space (blocking call)
            logEvent(threadName + " - Car " + car.getCarId() + " waiting for parking space...");
            availableSpaces.acquire();
            
            // Allocate specific space
            int spaceNumber = allocateSpace(car);
            if (spaceNumber != -1) {
                car.setParkingTime(LocalDateTime.now());
                car.setSpaceNumber(spaceNumber);
                
                logEvent(threadName + " - Car " + car.getCarId() + " parked in space " + spaceNumber + 
                        " (Available spaces: " + availableSpaces.availablePermits() + ")");
                return spaceNumber;
            } else {
                // This shouldn't happen if semaphore is working correctly
                availableSpaces.release();
                logEvent(threadName + " - ERROR: No space found for car " + car.getCarId());
                return -1;
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logEvent(threadName + " - Car " + car.getCarId() + " parking interrupted");
            return -1;
        }
    }
    
    /**
     * Allocate a specific parking space
     * @param car The car to park
     * @return space number or -1 if no space available
     */
    private int allocateSpace(Car car) {
        writeLock.lock();
        try {
            // Find first available space starting from hint
            for (int i = 0; i < TOTAL_SPACES; i++) {
                int spaceIndex = (nextAvailableSpace + i) % TOTAL_SPACES;
                
                if (!spaceStatus[spaceIndex]) {
                    // Allocate this space
                    spaceStatus[spaceIndex] = true;
                    occupiedSpaces.put(spaceIndex, car);
                    
                    // Update hint for next allocation
                    nextAvailableSpace = (spaceIndex + 1) % TOTAL_SPACES;
                    
                    return spaceIndex;
                }
            }
            return -1; // No space found
            
        } finally {
            writeLock.unlock();
        }
    }
    
    /**
     * Remove a car from parking lot
     * @param car The car exiting
     * @return true if successful, false otherwise
     */
    public boolean removeCar(Car car) {
        String threadName = Thread.currentThread().getName();
        
        if (car.getSpaceNumber() == -1) {
            logEvent(threadName + " - ERROR: Car " + car.getCarId() + " has no assigned space");
            return false;
        }
        
        writeLock.lock();
        try {
            int spaceNumber = car.getSpaceNumber();
            
            // Verify car is in the space
            Car parkedCar = occupiedSpaces.get(spaceNumber);
            if (parkedCar == null || !parkedCar.getCarId().equals(car.getCarId())) {
                logEvent(threadName + " - ERROR: Car " + car.getCarId() + 
                        " not found in space " + spaceNumber);
                return false;
            }
            
            // Free the space
            spaceStatus[spaceNumber] = false;
            occupiedSpaces.remove(spaceNumber);
            car.setExitTime(LocalDateTime.now());
            
            // Release semaphore permit
            availableSpaces.release();
            
            logEvent(threadName + " - Car " + car.getCarId() + " removed from space " + spaceNumber + 
                    " (Available spaces: " + availableSpaces.availablePermits() + ")");
            
            return true;
            
        } finally {
            writeLock.unlock();
        }
    }
    
    /**
     * Get current parking lot status
     * @return status information
     */
    public ParkingStatus getStatus() {
        readLock.lock();
        try {
            int occupied = TOTAL_SPACES - availableSpaces.availablePermits();
            int available = availableSpaces.availablePermits();
            int waitingThreads = availableSpaces.getQueueLength();
            
            return new ParkingStatus(TOTAL_SPACES, occupied, available, waitingThreads);
            
        } finally {
            readLock.unlock();
        }
    }
    
    /**
     * Check if parking lot is full
     * @return true if full, false otherwise
     */
    public boolean isFull() {
        return availableSpaces.availablePermits() == 0;
    }
    
    /**
     * Get car parked in specific space
     * @param spaceNumber space to check
     * @return Car object or null if space is empty
     */
    public Car getCarInSpace(int spaceNumber) {
        if (spaceNumber < 0 || spaceNumber >= TOTAL_SPACES) {
            return null;
        }
        return occupiedSpaces.get(spaceNumber);
    }
    
    /**
     * Get all currently parked cars
     * @return concurrent map of occupied spaces
     */
    public ConcurrentHashMap<Integer, Car> getOccupiedSpaces() {
        return new ConcurrentHashMap<Integer, Car>(occupiedSpaces);
    }
    
    /**
     * Utility method for consistent logging
     */
    private void logEvent(String message) {
        System.out.println("[" + LocalDateTime.now().format(TIME_FORMAT) + "] [PARKING_LOT] " + message);
    }
    
    /**
     * Inner class to represent parking lot status
     */
    public static class ParkingStatus {
        private final int totalSpaces;
        private final int occupiedSpaces;
        private final int availableSpaces;
        private final int waitingVehicles;
        
        public ParkingStatus(int total, int occupied, int available, int waiting) {
            this.totalSpaces = total;
            this.occupiedSpaces = occupied;
            this.availableSpaces = available;
            this.waitingVehicles = waiting;
        }
        
        // Getters
        public int getTotalSpaces() { return totalSpaces; }
        public int getOccupiedSpaces() { return occupiedSpaces; }
        public int getAvailableSpaces() { return availableSpaces; }
        public int getWaitingVehicles() { return waitingVehicles; }
        
        @Override
        public String toString() {
            return String.format("Parking Status - Total: %d, Occupied: %d, Available: %d, Waiting: %d",
                    totalSpaces, occupiedSpaces, availableSpaces, waitingVehicles);
        }
    }
}

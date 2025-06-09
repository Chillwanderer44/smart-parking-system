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
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Car - Entity representing a vehicle in the parking system
 * Contains all relevant information about the car's parking session
 */
public class Car {
    
    // Car identification
    private final String carId;
    private final String licensePlate;
    
    // Timing information
    private final LocalDateTime arrivalTime;
    private LocalDateTime parkingTime;
    private LocalDateTime exitTime;
    private final int plannedParkingDuration; // in minutes
    
    // Parking information
    private int spaceNumber = -1;
    private boolean isPaid = false;
    private double paymentAmount = 0.0;
    
    // Car characteristics
    private final CarType carType;
    private final String ownerName;
    
    /**
     * Constructor for creating a new car
     * @param carId unique identifier for the car
     * @param licensePlate car's license plate
     * @param ownerName owner's name
     */
    public Car(String carId, String licensePlate, String ownerName) {
        this.carId = carId;
        this.licensePlate = licensePlate;
        this.ownerName = ownerName;
        this.arrivalTime = LocalDateTime.now();
        this.carType = generateRandomCarType();
        this.plannedParkingDuration = generateRandomParkingDuration();
    }
    
    /**
     * Alternative constructor with specified parking duration
     */
    public Car(String carId, String licensePlate, String ownerName, int parkingDurationMinutes) {
        this.carId = carId;
        this.licensePlate = licensePlate;
        this.ownerName = ownerName;
        this.arrivalTime = LocalDateTime.now();
        this.carType = generateRandomCarType();
        this.plannedParkingDuration = parkingDurationMinutes;
    }
    
    /**
     * Generate random car type for simulation variety
     */
    private CarType generateRandomCarType() {
        CarType[] types = CarType.values();
        return types[ThreadLocalRandom.current().nextInt(types.length)];
    }
    
    /**
     * Generate random parking duration (15 minutes to 4 hours)
     */
    private int generateRandomParkingDuration() {
        return ThreadLocalRandom.current().nextInt(15, 241); // 15 to 240 minutes
    }
    
    /**
     * Calculate actual parking duration
     * @return duration in minutes, or -1 if not yet exited
     */
    public long getActualParkingDuration() {
        if (parkingTime == null) return -1;
        
        LocalDateTime endTime = (exitTime != null) ? exitTime : LocalDateTime.now();
        return Duration.between(parkingTime, endTime).toMinutes();
    }
    
    /**
     * Calculate waiting time before getting parked
     * @return duration in milliseconds, or -1 if not yet parked
     */
    public long getWaitingTime() {
        if (parkingTime == null) return -1;
        return Duration.between(arrivalTime, parkingTime).toMillis();
    }
    
    /**
     * Calculate total time in system (arrival to exit)
     * @return duration in minutes, or -1 if not yet exited
     */
    public long getTotalTimeInSystem() {
        if (exitTime == null) return -1;
        return Duration.between(arrivalTime, exitTime).toMinutes();
    }
    
    /**
     * Calculate payment amount based on parking duration and car type
     * @return amount to be paid
     */
    public double calculatePaymentAmount() {
        if (parkingTime == null) return 0.0;
        
        long durationMinutes = getActualParkingDuration();
        if (durationMinutes <= 0) return 0.0;
        
        // Base rate: $2 per hour, minimum charge for 30 minutes
        double hours = Math.max(0.5, Math.ceil(durationMinutes / 60.0));
        double baseAmount = hours * 2.0;
        
        // Apply car type multiplier
        double multiplier = carType.getPriceMultiplier();
        
        return Math.round(baseAmount * multiplier * 100.0) / 100.0; // Round to 2 decimal places
    }
    
    /**
     * Process payment for the parking session
     * @return true if payment successful, false otherwise
     */
    public boolean processPayment() {
        if (isPaid) return true;
        
        this.paymentAmount = calculatePaymentAmount();
        
        // Simulate payment processing time
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(100, 500)); // 100-500ms
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        
        // Simulate payment failure (5% chance)
        if (ThreadLocalRandom.current().nextDouble() < 0.05) {
            return false;
        }
        
        this.isPaid = true;
        return true;
    }
    
    /**
     * Check if the car is ready to exit (has been parked for planned duration)
     * @return true if ready to exit
     */
    public boolean isReadyToExit() {
        if (parkingTime == null) return false;
        
        long actualDuration = getActualParkingDuration();
        return actualDuration >= (plannedParkingDuration * 0.8); // Allow 20% variance
    }
    
    // Getters and Setters
    public String getCarId() { return carId; }
    public String getLicensePlate() { return licensePlate; }
    public String getOwnerName() { return ownerName; }
    public LocalDateTime getArrivalTime() { return arrivalTime; }
    public LocalDateTime getParkingTime() { return parkingTime; }
    public LocalDateTime getExitTime() { return exitTime; }
    public int getPlannedParkingDuration() { return plannedParkingDuration; }
    public int getSpaceNumber() { return spaceNumber; }
    public boolean isPaid() { return isPaid; }
    public double getPaymentAmount() { return paymentAmount; }
    public CarType getCarType() { return carType; }
    
    public void setParkingTime(LocalDateTime parkingTime) { this.parkingTime = parkingTime; }
    public void setExitTime(LocalDateTime exitTime) { this.exitTime = exitTime; }
    public void setSpaceNumber(int spaceNumber) { this.spaceNumber = spaceNumber; }
    public void setPaid(boolean paid) { this.isPaid = paid; }
    public void setPaymentAmount(double paymentAmount) { this.paymentAmount = paymentAmount; }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Car car = (Car) obj;
        return Objects.equals(carId, car.carId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(carId);
    }
    
    @Override
    public String toString() {
        return String.format("Car{id='%s', plate='%s', owner='%s', type=%s, space=%d, paid=%s}",
                carId, licensePlate, ownerName, carType, spaceNumber, isPaid);
    }
    
    /**
     * Get detailed status string for logging
     */
    public String getDetailedStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Car %s [%s] - Owner: %s, Type: %s", 
                carId, licensePlate, ownerName, carType));
        
        if (spaceNumber != -1) {
            sb.append(String.format(", Space: %d", spaceNumber));
        }
        
        if (parkingTime != null) {
            sb.append(String.format(", Parked: %d min", getActualParkingDuration()));
        }
        
        if (isPaid) {
            sb.append(String.format(", Paid: $%.2f", paymentAmount));
        }
        
        return sb.toString();
    }
}

/**
 * Enum for different car types with different pricing
 */
enum CarType {
    COMPACT(0.8, "Compact Car"),
    SEDAN(1.0, "Sedan"),
    SUV(1.2, "SUV"),
    LUXURY(1.5, "Luxury Car"),
    ELECTRIC(0.9, "Electric Vehicle");
    
    private final double priceMultiplier;
    private final String description;
    
    CarType(double priceMultiplier, String description) {
        this.priceMultiplier = priceMultiplier;
        this.description = description;
    }
    
    public double getPriceMultiplier() { return priceMultiplier; }
    public String getDescription() { return description; }
    
    @Override
    public String toString() { return description; }
}

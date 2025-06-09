package smartparkingsystem;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class SmartParkingSystem {
    
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * Helper method to repeat string (Java 8 compatible)
     */
    private static String repeatString(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }
    
    /**
     * Main method - Entry point for the Smart Parking System simulation
     */
    public static void main(String[] args) {
        // Create simulation controller
        SimulationController controller = new SimulationController();
        
        // Register shutdown hook for graceful cleanup
        controller.registerShutdownHook();
        
        // Prompt user to start simulation
        if (promptUserToStart()) {
            runSimulation(controller);
        } else {
            System.out.println("Simulation cancelled by user.");
        }
    }
    
    /**
     * Print system banner
     */
    private static void printSystemBanner() {
        System.out.println("\n" + repeatString("=", 100));
        System.out.println(repeatString("🚗", 10) + " SMART PARKING SYSTEM SIMULATION " + repeatString("🚗", 10));
        System.out.println(repeatString("=", 100));
        System.out.println("        Asia Pacific University of Technology & Innovation");
        System.out.println("                   Concurrent Programming Assignment");
        System.out.println("                    Course: CT074-3-2 Level 2");
        System.out.println(repeatString("=", 100));
        System.out.println("Current Time: " + LocalDateTime.now().format(TIME_FORMAT));
        System.out.println("Java Version: " + System.getProperty("java.version"));
        System.out.println(repeatString("=", 100) + "\n");
    }
    
    /**
     * Prompt user to start the simulation
     */
    private static boolean promptUserToStart() {
        Scanner scanner = new Scanner(System.in);
        
        System.out.print("Start Smart Parking System Simulation? (y/n): ");
        String response = scanner.nextLine().trim().toLowerCase();
        
        return response.equals("y") || response.equals("yes");
    }
    
    /**
     * Run the simulation with proper error handling
     */
    private static void runSimulation(SimulationController controller) {
        try {
            System.out.println("\nSTARTING SIMULATION...");
            System.out.println("The simulation will run for 5 minutes.");
            System.out.println("Statistics will be reported every minute.");
            System.out.println("Please wait for the simulation to complete...\n");
            
            // Start the simulation
            controller.startSimulation();
            
            // Wait for simulation to complete (with timeout for safety)
            boolean completed = controller.awaitCompletion(8, TimeUnit.MINUTES); // 8 min timeout
            
            if (completed) {
                System.out.println("\nSIMULATION COMPLETED SUCCESSFULLY!");
                printCompletionMessage(controller);
            } else {
                System.out.println("\nSIMULATION TIMEOUT!");
                System.out.println("The simulation took longer than expected and was terminated.");
                controller.stopSimulation();
            }
            
        } catch (Exception e) {
            System.out.println("\nSIMULATION ERROR!");
            System.out.println("An unexpected error occurred: " + e.getMessage());
            e.printStackTrace();
            controller.stopSimulation();
            
        } finally {
            printFinalMessage();
        }
    }
    
    /**
     * Print completion message with summary
     */
    private static void printCompletionMessage(SimulationController controller) {
        System.out.println("Simulation Duration: 5 minutes");
        System.out.println("All assignment requirements demonstrated successfully");
        System.out.println("Final statistics report generated above");
        
        // Get final statistics if available
        Statistics.SystemStatistics finalStats = controller.getCurrentStatistics();
        if (finalStats != null) {
            System.out.println("\nQUICK SUMMARY:");
            System.out.println("  • Vehicles Generated: " + finalStats.totalGenerated);
            System.out.println("  • Vehicles Entered: " + finalStats.totalEntered);
            System.out.println("  • Vehicles Exited: " + finalStats.totalExited);
            System.out.println("  • Total Revenue: $" + String.format("%.2f", finalStats.totalRevenue));
            System.out.println("  • Average Parking Duration: " + String.format("%.1f", finalStats.averageParkingDuration) + " minutes");
            System.out.println("  • Peak Occupancy: " + finalStats.peakOccupancy + " vehicles");
        }
    }
    
    /**
     * Print final message
     */
    private static void printFinalMessage() {
        System.out.println("\n" + repeatString("=", 100));
        System.out.println("CONCURRENT PROGRAMMING ASSIGNMENT DEMONSTRATION COMPLETE");
        System.out.println(repeatString("=", 100));
        System.out.println("Thank you for running the Smart Parking System simulation!");
        System.out.println(repeatString("=", 100) + "\n");
    }
    
    /**
     * Display help information for the simulation
     */
    private static void displayHelp() {
        System.out.println("📖 SMART PARKING SYSTEM HELP:");
        System.out.println("This simulation demonstrates a concurrent parking management system.");
        System.out.println();
        System.out.println("🎮 CONTROLS:");
        System.out.println("  • The simulation runs automatically for 5 minutes");
        System.out.println("  • Press Ctrl+C to force stop the simulation");
        System.out.println("  • Watch the console for real-time status updates");
        System.out.println();
        System.out.println("📊 WHAT TO OBSERVE:");
        System.out.println("  • Vehicle generation and queue formation");
        System.out.println("  • Concurrent gate operations with thread names");
        System.out.println("  • Parking space allocation and deallocation");
        System.out.println("  • Payment processing with occasional failures");
        System.out.println("  • System error handling and recovery");
        System.out.println("  • Real-time statistics and periodic reports");
        System.out.println();
        System.out.println("🔍 KEY CONCURRENT FEATURES TO NOTE:");
        System.out.println("  • Multiple threads operating simultaneously");
        System.out.println("  • Thread-safe shared resource access");
        System.out.println("  • Proper synchronization and coordination");
        System.out.println("  • Graceful handling of system failures");
        System.out.println();
    }
    
    /**
     * Check system requirements
     */
    private static boolean checkSystemRequirements() {
        System.out.println("🔍 CHECKING SYSTEM REQUIREMENTS...");
        
        // Check Java version
        String javaVersion = System.getProperty("java.version");
        System.out.println("  Java Version: " + javaVersion);
        
        // Check available memory
        long maxMemory = Runtime.getRuntime().maxMemory();
        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();
        
        System.out.println("  Max Memory: " + (maxMemory / 1024 / 1024) + " MB");
        System.out.println("  Available Memory: " + ((maxMemory - totalMemory + freeMemory) / 1024 / 1024) + " MB");
        
        // Check available processors
        int processors = Runtime.getRuntime().availableProcessors();
        System.out.println("  Available Processors: " + processors);
        
        boolean meetsRequirements = true;
        
        // Warn if insufficient memory
        if (maxMemory < 128 * 1024 * 1024) { // Less than 128MB
            System.out.println("  ⚠️  WARNING: Low memory available, simulation may be slow");
        }
        
        // Warn if single processor
        if (processors == 1) {
            System.out.println("  ⚠️  WARNING: Single processor detected, limited concurrency benefits");
        }
        
        System.out.println("  ✅ System requirements check complete");
        System.out.println();
        
        return meetsRequirements;
    }
}
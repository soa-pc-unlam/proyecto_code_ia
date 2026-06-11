package broker;

import java.util.Arrays;
import java.util.List;

/**
 * Main — simulation entry point.
 *
 * Simulates 5 concurrent traders operating over 4 CEDEARs for 30 seconds,
 * then prints a summary of executed transactions per asset.
 */
public class Main {

    private static final int SIMULATION_SECONDS = 30;

    public static void main(String[] args) throws InterruptedException {

        System.out.println("===========================================");
        System.out.println("  Concurrent Stock Broker — CEDEAR Sim    ");
        System.out.println("===========================================");

        // 1. Create and configure the Broker
        Broker broker = new Broker("ArgBroker");

        // 2. Register 4 assets (CEDEARs)
        List<String> assetSymbols = Arrays.asList("AAPL", "GOOGL", "TSLA", "AMZN");
        for (String symbol : assetSymbols) {
            broker.registerAsset(symbol);
        }

        System.out.println();

        // 3. Create 5 traders
        List<Trader> traders = Arrays.asList(
            new Trader("T-001", broker, assetSymbols),
            new Trader("T-002", broker, assetSymbols),
            new Trader("T-003", broker, assetSymbols),
            new Trader("T-004", broker, assetSymbols),
            new Trader("T-005", broker, assetSymbols)
        );

        // 4. Start traders via Broker's ExecutorService
        broker.startTraders(traders);

        System.out.printf("%n[Main] Simulation running for %d seconds...%n%n",
                SIMULATION_SECONDS);

        // 5. Let the simulation run
        Thread.sleep(SIMULATION_SECONDS * 1000L);

        // 6. Graceful shutdown
        broker.shutdown(5);

        // 7. Print summary
        printSummary(broker, assetSymbols);
    }

    private static void printSummary(Broker broker, List<String> symbols) {
        System.out.println("\n===========================================");
        System.out.println("           SIMULATION SUMMARY             ");
        System.out.println("===========================================");

        int totalTx = 0;
        for (String symbol : symbols) {
            Asset asset = broker.getAsset(symbol);
            List<Transaction> txList = asset.getTransactions();
            double totalVolume = txList.stream()
                    .mapToDouble(tx -> tx.getQuantity() * tx.getExecutionPrice())
                    .sum();

            System.out.printf("%-6s | Transactions: %3d | Volume: $%,.2f | "
                    + "Pending BUY: %d | Pending SELL: %d%n",
                    symbol,
                    txList.size(),
                    totalVolume,
                    asset.getBuyBookSize(),
                    asset.getSellBookSize());

            totalTx += txList.size();
        }

        System.out.println("-------------------------------------------");
        System.out.printf("TOTAL matched transactions: %d%n", totalTx);
        System.out.println("===========================================");
    }
}
package broker;

/**
 * MatchingEngine runs as a dedicated thread per Asset.
 *
 * Lifecycle:
 *  1. Takes an incoming order from the asset's BlockingQueue (blocks if empty).
 *  2. Adds it to the appropriate order book.
 *  3. Attempts to match BUY/SELL pairs.
 *  4. Repeats until interrupted (shutdown signal).
 */
public class MatchingEngine implements Runnable {

    private final Asset asset;
    private volatile boolean running = true;

    public MatchingEngine(Asset asset) {
        this.asset = asset;
    }

    public void stop() {
        running = false;
        Thread.currentThread().interrupt();
    }

    @Override
    public void run() {
        System.out.printf("[MatchingEngine] Started for %s%n", asset.getSymbol());

        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                // Blocks until an order arrives — avoids busy-waiting
                Order order = asset.takeNextOrder();

                System.out.printf("[MatchingEngine/%s] Received %s%n",
                        asset.getSymbol(), order);

                // Add to the corresponding order book
                asset.addToBook(order);

                // Try to match orders — may produce 0 or 1 transaction per cycle
                Transaction tx = asset.tryMatch();
                if (tx != null) {
                    System.out.println("[MatchingEngine/" + asset.getSymbol() + "] " + tx);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.printf("[MatchingEngine/%s] Shutting down.%n", asset.getSymbol());
            }
        }
    }
}
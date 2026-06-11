package broker;

import java.util.List;
import java.util.Random;

/**
 * Trader simulates a market participant sending orders to the Broker.
 *
 * Each Trader runs in its own thread (managed by ExecutorService in Broker),
 * generates random BUY/SELL orders at random intervals, and submits them
 * to the broker — which routes them to the correct Asset queue.
 */
public class Trader implements Runnable {

    private final String traderId;
    private final Broker broker;
    private final List<String> availableAssets;
    private final Random random = new Random();
    private volatile boolean running = true;

    // Price range per asset (simplified: all assets share same range for demo)
    private static final double BASE_PRICE = 100.0;
    private static final double PRICE_SPREAD = 20.0;

    public Trader(String traderId, Broker broker, List<String> availableAssets) {
        this.traderId        = traderId;
        this.broker          = broker;
        this.availableAssets = availableAssets;
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        System.out.printf("[Trader %s] Started%n", traderId);

        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                // Pick a random asset
                String asset = availableAssets.get(random.nextInt(availableAssets.size()));

                // Random order type
                Order.Type type = random.nextBoolean() ? Order.Type.BUY : Order.Type.SELL;

                // Random quantity between 1 and 20 shares
                int quantity = random.nextInt(20) + 1;

                // Random price with some spread around base price
                double price = BASE_PRICE + (random.nextDouble() * PRICE_SPREAD)
                               - (PRICE_SPREAD / 2.0);
                price = Math.round(price * 100.0) / 100.0;

                Order order = new Order(traderId, asset, type, quantity, price);
                broker.submitOrder(order);

                System.out.printf("[Trader %s] Submitted %s%n", traderId, order);

                // Wait between 200ms and 1200ms before next order
                Thread.sleep(200 + random.nextInt(1000));

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.printf("[Trader %s] Interrupted, stopping.%n", traderId);
            }
        }

        System.out.printf("[Trader %s] Stopped%n", traderId);
    }
}

package broker;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Broker is the central coordinator of the system.
 *
 * Responsibilities:
 *  - Manages the registry of Assets (CEDEARs).
 *  - Starts one MatchingEngine thread per Asset.
 *  - Manages the Trader thread pool via ExecutorService.
 *  - Routes incoming orders to the correct Asset queue.
 *  - Provides a clean shutdown mechanism.
 */
public class Broker {

    private final String name;

    // Asset registry — ConcurrentHashMap allows safe concurrent reads
    private final Map<String, Asset> assets = new ConcurrentHashMap<>();

    // One MatchingEngine thread per asset
    private final Map<String, Thread> engineThreads = new ConcurrentHashMap<>();

    // Thread pool for Traders (producers)
    private ExecutorService traderPool;

    public Broker(String name) {
        this.name = name;
    }

    /** Registers a new tradeable asset and starts its MatchingEngine. */
    public void registerAsset(String symbol) {
        Asset asset = new Asset(symbol);
        assets.put(symbol, asset);

        MatchingEngine engine = new MatchingEngine(asset);
        Thread engineThread = new Thread(engine, "MatchingEngine-" + symbol);
        engineThread.setDaemon(true);
        engineThread.start();
        engineThreads.put(symbol, engineThread);

        System.out.printf("[Broker %s] Registered asset: %s%n", name, symbol);
    }

    /** Starts the trader pool with the given list of traders. */
    public void startTraders(List<Trader> traders) {
        traderPool = Executors.newFixedThreadPool(traders.size());
        for (Trader trader : traders) {
            traderPool.submit(trader);
        }
        System.out.printf("[Broker %s] Started %d traders.%n", name, traders.size());
    }

    /**
     * Routes an order to the correct Asset's BlockingQueue.
     * Called by Traders — thread-safe by design (ConcurrentHashMap + BlockingQueue).
     */
    public void submitOrder(Order order) throws InterruptedException {
        Asset asset = assets.get(order.getAsset());
        if (asset == null) {
            System.out.printf("[Broker %s] Unknown asset: %s — order rejected.%n",
                    name, order.getAsset());
            return;
        }
        asset.submitOrder(order);
    }

    /**
     * Initiates an orderly shutdown:
     *  1. Stops accepting new trader tasks.
     *  2. Waits for traders to finish.
     *  3. Interrupts MatchingEngine threads.
     */
    public void shutdown(int waitSeconds) throws InterruptedException {
        System.out.printf("%n[Broker %s] Initiating shutdown...%n", name);

        if (traderPool != null) {
            traderPool.shutdownNow();
            traderPool.awaitTermination(waitSeconds, TimeUnit.SECONDS);
        }

        for (Thread t : engineThreads.values()) {
            t.interrupt();
        }

        System.out.printf("[Broker %s] Shutdown complete.%n", name);
    }

    public Collection<Asset> getAssets() {
        return Collections.unmodifiableCollection(assets.values());
    }

    public Asset getAsset(String symbol) {
        return assets.get(symbol);
    }

    public String getName() { return name; }
}
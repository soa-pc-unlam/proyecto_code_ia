package broker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Represents a tradeable asset (CEDEAR).
 *
 * - incomingOrders: BlockingQueue used by Traders to submit orders concurrently.
 * - buyBook / sellBook: order books protected by a ReentrantLock.
 * - transactions: history of matched trades, also protected by the lock.
 */
public class Asset {

    private final String symbol;

    // Thread-safe queue for incoming orders from Traders
    private final BlockingQueue<Order> incomingOrders = new LinkedBlockingQueue<>();

    // Order books — access protected by orderBookLock
    private final List<Order> buyBook  = new ArrayList<>();
    private final List<Order> sellBook = new ArrayList<>();

    // Transaction history — access protected by orderBookLock
    private final List<Transaction> transactions = new ArrayList<>();

    // Fair lock: prevents starvation under heavy load
    private final ReentrantLock orderBookLock = new ReentrantLock(true);

    public Asset(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() { return symbol; }

    /** Called by Traders (producers) — non-blocking, thread-safe via BlockingQueue. */
    public void submitOrder(Order order) throws InterruptedException {
        incomingOrders.put(order);
    }

    /**
     * Called by MatchingEngine — blocks until an order is available.
     * Returns null on InterruptedException so the engine can shut down cleanly.
     */
    public Order takeNextOrder() throws InterruptedException {
        return incomingOrders.take();
    }

    /** Adds a new order to the appropriate order book (BUY or SELL). */
    public void addToBook(Order order) {
        orderBookLock.lock();
        try {
            if (order.getType() == Order.Type.BUY) {
                buyBook.add(order);
                // Highest bid first
                buyBook.sort(Comparator.comparingDouble(Order::getPrice).reversed());
            } else {
                sellBook.add(order);
                // Lowest ask first
                sellBook.sort(Comparator.comparingDouble(Order::getPrice));
            }
        } finally {
            orderBookLock.unlock();
        }
    }

    /**
     * Attempts to match the best BUY and SELL orders.
     * A match occurs when: bestBid.price >= bestAsk.price
     * Execution price = average of both prices.
     *
     * @return the Transaction if matched, null otherwise.
     */
    public Transaction tryMatch() {
        orderBookLock.lock();
        try {
            if (buyBook.isEmpty() || sellBook.isEmpty()) return null;

            Order bestBuy  = buyBook.get(0);
            Order bestSell = sellBook.get(0);

            if (bestBuy.getPrice() >= bestSell.getPrice()) {
                int    qty   = Math.min(bestBuy.getQuantity(), bestSell.getQuantity());
                double price = (bestBuy.getPrice() + bestSell.getPrice()) / 2.0;

                Transaction tx = new Transaction(bestBuy, bestSell, qty, price);
                transactions.add(tx);

                // Remove fully matched orders (simplified: treat each order as single-fill)
                buyBook.remove(0);
                sellBook.remove(0);

                return tx;
            }
            return null;
        } finally {
            orderBookLock.unlock();
        }
    }

    public List<Transaction> getTransactions() {
        orderBookLock.lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(transactions));
        } finally {
            orderBookLock.unlock();
        }
    }

    public int getPendingOrderCount() {
        return incomingOrders.size();
    }

    public int getBuyBookSize() {
        orderBookLock.lock();
        try { return buyBook.size(); } finally { orderBookLock.unlock(); }
    }

    public int getSellBookSize() {
        orderBookLock.lock();
        try { return sellBook.size(); } finally { orderBookLock.unlock(); }
    }
}
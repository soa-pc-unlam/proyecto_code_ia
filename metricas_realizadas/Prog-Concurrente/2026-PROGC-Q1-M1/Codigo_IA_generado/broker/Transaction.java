package broker;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Transaction {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final String buyOrderId;
    private final String sellOrderId;
    private final String asset;
    private final int quantity;
    private final double executionPrice;
    private final LocalDateTime timestamp;

    public Transaction(Order buyOrder, Order sellOrder, int quantity, double executionPrice) {
        this.buyOrderId     = buyOrder.getOrderId();
        this.sellOrderId    = sellOrder.getOrderId();
        this.asset          = buyOrder.getAsset();
        this.quantity       = quantity;
        this.executionPrice = executionPrice;
        this.timestamp      = LocalDateTime.now();
    }

    public String getAsset()          { return asset; }
    public int    getQuantity()       { return quantity; }
    public double getExecutionPrice() { return executionPrice; }

    @Override
    public String toString() {
        return String.format("✔ MATCH [%s] BUY:%s <-> SELL:%s | %s x%d @ $%.2f",
                timestamp.format(FMT), buyOrderId, sellOrderId, asset, quantity, executionPrice);
    }
}
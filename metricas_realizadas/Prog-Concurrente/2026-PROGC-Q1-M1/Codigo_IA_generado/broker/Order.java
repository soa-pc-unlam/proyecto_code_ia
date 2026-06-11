package broker;

import java.time.LocalDateTime;
import java.util.UUID;

public class Order {

    public enum Type { BUY, SELL }

    private final String orderId;
    private final String traderId;
    private final String asset;
    private final Type type;
    private final int quantity;
    private final double price;
    private final LocalDateTime timestamp;

    public Order(String traderId, String asset, Type type, int quantity, double price) {
        this.orderId   = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.traderId  = traderId;
        this.asset     = asset;
        this.type      = type;
        this.quantity  = quantity;
        this.price     = price;
        this.timestamp = LocalDateTime.now();
    }

    public String getOrderId()  { return orderId; }
    public String getTraderId() { return traderId; }
    public String getAsset()    { return asset; }
    public Type   getType()     { return type; }
    public int    getQuantity() { return quantity; }
    public double getPrice()    { return price; }
    public LocalDateTime getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return String.format("[%s] %s | %s | %s x%d @ $%.2f",
                orderId, traderId, asset, type, quantity, price);
    }
}

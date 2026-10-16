package com.example.soa;

import org.json.JSONObject;

public class StateManager {
    public static final String ESTADO_VIRGEN = "IDLE";
    public static final String ESTADO_STOCK = "STOCK";
    public static final String ESTADO_SEGURIDAD = "SECURITY";

    private static String estadoActual = ESTADO_VIRGEN;
    private static boolean stockOn = false;
    private static boolean securityOn = false;
    private static boolean buzzerMuted = false;
    private static String availability = "offline";
    private static long lastUpdated = 0;
    
    public static class ShelfData {
        public String name = "";
        public double weight = 0;
        public double weightPerUnit = 0;
        public int stock = 0;
        public int min = 0;
        public boolean available = false;
        public boolean secure = true;
        public double baseline = 0;
        public double current = 0;
        public double delta = 0;
    }

    private static final ShelfData shelf01 = new ShelfData();
    public static String getEstadoActual() { return estadoActual; }
    public static boolean isStockOn() { return stockOn; }
    public static boolean isSecurityOn() { return securityOn; }
    public static boolean isBuzzerMuted() { return buzzerMuted; }
    public static String getAvailability() { return availability; }
    public static long getLastUpdated() { return lastUpdated; }
    public static ShelfData getShelf01() { return shelf01; }

    public static void updateFromBackend(JSONObject state) {
        lastUpdated = state.optLong("lastUpdate", lastUpdated);

        JSONObject health = state.optJSONObject("health");
        if(health != null) availability = health.optString("status", availability);

        JSONObject alarm = state.optJSONObject("alarm");
        if (alarm != null) {
            buzzerMuted = alarm.optBoolean("muted", buzzerMuted);
        }

        JSONObject system = state.optJSONObject("system");
        if (system != null) {
            stockOn = system.optBoolean("stock", stockOn);
            securityOn = system.optBoolean("security", securityOn);

            String sysStatus = system.optString("status", "");
            if (sysStatus.equals("SECURITY_MODE")) {
                estadoActual = ESTADO_SEGURIDAD;
                securityOn = true;
            } else if (sysStatus.equals("STOCK_MODE")) {
                estadoActual = ESTADO_STOCK;
                stockOn = true;
                // Si el status es STOCK_MODE, es que Seguridad no está corriendo
                securityOn = false;
            } else if (sysStatus.equals("VIRGIN_EMBEDDED")) {
                estadoActual = ESTADO_VIRGEN;
                stockOn = false;
                securityOn = false;
            }
        }

        JSONObject shelves = state.optJSONObject("shelves");
        if(shelves != null) updateShelf(shelf01, shelves.optJSONObject("shelf-01"));

    }

    private static void updateShelf(ShelfData data, JSONObject json) {
        if (json == null) return;

        JSONObject stock = json.optJSONObject("stock");
        if (stock != null) {
            data.name = stock.optString("name", data.name);
            data.weight = stock.optDouble("weight", data.weight);
            data.weightPerUnit = stock.optDouble("weightPerUnit", data.weightPerUnit);
            data.stock = stock.optInt("stock", data.stock);
            data.min = stock.optInt("minimumAcceptableStock", data.min);
            data.available = data.stock >= data.min;
        }

        JSONObject security = json.optJSONObject("security");
        if (security != null) {
            boolean anomaly = security.optBoolean("anomaly", !data.secure);
            data.secure = !anomaly;
            data.baseline = security.optDouble("baselineWeight", data.baseline);
            data.current = security.optDouble("weight", data.current);
            data.delta = Math.abs(data.current - data.baseline);
        }
    }
    
    public static void setStockOn(boolean on) { stockOn = on; }
    public static void setSecurityOn(boolean on) { securityOn = on; }
}
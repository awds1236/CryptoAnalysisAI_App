package com.coinsense.cryptoanalysisai.api;

public class ExchangeRateResponse {
    private String base;
    private String target;
    private double rate;
    private long timestamp;
    private String fetch_date;

    // Getters and Setters
    public String getBase() { return base; }
    public void setBase(String base) { this.base = base; }

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }

    public double getRate() { return rate; }
    public void setRate(double rate) { this.rate = rate; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getFetch_date() { return fetch_date; }
    public void setFetch_date(String fetch_date) { this.fetch_date = fetch_date; }


}
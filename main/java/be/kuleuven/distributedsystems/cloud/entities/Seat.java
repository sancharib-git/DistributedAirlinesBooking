package be.kuleuven.distributedsystems.cloud.entities;

import java.time.LocalDateTime;
import java.util.UUID;

public class Seat implements  Comparable{
    private String airline;
    private String flightId;
    private String seatId;
    private String time;
    private String type;
    private String name;
    private double price;

    private boolean occupied;

    public Seat() {
    }

    public Seat(String airline, String flightId, String seatId, String time, String type, String name, double price) {
        this.airline = airline;
        this.flightId = flightId;
        this.seatId = seatId;
        this.time = time;
        this.type = type;
        this.name = name;
        this.price = price;
        this.occupied = false;
    }

    public String getAirline() {
        return airline;
    }

    public String getFlightId() {
        return flightId;
    }

    public String getSeatId() {
        return this.seatId;
    }

    public String getTime() {
        return this.time;
    }

    public String getType() {
        return this.type;
    }

    public String getName() {
        return this.name;
    }

    public double getPrice() {
        return this.price;
    }

    public boolean getOccupied() {
        return this.occupied;
    }
    public void setOccupied(boolean b) {
        this.occupied = b;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Seat)) {
            return false;
        }
        var other = (Seat) o;
        return this.airline.equals(other.airline)
                && this.flightId.equals(other.flightId)
                && this.seatId.equals(other.seatId);
    }

    @Override
    public int hashCode() {
        return this.airline.hashCode() * this.flightId.hashCode() * this.seatId.hashCode();
    }

    @Override
    public int compareTo(Object o) {
        if(o instanceof Seat) {
            String oName = ((Seat) o).getName();
            Integer oNumber = Integer.parseInt(oName.substring(0,oName.length() -1));
            Integer thisNumber = Integer.parseInt(this.getName().substring(0,this.getName().length() -1));
            int res = Integer.compare(thisNumber,oNumber);
            if(res == 0) {
                return Character.compare(this.getName().charAt(this.getName().length()-1),oName.charAt(oName.length()-1));
            } else {
                return res;
            }
        } else {
            return -1;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(airline + " ");
        sb.append(flightId + " ");
        sb.append(seatId + " ");
        sb.append(time + " ");
        sb.append(type + " ");
        sb.append(name + " ");
        sb.append(price + " ");
        return sb.toString();
    }
}

package be.kuleuven.distributedsystems.cloud.entities;

import java.io.Serializable;
import java.util.List;

public class Booking implements Serializable {
    private String id;
    private String time;
    private List<Ticket> tickets;
    private String customer;

    public Booking() {}

    public Booking(String id, String time, List<Ticket> tickets, String customer) {
        this.id = id;
        this.time = time;
        this.tickets = tickets;
        this.customer = customer;
    }

    public String getId() {
        return this.id;
    }

    public String getTime() {
        return this.time;
    }

    public List<Ticket> getTickets() {
        return this.tickets;
    }

    public void addTickets(List<Ticket> tickets) {
        this.tickets.addAll(tickets);
    }
    public void addTicket(Ticket t) {
        this.tickets.add(t);
    }

    public String getCustomer() {
        return this.customer;
    }

    public void deleteTickets() {
        this.tickets.clear();
    }

    public void setTickets(List<Ticket> tickets) {
        this.tickets = tickets;
    }

}

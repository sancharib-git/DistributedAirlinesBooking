package be.kuleuven.distributedsystems.cloud.controller;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import javax.annotation.Resource;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import be.kuleuven.distributedsystems.cloud.SendGridEmail;
import be.kuleuven.distributedsystems.cloud.auth.WebSecurityConfig;
import be.kuleuven.distributedsystems.cloud.entities.Booking;
import be.kuleuven.distributedsystems.cloud.entities.Ticket;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import reactor.util.retry.Retry;

@RestController
@RequestMapping("/brock")
public class BrokerControler {
    @Resource(name = "webClientBuilder")
    private WebClient.Builder webclient;

    @PostMapping("/confirmQuote")
    public void confirmQuote (@RequestBody String body) throws ParseException, IOException {
        String res = new String (body.getBytes());
        JSONParser parser = new JSONParser();
        JSONObject json = (JSONObject) parser.parse(res);
        String s = (((JSONObject)parser.parse(json.get("message").toString())).get("data").toString());
        String email = ((JSONObject)parser.parse((((JSONObject)parser.parse(json.get("message").toString())).get("attributes").toString()))).get("email").toString();
        //String email = new String(Base64.getDecoder().decode(sEmail), "UTF-8");
        String s1 = new String(Base64.getDecoder().decode(s), "UTF-8");
        String[] quotes = s1.split("\n");
        ArrayList<Ticket> tix = new ArrayList<>();
        String bookingRef = UUID.randomUUID().toString();
        Booking booking = new Booking(bookingRef, LocalDateTime.now().toString(), tix, email);

       for (String quote : quotes) {
            String[] details = quote.split(" ");
            String flightId = details[0];
            String airline = details[1];
            String seatId = details[2];
            System.out.println(airline);
            if(!airline.equals(APIController.interAirline)) {
                try {
                    var ticket = this.webclient
                            .baseUrl("https://" + airline)
                            .build()
                            .put()
                            .uri(uriBuilder -> uriBuilder
                                    .pathSegment("flights", flightId, "seats", seatId, "ticket")
                                    .queryParam("bookingReference", bookingRef)
                                    .queryParam("customer", email)
                                    .queryParam("key", "Iw8zeveVyaPNWonPNaU0213uw3g6Ei")
                                    .build())
                            .retrieve()
                            .bodyToMono(new ParameterizedTypeReference<Ticket>() {
                            })
                            .retryWhen(Retry.fixedDelay(7, Duration.ofSeconds(1)))
                            .block();
                    booking.addTicket(ticket);
                } catch (Exception exception) {
                    for(Ticket t : tix) {
                        var ticket = this.webclient
                                .baseUrl("https://"+airline)
                                .build()
                                .delete()
                                .uri(uriBuilder -> uriBuilder
                                        .pathSegment("flights", t.getFlightId(), "seats", t.getSeatId(), "ticket", t.getTicketId())
                                        .queryParam("bookingReference", bookingRef)
                                        .queryParam("customer", email)
                                        .queryParam("key", "Iw8zeveVyaPNWonPNaU0213uw3g6Ei")
                                        .build())
                                .retrieve()
                                .bodyToMono(new ParameterizedTypeReference<Ticket>() {
                                })
                                .retryWhen(Retry.fixedDelay(7, Duration.ofSeconds(1)))
                                .block();
                    }
                    booking.deleteTickets();
                    /*var ticket = this.webclient
                            .baseUrl("https://" + airline)
                            .build()
                            .delete()
                            .uri(uriBuilder -> uriBuilder
                                    .pathSegment("flights", flightId, "seats", seatId, "ticket", "ticket", UUID.randomUUID().toString())
                                    .queryParam("bookingReference", bookingRef)
                                    .queryParam("customer", email)
                                    .queryParam("key", "Iw8zeveVyaPNWonPNaU0213uw3g6Ei")
                                    .build())
                            .retrieve()
                            .bodyToMono(new ParameterizedTypeReference<Ticket>() {
                            })
                            .retryWhen(Retry.fixedDelay(7, Duration.ofSeconds(1)))
                            .block();
                    //booking.addTicket(ticket); //tickets shouldnt be added if theres an error?
                    */
                }
            } else {

                Firestore db = APIController.getInstance().getDb();
                var docRef = db.collection("seats1").document(seatId);
                ApiFuture<Boolean> resTransac = db.runTransaction(transaction -> {
                    var seatData = transaction.get(docRef).get().getData();
                    if (seatData.get("occupied").toString().equals("false")) {
                        transaction.update(docRef, "occupied", true);
                        return true;
                    }
                    return false;
                });
                Ticket t = new Ticket(
                        airline,
                        flightId,
                        seatId,
                        UUID.randomUUID().toString(),
                        email,
                        bookingRef
                );
                booking.addTicket(t);
            }

                /**/
            }
        try {
            if(!booking.getTickets().isEmpty()) {
                APIController.getInstance().addBooking(booking.getCustomer(), booking);
                SendGridEmail.SendEmail(email, "Booking Success!", "Your booking was a success");
            }
            else {
                //SendGridEmail.SendEmail(email, "Booking Unsuccessful", "Your booking was not a success");
            }
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }

    }
}

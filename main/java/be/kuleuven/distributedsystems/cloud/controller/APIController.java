package be.kuleuven.distributedsystems.cloud.controller;


import java.awt.print.Book;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import be.kuleuven.distributedsystems.cloud.entities.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.*;
import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.google.gson.reflect.TypeToken;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.Link;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import com.google.api.core.ApiFuture;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;

import be.kuleuven.distributedsystems.cloud.auth.WebSecurityConfig;
import reactor.util.retry.Retry;


@RestController
@RequestMapping("/api")
public class APIController {
    @Resource(name = "webClientBuilder")
    private WebClient.Builder webclient;
    @Resource(name = "publisher")
    private Publisher publisher;
    @Resource(name = "db")
    private Firestore db;
    private final String[] urls = {"https://reliable-airline.com", "https://unreliable-airline.com"};
    public static final String interAirline = "internal-airline";
    private String bookingCollection = "bookings";
    private String flightCollection = "flights1";
    private String seatCollection = "seats1";
    private static APIController singleton;

    public static APIController getInstance() {
        return singleton;
    }
    public Firestore getDb() {
        return this.db;
    }



    /**
     *
     * @param email
     * @param booking
     * @throws ExecutionException
     * @throws InterruptedException
     * Each time a new booking is made, update the database in Firestore as well
     */
    public void addBooking(String email, Booking booking) throws ExecutionException, InterruptedException {
        this.db.collection(bookingCollection).document(booking.getId()).set(booking);
    }

    private List<Booking> getBookings(String email) throws ExecutionException, InterruptedException {
         List<Booking> bookings = new ArrayList<>();

        // Create a reference to the database
        CollectionReference ref = this.db.collection(bookingCollection);

        // Create the query
        Query query = ref.whereEqualTo("customer", email);

        // Get the object from the query
        ApiFuture<QuerySnapshot> querySnapshot = query.get();
        for(DocumentSnapshot documentSnapshot : querySnapshot.get().getDocuments()) {
            bookings.add(documentSnapshot.toObject(Booking.class));
        }
        return bookings;
    }

    /**
     * Constructor
     * @throws Exception
     * When API Controller is first launched, and if there is data in Firestore,
     * load data into local memory to be processed to ensure persistent storage across user sessions
     */
    public APIController() throws Exception {
        singleton = this;
    }

    @PostConstruct
    public void init() throws IOException, ExecutionException, InterruptedException {
        DocumentReference documentReference = this.db.collection("init").document("InitializationFlag");
        ApiFuture<DocumentSnapshot> future = documentReference.get();
        DocumentSnapshot doc = future.get();
        if(doc.exists()) {
            System.out.println("Document already exists");
        } else {
            System.out.println("Initializing");
            HashMap<String, String> hashMap = new HashMap<>();
            hashMap.put("InitializationFlag", "Yes");
            this.db.collection("init").document("InitializationFlag").set(hashMap);
            String data = new String(new ClassPathResource("data.json").getInputStream().readAllBytes());
            Gson gson = new Gson();

            Type type = new TypeToken<HashMap<String, Object>>(){}.getType();
            HashMap<String, Object> map = gson.fromJson(data, type);
            List internalAirline = (List) map.get("flights");

            for(int i = 0; i < internalAirline.size(); i++) {
                LinkedTreeMap treeMap = (LinkedTreeMap) internalAirline.get(i);
                System.out.println(treeMap.get("name"));
                String flightID = UUID.randomUUID().toString();
                Flight flight = new Flight(
                        interAirline,
                        flightID,
                        (String) treeMap.get("name"),
                        (String) treeMap.get("location"),
                        (String) treeMap.get("image"));
                this.db.collection(flightCollection).document(flightID).set(flight).get();
                ArrayList list = (ArrayList) treeMap.get("seats");

                for(Object seatObject : list) {
                    LinkedTreeMap seat = (LinkedTreeMap) seatObject;
                    String seatID = UUID.randomUUID().toString();

                    Seat s = new Seat(
                            interAirline,
                            flight.getFlightId(),
                            seatID,
                            (String) seat.get("time"),
                            (String) seat.get("type"),
                            (String) seat.get("name"),
                            (double) seat.get("price")
                    );
                    this.db.collection(seatCollection).document(seatID).set(s).get();
                    //this.db.collection(availableSeatCollection).add(s);
                }
            }



        }

    }

    @GetMapping("/getFlights")
    public List<Flight> getFlights() throws ExecutionException, InterruptedException {
        List<Flight> res = new ArrayList<>();
        for (String url : urls) {
            var flights = this.webclient
                    .baseUrl(url)
                    .build()
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .pathSegment("flights")
                            .queryParam("key", "Iw8zeveVyaPNWonPNaU0213uw3g6Ei")
                            .build())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<CollectionModel<Flight>>() {
                    })
                    .retryWhen(Retry.fixedDelay(7, Duration.ofSeconds(1)))
                    .block()
                    .getContent()
                    .stream().toList();
            res.addAll(flights);
        }

        ApiFuture<QuerySnapshot> querySnapshot = this.db.collection(flightCollection).get();
        for(DocumentSnapshot documentSnapshot : querySnapshot.get().getDocuments()) {
            res.add(documentSnapshot.toObject(Flight.class));
        }

        return res;
    }

    @GetMapping("/getFlight")
    public Flight getFlight(@RequestParam String airline, @RequestParam String flightId) throws ExecutionException, InterruptedException {
        if(!airline.equals(interAirline)) {
            var flight = this.webclient
                    .baseUrl("https://" + airline)
                    .build()
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/flights/{flightId}")
                            .queryParam("key", "Iw8zeveVyaPNWonPNaU0213uw3g6Ei")
                            .build(flightId))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Flight>() {
                    })
                    .retryWhen(Retry.fixedDelay(7, Duration.ofSeconds(1)))
                    .block();
            return flight;
        } else {
            var doc = this.db.collection(flightCollection).document(flightId).get().get();//.whereEqualTo("flightId",flightId).whereEqualTo("airline",airline);
            // Get the object from the query
            //ApiFuture<QuerySnapshot> querySnapshot = query.get();
            return doc.toObject(Flight.class);
        }
    }

    @GetMapping("/getFlightTimes")
    public List<String> getFlightTimes(@RequestParam String airline, @RequestParam String flightId) throws ExecutionException, InterruptedException {
        if(!airline.equals(interAirline)) {
            var flight = this.webclient
                    .baseUrl("https://" + airline)
                    .build()
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/flights/{flightId}/times")
                            .queryParam("key", "Iw8zeveVyaPNWonPNaU0213uw3g6Ei")
                            .build(flightId))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<CollectionModel<String>>() {
                    })
                    .retryWhen(Retry.fixedDelay(7, Duration.ofSeconds(1)))
                    .block();
            List<String> times = new ArrayList<>(flight.getContent().stream().toList());
            Collections.sort(times);
            return times;
        } else {
            List<String> times = new ArrayList<>();

            var queryDocs = this.db.collection(seatCollection).whereEqualTo("flightId", flightId).get().get().getDocuments();
            for(var documentSnapshot : queryDocs) {
                Seat seat = documentSnapshot.toObject(Seat.class);
                String date = seat.getTime();
                if(!times.contains(date))
                    times.add(date);
            }
            Collections.sort(times);
            return times;
        }
    }

    @GetMapping("/getAvailableSeats")
    public Map<String, List<Seat>> getAvailableSeats(@RequestParam String airline, @RequestParam String flightId, @RequestParam String time) throws ExecutionException, InterruptedException {
        if(!airline.equals(interAirline)) {
            //iterate over list and check the type -> if it corresponds to the type add into map
            var flight = this.webclient
                    .baseUrl("https://" + airline)
                    .build()
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .pathSegment("flights", flightId, "seats")
                            .queryParam("airline", airline)
                            //.queryParam("flightId", flightId)
                            .queryParam("time", time)
                            .queryParam("available", "true")
                            .queryParam("key", "Iw8zeveVyaPNWonPNaU0213uw3g6Ei")
                            .build())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<CollectionModel<Seat>>() {
                    })
                    .retryWhen(Retry.fixedDelay(7, Duration.ofSeconds(1)))
                    .block()
                    .getContent();

            ArrayList<Seat> seats = new ArrayList<>(flight.stream().toList());
            Map<String, List<Seat>> map = new HashMap<>();
            ArrayList<Seat> ec = new ArrayList<>();
            ArrayList<Seat> biz = new ArrayList<>();
            ArrayList<Seat> first = new ArrayList<>();


            for (Seat seat : seats) {
                if (seat.getType().equals("Economy")) {
                    ec.add(seat);
                } else if (seat.getType().equals("Business")) {
                    biz.add(seat);
                } else if (seat.getType().equals("First")) {
                    first.add(seat);
                }
            }

            Collections.sort(ec);
            Collections.sort(biz);
            Collections.sort(first);


            map.put("Economy", ec);
            map.put("Business", biz);
            map.put("First", first);

            return map;
        } else {
            ArrayList<Seat> ec = new ArrayList<>();
            ArrayList<Seat> biz = new ArrayList<>();
            ArrayList<Seat> first = new ArrayList<>();

            var queryDocuments = this.db.collection(seatCollection).whereEqualTo("time",time).whereEqualTo("occupied", false).get().get().getDocuments();
            //ApiFuture<QuerySnapshot> querySnapshot = query.get();
            for(var documentSnapshot : queryDocuments) {
                Seat seat = documentSnapshot.toObject(Seat.class);
                if (seat.getType().equals("Economy")) {
                    ec.add(seat);
                } else if (seat.getType().equals("Business")) {
                    biz.add(seat);
                } else if (seat.getType().equals("First")) {
                    first.add(seat);
                }
            }

            Collections.sort(ec);
            Collections.sort(biz);
            Collections.sort(first);

            Map<String, List<Seat>> map = new HashMap<>();
            map.put("Economy", ec);
            map.put("Business", biz);
            map.put("First", first);

            return map;
        }
    }

    @GetMapping("/getSeat")
    /*/flights/986195e5-2b53-42c1-aab4-e621cbc0e522/seats/fc580ec8-85ff-4a5b-b169-6161d3c5fefc?key=Iw8zeveVyaPNWonPNaU0213uw3g6Ei
    */
    public Seat getSeat(@RequestParam String airline, @RequestParam String flightId, @RequestParam String seatId) throws ExecutionException, InterruptedException {
        if(!airline.equals(interAirline)) {
            var flight = this.webclient
                    .baseUrl("https://" + airline)
                    .build()
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .pathSegment("flights", flightId, "seats", seatId)
                            .queryParam("key", "Iw8zeveVyaPNWonPNaU0213uw3g6Ei")
                            .queryParam("airline", airline)
                            .build())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Seat>() {
                    })
                    .retryWhen(Retry.fixedDelay(7, Duration.ofSeconds(1)))
                    .block();
            return flight;
        } else {
            var doc = this.db.collection(seatCollection).document(seatId).get().get();
            return doc.toObject(Seat.class);
        }
    }

    @PostMapping("/confirmQuotes")
    public void confirmQuotes(@RequestBody ArrayList<Quote> quotes) {

        StringBuilder sb = new StringBuilder();
        for (Quote q : quotes) {
            sb.append(q.getFlightId() + " " + q.getAirline() + " " + q.getSeatId() + "\n");
        }

        String message = sb.toString();
        ByteString data = ByteString.copyFromUtf8(message);
        PubsubMessage pubsubMessage = PubsubMessage.newBuilder().setData(data).putAttributes("email", WebSecurityConfig.getUser().getEmail()).build();
        ApiFuture<String> future = publisher.publish(pubsubMessage);
    }

    @GetMapping("/getBookings")
    public List<Booking> getBookings() throws ExecutionException, InterruptedException {
        return this.getBookings(SecurityContextHolder.getContext().getAuthentication().getName());
    }


    @GetMapping("/getBestCustomers")
    public List<String> getBestCustomers() throws ExecutionException, InterruptedException {
        if(!WebSecurityConfig.getUser().getRole().equals("\"manager\""))
            return null;

        List<Booking> bookings = this.getAllBookings();
        Map<String,Integer> nbTickPerClients = new HashMap<String, Integer>();
        bookings.forEach(booking -> {
            Integer nb = nbTickPerClients.get(booking.getCustomer());
            if(nb == null) {
                nbTickPerClients.put(booking.getCustomer(), booking.getTickets().size());
            } else {
                nbTickPerClients.put(booking.getCustomer(), nb.intValue() + booking.getTickets().size());
            }
        });

        List<String> result = new ArrayList<>();
        int max = 0;
        for(String customer : nbTickPerClients.keySet()) {
            int nbTickts = nbTickPerClients.get(customer);
            if(nbTickts > max) {
                max = nbTickts;
                result = new ArrayList<>();
                result.add(customer);
            } else if (nbTickts == max) {
                result.add(customer);
            }
        }
        return result;
    }

    @GetMapping("/getAllBookings")
    public List<Booking> getAllBookings() throws ExecutionException, InterruptedException {
        if(!WebSecurityConfig.getUser().getRole().equals("\"manager\""))
            return new ArrayList<>();

        List<Booking> result = new ArrayList<>();
        var docRefs = this.db.collection(bookingCollection).listDocuments();
        for(var document : docRefs) {
            result.add(document.get().get().toObject(Booking.class));
        }
        return result;
    }
}
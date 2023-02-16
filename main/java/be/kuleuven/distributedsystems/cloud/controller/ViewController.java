package be.kuleuven.distributedsystems.cloud.controller;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import be.kuleuven.distributedsystems.cloud.entities.Flight;

@Controller
public class ViewController {



    @GetMapping({"/flights/*/*", "/flights/*/*/*", "/cart", "/account", "/manager", "/login"})
    public String spa() {
        return "forward:/";
    }

    @GetMapping("/_ah/warmup")
    public String warmup() {
        String Msg = "Hello Cloud";
        return Msg;
    }




}

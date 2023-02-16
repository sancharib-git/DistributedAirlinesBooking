package be.kuleuven.distributedsystems.cloud;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.hateoas.config.EnableHypermediaSupport;
import org.springframework.hateoas.config.HypermediaWebClientConfigurer;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.web.firewall.DefaultHttpFirewall;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.web.reactive.function.client.WebClient;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.AlreadyExistsException;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.SubscriptionAdminSettings;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminSettings;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.PushConfig;
import com.google.pubsub.v1.Subscription;
import com.google.pubsub.v1.SubscriptionName;
import com.google.pubsub.v1.Topic;
import com.google.pubsub.v1.TopicName;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import reactor.netty.http.client.HttpClient;

//SENDGRID API KEY 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
// new api KEY: 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

@EnableHypermediaSupport(type = EnableHypermediaSupport.HypermediaType.HAL)
@SpringBootApplication
public class Application {

    private static TransportChannelProvider channelProvider;
    private static String projectID = "airlines-booking-f35af";
    private static String subscriptionID = "your-subscription-id";
    private static String topicID = "your-topic-id";
    private static String pushEndpoint = "http://localhost:8080/brock/confirmQuote";

    private static String siteURL = "https://airlines-booking-f35af.uc.r.appspot.com/brock/confirmQuote";


    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws IOException {
        System.setProperty("server.port", System.getenv().getOrDefault("PORT", "8080"));
        if(Objects.equals(System.getenv("GAE_ENV"), "standard")) {
            System.out.println("hello");

        } else {
            ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:8083").usePlaintext().build();

            channelProvider =
                    FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel));
            CredentialsProvider credentialsProvider = NoCredentialsProvider.create();


        }
        createTopicExample();
        createPushSubscriptionExample();

        ApplicationContext context = SpringApplication.run(Application.class, args);

        // TODO: (level 2) load this data into Firestore
        String data = new String(new ClassPathResource("data.json").getInputStream().readAllBytes());
    }

    public static void createTopicExample() throws IOException {
        if(Objects.equals(System.getenv("GAE_ENV"), "standard")) {
            TopicName topicName = TopicName.of(projectID, topicID);
            try (TopicAdminClient topicAdminClient = TopicAdminClient.create()) {
                Topic topic = topicAdminClient.createTopic(topicName);
                System.out.println("Created topic: " + topic.getName());
            } catch (AlreadyExistsException e) {
                System.out.println("Topic " + topicName.getTopic() + " already exists.");
            }
        } else {

            try (TopicAdminClient topicAdminClient = TopicAdminClient.create(
                    TopicAdminSettings.newBuilder().setTransportChannelProvider(channelProvider).setCredentialsProvider(NoCredentialsProvider.create()).build()
            )) {
                TopicName topicName = TopicName.of(projectID, topicID);
                Topic topic = topicAdminClient.createTopic(topicName);
                System.out.println("Created topic: " + topic.getName());
            }
        }
    }

    @Bean
    public boolean isProduction() {
        return Objects.equals(System.getenv("GAE_ENV"), "standard");
    }

    @Bean
    public String projectId() {
        return "demo-distributed-systems-kul";
    }

    /*
     * You can use this builder to create a Spring WebClient instance which can be used to make REST-calls.
     */
    @Bean
    WebClient.Builder webClientBuilder(HypermediaWebClientConfigurer configurer) {
        return configurer.registerHypermediaTypes(WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create()))
                .codecs(clientCodecConfigurer -> clientCodecConfigurer.defaultCodecs().maxInMemorySize(100 * 1024 * 1024)));
    }


    @Bean
    HttpFirewall httpFirewall() {
        DefaultHttpFirewall firewall = new DefaultHttpFirewall();
        firewall.setAllowUrlEncodedSlash(true);
        return firewall;
    }

    @Bean
    Firestore db() throws Exception {
        // [START firestore_setup_client_create]
        // Option 1: Initialize a Firestore client with a specific `projectId` and
        //           authorization credential.
        // [START firestore_setup_client_create_with_project_id]
        FirestoreOptions firestoreOptions;
        String env = System.getenv("GAE_ENV");
        if (Objects.equals(System.getenv("GAE_ENV"), "standard")) {
            try {
                firestoreOptions = FirestoreOptions.getDefaultInstance().toBuilder()
                        .setProjectId("airlines-booking-f35af")
                        .setCredentials(GoogleCredentials.getApplicationDefault())
                        .build();
                return firestoreOptions.getService();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            firestoreOptions =
                    FirestoreOptions.getDefaultInstance().toBuilder()
                            .setProjectId("demo-distributed-systems-kul")
                            .setEmulatorHost("0.0.0.0:8084")
                            .setCredentials(new FirestoreOptions.EmulatorCredentials())
                            .build();
            // [END firestore_setup_client_create_with_project_id]
            // [END firestore_setup_client_create]
            return firestoreOptions.getService();
        }
        return null;
    }


    @Bean
    public Publisher publisher() throws IOException, InterruptedException, ExecutionException {
        TopicName topicName = TopicName.of(projectID, topicID);
        Publisher publisher = null;
        if(!Objects.equals(System.getenv("GAE_ENV"), "standard")) { //emulator environment
            ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:8083").usePlaintext().build();
            try {
                CredentialsProvider credentialsProvider = NoCredentialsProvider.create();

                // Set the channel and credentials provider when creating a `TopicAdminClient`.
                // Similarly for SubscriptionAdminClient
                TopicAdminClient topicClient =
                        TopicAdminClient.create(
                                TopicAdminSettings.newBuilder()
                                        .setTransportChannelProvider(channelProvider)
                                        .setCredentialsProvider(credentialsProvider)
                                        .build());



                return Publisher.newBuilder(topicName)
                        .setChannelProvider(channelProvider)
                        .setCredentialsProvider(credentialsProvider)
                        .build();
            } finally {
                channel.shutdown();
            }
        } else { //GAE environment
            System.out.println("anything published");


            try {
                // Create a publisher instance with default settings bound to the topic
                publisher = Publisher.newBuilder(topicName).build();

                return publisher;
            } finally {
                if (publisher != null) {
                    //publisher.publish(PubsubMessage.newBuilder().build());
                    // When finished with the publisher, shutdown to free up resources.
                    //publisher.shutdown();
                    //publisher.awaitTermination(1, TimeUnit.MINUTES);
                }
            }
        }

    }

    public static void createPushSubscriptionExample()
            throws IOException {
        if(!Objects.equals(System.getenv("GAE_ENV"), "standard")) { //emulator env
            try (SubscriptionAdminClient subscriptionAdminClient = SubscriptionAdminClient.create(
                    SubscriptionAdminSettings.newBuilder().setTransportChannelProvider(channelProvider)
                            .setCredentialsProvider(NoCredentialsProvider.create()).build()
            )) {
                TopicName topicName = TopicName.of(projectID, topicID);
                SubscriptionName subscriptionName = SubscriptionName.of(projectID, subscriptionID);
                PushConfig pushConfig = PushConfig.newBuilder().setPushEndpoint(pushEndpoint).build();

                Subscription subscription =
                        subscriptionAdminClient.createSubscription(subscriptionName, topicName, pushConfig, 10);
                System.out.println("Created push subscription: " + subscription.getName());
            }
        } else { //gae env

            try (SubscriptionAdminClient subscriptionAdminClient = SubscriptionAdminClient.create()) {
                TopicName topicName = TopicName.of(projectID, topicID);
                SubscriptionName subscriptionName = SubscriptionName.of(projectID, subscriptionID);
                PushConfig pushConfig = PushConfig.newBuilder().setPushEndpoint(siteURL).build();

                // Create a push subscription with default acknowledgement deadline of 10 seconds.
                // Messages not successfully acknowledged within 10 seconds will get resent by the server.
                Subscription subscription =
                        subscriptionAdminClient.createSubscription(subscriptionName, topicName, pushConfig, 10);
                System.out.println("Created push subscription: " + subscription.getName());
            } catch (Exception e) {
                System.out.println(e);
            }
        }

    }
}
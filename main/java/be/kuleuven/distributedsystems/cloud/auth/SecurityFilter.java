package be.kuleuven.distributedsystems.cloud.auth;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Resource;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.reactive.function.client.WebClient;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ServiceException;

import be.kuleuven.distributedsystems.cloud.entities.User;
import net.minidev.json.parser.ParseException;
import reactor.core.publisher.Mono;

@Component
public class SecurityFilter extends OncePerRequestFilter {
    @Resource(name = "isProduction")
    private boolean isProduction; //this can go wrong

    @Resource(name = "webClientBuilder")
    private WebClient.Builder webclient; //this can go wrong

    private static final String publicKeyURL = "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com";
    static final String projectID = "airlines-booking-f35af";



    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // TODO: (level 1) decode Identity Token and assign correct email and role
        String token = request.getHeader("Authorization").substring(7);
        if(token.length() > 1) {
            DecodedJWT jwt = JWT.decode(token);
            String email = String.valueOf(jwt.getClaim("email"));
            String role = String.valueOf(jwt.getClaim("role"));


        // TODO: (level 2) verify Identity Token
        //verify the signature of the token
        //dynamically request public keys from google api endpoint
        // use java jwt library from auth0 to verify manually

        if(this.isProduction) {
            try {
                if(!verifyIdentityToken(token)) {
                    throw new ServletException("Identity token verification failed");
                }
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }

        }


        //var user = new User("test@example.com", "");
        var user = new User(email, role);
        SecurityContext context = SecurityContextHolder.getContext();
        context.setAuthentication(new FirebaseAuthentication(user));
        filterChain.doFilter(request, response);
        }

    }

    private boolean verifyIdentityToken(String token) throws IOException, ParseException {
        DecodedJWT jwt = JWT.decode(token);
        if(!jwt.getAlgorithm().equals("RS256")) return false;
        // Get keyId
        Map publicKeys = this.getCertFromURL();
        String key = jwt.getKeyId(); //or is it get keyID
        if(!publicKeys.containsKey(key)) {
            return false;
        }
        // Corresponding certificate for obtained key
        String certificate = (String) publicKeys.get(key);

        try {
            var pubKey = getPublicKey(certificate); //just need to get public key from the url
            Algorithm algorithm = Algorithm.RSA256(pubKey, null);
            DecodedJWT jwt1 = JWT.require(algorithm)
                    .withIssuer("https://securetoken.google.com/" + projectID)
                    .build()
                    .verify(token);

        } catch (JWTVerificationException | IOException | GeneralSecurityException e) {
            System.out.println(e.getLocalizedMessage());
        }
        return true;
    }

    private Map getCertFromURL() throws IOException, ParseException {
            ObjectMapper mapper = new ObjectMapper();
            ResponseEntity<String> respEntity =  Objects.requireNonNull(this.webclient.baseUrl(publicKeyURL)
                    .build()
                    .get()
                    .retrieve()
                    .onStatus(HttpStatus:: isError,
                            response -> Mono.error(new ServiceException("Error while trying to fetch keys...")))
                    .toEntity(String.class)
                    .block());
        return mapper.readValue(respEntity.getBody(), Map.class);
    }

    /**
     *
     * @param cert
     * @return
     * @throws GeneralSecurityException
     */
    private RSAPublicKey getPublicKey(String cert) throws GeneralSecurityException, IOException {

        // generate x509 cert
        InputStream inputStream = new ByteArrayInputStream(cert.getBytes(StandardCharsets.UTF_8));
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cer = (X509Certificate)cf.generateCertificate(inputStream); //may go wrong

        return (RSAPublicKey) cer.getPublicKey();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/api");
    }

    private static class FirebaseAuthentication implements Authentication {
        private final User user;

        FirebaseAuthentication(User user) {
            this.user = user;
        }

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            if (user.isManager()) {
                return List.of(new SimpleGrantedAuthority("manager"));
            } else {
                return new ArrayList<>();
            }
        }

        @Override
        public Object getCredentials() {
            return null;
        }

        @Override
        public Object getDetails() {
            return null;
        }

        @Override
        public User getPrincipal() {
            return this.user;
        }

        @Override
        public boolean isAuthenticated() {
            return true;
        }

        @Override
        public void setAuthenticated(boolean b) throws IllegalArgumentException {

        }

        @Override
        public String getName() {
            return user.getEmail();
        }
    }
}


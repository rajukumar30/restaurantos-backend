package com.restaurantos.modules.orders;

import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.auth.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/customer-auth")
public class CustomerAuthController {
    private final JdbcTemplate jdbc;
    private final JwtService jwt;
    private final String accountSid, authToken, verifyServiceSid;
    private final HttpClient client=HttpClient.newHttpClient();

    public CustomerAuthController(JdbcTemplate jdbc, JwtService jwt,
        @Value("${restaurantos.sms.twilio-account-sid:}") String accountSid,
        @Value("${restaurantos.sms.twilio-auth-token:}") String authToken,
        @Value("${restaurantos.sms.twilio-verify-service-sid:}") String verifyServiceSid) {
        this.jdbc=jdbc;this.jwt=jwt;this.accountSid=accountSid;this.authToken=authToken;this.verifyServiceSid=verifyServiceSid;
    }
    public record StartRequest(@NotBlank @Pattern(regexp="\\+91[6-9][0-9]{9}") String phone) {}
    public record VerifyRequest(@NotBlank @Pattern(regexp="\\+91[6-9][0-9]{9}") String phone,
        @NotBlank @Size(min=4,max=10) String code, @Size(max=120) String fullName) {}
    public record CustomerSession(String token, UUID customerId, String fullName, String phone) {}

    private void requireProvider(){
        if(accountSid.isBlank()||authToken.isBlank()||verifyServiceSid.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Phone verification is not configured yet.");
    }
    private String callVerify(String suffix,String body){
        requireProvider();
        try{
            String url="https://verify.twilio.com/v2/Services/"+URLEncoder.encode(verifyServiceSid,StandardCharsets.UTF_8)+suffix;
            String basic=Base64.getEncoder().encodeToString((accountSid+":"+authToken).getBytes(StandardCharsets.UTF_8));
            HttpRequest request=HttpRequest.newBuilder(URI.create(url)).header("Authorization","Basic "+basic)
                .header("Content-Type","application/x-www-form-urlencoded")
                .timeout(java.time.Duration.ofSeconds(12)).POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response=client.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()<200||response.statusCode()>=300)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unable to verify phone. Please retry later.");
            return response.body();
        }catch(ResponseStatusException ex){throw ex;}
        catch(Exception ex){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Phone verification service is unavailable.");}
    }
    private static String form(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    @PostMapping("/start")
    public ApiResponse<String> start(@Valid @RequestBody StartRequest request){
        callVerify("/Verifications","To="+form(request.phone())+"&Channel=sms");
        return ApiResponse.success("Verification code requested","SENT");
    }
    @PostMapping("/verify")
    public ApiResponse<CustomerSession> verify(@Valid @RequestBody VerifyRequest request){
        String result=callVerify("/VerificationCheck","To="+form(request.phone())+"&Code="+form(request.code()));
        // Only the explicit Twilio approved status grants a customer session.
        if(!result.matches("(?s).*\\\"status\\\"\\s*:\\s*\\\"approved\\\".*"))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Incorrect or expired verification code.");
        List<CustomerSession> existing=jdbc.query("SELECT id,full_name,phone_e164 FROM restaurant_customers WHERE phone_e164=?",
            (rs,n)->new CustomerSession("",rs.getObject("id",UUID.class),rs.getString("full_name"),rs.getString("phone_e164")),request.phone());
        UUID id;String name;
        if(existing.isEmpty()){
            if(request.fullName()==null||request.fullName().trim().length()<2)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Full name is required for registration.");
            name=request.fullName().trim();
            id=jdbc.queryForObject("""
                INSERT INTO restaurant_customers(phone_e164,full_name) VALUES (?,?)
                ON CONFLICT(phone_e164) DO UPDATE SET updated_at=NOW()
                RETURNING id
                """,UUID.class,request.phone(),name);
        }else{id=existing.get(0).customerId();name=existing.get(0).fullName();}
        return ApiResponse.success("Customer verified",new CustomerSession(jwt.issue(id,request.phone(),List.of("CUSTOMER")),id,name,request.phone()));
    }
}

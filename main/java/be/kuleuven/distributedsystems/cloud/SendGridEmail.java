package be.kuleuven.distributedsystems.cloud;
import com.sendgrid.*;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;

import java.io.IOException;

public class SendGridEmail {
    public SendGridEmail() {};
    public static void SendEmail (String emailTo, String subject, String body) throws IOException {
        System.out.println("Sending email");
        try {
            Email from = new Email("sanchari.bandyopadhyay@student.kuleuven.be");
            Email to = new Email(emailTo.replaceAll("\"", ""));
            Content content = new Content("text/plain", body);
            Mail mail = new Mail(from, subject, to, content);

            SendGrid sg = new SendGrid("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");
            Request request = new Request();
            try {
                System.out.println("trying to send email");
                request.setMethod(Method.POST);
                request.setEndpoint("mail/send");
                request.setBody(mail.build());
                Response response = sg.api(request);
                System.out.println(response.getStatusCode());
                System.out.println(response.getBody());
                System.out.println(response.getHeaders());
            } catch (Exception ex) {
                System.out.println("error");
                throw ex;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}






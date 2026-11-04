// Envio de e-mail por SMTP (Mailpit no desenvolvimento, provedor real em produção).
package br.edu.ifba.agrofacil.mail;

import br.edu.ifba.agrofacil.config.AppProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpMailGateway implements MailGateway {

  private final JavaMailSender sender;
  private final AppProperties props;

  public SmtpMailGateway(JavaMailSender sender, AppProperties props) {
    this.sender = sender;
    this.props = props;
  }

  @Override
  public void send(MailMessage message) {
    var mail = new SimpleMailMessage();
    mail.setFrom(props.mail().from());
    mail.setTo(message.to());
    mail.setSubject(message.subject());
    mail.setText(message.body());
    sender.send(mail);
  }
}

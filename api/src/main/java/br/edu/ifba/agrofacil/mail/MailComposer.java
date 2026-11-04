// Textos dos e-mails da API, em português simples.
package br.edu.ifba.agrofacil.mail;

import br.edu.ifba.agrofacil.config.AppProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/** Os links apontam para as rotas do painel (doc 3, Parte D). */
@Component
public class MailComposer {

  private final AppProperties props;

  public MailComposer(AppProperties props) {
    this.props = props;
  }

  public MailMessage twoFactorCode(String to, String code) {
    return new MailMessage(
        to,
        "Seu código de acesso ao Agrofácil: " + code,
        """
        Olá!

        Use este código para entrar no painel do Agrofácil:

            %s

        Ele vale por 10 minutos. Se não foi você que tentou entrar, troque sua senha.
        """
            .formatted(code));
  }

  public MailMessage invite(String to, String token) {
    return new MailMessage(
        to,
        "Convite para o painel do Agrofácil",
        """
        Olá!

        Você foi convidado para coordenar a associação no Agrofácil.
        Para criar sua senha, abra o link abaixo (vale por 7 dias):

            %s
        """
            .formatted(link("/convite", token)));
  }

  public MailMessage passwordReset(String to, String token) {
    return new MailMessage(
        to,
        "Redefinição de senha do Agrofácil",
        """
        Olá!

        Recebemos um pedido para trocar sua senha. Abra o link abaixo (vale por 30 minutos):

            %s

        Se não foi você, ignore este e-mail: sua senha continua a mesma.
        """
            .formatted(link("/redefinir-senha", token)));
  }

  /**
   * Aviso para um coordenador, com o link da tela no painel.
   *
   * @param panelPath rota do painel, ex.: {@code /certificacoes/<id>}
   */
  public MailMessage coordinatorNotice(String to, String subject, String text, String panelPath) {
    return new MailMessage(
        to,
        subject,
        """
        Olá!

        %s

        Veja no painel: %s
        """
            .formatted(text, props.publicUrl() + panelPath));
  }

  /** Aviso para um agricultor que cadastrou e-mail (o principal canal dele é o app). */
  public MailMessage farmerNotice(String to, String subject, String text) {
    return new MailMessage(
        to,
        subject,
        """
        Olá!

        %s

        Os detalhes também aparecem no app Agrofácil.
        """
            .formatted(text));
  }

  private String link(String path, String token) {
    return props.publicUrl() + path + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
  }
}

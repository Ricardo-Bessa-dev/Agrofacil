// Gateway de e-mail dos testes: guarda as mensagens em vez de enviar.
package br.edu.ifba.agrofacil.support;

import br.edu.ifba.agrofacil.mail.MailGateway;
import br.edu.ifba.agrofacil.mail.MailMessage;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

public class CapturingMailGateway implements MailGateway {

  private final List<MailMessage> sent = new CopyOnWriteArrayList<>();

  @Override
  public void send(MailMessage message) {
    sent.add(message);
  }

  /** Última mensagem enviada para o endereço. */
  public MailMessage lastTo(String to) {
    return sent.reversed().stream()
        .filter(m -> m.to().equalsIgnoreCase(to))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Nenhum e-mail para " + to));
  }

  /** Todas as mensagens enviadas para o endereço, da mais antiga para a mais nova. */
  public List<MailMessage> allTo(String to) {
    return sent.stream().filter(m -> m.to().equalsIgnoreCase(to)).toList();
  }

  /** Primeiro trecho do corpo que casa com a expressão (grupo 1). */
  public String extract(String to, String regex) {
    var matcher = Pattern.compile(regex).matcher(lastTo(to).body());
    if (!matcher.find()) {
      throw new AssertionError("Trecho não encontrado no e-mail: " + regex);
    }
    return matcher.group(1);
  }
}

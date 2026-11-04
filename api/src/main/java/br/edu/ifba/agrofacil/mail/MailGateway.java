// Porta de saída para envio de e-mail; nos testes é trocada por uma que só guarda as mensagens.
package br.edu.ifba.agrofacil.mail;

public interface MailGateway {

  /** Envia de fato; pode lançar exceção se o SMTP falhar. */
  void send(MailMessage message);
}

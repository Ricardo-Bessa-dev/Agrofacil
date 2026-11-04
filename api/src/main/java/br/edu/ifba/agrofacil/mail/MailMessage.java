// Um e-mail de texto simples a enviar.
package br.edu.ifba.agrofacil.mail;

/**
 * @param to destinatário
 * @param subject assunto
 * @param body texto puro (sem HTML)
 */
public record MailMessage(String to, String subject, String body) {}

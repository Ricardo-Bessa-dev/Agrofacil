// Códigos de erro do contrato (doc 1 §3.18), com o status HTTP, o título e a mensagem padrão.
package br.edu.ifba.agrofacil.common.error;

import java.util.Locale;
import org.springframework.http.HttpStatus;

/** O {@code code} que o front usa para decidir o que fazer; a mensagem é para exibir. */
public enum ErrorCode {
  BAD_REQUEST(HttpStatus.BAD_REQUEST, "Requisição inválida", "Não foi possível entender o pedido."),
  UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Não autenticado", "Entre de novo para continuar."),
  TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Sessão expirada", "Sua sessão expirou. Renove o acesso."),
  REFRESH_INVALID(
      HttpStatus.UNAUTHORIZED, "Sessão encerrada", "Sua sessão terminou. Entre de novo."),
  DEVICE_REVOKED(
      HttpStatus.UNAUTHORIZED,
      "Aparelho desconectado",
      "Este celular foi desconectado. Peça um novo código à associação."),
  INVALID_CREDENTIALS(
      HttpStatus.UNAUTHORIZED, "Dados de acesso incorretos", "E-mail ou senha não conferem."),
  FORBIDDEN(HttpStatus.FORBIDDEN, "Sem permissão", "Você não tem permissão para fazer isso."),
  USER_INACTIVE(
      HttpStatus.FORBIDDEN,
      "Acesso desativado",
      "Seu acesso está desativado. Fale com a associação."),
  NOT_FOUND(HttpStatus.NOT_FOUND, "Não encontrado", "Não encontramos o que você procura."),
  ATTACHMENT_NOT_FOUND(
      HttpStatus.NOT_FOUND, "Anexo não encontrado", "O anexo ainda não chegou ao servidor."),
  METHOD_NOT_ALLOWED(
      HttpStatus.METHOD_NOT_ALLOWED, "Método não permitido", "Esta operação não existe aqui."),
  VERSION_CONFLICT(
      HttpStatus.CONFLICT,
      "Conflito de versão",
      "Alguém alterou este item. Recarregue para ver a versão atual."),
  CPF_IN_USE(HttpStatus.CONFLICT, "CPF já cadastrado", "Já existe uma pessoa com este CPF."),
  EMAIL_IN_USE(
      HttpStatus.CONFLICT, "E-mail já cadastrado", "Já existe uma pessoa com este e-mail."),
  PROPERTY_HAS_ACTIVE_CERTIFICATION(
      HttpStatus.CONFLICT,
      "Propriedade certificada",
      "Esta propriedade tem certificação em vigor e não pode ser apagada."),
  PLANTING_HAS_DEPENDENTS(
      HttpStatus.CONFLICT,
      "Plantação com registros",
      "Apague antes as colheitas e aplicações desta plantação."),
  LAST_PLOT(
      HttpStatus.CONFLICT, "Última área", "A propriedade precisa ter pelo menos uma área ativa."),
  PLOT_NAME_IN_USE(
      HttpStatus.CONFLICT, "Nome repetido", "Já existe uma área com este nome na propriedade."),
  LAST_COORDINATOR(
      HttpStatus.CONFLICT,
      "Último coordenador",
      "Não é possível desativar o último coordenador ativo."),
  GROUP_NOT_EMPTY(
      HttpStatus.CONFLICT, "Grupo com agricultores", "Tire os agricultores do grupo antes."),
  INVALID_STATUS_TRANSITION(
      HttpStatus.CONFLICT, "Mudança não permitida", "Esta mudança de situação não é permitida."),
  VISIT_NOT_EDITABLE(
      HttpStatus.CONFLICT,
      "Visita não pode ser alterada",
      "Esta visita já foi concluída, cancelada ou passou do prazo de correção."),
  MEMBER_CONFLICT_EXISTS(
      HttpStatus.CONFLICT, "Impedimento já cadastrado", "Este impedimento já está cadastrado."),
  FILE_TOO_LARGE(
      HttpStatus.CONTENT_TOO_LARGE, "Arquivo grande demais", "O arquivo é grande demais."),
  UNSUPPORTED_MEDIA_TYPE(
      HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Formato não aceito", "O formato enviado não é aceito."),
  VALIDATION_ERROR(
      HttpStatus.UNPROCESSABLE_CONTENT,
      "Dados inválidos",
      "Alguns campos precisam ser corrigidos."),
  INVALID_CPF(
      HttpStatus.UNPROCESSABLE_CONTENT, "CPF inválido", "CPF inválido. Confira os números."),
  INVALID_ACCESS_CODE(
      HttpStatus.UNPROCESSABLE_CONTENT,
      "Código não confere",
      "CPF ou código não confere. Confira os números ou peça um novo código à associação."),
  ACCESS_CODE_LOCKED(
      HttpStatus.UNPROCESSABLE_CONTENT,
      "Código bloqueado",
      "Este código foi bloqueado. Peça um novo à associação."),
  INVALID_2FA_CODE(
      HttpStatus.UNPROCESSABLE_CONTENT, "Código incorreto", "O código não confere. Tente de novo."),
  EXPIRED_2FA_CODE(
      HttpStatus.UNPROCESSABLE_CONTENT, "Código vencido", "O código venceu. Entre de novo."),
  LOCKED_2FA_CODE(
      HttpStatus.UNPROCESSABLE_CONTENT,
      "Código bloqueado",
      "Muitas tentativas erradas. Entre de novo para receber outro código."),
  TOKEN_INVALID(
      HttpStatus.UNPROCESSABLE_CONTENT, "Link inválido", "Este link não vale mais. Peça um novo."),
  WEAK_PASSWORD(
      HttpStatus.UNPROCESSABLE_CONTENT,
      "Senha fraca",
      "A senha precisa ter pelo menos 10 caracteres e não pode ser uma senha comum."),
  FILE_TYPE_NOT_ALLOWED(
      HttpStatus.UNPROCESSABLE_CONTENT,
      "Tipo de arquivo não aceito",
      "Este tipo de arquivo não é aceito."),
  CHECKSUM_MISMATCH(
      HttpStatus.UNPROCESSABLE_CONTENT,
      "Arquivo corrompido",
      "O arquivo chegou diferente do enviado. Tente de novo."),
  VISITOR_NOT_ELIGIBLE(
      HttpStatus.UNPROCESSABLE_CONTENT,
      "Visitador não pode participar",
      "Uma das pessoas escolhidas não pode participar desta visita. Veja o motivo."),
  UNANNOUNCED_ONLY_VERIFICATION(
      HttpStatus.UNPROCESSABLE_CONTENT,
      "Visita sem aviso",
      "Só a visita de verificação pode ser feita sem aviso."),
  CHECKLIST_INCOMPLETE(
      HttpStatus.UNPROCESSABLE_CONTENT,
      "Roteiro incompleto",
      "Responda todas as perguntas do roteiro (\"Não deu para ver\" também vale)."),
  DOCUMENT_PRECONDITION_FAILED(
      HttpStatus.UNPROCESSABLE_CONTENT,
      "Documento ainda não pode ser gerado",
      "Ainda falta algo para gerar este documento."),
  APP_UPDATE_REQUIRED(
      HttpStatus.UPGRADE_REQUIRED, "Atualize o app", "Esta versão do app é antiga. Atualize."),
  RATE_LIMITED(
      HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas", "Muitas tentativas. Espere um pouco."),
  INTERNAL_ERROR(
      HttpStatus.INTERNAL_SERVER_ERROR,
      "Erro inesperado",
      "Aconteceu um erro inesperado. Tente de novo mais tarde.");

  private final HttpStatus status;
  private final String title;
  private final String defaultMessage;

  ErrorCode(HttpStatus status, String title, String defaultMessage) {
    this.status = status;
    this.title = title;
    this.defaultMessage = defaultMessage;
  }

  public HttpStatus status() {
    return status;
  }

  public String title() {
    return title;
  }

  public String defaultMessage() {
    return defaultMessage;
  }

  /** Trecho final do {@code type} do Problem Details, ex.: {@code validation-error}. */
  public String slug() {
    return name().toLowerCase(Locale.ROOT).replace('_', '-');
  }
}

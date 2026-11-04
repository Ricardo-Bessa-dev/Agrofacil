// Publica, edita e exclui comunicados, avisando os agricultores do público escolhido (RN23).
package br.edu.ifba.agrofacil.announcement;

import br.edu.ifba.agrofacil.attachment.Attachment;
import br.edu.ifba.agrofacil.attachment.AttachmentRepository;
import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.security.CurrentUser;
import br.edu.ifba.agrofacil.common.security.Role;
import br.edu.ifba.agrofacil.common.web.JsonPatch;
import br.edu.ifba.agrofacil.common.web.PageResponse;
import br.edu.ifba.agrofacil.common.web.VersionCheck;
import br.edu.ifba.agrofacil.group.GroupRepository;
import br.edu.ifba.agrofacil.notification.NotificationService;
import br.edu.ifba.agrofacil.notification.NotificationType;
import br.edu.ifba.agrofacil.user.AppUser;
import br.edu.ifba.agrofacil.user.FarmerProfileRepository;
import br.edu.ifba.agrofacil.user.UserRepository;
import java.time.Clock;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class AnnouncementService {

  /** Tamanho do trecho do comunicado mostrado na notificação e no push. */
  private static final int PREVIEW_CHARS = 140;

  private final AnnouncementRepository repository;
  private final GroupRepository groups;
  private final UserRepository users;
  private final FarmerProfileRepository profiles;
  private final AttachmentRepository attachments;
  private final NotificationService notifications;
  private final JsonPatch jsonPatch;
  private final Clock clock;

  public AnnouncementService(
      AnnouncementRepository repository,
      GroupRepository groups,
      UserRepository users,
      FarmerProfileRepository profiles,
      AttachmentRepository attachments,
      NotificationService notifications,
      JsonPatch jsonPatch,
      Clock clock) {
    this.repository = repository;
    this.groups = groups;
    this.users = users;
    this.profiles = profiles;
    this.attachments = attachments;
    this.notifications = notifications;
    this.jsonPatch = jsonPatch;
    this.clock = clock;
  }

  /** Página de comunicados da associação, dos mais novos para os mais antigos. */
  @Transactional(readOnly = true)
  public PageResponse<AnnouncementView> list(
      CurrentUser user, AnnouncementType type, Pageable page) {
    Page<Announcement> result =
        type == null
            ? repository.findByAssociationIdAndDeletedAtIsNull(user.associationId(), page)
            : repository.findByAssociationIdAndTypeAndDeletedAtIsNull(
                user.associationId(), type, page);
    return PageResponse.of(result, views(result.getContent()));
  }

  /**
   * Publica e avisa cada agricultor ativo do público com notificação e push (RN23).
   *
   * @throws ApiException 422 se o grupo não for da associação
   */
  @Transactional
  public AnnouncementView publish(CurrentUser user, AnnouncementWrite write) {
    if (write.groupId() != null
        && groups
            .findByIdAndAssociationIdAndDeletedAtIsNull(write.groupId(), user.associationId())
            .isEmpty()) {
      throw ApiException.validation("groupId", "NOT_FOUND", "Grupo não encontrado.");
    }
    var announcement =
        new Announcement(user.associationId(), write.groupId(), clock.instant(), user.id());
    announcement.update(write.type(), write.title().trim(), write.body().trim());
    repository.saveAndFlush(announcement);
    notifications.notify(
        recipients(user.associationId(), write.groupId()),
        NotificationType.ANNOUNCEMENT,
        announcement.getTitle(),
        preview(announcement.getBody()),
        Map.of("announcementId", announcement.getId().toString()));
    return views(List.of(announcement)).getFirst();
  }

  /** Edição do texto; não reenvia o push. O público não muda depois de publicar. */
  @Transactional
  public AnnouncementView patch(CurrentUser user, UUID id, JsonNode body) {
    var announcement = findVisible(user, id);
    var current = views(List.of(announcement)).getFirst();
    var write = jsonPatch.merge(current.toWrite(), body, AnnouncementWrite.class);
    VersionCheck.ensure(body, write.version(), announcement.getVersion());
    if (!Objects.equals(write.groupId(), announcement.getGroupId())) {
      // Mudar o público deixaria cópias velhas nos celulares de quem saiu dele.
      throw ApiException.validation(
          "groupId", "NOT_EDITABLE", "Não é possível mudar o público. Apague e publique de novo.");
    }
    announcement.update(write.type(), write.title().trim(), write.body().trim());
    return views(List.of(repository.saveAndFlush(announcement))).getFirst();
  }

  /** Exclusão lógica: some do app no próximo pull (lápide). */
  @Transactional
  public void delete(CurrentUser user, UUID id) {
    findVisible(user, id).markDeleted(clock.instant());
  }

  /** Converte em lote, achando a foto de cada comunicado sem uma consulta por linha. */
  @Transactional(readOnly = true)
  public List<AnnouncementView> views(List<Announcement> list) {
    if (list.isEmpty()) {
      return List.of();
    }
    var images = new HashMap<UUID, UUID>();
    attachments
        .findByOwnerTypeAndOwnerIdInAndKindAndDeletedAtIsNull(
            Attachment.OwnerType.ANNOUNCEMENT,
            list.stream().map(Announcement::getId).toList(),
            Attachment.Kind.PHOTO)
        .stream()
        .collect(Collectors.groupingBy(Attachment::getOwnerId))
        .forEach((owner, photos) ->
            photos.stream()
                .max(Comparator.comparing(Attachment::getCreatedAt))
                .ifPresent(a -> images.put(owner, a.getId())));
    return list.stream()
        .map(a ->
            new AnnouncementView(
                a.getId(),
                a.getType(),
                a.getTitle(),
                a.getBody(),
                a.getGroupId(),
                images.get(a.getId()),
                a.getPublishedAt(),
                a.getCreatedBy(),
                a.getVersion()))
        .toList();
  }

  /** Comunicado da associação, não excluído; 404 caso contrário. */
  @Transactional(readOnly = true)
  public Announcement findVisible(CurrentUser user, UUID id) {
    return repository
        .findByIdAndAssociationIdAndDeletedAtIsNull(id, user.associationId())
        .orElseThrow(ApiException::notFound);
  }

  /** Agricultores ativos da associação, ou só os do grupo. */
  private List<UUID> recipients(UUID associationId, UUID groupId) {
    var farmers =
        groupId == null
            ? users.findByAssociationIdAndRoleAndDeletedAtIsNullOrderByName(
                associationId, Role.FARMER)
            : profiles.findFarmersOfGroup(groupId);
    return farmers.stream()
        .filter(u -> u.getDeletedAt() == null && u.isActive())
        .map(AppUser::getId)
        .toList();
  }

  private static String preview(String body) {
    return body.length() <= PREVIEW_CHARS ? body : body.substring(0, PREVIEW_CHARS - 1) + "…";
  }
}

// Simulação e confirmação da retenção dos agricultores desligados (doc 1 §5.7).
package br.edu.ifba.agrofacil.retention;

import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.security.CurrentUser;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FarmerRetentionService {

  private static final String ELIGIBLE =
      """
      from app_user u join association s on s.id = u.association_id
      where u.association_id = :association
        and u.role = 'FARMER' and u.status = 'INACTIVE' and u.deleted_at is null
        and u.departed_at <= cast(:now as timestamptz) - make_interval(years => s.retention_years)
      """;

  private final JdbcClient jdbc;
  private final Clock clock;

  public FarmerRetentionService(JdbcClient jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  public record FarmerRetentionCandidate(
      UUID id,
      String name,
      Instant departedAt,
      long attachments,
      long documents,
      long sharedDocumentsToReview) {}

  @Transactional(readOnly = true)
  public List<FarmerRetentionCandidate> preview(CurrentUser user) {
    return jdbc.sql(
            """
            select u.id, u.name, u.departed_at,
              (select count(*) from farmer_retention_attachments(u.id)) as attachments,
              (select count(*) from farmer_retention_documents(u.id)) as documents,
              (select count(*) from generated_document d
               where d.association_id = u.association_id and d.storage_key <> ''
                 and not exists (select 1 from farmer_retention_documents(u.id) x where x.id = d.id)
                 and jsonb_path_exists(d.data_snapshot, '$.** ? (@ == $name)', jsonb_build_object('name', u.name))
              ) as shared_documents_to_review
            """
                + ELIGIBLE
                + " order by u.departed_at, u.id")
        .param("association", user.associationId())
        .param("now", Timestamp.from(clock.instant()))
        .query((row, index) ->
            new FarmerRetentionCandidate(
                row.getObject("id", UUID.class),
                row.getString("name"),
                row.getTimestamp("departed_at").toInstant(),
                row.getLong("attachments"),
                row.getLong("documents"),
                row.getLong("shared_documents_to_review")))
        .list();
  }

  @Transactional
  public void anonymize(CurrentUser user, UUID id) {
    jdbc.sql("select u.id " + ELIGIBLE + " and u.id = :id for update of u")
        .param("association", user.associationId())
        .param("now", Timestamp.from(clock.instant()))
        .param("id", id)
        .query(UUID.class)
        .optional()
        .orElseThrow(ApiException::notFound);
    jdbc.sql("select anonymize_departed_farmer(:id)")
        .param("id", id)
        .query((row, index) -> true)
        .single();
  }
}

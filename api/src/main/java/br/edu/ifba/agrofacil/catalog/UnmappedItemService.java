// Itens sem catálogo: textos livres ("Outro") dos registros e a associação a um item (RN22).
package br.edu.ifba.agrofacil.catalog;

import br.edu.ifba.agrofacil.catalog.CatalogViews.ResolveResult;
import br.edu.ifba.agrofacil.catalog.CatalogViews.UnmappedItem;
import br.edu.ifba.agrofacil.catalog.CatalogWrites.ResolveUnmapped;
import br.edu.ifba.agrofacil.catalog.CatalogWrites.UnmappedKind;
import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.security.CurrentUser;
import br.edu.ifba.agrofacil.record.InputApplication;
import br.edu.ifba.agrofacil.record.InputPurchase;
import br.edu.ifba.agrofacil.record.Outflow;
import br.edu.ifba.agrofacil.record.Planting;
import br.edu.ifba.agrofacil.record.RecordRepository;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Os textos são agrupados sem diferenciar maiúsculas e espaços nas pontas. Resolver altera os
 * registros pelo JPA (e não por SQL em massa) para gerar histórico e o novo sync_seq de cada um.
 */
@Service
public class UnmappedItemService {

  /** Textos livres de cultura (plantio e saída) e de insumo (aplicação e compra). */
  private static final String CROP_TEXTS =
      "select record_id, crop_other_name as name from planting"
          + " where crop_id is null and crop_other_name is not null"
          + " union all select record_id, product_other_name from outflow"
          + " where crop_id is null and product_other_name is not null";

  private static final String INPUT_TEXTS =
      "select record_id, input_other_name as name from input_application"
          + " where input_id is null and input_other_name is not null"
          + " union all select record_id, input_other_name from input_purchase"
          + " where input_id is null and input_other_name is not null";

  private final JdbcClient jdbc;
  private final RecordRepository records;
  private final CatalogAdminService catalog;

  public UnmappedItemService(
      JdbcClient jdbc, RecordRepository records, CatalogAdminService catalog) {
    this.jdbc = jdbc;
    this.records = records;
    this.catalog = catalog;
  }

  /** Textos livres agrupados, dos mais usados para os menos usados. */
  @Transactional(readOnly = true)
  public List<UnmappedItem> list(CurrentUser user, UnmappedKind kind) {
    var kinds = kind == null ? List.of(UnmappedKind.values()) : List.of(kind);
    var result = new ArrayList<UnmappedItem>();
    for (var k : kinds) {
      result.addAll(
          jdbc.sql(
                  "select min(trim(t.name)) as text, count(*) as occurrences,"
                      + " max(r.performed_on) as last_used_on from ("
                      + textsOf(k)
                      + ") t join management_record r on r.id = t.record_id"
                      + " where r.association_id = ? and r.deleted_at is null"
                      + " and trim(t.name) <> '' group by lower(trim(t.name))"
                      + " order by occurrences desc, text")
              .param(user.associationId())
              .query((rs, i) ->
                  new UnmappedItem(
                      rs.getString("text"),
                      k,
                      rs.getLong("occurrences"),
                      rs.getObject("last_used_on", Date.class).toLocalDate()))
              .list());
    }
    return result;
  }

  /** Quantidade de textos distintos sem catálogo (para o painel inicial). */
  @Transactional(readOnly = true)
  public int count(CurrentUser user) {
    return list(user, null).size();
  }

  /**
   * Associa o texto a um item do catálogo em todos os registros da associação que o usam.
   *
   * @return quantos registros mudaram
   */
  @Transactional
  public ResolveResult resolve(CurrentUser user, ResolveUnmapped req) {
    UUID target = req.targetId();
    try {
      if (req.kind() == UnmappedKind.CROP) {
        catalog.findCrop(user, target);
      } else {
        catalog.findInput(user, target);
      }
    } catch (ApiException e) {
      throw ApiException.validation("targetId", "NOT_FOUND", "Item do catálogo não encontrado.");
    }
    List<UUID> ids =
        jdbc.sql(
                "select t.record_id from ("
                    + textsOf(req.kind())
                    + ") t join management_record r on r.id = t.record_id"
                    + " where r.association_id = ? and r.deleted_at is null"
                    + " and lower(trim(t.name)) = lower(trim(?))")
            .params(user.associationId(), req.text())
            .query(UUID.class)
            .list();
    for (var record : records.findByIdIn(ids)) {
      switch (record) {
        case Planting p -> p.mapCrop(target);
        case Outflow o -> o.mapCrop(target);
        case InputApplication a -> a.mapInput(target);
        case InputPurchase p -> p.mapInput(target);
        default -> throw new IllegalStateException("Registro sem texto livre: " + record.getId());
      }
    }
    return new ResolveResult(ids.size());
  }

  private static String textsOf(UnmappedKind kind) {
    return kind == UnmappedKind.CROP ? CROP_TEXTS : INPUT_TEXTS;
  }
}

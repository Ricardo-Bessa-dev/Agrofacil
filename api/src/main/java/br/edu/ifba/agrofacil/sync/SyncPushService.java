// Processa o lote de mutações do app; cada mutação roda numa transação própria (doc 1 §4.7).
package br.edu.ifba.agrofacil.sync;

import br.edu.ifba.agrofacil.common.error.ApiException;
import br.edu.ifba.agrofacil.common.error.ErrorCode;
import br.edu.ifba.agrofacil.common.security.CurrentUser;
import br.edu.ifba.agrofacil.user.DeviceService;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * O método não é transacional de propósito: uma rejeição não desfaz as outras mutações. Exceções
 * que não são de negócio sobem e viram 500; o app repete o lote inteiro, o que é seguro pelo {@code
 * mutationId}.
 */
@Service
public class SyncPushService {

  private static final Logger log = LoggerFactory.getLogger(SyncPushService.class);

  private final SyncMutationRepository mutations;
  private final Map<String, MutationHandler> handlers;
  private final DeviceService devices;
  private final TransactionTemplate tx;
  private final ObjectMapper mapper;
  private final Clock clock;

  public SyncPushService(
      SyncMutationRepository mutations,
      List<MutationHandler> handlers,
      DeviceService devices,
      PlatformTransactionManager txManager,
      ObjectMapper mapper,
      Clock clock) {
    this.mutations = mutations;
    this.handlers =
        handlers.stream().collect(Collectors.toMap(MutationHandler::entity, Function.identity()));
    this.devices = devices;
    this.tx = new TransactionTemplate(txManager);
    this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.mapper = mapper;
    this.clock = clock;
  }

  /**
   * Aplica as mutações na ordem enviada.
   *
   * @return um resultado por mutação; 200 mesmo quando há rejeições
   */
  public SyncDtos.PushResponse push(CurrentUser user, SyncDtos.PushRequest req) {
    if (!req.deviceId().equals(user.deviceId())) {
      throw new ApiException(ErrorCode.DEVICE_REVOKED);
    }
    tx.executeWithoutResult(s -> devices.touch(user, req.deviceId()));
    var results = req.mutations().stream().map(m -> processOne(user, m)).toList();
    return new SyncDtos.PushResponse(results, clock.instant());
  }

  private SyncDtos.MutationResult processOne(CurrentUser user, SyncDtos.Mutation m) {
    var previous = mutations.findById(m.mutationId());
    if (previous.isPresent()) {
      if (!previous.get().getUserId().equals(user.id())) {
        return rejected(m, new ApiException(ErrorCode.NOT_FOUND)); // id de mutação de outra pessoa
      }
      return mapper.readValue(previous.get().getResponse(), SyncDtos.MutationResult.class);
    }
    var handler = handlers.get(m.entity());
    if (handler == null) {
      var result =
          rejected(m, ApiException.validation("entity", "NOT_SUPPORTED", "Tipo não aceito."));
      tx.executeWithoutResult(s -> save(user, m, result));
      return result;
    }
    try {
      return tx.execute(status -> {
        Long version = handler.apply(user, m);
        var result =
            new SyncDtos.MutationResult(
                m.mutationId(), SyncDtos.ResultStatus.APPLIED, m.entityId(), version, null);
        save(user, m, result); // na mesma transação do dado: ou os dois, ou nenhum
        return result;
      });
    } catch (ApiException e) {
      log.info("Mutação {} rejeitada: {}", m.entity(), e.code());
      var result = rejected(m, e);
      tx.executeWithoutResult(s -> save(user, m, result));
      return result;
    }
  }

  private void save(CurrentUser user, SyncDtos.Mutation m, SyncDtos.MutationResult result) {
    mutations.save(
        new SyncMutation(
            m.mutationId(),
            user.id(),
            user.deviceId(),
            m.entity(),
            m.entityId(),
            result.status().name(),
            mapper.writeValueAsString(result),
            clock.instant()));
  }

  private static SyncDtos.MutationResult rejected(SyncDtos.Mutation m, ApiException e) {
    return new SyncDtos.MutationResult(
        m.mutationId(),
        SyncDtos.ResultStatus.REJECTED,
        m.entityId(),
        null,
        new SyncDtos.MutationError(e.code().name(), e.getMessage(), e.fieldErrors()));
  }
}

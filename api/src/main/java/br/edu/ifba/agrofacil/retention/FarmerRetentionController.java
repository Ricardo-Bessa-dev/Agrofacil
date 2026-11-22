package br.edu.ifba.agrofacil.retention;

import br.edu.ifba.agrofacil.common.security.CurrentUserProvider;
import br.edu.ifba.agrofacil.retention.FarmerRetentionService.FarmerRetentionCandidate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FarmerRetentionController {

  private final CurrentUserProvider currentUser;
  private final FarmerRetentionService retention;

  public FarmerRetentionController(
      CurrentUserProvider currentUser, FarmerRetentionService retention) {
    this.currentUser = currentUser;
    this.retention = retention;
  }

  @GetMapping("/retention/farmers")
  public List<FarmerRetentionCandidate> previewFarmerRetention() {
    return retention.preview(currentUser.get());
  }

  @PostMapping("/retention/farmers/{id}/anonymize")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void anonymizeFarmer(@PathVariable UUID id) {
    retention.anonymize(currentUser.get(), id);
  }
}

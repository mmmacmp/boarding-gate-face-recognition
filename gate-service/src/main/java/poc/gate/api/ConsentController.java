package poc.gate.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import poc.gate.gallery.ConsentService;
import poc.gate.gallery.PassengerConsent;

/** A passenger opts in or out of biometric boarding. Withdrawal applies from the very next gate event. */
@RestController
public class ConsentController {

    public record ConsentRequest(String profileId) {
    }

    public record ConsentResponse(String profileId, PassengerConsent.Status status) {
    }

    private final ConsentService consents;

    public ConsentController(ConsentService consents) {
        this.consents = consents;
    }

    @PostMapping("/v1/consents")
    @ResponseStatus(HttpStatus.CREATED)
    public ConsentResponse grant(@RequestBody ConsentRequest request) {
        PassengerConsent consent = consents.grant(request.profileId());
        return new ConsentResponse(consent.getProfileId(), consent.getStatus());
    }

    @DeleteMapping("/v1/consents/{profileId}")
    public ResponseEntity<ConsentService.Withdrawal> withdraw(@PathVariable String profileId) {
        return ResponseEntity.of(consents.withdraw(profileId)); // 404 if the profile never consented
    }
}

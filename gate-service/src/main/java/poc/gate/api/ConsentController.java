package poc.gate.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import poc.gate.gallery.PassengerConsent;
import poc.gate.gallery.PassengerConsentRepository;

/** Records a passenger's opt-in. (Withdrawal comes in Step 4.) */
@RestController
public class ConsentController {

    public record ConsentRequest(String profileId) {
    }

    public record ConsentResponse(String profileId, PassengerConsent.Status status) {
    }

    private final PassengerConsentRepository consents;

    public ConsentController(PassengerConsentRepository consents) {
        this.consents = consents;
    }

    @PostMapping("/v1/consents")
    @ResponseStatus(HttpStatus.CREATED)
    public ConsentResponse grant(@RequestBody ConsentRequest request) {
        PassengerConsent consent = consents.save(new PassengerConsent(request.profileId())); // save = insert or update
        return new ConsentResponse(consent.getProfileId(), consent.getStatus());
    }
}

package tn.oliveconnect.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** What the Admin adds when approving: the request has no member number or delegation yet. */
public record RegistrationApproveDto(@NotBlank String memberNumber, @NotNull Delegation delegation) {
}

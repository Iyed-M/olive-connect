package tn.oliveconnect.user;

/** Response of approve. The temporary password is returned this once and is never stored in clear. */
public record RegistrationApprovalDto(RegistrationDto registration, String userId, String temporaryPassword) {
}

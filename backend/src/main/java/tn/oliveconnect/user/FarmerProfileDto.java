package tn.oliveconnect.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record FarmerProfileDto(
        @NotBlank String memberNumber,
        @NotBlank @Pattern(regexp = "\\d{8}", message = "must be exactly 8 digits") String cin,
        String address,
        @NotNull Delegation delegation) {

    static FarmerProfileDto from(FarmerProfile profile) {
        return profile == null ? null
                : new FarmerProfileDto(profile.getMemberNumber(), profile.getCin(), profile.getAddress(),
                        profile.getDelegation());
    }

    FarmerProfile toEntity() {
        FarmerProfile profile = new FarmerProfile();
        profile.setMemberNumber(memberNumber.trim());
        profile.setCin(cin);
        profile.setAddress(address);
        profile.setDelegation(delegation);
        return profile;
    }
}

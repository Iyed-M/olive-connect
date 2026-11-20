package tn.oliveconnect.user;

import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import tn.oliveconnect.common.ConflictException;
import tn.oliveconnect.common.NotFoundException;

/** Visitor registration requests (FR-6.1) and their approval by an Admin. */
@Service
public class RegistrationService {

    // no 0/O/1/l/I: the Admin may read the password to the Farmer
    private static final String PASSWORD_CHARS = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int PASSWORD_LENGTH = 12;

    private final RegistrationRequestRepository registrationRequestRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    public RegistrationService(RegistrationRequestRepository registrationRequestRepository, UserRepository userRepository,
                               PasswordEncoder passwordEncoder) {
        this.registrationRequestRepository = registrationRequestRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public RegistrationDto create(RegistrationCreateDto dto) {
        if (userRepository.findByEmail(dto.email()).isPresent()
                || registrationRequestRepository.existsByEmailAndStatus(dto.email(), RegistrationStatus.PENDING)) {
            throw new ConflictException("EMAIL_TAKEN", "This email is already used");
        }
        RegistrationRequest request = new RegistrationRequest();
        request.setFirstName(dto.firstName().trim());
        request.setLastName(dto.lastName().trim());
        request.setEmail(dto.email());
        request.setPhone(dto.phone());
        request.setCin(dto.cin());
        request.setMessage(dto.message());
        return RegistrationDto.from(registrationRequestRepository.save(request));
    }

    /** Newest first. The status filter is optional. */
    public List<RegistrationDto> list(RegistrationStatus status) {
        Sort newestFirst = Sort.by(Sort.Direction.DESC, "createdAt");
        List<RegistrationRequest> requests = status == null
                ? registrationRequestRepository.findAll(newestFirst)
                : registrationRequestRepository.findByStatus(status, newestFirst);
        return requests.stream().map(RegistrationDto::from).toList();
    }

    public RegistrationApprovalDto approve(String id, RegistrationApproveDto dto, String adminId) {
        RegistrationRequest request = findPending(id);
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new ConflictException("EMAIL_TAKEN", "This email is already used");
        }
        String memberNumber = dto.memberNumber().trim();
        if (userRepository.findByFarmerProfileMemberNumber(memberNumber).isPresent()) {
            throw new ConflictException("MEMBER_NUMBER_TAKEN", "This member number is already used");
        }

        FarmerProfile profile = new FarmerProfile();
        profile.setMemberNumber(memberNumber);
        profile.setCin(request.getCin());
        profile.setDelegation(dto.delegation());

        String temporaryPassword = generatePassword();
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPhone(request.getPhone());
        user.setRoles(EnumSet.of(Role.FARMER));
        user.setFarmerProfile(profile);
        user.setMustChangePassword(true);
        User saved = userRepository.save(user);

        request.setStatus(RegistrationStatus.APPROVED);
        request.setProcessedById(adminId);
        request.setCreatedUserId(saved.getId());
        RegistrationRequest processed = registrationRequestRepository.save(request);
        return new RegistrationApprovalDto(RegistrationDto.from(processed), saved.getId(), temporaryPassword);
    }

    public RegistrationDto reject(String id, String adminId) {
        RegistrationRequest request = findPending(id);
        request.setStatus(RegistrationStatus.REJECTED);
        request.setProcessedById(adminId);
        return RegistrationDto.from(registrationRequestRepository.save(request));
    }

    private RegistrationRequest findPending(String id) {
        RegistrationRequest request = registrationRequestRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Registration request not found"));
        if (request.getStatus() != RegistrationStatus.PENDING) {
            throw new ConflictException("REGISTRATION_ALREADY_PROCESSED", "This request was already processed");
        }
        return request;
    }

    private String generatePassword() {
        StringBuilder password = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            password.append(PASSWORD_CHARS.charAt(random.nextInt(PASSWORD_CHARS.length())));
        }
        return password.toString();
    }
}

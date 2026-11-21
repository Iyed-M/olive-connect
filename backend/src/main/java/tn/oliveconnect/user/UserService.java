package tn.oliveconnect.user;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import tn.oliveconnect.auth.RefreshTokenStore;
import tn.oliveconnect.common.BusinessRuleException;
import tn.oliveconnect.common.ConflictException;
import tn.oliveconnect.common.FieldValidationException;
import tn.oliveconnect.common.NotFoundException;
import tn.oliveconnect.common.PageResponse;
import tn.oliveconnect.mill.MillRepository;

/** Admin management of Users (M1b). Users are never deleted: disabling replaces deletion. */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenStore refreshTokenStore;
    private final MillRepository millRepository;

    public UserService(UserRepository userRepository, MongoTemplate mongoTemplate, PasswordEncoder passwordEncoder,
                       RefreshTokenStore refreshTokenStore, MillRepository millRepository) {
        this.userRepository = userRepository;
        this.mongoTemplate = mongoTemplate;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenStore = refreshTokenStore;
        this.millRepository = millRepository;
    }

    /** Both filters are optional. Sorted by lastName (then id, so pages are stable). */
    public PageResponse<UserDto> list(Role role, Boolean enabled, int page, int size) {
        Query filter = new Query();
        if (role != null) {
            filter.addCriteria(Criteria.where("roles").is(role));
        }
        if (enabled != null) {
            filter.addCriteria(Criteria.where("enabled").is(enabled));
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by("lastName", "id"));
        var users = mongoTemplate.find(Query.of(filter).with(pageable), User.class);
        Page<User> result = PageableExecutionUtils.getPage(users, pageable,
                () -> mongoTemplate.count(Query.of(filter).limit(-1).skip(-1), User.class));
        return PageResponse.of(result, UserDto::from);
    }

    public UserDto get(String id) {
        return UserDto.from(find(id));
    }

    public UserDto create(UserCreateDto dto) {
        User user = new User();
        user.setEmail(normalize(dto.email()));
        checkEmailFree(user.getEmail(), null);
        user.setPasswordHash(passwordEncoder.encode(dto.password()));
        applyDetails(user, dto.firstName(), dto.lastName(), dto.phone(), dto.roles(), dto.farmerProfile(),
                dto.assignedMillId());
        return UserDto.from(userRepository.save(user));
    }

    public UserDto update(String id, UserUpdateDto dto, String actingUserId) {
        User user = find(id);
        if (id.equals(actingUserId) && !dto.roles().contains(Role.ADMIN)) {
            throw new BusinessRuleException("SELF_LOCKOUT", "You cannot remove your own ADMIN role");
        }

        String email = normalize(dto.email());
        boolean emailChanged = !email.equals(user.getEmail());
        if (emailChanged) {
            checkEmailFree(email, id);
            user.setEmail(email);
        }
        boolean passwordReset = dto.password() != null;
        if (passwordReset) {
            user.setPasswordHash(passwordEncoder.encode(dto.password()));
        }
        applyDetails(user, dto.firstName(), dto.lastName(), dto.phone(), dto.roles(), dto.farmerProfile(),
                dto.assignedMillId());
        User saved = userRepository.save(user);

        // the old email / password must stop working everywhere, not only when the access token expires
        if (emailChanged || passwordReset) {
            refreshTokenStore.revokeAll(id);
        }
        return UserDto.from(saved);
    }

    public void enable(String id) {
        User user = find(id);
        user.setEnabled(true);
        userRepository.save(user);
    }

    public void disable(String id, String actingUserId) {
        if (id.equals(actingUserId)) {
            throw new BusinessRuleException("SELF_LOCKOUT", "You cannot disable your own account");
        }
        User user = find(id);
        user.setEnabled(false);
        userRepository.save(user);
        refreshTokenStore.revokeAll(id);
    }

    private User find(String id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void checkEmailFree(String email, String ownId) {
        userRepository.findByEmail(email).filter(other -> !other.getId().equals(ownId)).ifPresent(other -> {
            throw new ConflictException("EMAIL_TAKEN", "This email is already used");
        });
    }

    /** Fields shared by create and update. The Farmer profile is required when FARMER is a role. */
    private void applyDetails(User user, String firstName, String lastName, String phone, Set<Role> roles,
                              FarmerProfileDto profile, String assignedMillId) {
        user.setFirstName(firstName.trim());
        user.setLastName(lastName.trim());
        user.setPhone(phone);
        user.setRoles(EnumSet.copyOf(roles));
        validateMillAssignment(roles, assignedMillId);
        user.setAssignedMillId(assignedMillId);
        // when FARMER is removed, the stored profile is kept but ignored
        if (roles.contains(Role.FARMER)) {
            if (profile == null) {
                throw new FieldValidationException("farmerProfile", "is required for a Farmer");
            }
            FarmerProfile entity = profile.toEntity();
            userRepository.findByFarmerProfileMemberNumber(entity.getMemberNumber())
                    .filter(other -> !other.getId().equals(user.getId()))
                    .ifPresent(other -> {
                        throw new ConflictException("MEMBER_NUMBER_TAKEN", "This member number is already used");
                    });
            user.setFarmerProfile(entity);
        }
    }

    private void validateMillAssignment(Set<Role> roles, String assignedMillId) {
        if (assignedMillId == null) {
            return;
        }
        if (!roles.contains(Role.MILL_MANAGER)) {
            throw new BusinessRuleException("MILL_ASSIGNMENT_REQUIRES_ROLE",
                    "Only a Mill manager can be assigned to a mill");
        }
        if (millRepository.findById(assignedMillId).isEmpty()) {
            throw new BusinessRuleException("MILL_NOT_FOUND", "The assigned mill does not exist");
        }
    }
}

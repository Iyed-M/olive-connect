package tn.oliveconnect.resource;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tn.oliveconnect.common.BusinessRuleException;
import tn.oliveconnect.common.ConflictException;
import tn.oliveconnect.round.CollectionRoundRepository;

/** Checks and persists the resource bookings that belong to a collection round. */
@Service
public class BookingService {

    private static final Duration LOCK_TTL = Duration.ofSeconds(5);
    private static final Duration LOCK_RENEWAL_INTERVAL = Duration.ofSeconds(1);
    private static final DefaultRedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);
    private static final DefaultRedisScript<Long> RENEW_LOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end",
            Long.class);

    private final ResourceBookingRepository bookingRepository;
    private final WorkerRepository workerRepository;
    private final EquipmentRepository equipmentRepository;
    private final CollectionRoundRepository roundRepository;
    private final StringRedisTemplate redis;
    private final ScheduledExecutorService lockRenewer = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "oliveconnect-booking-lock-renewer");
        thread.setDaemon(true);
        return thread;
    });

    public BookingService(ResourceBookingRepository bookingRepository, WorkerRepository workerRepository,
                          EquipmentRepository equipmentRepository, CollectionRoundRepository roundRepository,
                          StringRedisTemplate redis) {
        this.bookingRepository = bookingRepository;
        this.workerRepository = workerRepository;
        this.equipmentRepository = equipmentRepository;
        this.roundRepository = roundRepository;
        this.redis = redis;
    }

    public void checkAndBook(String roundId, String teamLeaderId, List<String> workerIds, List<String> equipmentIds,
                             Instant start, Instant end) {
        List<ResourceRef> resources = resolveResources(teamLeaderId, workerIds, equipmentIds, start, end);
        List<String> resourceIds = resources.stream().map(ResourceRef::id).toList();
        List<ResourceLock> locks = acquireLocks(resources);
        LockLease lease = new LockLease(locks);
        boolean handOffToTransaction = false;
        try {
            List<ResourceBooking> conflicts = bookingRepository.findOverlapping(resourceIds, start, end);
            if (!conflicts.isEmpty()) {
                ResourceBooking conflict = conflicts.get(0);
                ResourceRef resource = resources.stream()
                        .filter(candidate -> candidate.id().equals(conflict.getResourceId()))
                        .findFirst().orElse(null);
                String label = resource == null ? conflict.getResourceId() : resource.label();
                String roundCode = roundRepository.findById(conflict.getRoundId())
                        .map(round -> round.getCode() == null ? conflict.getRoundId() : round.getCode())
                        .orElse(conflict.getRoundId());
                throw new ConflictException("BOOKING_CONFLICT",
                        label + " is already booked by round " + roundCode + " ("
                                + conflict.getStart() + "–" + conflict.getEnd() + ").");
            }

            List<ResourceBooking> bookings = resources.stream().map(resource -> resource.toBooking(roundId, start, end))
                    .toList();
            bookingRepository.saveAll(bookings);
            registerReleaseAfterTransaction(lease);
            handOffToTransaction = true;
        } finally {
            if (!handOffToTransaction) {
                lease.close();
            }
        }
    }

    @PreDestroy
    void stopLockRenewer() {
        lockRenewer.shutdownNow();
    }

    public void releaseByRoundId(String roundId) {
        List<ResourceBooking> bookings = bookingRepository.findByRoundId(roundId);
        bookings.forEach(booking -> booking.setStatus(BookingStatus.RELEASED));
        if (!bookings.isEmpty()) {
            bookingRepository.saveAll(bookings);
        }
    }

    private List<ResourceRef> resolveResources(String teamLeaderId, List<String> workerIds, List<String> equipmentIds,
                                               Instant start, Instant end) {
        List<String> workers = workerIds == null ? List.of() : workerIds;
        List<String> equipment = equipmentIds == null ? List.of() : equipmentIds;
        Set<String> uniqueIds = new HashSet<>();
        List<ResourceRef> resources = new ArrayList<>();

        if (teamLeaderId != null && !teamLeaderId.isBlank()) {
            resources.add(new ResourceRef(teamLeaderId, ResourceKind.TEAM_LEADER,
                    ResourceKind.TEAM_LEADER.name(), "Team leader " + teamLeaderId));
            uniqueIds.add(teamLeaderId);
        }

        for (String id : workers) {
            if (!uniqueIds.add(id)) {
                throw new BusinessRuleException("DUPLICATE_RESOURCE", "Resource " + id + " was selected twice");
            }
            Worker worker = workerRepository.findById(id)
                    .orElseThrow(() -> new BusinessRuleException("RESOURCE_NOT_FOUND", "Worker " + id + " not found"));
            if (!worker.isActive()) {
                throw new BusinessRuleException("RESOURCE_UNAVAILABLE", "Worker " + id + " is not active");
            }
            assertNoUnavailability(worker.getUnavailabilities(), start, end, id);
            resources.add(new ResourceRef(id, ResourceKind.WORKER, ResourceKind.WORKER.name(),
                    worker.getFirstName() + " " + worker.getLastName()));
        }

        for (String id : equipment) {
            if (!uniqueIds.add(id)) {
                throw new BusinessRuleException("DUPLICATE_RESOURCE", "Resource " + id + " was selected twice");
            }
            Equipment item = equipmentRepository.findById(id)
                    .orElseThrow(() -> new BusinessRuleException("RESOURCE_NOT_FOUND", "Equipment " + id + " not found"));
            if (item.getStatus() != EquipmentStatus.AVAILABLE) {
                throw new BusinessRuleException("RESOURCE_UNAVAILABLE", "Equipment " + item.getCode()
                        + " is not available");
            }
            assertNoUnavailability(item.getUnavailabilities(), start, end, item.getCode());
            resources.add(new ResourceRef(id, ResourceKind.EQUIPMENT, item.getType().name(), item.getCode()));
        }
        return resources;
    }

    private void assertNoUnavailability(List<Unavailability> periods, Instant start, Instant end, String label) {
        boolean unavailable = periods.stream().anyMatch(period -> period.getFrom().isBefore(end)
                && period.getTo().isAfter(start));
        if (unavailable) {
            throw new BusinessRuleException("RESOURCE_UNAVAILABLE", label + " is unavailable for this slot");
        }
    }

    private List<ResourceLock> acquireLocks(List<ResourceRef> resources) {
        List<ResourceLock> locks = resources.stream().distinct()
                .sorted(java.util.Comparator.comparing(ResourceRef::lockId))
                .map(resource -> new ResourceLock("lock:resource:" + resource.lockId(),
                        UUID.randomUUID().toString())).toList();
        List<ResourceLock> acquired = new ArrayList<>();
        for (ResourceLock lock : locks) {
            if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lock.key(), lock.token(), LOCK_TTL))) {
                releaseLocks(acquired);
                throw new ConflictException("BOOKING_CONFLICT", "Resource " + lock.key().substring("lock:resource:".length())
                        + " is currently being planned by another manager");
            }
            acquired.add(lock);
        }
        return acquired;
    }

    private void registerReleaseAfterTransaction(LockLease lease) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            lease.close();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                lease.close();
            }
        });
    }

    private void releaseLocks(List<ResourceLock> locks) {
        locks.forEach(lock -> {
            try {
                redis.execute(RELEASE_LOCK, List.of(lock.key()), lock.token());
            } catch (RuntimeException ignored) {
                // Renewal and the five-second TTL are the fallback if Redis becomes unavailable during cleanup.
            }
        });
    }

    private record ResourceLock(String key, String token) {
    }

    private final class LockLease implements AutoCloseable {
        private final List<ResourceLock> locks;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final ScheduledFuture<?> renewal;

        private LockLease(List<ResourceLock> locks) {
            this.locks = locks;
            this.renewal = lockRenewer.scheduleAtFixedRate(this::renew, LOCK_RENEWAL_INTERVAL.toMillis(),
                    LOCK_RENEWAL_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
        }

        private void renew() {
            if (closed.get()) {
                return;
            }
            locks.forEach(lock -> {
                try {
                    redis.execute(RENEW_LOCK, List.of(lock.key()), lock.token(),
                            String.valueOf(LOCK_TTL.toMillis()));
                } catch (RuntimeException ignored) {
                    // The lock remains guarded by its TTL if Redis is briefly unavailable.
                }
            });
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                renewal.cancel(false);
                releaseLocks(locks);
            }
        }
    }

    private record ResourceRef(String id, ResourceKind kind, String type, String label) {
        String lockId() {
            return kind.name() + ":" + id;
        }

        ResourceBooking toBooking(String roundId, Instant start, Instant end) {
            if (kind == ResourceKind.WORKER) {
                return ResourceBooking.forWorker(id, roundId, start, end);
            }
            if (kind == ResourceKind.TEAM_LEADER) {
                return ResourceBooking.forTeamLeader(id, roundId, start, end);
            }
            return ResourceBooking.forEquipment(id, EquipmentType.valueOf(type), roundId, start, end);
        }
    }
}

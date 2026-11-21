package tn.oliveconnect.resource;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.oliveconnect.common.BusinessRuleException;
import tn.oliveconnect.common.ConflictException;
import tn.oliveconnect.common.NotFoundException;
import tn.oliveconnect.common.PageResponse;

/** Supply orders of nets and shakers (FR-3.5). */
@Service
public class SupplyService {

    private final SupplyOrderRepository orderRepository;
    private final EquipmentRepository equipmentRepository;
    private final MongoTemplate mongoTemplate;

    public SupplyService(SupplyOrderRepository orderRepository, EquipmentRepository equipmentRepository,
                         MongoTemplate mongoTemplate) {
        this.orderRepository = orderRepository;
        this.equipmentRepository = equipmentRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /** Newest first. "status" is optional. */
    public PageResponse<SupplyOrderDto> list(SupplyStatus status, int page, int size) {
        Query filter = new Query();
        if (status != null) {
            filter.addCriteria(Criteria.where("status").is(status));
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "orderedAt").and(Sort.by("id")));
        List<SupplyOrder> orders = mongoTemplate.find(Query.of(filter).with(pageable), SupplyOrder.class);
        Page<SupplyOrder> result = PageableExecutionUtils.getPage(orders, pageable,
                () -> mongoTemplate.count(Query.of(filter).limit(-1).skip(-1), SupplyOrder.class));
        Map<String, Equipment> tools = equipmentRepository
                .findAllById(orders.stream().map(SupplyOrder::getToolId).distinct().toList()).stream()
                .collect(Collectors.toMap(Equipment::getId, Function.identity()));
        return PageResponse.of(result, order -> SupplyOrderDto.from(order, asTool(tools.get(order.getToolId()))));
    }

    public SupplyOrderDto create(SupplyOrderCreateDto dto) {
        Tool tool = findTool(dto.toolId());
        SupplyOrder order = new SupplyOrder();
        order.setToolId(tool.getId());
        order.setQuantity(dto.quantity());
        order.setSupplier(dto.supplier().trim());
        order.setOrderedAt(LocalDate.now());
        order.setExpectedAt(dto.expectedAt());
        return SupplyOrderDto.from(orderRepository.save(order), tool);
    }

    /** Marks the order received and adds its quantity to the stock, in one transaction. */
    @Transactional
    public SupplyOrderDto receive(String id) {
        SupplyOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Supply order not found"));
        if (order.getStatus() == SupplyStatus.RECEIVED) {
            throw new ConflictException("SUPPLY_ALREADY_RECEIVED", "This supply order was already received");
        }
        order.receive(LocalDate.now());
        orderRepository.save(order);
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(order.getToolId())),
                new Update().inc("stockQuantity", order.getQuantity()), Equipment.class);
        return SupplyOrderDto.from(order, asTool(equipmentRepository.findById(order.getToolId()).orElse(null)));
    }

    private Tool findTool(String toolId) {
        Equipment equipment = equipmentRepository.findById(toolId)
                .orElseThrow(() -> new NotFoundException("Tool not found"));
        if (!(equipment instanceof Tool tool)) {
            throw new BusinessRuleException("NOT_A_TOOL", "Only nets and shakers can be ordered");
        }
        return tool;
    }

    private Tool asTool(Equipment equipment) {
        return equipment instanceof Tool tool ? tool : null;
    }
}

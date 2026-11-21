package tn.oliveconnect.mill;

import java.util.List;
import org.springframework.stereotype.Service;
import tn.oliveconnect.common.NotFoundException;

@Service
public class MillService {

    private final MillRepository millRepository;

    public MillService(MillRepository millRepository) {
        this.millRepository = millRepository;
    }

    public List<MillDto> list() {
        return millRepository.findAllByOrderByNameAsc().stream().map(MillDto::from).toList();
    }

    public Mill find(String id) {
        return millRepository.findById(id).orElseThrow(() -> new NotFoundException("Mill not found"));
    }
}

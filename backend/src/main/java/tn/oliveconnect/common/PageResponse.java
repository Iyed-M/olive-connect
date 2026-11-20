package tn.oliveconnect.common;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Pagination envelope of the API (docs/06): {content, page, size, totalElements}. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }
}

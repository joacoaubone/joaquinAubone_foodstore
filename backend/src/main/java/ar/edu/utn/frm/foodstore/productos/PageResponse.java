package ar.edu.utn.frm.foodstore.productos;

import java.util.List;

public record PageResponse<T>(List<T> items, long total, int page, int size) {

    public PageResponse {
        items = List.copyOf(items);
    }
}

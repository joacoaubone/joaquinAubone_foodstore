package ar.edu.utn.frm.foodstore.productos;

import java.util.Optional;

public interface ProductCatalogCache {

    Optional<PageResponse<ProductRead>> get(int page, int size);

    void put(int page, int size, PageResponse<ProductRead> products);
}

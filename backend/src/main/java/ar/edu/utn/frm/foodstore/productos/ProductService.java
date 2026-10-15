package ar.edu.utn.frm.foodstore.productos;

import java.math.BigDecimal;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final StockMovementService stockMovementService;
    private final ProductCatalogCache productCatalogCache;

    public ProductService(
            ProductRepository productRepository,
            StockMovementService stockMovementService,
            ProductCatalogCache productCatalogCache) {
        this.productRepository = productRepository;
        this.stockMovementService = stockMovementService;
        this.productCatalogCache = productCatalogCache;
    }

    @Transactional
    public ProductRead create(ProductCreateRequest request) {
        boolean available = request.disponible() == null || request.disponible();
        long productId = productRepository.create(request.nombre(), request.precio(), available);
        if (request.stockCantidad() > 0) {
            stockMovementService.applyManualMovement(
                    new StockMovementRequest(productId, "ENTRADA", request.stockCantidad()));
        }
        return productRepository.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
    }

    @Transactional
    public ProductRead update(long productId, ProductUpdateRequest request) {
        ProductRead current = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        String name = request.nombre() == null ? current.nombre() : request.nombre().trim();
        BigDecimal price = request.precio() == null ? current.precio() : request.precio();
        boolean available = request.disponible() == null ? current.disponible() : request.disponible();
        productRepository.update(productId, name, price, available);
        return productRepository.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductRead> list(int page, int size) {
        if (page < 0 || size <= 0) {
            throw new InvalidPaginationException(page, size);
        }
        try {
            var cached = productCatalogCache.get(page, size);
            if (cached.isPresent()) {
                return cached.get();
            }
        } catch (DataAccessException exception) {
            // Redis is an accelerator; a failure falls through to PostgreSQL.
        }

        PageResponse<ProductRead> products = productRepository.findPage(page, size);
        try {
            productCatalogCache.put(page, size, products);
        } catch (DataAccessException exception) {
            // A cache write failure does not invalidate the database-backed response.
        }
        return products;
    }
}

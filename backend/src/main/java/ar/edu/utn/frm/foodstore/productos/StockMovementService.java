package ar.edu.utn.frm.foodstore.productos;

import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockMovementService {

    private final StockMovementRepository repository;

    public StockMovementService(StockMovementRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public StockMovementRead applyManualMovement(StockMovementRequest request) {
        StockMovementRepository.LockedProduct product = repository.lockProduct(request.productoId())
                .orElseThrow(() -> new ProductNotFoundException(request.productoId()));
        int quantity = Objects.requireNonNull(request.cantidad());
        long newStock = (long) product.stockQuantity() + quantity;
        if (newStock < 0 || newStock > Integer.MAX_VALUE) {
            throw new InsufficientStockException(product.id());
        }

        StockMovementRead movement = repository.insertMovement(
                product.id(), request.tipo(), quantity);
        repository.updateStock(product.id(), (int) newStock);
        return movement;
    }

    @Transactional(readOnly = true)
    public PageResponse<StockMovementRead> list(Long productId, int page, int size) {
        if (page < 0 || size <= 0) {
            throw new InvalidPaginationException(page, size);
        }
        return repository.findPage(productId, page, size);
    }
}

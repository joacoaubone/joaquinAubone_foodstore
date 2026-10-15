package ar.edu.utn.frm.foodstore.productos;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(long productId) {
        super("No existe el producto " + productId);
    }
}

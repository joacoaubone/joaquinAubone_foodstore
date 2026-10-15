package ar.edu.utn.frm.foodstore.catalogos;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleCatalogService {

    private final RoleCatalogRepository repository;

    public RoleCatalogService(RoleCatalogRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<String> listRoleCodes() {
        return repository.findRoleCodes();
    }
}

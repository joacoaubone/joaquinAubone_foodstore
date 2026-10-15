package ar.edu.utn.frm.foodstore.catalogos;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleCatalogController {

    private final RoleCatalogService roleCatalogService;

    public RoleCatalogController(RoleCatalogService roleCatalogService) {
        this.roleCatalogService = roleCatalogService;
    }

    @GetMapping
    public List<String> listRoleCodes() {
        return roleCatalogService.listRoleCodes();
    }
}

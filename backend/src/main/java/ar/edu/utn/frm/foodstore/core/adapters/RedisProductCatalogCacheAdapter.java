package ar.edu.utn.frm.foodstore.core.adapters;

import ar.edu.utn.frm.foodstore.productos.PageResponse;
import ar.edu.utn.frm.foodstore.productos.ProductCatalogCache;
import ar.edu.utn.frm.foodstore.productos.ProductRead;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RedisProductCatalogCacheAdapter implements ProductCatalogCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisProductCatalogCacheAdapter.class);
    private static final String KEY_PREFIX = "fs:productos:list:";
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final JavaType pageType;

    public RedisProductCatalogCacheAdapter(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.pageType = objectMapper.getTypeFactory().constructParametricType(PageResponse.class, ProductRead.class);
    }

    @Override
    public Optional<PageResponse<ProductRead>> get(int page, int size) {
        try {
            String json = redisTemplate.opsForValue().get(key(page, size));
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, pageType));
        } catch (DataAccessException | JsonProcessingException exception) {
            LOGGER.warn("No se pudo leer la caché de productos; se consultará PostgreSQL", exception);
            return Optional.empty();
        }
    }

    @Override
    public void put(int page, int size, PageResponse<ProductRead> products) {
        try {
            redisTemplate.opsForValue().set(key(page, size), objectMapper.writeValueAsString(products), CACHE_TTL);
        } catch (DataAccessException | JsonProcessingException exception) {
            LOGGER.warn("No se pudo escribir la caché de productos; se conserva la respuesta PostgreSQL", exception);
        }
    }

    private static String key(int page, int size) {
        return KEY_PREFIX + page + ":" + size;
    }
}

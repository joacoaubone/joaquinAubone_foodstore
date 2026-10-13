INSERT INTO rol (codigo)
VALUES ('ADMIN'), ('STOCK'), ('PEDIDOS'), ('CLIENTE');

INSERT INTO estado_pedido (codigo, orden, es_terminal)
VALUES
    ('PENDIENTE', 1, FALSE),
    ('CONFIRMADO', 2, FALSE),
    ('EN_PREP', 3, FALSE),
    ('ENTREGADO', 4, TRUE),
    ('CANCELADO', NULL, TRUE);

INSERT INTO forma_pago (codigo, habilitada)
VALUES
    ('TRANSFERENCIA', TRUE),
    ('TARJETA', TRUE),
    ('EFECTIVO', FALSE);

INSERT INTO usuario (email, password_hash)
VALUES ('admin@example.test', '${admin_password_hash}');

INSERT INTO usuario_rol (usuario_id, rol_codigo)
SELECT id, 'ADMIN'
FROM usuario
WHERE email = 'admin@example.test';

-- insert admin (username a, password aa)
INSERT INTO IWUser (id, enabled, roles, username, password)
VALUES (1, TRUE, 'ADMIN,USER', 'a',
    '{bcrypt}$2a$10$2BpNTbrsarbHjNsUWgzfNubJqBRf.0Vz9924nRSHBqlbPKerkgX.W');
INSERT INTO IWUser (id, enabled, roles, username,money, password)
VALUES (2, TRUE, 'USER', 'b', 1000,
    '{bcrypt}$2a$10$2BpNTbrsarbHjNsUWgzfNubJqBRf.0Vz9924nRSHBqlbPKerkgX.W');

-- start id numbering from a value that is larger than any assigned above
ALTER SEQUENCE "PUBLIC"."GEN" RESTART WITH 1024;

--Poblamos la base de datos
-- Insertar Facultades
INSERT INTO Facultad (id, nombre) VALUES (1, 'Facultad de Informática');
INSERT INTO Facultad (id, nombre) VALUES (2, 'Facultad de Derecho');
INSERT INTO Facultad (id, nombre) VALUES (3, 'Facultad de Telecomunicaciones');
INSERT INTO Facultad (id, nombre) VALUES (4, 'Facultad de Medicina');
INSERT INTO Facultad (id, nombre) VALUES (5, 'Facultad de Biología');
INSERT INTO Facultad (id, nombre) VALUES (6, 'Facultad de Filosofía');


-- Insertar Platos
-- Nota: 'activo' es booleano (TRUE/FALSE) y 'precio' es double
INSERT INTO Plato (id, nombre, descripcion, imagen, activo, precio) 
VALUES (100, 'Hamburguesa', 'Hamburguesa con queso', 'Hamburguesa-clasica.png', TRUE, 5.50);
INSERT INTO Plato (id, nombre, descripcion, imagen, activo, precio) 
VALUES (101, 'Ensalada Cesar', 'Ensalada fresca', 'Ensalada-Cesar.png', TRUE, 4.20);

INSERT INTO Plato (id, nombre, descripcion, imagen, activo, precio) 
VALUES (102, 'Pizza Margarita', 'Pizza con queso tomate y oregano', 'Pizza-Margarita.png', TRUE, 8);

INSERT INTO Plato (id, nombre, descripcion, imagen, activo, precio) 
VALUES (103, 'Bocadillo Lomo', 'Tu bocadillo de toda la vida', 'Bocadillo-Lomo.png', TRUE, 4.8);

INSERT INTO Plato (id, nombre, descripcion, imagen, activo, precio) 
VALUES (104, 'Tortilla de Patatas', 'EL clasico pincho de tortilla', 'Tortilla-papa.png', TRUE, 1.65);

-- Relacionar platos con facultades (tabla intermedia plato_facultades)
INSERT INTO plato_facultades (plato_id, facultad_id) VALUES (100, 1);
INSERT INTO plato_facultades (plato_id, facultad_id) VALUES (101, 1);
INSERT INTO plato_facultades (plato_id, facultad_id) VALUES (101, 2);
INSERT INTO plato_facultades (plato_id, facultad_id) VALUES (102, 2);
INSERT INTO plato_facultades (plato_id, facultad_id) VALUES (103, 3);
INSERT INTO plato_facultades (plato_id, facultad_id) VALUES (104, 6);
INSERT INTO plato_facultades (plato_id, facultad_id) VALUES (103, 5);

-- Votos de los platos

INSERT INTO plato_votos (plato_id, votos) VALUES (100, 5);
INSERT INTO plato_votos (plato_id, votos) VALUES (100, 4);
INSERT INTO plato_votos (plato_id, votos) VALUES (100, 5);
-- insert admin (username a, password aa)
INSERT INTO IWUser (id, enabled, roles, username, password)
VALUES (1, TRUE, 'ADMIN,USER', 'a',
    '{bcrypt}$2a$10$2BpNTbrsarbHjNsUWgzfNubJqBRf.0Vz9924nRSHBqlbPKerkgX.W');
INSERT INTO IWUser (id, enabled, roles, username,money, password)
VALUES (2, TRUE, 'USER', 'b', 1000,
    '{bcrypt}$2a$10$2BpNTbrsarbHjNsUWgzfNubJqBRf.0Vz9924nRSHBqlbPKerkgX.W');

-- insert gestor de cafeteria (username c, password aa)
INSERT INTO IWUser (id, enabled, roles, username, password)
VALUES (3, TRUE, 'USER,GESTOR_CAFETERIA', 'c',
    '{bcrypt}$2a$10$2BpNTbrsarbHjNsUWgzfNubJqBRf.0Vz9924nRSHBqlbPKerkgX.W');

-- start id numbering from a value that is larger than any assigned above
ALTER SEQUENCE "PUBLIC"."GEN" RESTART WITH 1024;

--Poblamos la base de datos
-- Insertar Facultades
INSERT INTO Facultad (id, nombre, ubicacion, descripcion, horario, aforo) 
VALUES (1, 'Facultad de Informática', 'Calle del Prof. José García Santesmases, 9', 'Menús variados, opciones veganas y amplia zona de mesas.', '08:30 - 19:30', 'Bajo');

INSERT INTO Facultad (id, nombre, ubicacion, descripcion, horario, aforo) 
VALUES (2, 'Facultad de Derecho', 'Plaza de Menéndez Pelayo, 4', 'Cafetería clásica con terraza exterior y servicio de desayunos.', '08:00 - 20:00', 'Medio');

INSERT INTO Facultad (id, nombre, ubicacion, descripcion, horario, aforo) 
VALUES (3, 'Facultad de Telecomunicaciones', 'Avenida Complutense, 30', 'Rápido servicio de cafetería y bocadillos recién hechos.', '08:30 - 18:30', 'Bajo');

INSERT INTO Facultad (id, nombre, ubicacion, descripcion, horario, aforo) 
VALUES (4, 'Facultad de Medicina', 'Plaza de Ramón y Cajal', 'Especialidad en bocadillos y platos combinados a muy buenos precios.', '08:00 - 18:00', 'Alto');

INSERT INTO Facultad (id, nombre, ubicacion, descripcion, horario, aforo) 
VALUES (5, 'Facultad de Biología', 'Calle José Antonio Novais, 12', 'Ambiente tranquilo y opciones de comida saludable.', '09:00 - 19:00', 'Medio');

INSERT INTO Facultad (id, nombre, ubicacion, descripcion, horario, aforo) 
VALUES (6, 'Facultad de Filosofía', 'Plaza de Menéndez Pelayo, s/n', 'La mejor selección de café y bollería artesana.', '08:30 - 20:30', 'Bajo');


-- Insertar Platos
INSERT INTO Plato (id, nombre, descripcion, activo, precio) 
VALUES (100, 'Hamburguesa', 'Hamburguesa con queso', TRUE, 5.50);
INSERT INTO Plato (id, nombre, descripcion, activo, precio) 
VALUES (101, 'Ensalada Cesar', 'Ensalada fresca', TRUE, 4.20);

INSERT INTO Plato (id, nombre, descripcion, activo, precio) 
VALUES (102, 'Pizza Margarita', 'Pizza con queso tomate y oregano', TRUE, 8);

INSERT INTO Plato (id, nombre, descripcion, activo, precio) 
VALUES (103, 'Bocadillo Lomo', 'Tu bocadillo de toda la vida', TRUE, 4.8);

INSERT INTO Plato (id, nombre, descripcion, activo, precio) 
VALUES (104, 'Tortilla de Patatas', 'EL clasico pincho de tortilla', TRUE, 1.65);

-- Relacionar platos con facultades (tabla intermedia plato_facultades)
INSERT INTO plato_facultades (plato_id, facultad_id) VALUES (100, 1);
INSERT INTO plato_facultades (plato_id, facultad_id) VALUES (101, 1);
INSERT INTO plato_facultades (plato_id, facultad_id) VALUES (101, 2);
INSERT INTO plato_facultades (plato_id, facultad_id) VALUES (102, 2);
INSERT INTO plato_facultades (plato_id, facultad_id) VALUES (103, 3);
INSERT INTO plato_facultades (plato_id, facultad_id) VALUES (104, 6);
INSERT INTO plato_facultades (plato_id, facultad_id) VALUES (103, 5);

-- Votos de los platos

INSERT INTO valoracion (id, puntuacion, plato_id, facultad_id, user_id) VALUES (200, 5, 100, 1, 1);
INSERT INTO valoracion (id, puntuacion, plato_id, facultad_id, user_id) VALUES (201, 4, 100, 1, 2);
INSERT INTO valoracion (id, puntuacion, plato_id, facultad_id, user_id) VALUES (202, 5, 100, 1, 3);
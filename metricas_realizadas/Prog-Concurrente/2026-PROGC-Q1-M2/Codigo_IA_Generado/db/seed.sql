-- =============================================================================
-- Seed: test users
-- Passwords are bcrypt hashes of the value shown in the comment.
-- Generate new hashes with: python -c "from passlib.context import CryptContext; print(CryptContext(['bcrypt']).hash('PASSWORD'))"
-- =============================================================================

INSERT INTO users (username, password_hash, email, full_name) VALUES
-- password: admin123
('admin',     '$2b$12$iaVzpULObLsneD0VI5gQPOZ9QQ5azGJOghPET3SgQworyvjhBYzUm', 'admin@ticketsystem.com',    'Administrador'),
-- password: test123
('test_user', '$2b$12$RhGpZmGEaXP9EQPEh0KetOGghJFM/M1j6S50Z9tfrFMwk4.mJUJP6', 'testuser@example.com',      'Usuario de Prueba'),
('testuser1', '$2b$12$RhGpZmGEaXP9EQPEh0KetOGghJFM/M1j6S50Z9tfrFMwk4.mJUJP6', 'user1@example.com',         'Usuario de Prueba 1'),
('testuser2', '$2b$12$RhGpZmGEaXP9EQPEh0KetOGghJFM/M1j6S50Z9tfrFMwk4.mJUJP6', 'user2@example.com',         'Usuario de Prueba 2'),
('testuser3', '$2b$12$RhGpZmGEaXP9EQPEh0KetOGghJFM/M1j6S50Z9tfrFMwk4.mJUJP6', 'user3@example.com',         'Usuario de Prueba 3'),
('testuser4', '$2b$12$RhGpZmGEaXP9EQPEh0KetOGghJFM/M1j6S50Z9tfrFMwk4.mJUJP6', 'user4@example.com',         'Usuario de Prueba 4'),
('testuser5', '$2b$12$RhGpZmGEaXP9EQPEh0KetOGghJFM/M1j6S50Z9tfrFMwk4.mJUJP6', 'user5@example.com',         'Usuario de Prueba 5'),
('testuser6', '$2b$12$RhGpZmGEaXP9EQPEh0KetOGghJFM/M1j6S50Z9tfrFMwk4.mJUJP6', 'user6@example.com',         'Usuario de Prueba 6'),
('testuser7', '$2b$12$RhGpZmGEaXP9EQPEh0KetOGghJFM/M1j6S50Z9tfrFMwk4.mJUJP6', 'user7@example.com',         'Usuario de Prueba 7'),
('testuser8', '$2b$12$RhGpZmGEaXP9EQPEh0KetOGghJFM/M1j6S50Z9tfrFMwk4.mJUJP6', 'user8@example.com',         'Usuario de Prueba 8'),
('testuser9', '$2b$12$RhGpZmGEaXP9EQPEh0KetOGghJFM/M1j6S50Z9tfrFMwk4.mJUJP6', 'user9@example.com',         'Usuario de Prueba 9'),
('testuser10','$2b$12$RhGpZmGEaXP9EQPEh0KetOGghJFM/M1j6S50Z9tfrFMwk4.mJUJP6', 'user10@example.com',        'Usuario de Prueba 10'),
-- password: kevin123
('kevin',     '$2b$12$aLoEIhmpqic6EkPwC5Kwi.0afLkwDNIJAMaMetm1tkr4gWsak7Ft6', 'kevin@example.com',         'Kevin Arias')
ON CONFLICT (username) DO NOTHING;

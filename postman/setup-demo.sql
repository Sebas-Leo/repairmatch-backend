-- Ejecutar en la base de desarrollo antes del primer Run en un equipo nuevo.
INSERT INTO appliance_types (name, description)
VALUES ('Lavadora Postman', 'Dato local para pruebas Postman')
ON CONFLICT (name) DO NOTHING;
SELECT id AS appliance_type_id FROM appliance_types WHERE name = 'Lavadora Postman';

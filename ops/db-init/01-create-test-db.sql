-- Separate database for gate-service integration tests (*IT), so tests never touch demo data in "gate".
CREATE DATABASE gate_test OWNER poc;

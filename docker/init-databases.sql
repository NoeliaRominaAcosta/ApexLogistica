-- Script de inicialización para crear las bases de datos de cada microservicio en PostgreSQL 16
CREATE DATABASE warehouse_db;
CREATE DATABASE dispatch_db;

\c warehouse_db;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

\c dispatch_db;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

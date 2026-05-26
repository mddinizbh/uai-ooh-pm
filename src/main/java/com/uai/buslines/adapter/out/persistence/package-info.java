/**
 * Persistence adapter — JPA entities, Spring Data repositories, and mappers.
 *
 * <p>Implements the driven ports defined in {@code domain.port.out}.
 * JPA entity fields MUST match {@code V1__create_tables.sql} exactly
 * (hibernate.ddl-auto=validate enforces this at startup).
 * Populated in task_04.
 */
package com.uai.buslines.adapter.out.persistence;

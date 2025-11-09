-- V5__accounts_consents.sql
CREATE TABLE accounts_consents (
                                       user_id        BIGINT      NOT NULL,
                                       bank           VARCHAR(32) NOT NULL,
                                       client_id      VARCHAR(128) NOT NULL,
                                       consent_id     VARCHAR(128) NOT NULL,
                                       status         VARCHAR(32)  NOT NULL,
                                       created_at     TIMESTAMP    NOT NULL DEFAULT now(),

                                       CONSTRAINT pk_accounts_consents PRIMARY KEY (user_id, bank)
);

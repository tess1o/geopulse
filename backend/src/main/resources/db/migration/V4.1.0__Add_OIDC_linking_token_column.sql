-- Add linking_token column to support OIDC account linking verification flows
-- This column stores the linking token during OIDC-to-OIDC verification scenarios
ALTER TABLE oidc_session_states 
ADD COLUMN linking_token VARCHAR(255);
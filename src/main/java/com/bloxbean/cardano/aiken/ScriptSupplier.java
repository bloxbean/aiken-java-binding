package com.bloxbean.cardano.aiken;

import com.bloxbean.cardano.client.plutus.spec.PlutusScript;

/**
 * Interface for supplying Plutus scripts
 */
public interface ScriptSupplier {
    PlutusScript getScript(String scriptHash);
}

package com.bloxbean.cardano.aiken;

import com.bloxbean.cardano.aiken.exception.ApplyParamException;
import com.bloxbean.cardano.aiken.jna.CardanoJNAUtil;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.fasterxml.jackson.databind.JsonNode;

import static com.bloxbean.cardano.client.util.JsonUtil.parseJson;

/**
 * Utility class to apply params to a plutus script
 */
public class AikenScriptUtil {

    /**
     * Apply params to a plutus script (parametrized contract)
     * @param params List of PlutusData
     * @param compiledCode compiled code of parametrized contract
     * @return compiled code of the contract after applying params
     */
    public static String applyParamToScript(ListPlutusData params, String compiledCode) {
        try {
            String json = CardanoJNAUtil.apply_params_to_plutus_script(params.serializeToHex(), compiledCode);

            JsonNode node = parseJson(json);
            String status = node.get("status").asText();

            if (status.equalsIgnoreCase("SUCCESS")) {
                return node.get("compiled_code").asText();
            }
            throw new ApplyParamException(node.get("error").asText());
        } catch (ApplyParamException ex) {
            throw ex;
        } catch (Exception e) {
            throw new ApplyParamException("Error applying param to script", e);
        }
    }
}

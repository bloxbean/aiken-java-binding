package com.bloxbean.cardano.aiken.tx.evaluator;

import com.bloxbean.cardano.aiken.jna.CardanoJNAUtil;
import com.bloxbean.cardano.aiken.tx.evaluator.exception.TxEvaluationException;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.plutus.spec.CostMdls;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.bloxbean.cardano.aiken.tx.evaluator.AikenCbors.*;
import static com.bloxbean.cardano.client.util.HexUtil.encodeHexString;
import static com.bloxbean.cardano.client.util.JsonUtil.parseJson;

/**
 * Evaluate script costs for a transaction using two phase eval from Aiken.
 */
@Slf4j
public class TxEvaluator {

    private final SlotConfig.SlotConfigByReference slotConfig;
    private final InitialBudgetConfig.InitialBudgetByValue initialBudgetConfig;

    /**
     * Construct instance of TxEvaluator, this is a typical work flow, values from here represent defaults
     * On Cardano's main-net
     */
    public TxEvaluator() {
        this.slotConfig = getDefaultSlotConfig();
        this.initialBudgetConfig = getDefaultInitialBudgetConfig();
    }

    /**
     * Construct instance of TxEvaluator with an initial transaction budget config.
     * Those values are typically present on protocol-parameters.json.
     *
     * "maxTxExecutionUnits": {
     *   "memory": 14000000,
     *   "steps": 10000000000
     *  }
     *
     *  Typical scenario to override those values is where one uses L2 solution, e.g. Hydra
     *  with custom config or custom dev network (e.g. using yaci-devkit).
     *
     * @param initialBudgetConfig max transaction execution units
     */
    public TxEvaluator(InitialBudgetConfig initialBudgetConfig) {
        this.slotConfig = getDefaultSlotConfig();

        this.initialBudgetConfig = new InitialBudgetConfig.InitialBudgetByValue();
        this.initialBudgetConfig.mem = initialBudgetConfig.mem;
        this.initialBudgetConfig.cpu = initialBudgetConfig.cpu;
    }

    /**
     * Construct instance of TxEvaluator with an initial transaction budget config.
     * Those values are typically present on
     *
     * protocol-parameters.json:
     * * "maxTxExecutionUnits": {
     *   "memory": 14000000,
     *   "steps": 10000000000
     *  }
     *
     *  shelley-genesis.json:
     *  "zero_time": 1660003200000L,
     *  "zero_slot": 0,
     *  "slot_length: 1000
     *
     *  Typical scenario to override those values is where one uses L2 solution, e.g. Hydra
     *  with custom config or custom dev network (e.g. using yaci-devkit).
     *
     * @param initialBudgetConfig - max transaction execution units
     * @param slotConfig - slot config values as specified during shelley genesis era
     */
    public TxEvaluator(SlotConfig slotConfig,
                       InitialBudgetConfig initialBudgetConfig) {
        this.slotConfig = new SlotConfig.SlotConfigByReference();
        this.slotConfig.zero_slot = slotConfig.zero_slot;
        this.slotConfig.zero_time = slotConfig.zero_time;
        this.slotConfig.slot_length = slotConfig.slot_length;

        this.initialBudgetConfig = new InitialBudgetConfig.InitialBudgetByValue();
        this.initialBudgetConfig.mem = initialBudgetConfig.mem;
        this.initialBudgetConfig.cpu = initialBudgetConfig.cpu;
    }

    /**
     * Evaluate script costs for a transaction
     * @param transaction Transaction
     * @param inputUtxos List of utxos used in transaction inputs
     * @param costMdls Cost models
     * @return List of {@link Redeemer} with estimated script costs as {@link com.bloxbean.cardano.client.plutus.spec.ExUnits}
     * @throws TxEvaluationException if script evaluation fails
     */
    public List<Redeemer> evaluateTx(Transaction transaction, Set<Utxo> inputUtxos, CostMdls costMdls) {
        List<PlutusScript> scripts = new ArrayList<>();
        scripts.addAll(transaction.getWitnessSet().getPlutusV1Scripts());
        scripts.addAll(transaction.getWitnessSet().getPlutusV2Scripts());

        return evaluateTx(transaction, inputUtxos, scripts, costMdls);
    }

    /**
     * Evaluate script costs for a transaction
     * @param transaction Transaction
     * @param inputUtxos List utxos used in transaction inputs
     * @param scripts Plutus Scripts in transaction
     * @param costMdls Cost models
     * @return List of {@link Redeemer} with estimated script costs as {@link com.bloxbean.cardano.client.plutus.spec.ExUnits}
     * @throws TxEvaluationException if script evaluation fails
     */
    public List<Redeemer> evaluateTx(Transaction transaction, Set<Utxo> inputUtxos, List<PlutusScript> scripts, CostMdls costMdls) {
        List<TransactionInput> txInputs = transaction.getBody().getInputs();
        List<TransactionInput> refTxInputs = transaction.getBody().getReferenceInputs();
        List<TransactionInput> allInputs = Stream.concat(txInputs.stream(),refTxInputs.stream()).collect(Collectors.toList());
        List<TransactionOutput> txOutputs = resolveTxInputs(allInputs, inputUtxos, scripts);

        try {
            String costMdlsHex = encodeHexString(CborSerializationUtil.serialize(costMdls.serialize()));
            String trxCbor = transaction.serializeToHex();
            String inputsCbor = encodeHexString(CborSerializationUtil.serialize(serialiseInputs(allInputs)));
            String outputsCbor = encodeHexString(CborSerializationUtil.serialize(serialiseOutputs(txOutputs)));

            String json = CardanoJNAUtil.eval_phase_two_raw(
                    trxCbor,
                    inputsCbor,
                    outputsCbor,
                    costMdlsHex,
                    initialBudgetConfig,
                    slotConfig
            );

            if (log.isTraceEnabled()) {
                log.trace("Transaction: " + trxCbor);
                log.trace("Inputs : " + inputsCbor);
                log.trace("Outputs : " + outputsCbor);
                log.trace("CostMdlsHex : " + costMdlsHex);
            }

            JsonNode node = parseJson(json);
            String status = node.get("status").asText();

            if (status.equalsIgnoreCase("SUCCESS")) {
                return deserializeRedeemerArray(node.get("redeemer_cbor").asText());
            }

            throw new TxEvaluationException(node.get("error").asText());
        } catch (TxEvaluationException e) {
            throw e;
        // catch all
        } catch (Exception e) {
            throw new TxEvaluationException("TxEvaluation failed", e);
        }
    }

    private static SlotConfig.SlotConfigByReference getDefaultSlotConfig() {
        SlotConfig.SlotConfigByReference slotConfig = new SlotConfig.SlotConfigByReference();
        slotConfig.zero_time = 1660003200000L;
        slotConfig.zero_slot = 0;
        slotConfig.slot_length = 1000;

        return slotConfig;
    }

    private static InitialBudgetConfig.InitialBudgetByValue getDefaultInitialBudgetConfig() {
        InitialBudgetConfig.InitialBudgetByValue initialBudgetConfig = new InitialBudgetConfig.InitialBudgetByValue();
        initialBudgetConfig.mem = 14000000L;
        initialBudgetConfig.cpu = 10000000000L;

        return initialBudgetConfig;
    }

    private List<TransactionOutput> resolveTxInputs(List<TransactionInput> transactionInputs, Set<Utxo> utxos, List<PlutusScript> plutusScripts) {
        return transactionInputs.stream().map(input -> {
            try {
                Utxo utxo = utxos.stream()
                        .filter(_utxo -> input.getTransactionId().equals(_utxo.getTxHash()))
                        .filter(_utxo -> input.getIndex() == _utxo.getOutputIndex())
                        .findFirst()
                        .orElseThrow();

                String address = utxo.getAddress();

                // Calculate script ref
                PlutusScript plutusScript = plutusScripts.stream().filter(script -> {
                    try {
                        return encodeHexString(script.getScriptHash()).equals(utxo.getReferenceScriptHash());
                    } catch (CborSerializationException e) {
                        throw new IllegalStateException(e);
                    }
                }).findFirst().orElse(null);

                PlutusData inlineDatum = utxo.getInlineDatum() != null ? PlutusData.deserialize(HexUtil.decodeHexString(utxo.getInlineDatum())) : null;
                byte[] datumHash = utxo.getDataHash() != null ? HexUtil.decodeHexString(utxo.getDataHash()) : null;

                return TransactionOutput.builder()
                        .address(address)
                        .value(utxo.toValue())
                        .datumHash(inlineDatum == null ? datumHash : null)
                        .inlineDatum(inlineDatum)
                        .scriptRef(plutusScript)
                        .build();
            } catch (CborDeserializationException e) {
                throw new IllegalStateException(e);
            }

        }).collect(Collectors.toList());
    }
}
